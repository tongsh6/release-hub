package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.run.plan.RunPlanPort;
import io.releasehub.application.run.plan.SavedRunPlan;
import io.releasehub.bootstrap.ReleaseHubApplication;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunPlanSnapshot;
import io.releasehub.domain.iteration.BranchCreationMode;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@Import(RunPlanApiIT.PreciseClock.class)
class RunPlanApiIT {
    @TestConfiguration
    static class PreciseClock {
        @Bean @Primary
        Clock planClock() { return Clock.fixed(Instant.parse("2026-10-05T00:00:00.123456789Z"), ZoneOffset.UTC); }
    }
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("run_plan_test").withUsername("plan_test").withPassword("isolated-plan-db");
    static { DB.start(); }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }
    @AfterAll static void stopDatabase() { DB.stop(); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ReleaseWindowPort windows;
    @Autowired IterationPort iterations;
    @Autowired IterationRepoPort versions;
    @Autowired CodeRepositoryPort repos;
    @Autowired WindowIterationPort bindings;
    @MockBean GitLabPort gitLab;
    @MockBean GitLabFilePort gitFiles;
    @MockBean GitBranchAdapterFactory gitBranches;
    private String windowId, repoId, iterationKey, requestKey, token;

    @BeforeEach
    void fixtures() throws Exception {
        windowId = UUID.randomUUID().toString();
        repoId = UUID.randomUUID().toString();
        iterationKey = "ITER-" + UUID.randomUUID();
        requestKey = UUID.randomUUID().toString();
        Instant now = Instant.now();
        // 仅为本测试独占数据库准备持久化 fixture，不冒充前端用户旅程。
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            windows.save(ReleaseWindow.rehydrate(ReleaseWindowId.of(windowId), "RW-" + windowId,
                    "预览测试", "", now, "G001", ReleaseWindowStatus.DRAFT, now, now, false, null));
            repos.save(CodeRepository.rehydrate(RepoId.of(repoId), "预览仓库", "https://gitlab.invalid/acme/repo.git",
                    "main", "G001", RepoType.SERVICE, GitProvider.GITLAB, "secret-fixture-token", false,
                    0, 0, 0, 0, 0, 0, 0, null, now, now, 0));
            iterations.save(Iteration.rehydrate(IterationKey.of(iterationKey), "预览迭代", "", null,
                    "G001", Set.of(RepoId.of(repoId)), IterationStatus.ACTIVE, now, now));
            versions.saveWithVersion(iterationKey, repoId, "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                    "feature/original", "SYSTEM", now, BranchCreationMode.AUTO);
            bindings.attach(ReleaseWindowId.of(windowId), IterationKey.of(iterationKey), now);
            bindings.updateReleaseBranch(windowId, iterationKey, "release/original", now);
        });
        token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("token").asText();
        clearInvocations(gitLab, gitFiles, gitBranches);
    }

    @Test
    void createsAndQueriesPlanWithoutAnyGitCall() throws Exception {
        JsonNode plan = create(requestKey, List.of(repoId), true);
        assertThat(plan.path("status").asText()).isEqualTo("PLANNED");
        assertThat(plan.path("executionPermitted").asBoolean()).isFalse();
        assertThat(plan.path("operator").asText()).isEqualTo("admin");
        assertThat(plan.toString()).doesNotContain("secret-fixture-token", "gitlab.invalid", "mallory");
        String id = plan.path("runId").asText();
        mvc.perform(get("/api/v1/runs/{id}/plan", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.snapshot.items[0].featureBranch").value("feature/original"));
        mvc.perform(get("/api/v1/runs/{id}/export.json", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLANNED"));
        mvc.perform(get("/api/v1/runs/paged").param("windowKey", "RW-" + windowId).param("status", "PLANNED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.total").value(1));
        mvc.perform(get("/api/v1/runs/paged").param("windowKey", "RW-" + windowId).param("status", "RUNNING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.total").value(0));
        mvc.perform(get(path()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].runId").value(id));
        mvc.perform(post("/api/v1/runs/{id}/retry", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[],\"operator\":\"mallory\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void replaysSameRequestAndKeepsSnapshotAfterSourceChanges() throws Exception {
        JsonNode first = create(requestKey, List.of(repoId), true);
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                versions.updateVersion(iterationKey, repoId, "9.9.9-SNAPSHOT", "SYSTEM", Instant.now()));
        JsonNode replay = create(requestKey, List.of(repoId, repoId), true);
        assertThat(replay).isEqualTo(first);
        assertThat(replay.path("snapshot").path("items").get(0).path("devVersion").asText()).isEqualTo("1.1.0-SNAPSHOT");
        mvc.perform(post(path()).header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), false)))
                .andExpect(status().isConflict());
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void concurrentDuplicateRequestsCreateOnePlan() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return create(requestKey, List.of(repoId), true); });
            var b = pool.submit(() -> { start.await(); return create(requestKey, List.of(repoId), true); });
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from run_plan where operator = ? and idempotency_key = ?",
                Integer.class, "admin", requestKey)).isEqualTo(1);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void rejectsInvalidScopeAndMissingAuthentication() throws Exception {
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), true)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path()).header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of("missing-repo"), true)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from run_plan where idempotency_key = ?", Integer.class, requestKey)).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void malformedPreviewRequestsReturnBadRequestWithoutPersisting() throws Exception {
        for (String payload : List.of("null", "{}", "{\"repoIds\":[null]}")) {
            mvc.perform(post(path()).header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from run_plan where idempotency_key = ?", Integer.class, requestKey)).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void rejectsCrossGroupClosedWindowAndMissingKeyWithoutCreatingRun() throws Exception {
        mvc.perform(post(path()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), true)))
                .andExpect(status().isBadRequest());
        jdbc.update("update code_repository set group_code = 'OTHER' where id = ?", repoId);
        mvc.perform(post(path()).header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), true)))
                .andExpect(status().isBadRequest());
        jdbc.update("update code_repository set group_code = 'G001' where id = ?", repoId);
        jdbc.update("update release_window set status = 'CLOSED' where id = ?", windowId);
        mvc.perform(post(path()).header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), true)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from run_plan where idempotency_key = ?", Integer.class, requestKey)).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void differentKeysAtTheSameInstantRetainIndependentRunItems() throws Exception {
        JsonNode first = create(requestKey, List.of(repoId), true);
        JsonNode second = create(UUID.randomUUID().toString(), List.of(repoId), true);
        assertThat(first.path("runId")).isNotEqualTo(second.path("runId"));
        assertThat(first.path("planHash")).isEqualTo(second.path("planHash"));
        for (JsonNode plan : List.of(first, second)) {
            assertThat(jdbc.queryForObject("select count(*) from run_item where run_id = ?", Integer.class, plan.path("runId").asText()))
                    .isEqualTo(1);
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void snapshotInsertFailureRollsBackTheRunAndItsItems() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from run", Integer.class);
        jdbc.execute("alter table run_plan add constraint reject_fixture_preview check(idempotency_key <> 'rollback-preview-key')");
        try {
            mvc.perform(post(path()).header("Authorization", "Bearer " + token).header("Idempotency-Key", "rollback-preview-key")
                            .contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), true)))
                    .andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from run_plan where idempotency_key = 'rollback-preview-key'", Integer.class)).isZero();
        } finally {
            jdbc.execute("alter table run_plan drop constraint reject_fixture_preview");
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void localProfileCreatesAndUsesPreviewTableInItsConfiguredSchema() {
        try (var local = new SpringApplicationBuilder(ReleaseHubApplication.class)
                .initializers(context -> TestPropertyValues.of(Map.of(
                        "spring.datasource.url", DB.getJdbcUrl(), "spring.datasource.username", DB.getUsername(),
                        "spring.datasource.password", DB.getPassword(), "spring.jpa.properties.hibernate.default_schema", "preview_local",
                        "releasehub.seed.enabled", "false"
                )).applyTo(context.getEnvironment()))
                .run("--spring.profiles.active=local", "--server.port=0")) {
            Instant now = Instant.parse("2026-10-05T00:00:00Z");
            Run run = Run.preview("schema-test", now);
            RunItem item = RunItem.create("LOCAL", RepoId.of("repo"), IterationKey.of("iteration"), 1, now);
            run.addItem(item);
            var snapshot = new RunPlanSnapshot("LOCAL", "G001", "DRAFT", false, true, List.of(
                    new RunPlanSnapshot.Item(item.getId().value(), 1, "repo", "repo", "iteration", "GITLAB", "main",
                            "feature/local", "release/local", "1.0.0", "1.1.0-SNAPSHOT", "1.1.0", List.of("TRY_MERGE"))));
            var saved = new SavedRunPlan(run.getId().value(), windowId, "schema-test", requestKey, "a".repeat(64), "b".repeat(64), now, snapshot);
            var localPlans = local.getBean(RunPlanPort.class);
            new TransactionTemplate(local.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx -> {
                local.getBean(RunPort.class).save(run);
                localPlans.save(saved);
            });
            assertThat(localPlans.findByRunId(saved.runId()).orElseThrow()).isEqualTo(saved);
            assertThat(jdbc.queryForObject("select count(*) from preview_local.run_plan where run_id = ?", Integer.class, saved.runId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from public.run_plan where run_id = ?", Integer.class, saved.runId())).isZero();
        }
    }

    private JsonNode create(String key, List<String> selectedRepos, boolean failFast) throws Exception {
        return json.readTree(mvc.perform(post(path()).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(body(selectedRepos, failFast)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private String body(List<String> selectedRepos, boolean failFast) throws Exception {
        return json.writeValueAsString(Map.of("repoIds", selectedRepos, "iterationKeys", List.of(iterationKey),
                "failFast", failFast, "operator", "mallory"));
    }
    private String path() { return "/api/v1/release-windows/" + windowId + "/run-plans"; }
}
