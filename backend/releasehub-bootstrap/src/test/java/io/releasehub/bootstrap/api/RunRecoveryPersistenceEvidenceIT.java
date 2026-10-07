package io.releasehub.bootstrap.api;

import io.releasehub.application.run.RunPort;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S1 验证历史隔离；事务外副作用证据仍保留给尚未实现的恢复切片。 */
@SpringBootTest
@ActiveProfiles("e2e")
class RunRecoveryPersistenceEvidenceIT {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("run_recovery_design").withUsername("recovery_test").withPassword("isolated-recovery-db");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired RunPort runPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void keepsBothRunsItemsWhenSavingTheSameScopeAgain() {
        Instant now = Instant.parse("2026-10-05T00:00:00Z");
        Run first = completedRun("operator-one", "RW-ID-EVIDENCE", now);
        Run second = completedRun("operator-two", "RW-ID-EVIDENCE", now.plusMillis(1));
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> runPort.save(first));
        transaction.executeWithoutResult(status -> runPort.save(second));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getItems().get(0).getId()).isNotEqualTo(second.getItems().get(0).getId());
        assertThat(jdbc.queryForObject("select count(*) from run_item where run_id = ?", Integer.class, first.getId().value()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from run_item where run_id = ?", Integer.class, second.getId().value()))
                .isEqualTo(1);
    }

    @Test
    void observesDatabaseRollbackWithoutUndoingAnExternalEffect() {
        Run invalid = completedRun(null, "RW-COMMIT-EVIDENCE", Instant.parse("2026-10-05T00:01:00Z"));
        var externalEffect = new AtomicBoolean(); // 代表数据库事务之外的副作用，不是真实 GitLab 写入。
        var transaction = new TransactionTemplate(transactions);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            externalEffect.set(true);
            runPort.save(invalid); // 非空约束触发真实持久化失败。
        })).isInstanceOf(RuntimeException.class);
        assertThat(externalEffect.get()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from run where id = ?", Integer.class, invalid.getId().value()))
                .isZero();
    }

    @Test
    void readsLegacyRunWithoutRewritingItsIdentifierOrProtocol() {
        Instant now = Instant.parse("2026-10-05T00:02:00Z");
        String legacyId = "WINDOW_ORCHESTRATION::1791158520000";
        Run legacy = Run.rehydrate(io.releasehub.domain.run.RunId.of(legacyId), RunType.WINDOW_ORCHESTRATION,
                "legacy", now, now, java.util.List.of(), now, now, 0);
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> runPort.save(legacy));
        jdbc.update("update run set execution_version = null where id = ?", legacyId);
        Run restored = transaction.execute(status -> runPort.findById(legacyId).orElseThrow());
        assertThat(restored).isNotNull();
        assertThat(restored.getId().value()).isEqualTo(legacyId);
        assertThat(restored.getExecutionVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select execution_version from run where id = ?", Integer.class, legacyId)).isNull();
    }

    private Run completedRun(String operator, String windowKey, Instant now) {
        Run run = Run.start(RunType.WINDOW_ORCHESTRATION, operator, now);
        RunItem item = RunItem.create(windowKey, RepoId.of("recovery-repo"), IterationKey.of("recovery-iteration"), 1, now);
        item.finishWith(RunItemResult.MERGED, now);
        run.addItem(item);
        run.finish(now);
        return run;
    }
}
