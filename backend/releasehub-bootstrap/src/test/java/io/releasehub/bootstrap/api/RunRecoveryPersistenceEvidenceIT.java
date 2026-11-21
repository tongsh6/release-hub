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

    @Test
    void migratesMixedV36ExecutionEvidenceOnPostgres() {
        verifyV37Backfill(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void migratesMixedV36ExecutionEvidenceOnH2() {
        verifyV37Backfill("jdbc:h2:mem:v37-" + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "sa");
    }

    private void verifyV37Backfill(String url, String username, String password) {
        String schema = "v37_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, username, password);
        var sql = new JdbcTemplate(source);
        try {
            org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/migration")
                    .schemas(schema).defaultSchema(schema).target("36").load().migrate();
            insertLegacyRun(sql, schema, "planned", "WINDOW_ORCHESTRATION", false, "PLANNED",
                    new String[][]{{"a", null, null, null}});
            insertLegacyRun(sql, schema, "running", "WINDOW_ORCHESTRATION", false, "COMPLETED",
                    new String[][]{{"a", "MERGED", "TRY_MERGE", "MERGED"}});
            insertLegacyRun(sql, schema, "done", "WINDOW_ORCHESTRATION", true, "SUCCESS",
                    new String[][]{{"a", "MERGED", "TRY_MERGE", "MERGED"}, {"b", "ALREADY_MERGED", "TRY_MERGE", "ALREADY_MERGED"}});
            insertLegacyRun(sql, schema, "partial", "WINDOW_ORCHESTRATION", true, "SUCCESS",
                    new String[][]{{"a", "MERGED", "TRY_MERGE", "MERGED"}, {"b", "MERGE_BLOCKED", "TRY_MERGE", "MERGE_BLOCKED"}});
            insertLegacyRun(sql, schema, "all-failed", "WINDOW_ORCHESTRATION", true, null,
                    new String[][]{{"a", "FAILED", "ENSURE_FEATURE", "FAILED"}, {"b", "MERGE_BLOCKED", "TRY_MERGE", "MERGE_BLOCKED"}});
            insertLegacyRun(sql, schema, "same-repo-partial", "WINDOW_ORCHESTRATION", true, "COMPLETED",
                    new String[][]{{"a", "MERGED", "TRY_MERGE", "MERGED"}, {"a", "MERGE_BLOCKED", "TRY_MERGE", "MERGE_BLOCKED"}});
            insertLegacyRun(sql, schema, "mixed-repositories", "WINDOW_ORCHESTRATION", true, null,
                    new String[][]{{"a", "MERGED", "TRY_MERGE", "MERGED"}, {"b", "MERGED", "TRY_MERGE", "MERGED"},
                            {"b", "SKIPPED_DUE_TO_BLOCK", "TRY_MERGE", "SKIPPED_DUE_TO_BLOCK"}});
            insertLegacyRun(sql, schema, "unknown-item", "WINDOW_ORCHESTRATION", true, "FAILED",
                    new String[][]{{"a", "UNKNOWN", "TRY_MERGE", "UNKNOWN"}});
            insertLegacyRun(sql, schema, "unknown-step", "WINDOW_ORCHESTRATION", true, "SUCCESS",
                    new String[][]{{"a", "MERGED", "TRY_MERGE", "UNKNOWN"}});
            insertLegacyRun(sql, schema, "failed-step", "WINDOW_ORCHESTRATION", true, "SUCCESS",
                    new String[][]{{"a", "MERGED", "ENSURE_FEATURE", "FAILED"}});
            insertLegacyRun(sql, schema, "empty", "WINDOW_ORCHESTRATION", true, "COMPLETED", new String[][]{});
            insertLegacyRun(sql, schema, "unfinished-item", "WINDOW_ORCHESTRATION", true, null,
                    new String[][]{{"a", null, null, null}});
            insertLegacyRun(sql, schema, "cleanup", "WINDOW_ORCHESTRATION", true, "SUCCESS",
                    new String[][]{{"a", "SUCCESS", "CREATE_TAG", "TAG_CREATED"}});
            insertLegacyRun(sql, schema, "attach", "ATTACH_ITERATION", true, null,
                    new String[][]{{"a", "MERGED", "TRY_MERGE", "MERGED"}});
            insertLegacyRun(sql, schema, "version", "VERSION_UPDATE", true, "COMPLETED",
                    new String[][]{{"a", "VERSION_UPDATE_SUCCESS", "UPDATE_VERSION", "VERSION_UPDATE_SUCCESS"}});

            org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/migration")
                    .schemas(schema).defaultSchema(schema).target("37").load().migrate();
            java.util.Map<String, String> actual = sql.query("select id, execution_status from " + schema + ".run", rs -> {
                var rows = new java.util.LinkedHashMap<String, String>();
                while (rs.next()) rows.put(rs.getString(1), rs.getString(2));
                return rows;
            });
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(java.util.Map.ofEntries(
                    java.util.Map.entry("planned", "PLANNED"), java.util.Map.entry("running", "RUNNING"),
                    java.util.Map.entry("done", "DONE"), java.util.Map.entry("partial", "PARTIAL_FAILED"),
                    java.util.Map.entry("all-failed", "FAILED"), java.util.Map.entry("same-repo-partial", "FAILED"),
                    java.util.Map.entry("mixed-repositories", "PARTIAL_FAILED"),
                    java.util.Map.entry("unknown-item", "NEEDS_REVIEW"), java.util.Map.entry("unknown-step", "NEEDS_REVIEW"),
                    java.util.Map.entry("failed-step", "FAILED"), java.util.Map.entry("empty", "FAILED"),
                    java.util.Map.entry("unfinished-item", "FAILED"), java.util.Map.entry("cleanup", "DONE"),
                    java.util.Map.entry("attach", "DONE"), java.util.Map.entry("version", "DONE")));
            assertThat(sql.queryForObject("select run_type from " + schema + ".run where id = 'cleanup'", String.class))
                    .isEqualTo("WINDOW_CLEANUP");
            assertThat(sql.queryForObject("select count(*) from " + schema + ".flyway_schema_history where version = '37' and success = true", Integer.class))
                    .isEqualTo(1);
            assertThatThrownBy(() -> sql.update("update " + schema + ".run set execution_status = null where id = 'done'"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void insertLegacyRun(JdbcTemplate sql, String schema, String id, String type, boolean finished,
                                 String executionStatus, String[][] items) {
        var now = java.sql.Timestamp.from(Instant.parse("2026-10-05T00:00:00Z"));
        sql.update("insert into " + schema + ".run (id, run_type, operator, started_at, finished_at, created_at, updated_at, version, execution_version, execution_status)"
                + " values (?, ?, 'migration-fixture', ?, ?, ?, ?, 0, 1, ?)", id, type, now, finished ? now : null, now, now, executionStatus);
        for (int index = 0; index < items.length; index++) {
            String[] item = items[index];
            String itemId = id + "-item-" + index;
            sql.update("insert into " + schema + ".run_item (id, run_id, window_key, repo_id, iteration_key, planned_order, executed_order, final_result, created_at, updated_at)"
                    + " values (?, ?, 'RW-MIGRATION', ?, ?, ?, ?, ?, ?, ?)", itemId, id, item[0], "ITER-" + index,
                    index + 1, "SKIPPED_DUE_TO_BLOCK".equals(item[1]) ? 0 : index + 1, item[1], now, now);
            if (item[2] != null) {
                sql.update("insert into " + schema + ".run_step (run_item_id, action_type, result, start_at, end_at, message)"
                        + " values (?, ?, ?, ?, ?, 'Legacy execution evidence')", itemId, item[2], item[3], now, now);
            }
        }
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
