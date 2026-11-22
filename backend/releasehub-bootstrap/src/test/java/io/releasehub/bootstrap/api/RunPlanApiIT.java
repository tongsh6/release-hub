package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.run.plan.RunPlanPort;
import io.releasehub.application.run.plan.RunPlanAppService;
import io.releasehub.application.run.plan.SavedRunPlan;
import io.releasehub.bootstrap.ReleaseHubApplication;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunStep;
import io.releasehub.domain.run.RunItemResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "releasehub.run.worker.enabled=false"})
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
    @Autowired RunPlanAppService planService;
    @Autowired RunPlanPort persistedPlans;
    @Autowired RunPort runs;
    @Autowired GitOperationJournalPort journal;
    @Autowired SettingsPort settings;
    @Autowired io.releasehub.application.run.RunAppService taskDriver;
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
    void executionRequiresIdempotencyHeaderBeforeAdmission() throws Exception {
        GitBranchPort git = orchestrationGit();
        mvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of(repoId), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        mvc.perform(post("/api/v1/runs/{id}/retry", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("items", List.of("unadmitted-item")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        assertThat(persistedPlans.listByWindow(windowId, 20)).isEmpty();
        verifyNoInteractions(git);
    }

    @Test
    void acceptsDurableIntentWithoutGitAndFindsReceiptAfterWindowFreezes() throws Exception {
        GitBranchPort git = orchestrationGit();
        String payload = body(List.of(repoId), true);
        var response = mvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse();
        String runId = json.readTree(response.getContentAsString()).path("data").asText();
        assertThat(runId).isNotBlank();
        assertThat(runs.findTasks(runId)).hasSize(4).allSatisfy(task -> assertThat(task.status()).isEqualTo("PENDING"));
        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(persistedPlans.listByWindow(windowId, 20)).hasSize(1);
        verifyNoInteractions(git);

        jdbc.update("update release_window set frozen=true where id=?", windowId);
        var repeated = mvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        assertThat(json.readTree(repeated.getContentAsString()).path("data").asText()).isEqualTo(runId);
        mvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RUN_006"));
        assertThat(persistedPlans.listByWindow(windowId, 20)).hasSize(1);
        assertThat(runs.findTasks(runId)).allSatisfy(task -> assertThat(task.status()).isEqualTo("PENDING"));
        verifyNoInteractions(git);
    }

    @Test
    void executionReplayReturnsOriginalReceiptWithoutRepeatingGit() throws Exception {
        GitBranchPort git = orchestrationGit();
        String first = executeRequest(requestKey, true);
        String repeated = executeRequest(requestKey, true);
        assertThat(repeated).isEqualTo(first);
        assertThat(persistedPlans.listByWindow(windowId, 20)).hasSize(1);
        assertThat(runs.findById(first).orElseThrow().getOperator()).isEqualTo("admin");
        verify(git, times(1)).ensureMergeRequest(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(git, times(1)).tryMergeRequest(anyString(), anyString(), any(GitBranchPort.MrEvidence.class), anyString());
        verify(git, never()).mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void executionKeyRejectsChangedPayloadWithoutAdditionalGit() throws Exception {
        GitBranchPort git = orchestrationGit();
        executeRequest(requestKey, true);
        mvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), false)))
                .andExpect(status().isConflict());
        assertThat(persistedPlans.listByWindow(windowId, 20)).hasSize(1);
        verify(git, times(1)).ensureMergeRequest(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(git, times(1)).tryMergeRequest(anyString(), anyString(), any(GitBranchPort.MrEvidence.class), anyString());
        verify(git, never()).mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void previewKeyCannotBeReusedForExecution() throws Exception {
        create(requestKey, List.of(repoId), true);
        GitBranchPort git = orchestrationGit();
        mvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), true)))
                .andExpect(status().isConflict());
        assertThat(persistedPlans.listByWindow(windowId, 20)).hasSize(1);
        verifyNoInteractions(git);
    }

    @Test
    void executionTasksExposePersistedFourActionsWithoutClaimsOrCredentials() throws Exception {
        orchestrationGit();
        String runId = executeRequest(requestKey, true);
        JsonNode response = json.readTree(mvc.perform(get("/api/v1/runs/{id}/tasks", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode taskRows = response.path("data");
        assertThat(taskRows).hasSize(4);
        List<String> actions = List.of("ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR", "TRY_MERGE");
        String itemId = runs.findById(runId).orElseThrow().getItems().getFirst().getId().value();
        var taskIds = new java.util.HashSet<String>();
        for (int i = 0; i < actions.size(); i++) {
            JsonNode task = taskRows.get(i);
            assertThat(task.path("taskId").asText()).isNotBlank();
            taskIds.add(task.path("taskId").asText());
            assertThat(task.path("runId").asText()).isEqualTo(runId);
            assertThat(task.path("itemId").asText()).isEqualTo(itemId);
            assertThat(task.path("actionType").asText()).isEqualTo(actions.get(i));
            assertThat(task.path("taskOrder").asInt()).isEqualTo(i + 1);
            assertThat(task.path("status").asText()).isEqualTo("COMPLETED");
        }
        assertThat(taskIds).hasSize(4);
        assertThat(response.toString()).doesNotContain("secret-fixture-token", "claimToken", "claim_token");
    }

    @Test
    void retryReplayReturnsOriginalReceiptAndKeepsActualSourceItem() throws Exception {
        GitBranchPort git = orchestrationGit();
        when(git.tryMergeRequest(anyString(), anyString(), any(GitBranchPort.MrEvidence.class), anyString()))
                .thenReturn(GitBranchPort.MergeResult.conflict("fixture conflict")
                        .withEvidence(orchestrationCheckpoint()), GitBranchPort.MergeResult.success()
                        .withEvidence(orchestrationEvidence()));
        String sourceRun = executeRequest(requestKey, true);
        String itemId = runs.findById(sourceRun).orElseThrow().getItems().getFirst().getId().value();
        String key = UUID.randomUUID().toString();
        String request = json.writeValueAsString(Map.of("items", List.of(itemId)));
        String first = json.readTree(mvc.perform(post("/api/v1/runs/{id}/retry", sourceRun)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("data").asText();
        String repeated = json.readTree(mvc.perform(post("/api/v1/runs/{id}/retry", sourceRun)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("data").asText();
        assertThat(repeated).isEqualTo(first).isNotEqualTo(sourceRun);
        driveTasks(first);
        assertThat(runs.findById(first).orElseThrow().getExecutionStatus().name()).isEqualTo("DONE");
        assertThat(persistedPlans.listByWindow(windowId, 20)).hasSize(2);
        assertThat(runs.findById(first).orElseThrow().getItems().getFirst().getMetadata())
                .containsEntry("retry.sourceItemId", itemId).containsEntry("retry.sourceRunId", sourceRun);
        verify(git, times(1)).ensureMergeRequest(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(git, times(2)).tryMergeRequest(anyString(), anyString(), any(GitBranchPort.MrEvidence.class), anyString());
        verify(git, never()).mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private GitBranchPort orchestrationGit() {
        GitBranchPort git = mock(GitBranchPort.class);
        when(gitBranches.getAdapter(GitProvider.GITLAB)).thenReturn(git);
        when(git.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.present("101", "a".repeat(40)));
        when(git.ensureMergeRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(GitWriteResult.applied(orchestrationCheckpoint()));
        when(git.tryMergeRequest(anyString(), anyString(), any(GitBranchPort.MrEvidence.class), anyString()))
                .thenReturn(GitBranchPort.MergeResult.success().withEvidence(orchestrationEvidence()));
        return git;
    }

    private GitBranchPort.MrEvidence orchestrationCheckpoint() {
        return new GitBranchPort.MrEvidence("101", "feature/original", "release/original", "17",
                "https://gitlab.invalid/acme/repo/-/merge_requests/17", "a".repeat(40), null,
                GitBranchPort.MrDisposition.MR_REUSED, null);
    }

    private GitBranchPort.MrEvidence orchestrationEvidence() {
        return new GitBranchPort.MrEvidence("101", "feature/original", "release/original", "17",
                "https://gitlab.invalid/acme/repo/-/merge_requests/17", "a".repeat(40), "b".repeat(40),
                GitBranchPort.MrDisposition.MR_REUSED, null);
    }

    private String acceptRequest(String key, boolean failFast) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(repoId), failFast)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("data").asText();
    }

    private String executeRequest(String key, boolean failFast) throws Exception {
        String runId = acceptRequest(key, failFast);
        driveTasks(runId);
        return runId;
    }

    // 本类隔离受理/事务契约；真实调度与重启由后台和进程集成测试验证。
    private void driveTasks(String runId) {
        int budget = runs.findTasks(runId).size() + 1;
        while (budget-- > 0) {
            if (!taskDriver.executeNextTask(runId)) return;
        }
        throw new AssertionError("Task driver exceeded the persisted task count for " + runId);
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
        String plannedItemId = runs.findById(id).orElseThrow().getItems().getFirst().getId().value();
        mvc.perform(post("/api/v1/runs/{id}/retry", id).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("items", List.of(plannedItemId)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUN_007"));
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
    void admissionPersistsOneRunningSnapshotAndRollsBackWhenSnapshotPersistenceFails() {
        var admission = planService.admit(windowId, List.of(repoId), List.of(iterationKey), true,
                "admission-success", UUID.randomUUID().toString());
        Run admitted = runs.findById(admission.runId()).orElseThrow();
        assertThat(admitted.getExecutionStatus().name()).isEqualTo("RUNNING");
        SavedRunPlan persisted = persistedPlans.findByRunId(admitted.getId().value()).orElseThrow();
        assertThat(persisted.snapshot().items().stream().map(RunPlanSnapshot.Item::itemId).toList())
                .containsExactlyElementsOf(admitted.getItems().stream().map(item -> item.getId().value()).toList());
        assertThat(jdbc.queryForObject("select count(*) from run_item where run_id = ?", Integer.class,
                admitted.getId().value())).isEqualTo(1);

        assertThat(runs.findTasks(admitted.getId().value())).hasSize(4);
        // 使用另一真实绑定窗口，使失败来自快照约束，而不是首个 RUNNING 意图的范围互斥。
        String rollbackWindowId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            windows.save(ReleaseWindow.rehydrate(ReleaseWindowId.of(rollbackWindowId), "RW-" + rollbackWindowId,
                    "受理回滚测试", "", now, "G001", ReleaseWindowStatus.DRAFT, now, now, false, null));
            bindings.attach(ReleaseWindowId.of(rollbackWindowId), IterationKey.of(iterationKey), now);
            bindings.updateReleaseBranch(rollbackWindowId, iterationKey, "release/rollback-" + rollbackWindowId, now);
        });
        Integer before = jdbc.queryForObject("select count(*) from run", Integer.class);
        Integer tasksBefore = jdbc.queryForObject("select count(*) from run_task", Integer.class);
        jdbc.execute("alter table run_plan add constraint reject_fixture_admission check(operator <> 'admission-rollback')");
        try {
            assertThatThrownBy(() -> planService.admit(rollbackWindowId, List.of(repoId), List.of(iterationKey), true,
                    "admission-rollback", UUID.randomUUID().toString()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from run_task", Integer.class)).isEqualTo(tasksBefore);
            assertThat(jdbc.queryForObject("select count(*) from run_item where window_key = ?", Integer.class,
                    "RW-" + rollbackWindowId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from run_plan where window_id = ?", Integer.class,
                    rollbackWindowId)).isZero();
        } finally {
            jdbc.execute("alter table run_plan drop constraint reject_fixture_admission");
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }


    @Test
    void concurrentCloseCreatesOneRepositoryCleanupAndReturnsItsStoredRun() throws Exception {
        publishFixture();
        String secondIteration = attachSharedRepositoryIteration();
        GitBranchPort git = cleanupGit();
        var enteredGit = new CountDownLatch(1);
        var releaseGit = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertCleanupCommittedBeforeGit();
            enteredGit.countDown();
            assertThat(releaseGit.await(20, TimeUnit.SECONDS)).isTrue();
            return cleanupMerge();
        }).when(git).mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString());

        JsonNode completed;
        String runId;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> closeRequest(null));
            try {
                assertThat(enteredGit.await(20, TimeUnit.SECONDS)).isTrue();
                var concurrent = pool.submit(() -> closeRequest("{}"));
                JsonNode accepted = concurrent.get(20, TimeUnit.SECONDS);
                runId = accepted.path("id").asText();
                assertThat(runId).isNotBlank();
                assertThat(accepted.path("runType").asText()).isEqualTo("WINDOW_CLEANUP");
                assertThat(accepted.path("status").asText()).isEqualTo("RUNNING");
                assertThat(accepted.path("items")).hasSize(1);
                assertThat(accepted.path("items").get(0).path("iterationKey").isNull()).isTrue();
                assertCleanupCommittedBeforeGit();
                for (String action : List.of("freeze", "unfreeze", "attach")) {
                    String body = action.equals("attach")
                            ? json.writeValueAsString(Map.of("iterationKeys", List.of(iterationKey))) : "{}";
                    mvc.perform(post("/api/v1/release-windows/{id}/{action}", windowId, action)
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andExpect(status().isBadRequest());
                    assertThat(jdbc.queryForObject("select status from release_window where id = ?", String.class, windowId))
                            .isEqualTo("CLOSED");
                }
                mvc.perform(post("/api/v1/release-windows/{id}/orchestrate", windowId)
                                .header("Authorization", "Bearer " + token).header("Idempotency-Key", requestKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("repoIds", List.of(repoId),
                                        "iterationKeys", List.of(iterationKey), "failFast", true))))
                        .andExpect(status().isBadRequest());
                assertThat(jdbc.queryForObject("select count(*) from run_plan where window_id = ?", Integer.class, windowId)).isEqualTo(1);
            } finally {
                releaseGit.countDown();
            }
            completed = first.get(20, TimeUnit.SECONDS);
        }
        assertThat(completed.path("id").asText()).isEqualTo(runId);
        assertThat(completed.path("status").asText()).isEqualTo("DONE");
        assertThat(completed.path("operator").asText()).isEqualTo("admin");
        JsonNode item = completed.path("items").get(0);
        assertThat(item.path("iterationKey").isNull()).isTrue();
        assertThat(json.readTree(item.path("metadata").path("plan.participatingIterationKeys").asText()))
                .isEqualTo(json.valueToTree(List.of(iterationKey, secondIteration)));
        assertThat(item.path("metadata").path("cleanup.referencePolicy").asText()).isEqualTo("RETAIN");
        assertThat(closeRequest("{\"triggerCi\":false}").path("id").asText()).isEqualTo(runId);
        mvc.perform(post(closePath()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"triggerCi\":true}"))
                .andExpect(status().isConflict());

        var snapshot = persistedPlans.findByRunId(runId).orElseThrow().snapshot();
        assertThat(snapshot.items()).hasSize(1);
        assertThat(snapshot.items().getFirst().iterationKey()).isNull();
        assertThat(snapshot.items().getFirst().participatingIterationKeys()).containsExactly(iterationKey, secondIteration);
        assertThat(snapshot.items().getFirst().triggerCi()).isFalse();
        mvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/api/v1/runs/paged").header("Authorization", "Bearer " + token)
                        .param("windowKey", "RW-" + windowId).param("runType", "WINDOW_CLEANUP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.total").value(1));
        JsonNode exported = json.readTree(mvc.perform(get("/api/v1/runs/{id}/export.json", runId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(exported.path("items").get(0).path("iterationKey").isNull()).isTrue();
        assertThat(exported.path("items").get(0).path("metadata")).isEqualTo(item.path("metadata"));
        JsonNode report = json.readTree(mvc.perform(get("/api/v1/release-windows/{id}/report.json", windowId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(report.path("itemCount").asInt()).isEqualTo(1);
        assertThat(report.path("runs").get(0).path("items").get(0).path("metadata")).isEqualTo(item.path("metadata"));
        String markdown = mvc.perform(get("/api/v1/release-windows/{id}/report.md", windowId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(markdown).contains("Repository cleanup", iterationKey, secondIteration, "RETAIN");
        String csv = mvc.perform(get("/api/v1/release-windows/{id}/report.csv", windowId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("participatingIterationKeys,referencePolicy", "WINDOW_CLEANUP", iterationKey, secondIteration, "RETAIN");
        verify(git, times(1)).mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(git, times(1)).createTag(anyString(), anyString(), eq("101"), anyString(), eq("b".repeat(40)), anyString());
        verify(git, never()).triggerPipeline(anyString(), anyString(), anyString(), anyString(), anyString());
        assertThat(org.mockito.Mockito.mockingDetails(git).getInvocations())
                .noneMatch(call -> Set.of("archiveBranch", "deleteBranch").contains(call.getMethod().getName()));
        verifyNoInteractions(gitLab, gitFiles);
    }

    @Test
    void closeWithExplicitCiUsesTheSavedTagAndAuthenticatedOperator() throws Exception {
        publishFixture();
        GitBranchPort git = cleanupGit();
        JsonNode run = closeRequest("{\"triggerCi\":true,\"operator\":\"mallory\"}");
        String runId = run.path("id").asText();
        assertThat(run.path("status").asText()).isEqualTo("DONE");
        assertThat(run.path("operator").asText()).isEqualTo("admin");
        var planned = persistedPlans.findByRunId(runId).orElseThrow().snapshot().items().getFirst();
        assertThat(planned.triggerCi()).isTrue();
        assertThat(planned.ciRef()).isNotBlank().isEqualTo(planned.tagName());
        assertThat(run.path("items").get(0).path("metadata").path("git.pipelineRef").asText()).isEqualTo(planned.ciRef());
        verify(git, times(1)).triggerPipeline(anyString(), anyString(), eq("101"), eq(planned.ciRef()), eq("b".repeat(40)));
        assertThat(closeRequest("{\"triggerCi\":true}").path("id").asText()).isEqualTo(runId);
        verify(git, times(1)).triggerPipeline(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unresolvedCleanupCiReturnsNeedsReviewAndCannotBeResentOrOrdinarilyRetried() throws Exception {
        publishFixture();
        GitBranchPort git = cleanupGit();
        doReturn(GitWriteResult.unknown("UNCONFIRMED_FIXTURE_RESPONSE")).when(git)
                .triggerPipeline(anyString(), anyString(), anyString(), anyString(), anyString());
        JsonNode run = closeRequest("{\"triggerCi\":true}");
        String runId = run.path("id").asText();
        assertThat(run.path("status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(run.path("items").get(0).path("finalResult").asText()).isEqualTo("UNKNOWN");
        assertThat(closeRequest("{\"triggerCi\":true}").path("id").asText()).isEqualTo(runId);
        mvc.perform(post("/api/v1/runs/{id}/retry", runId).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                                Map.of("items", List.of(run.path("items").get(0).path("id").asText())))))
                .andExpect(status().isConflict());
        verify(git, times(1)).mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(git, times(1)).triggerPipeline(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cleanupSnapshotFailureRollsBackWindowRunAndItemsBeforeAnyGit() throws Exception {
        publishFixture();
        Integer before = jdbc.queryForObject("select count(*) from run", Integer.class);
        jdbc.execute("alter table run_plan add constraint reject_cleanup_snapshot check(window_id <> '" + windowId + "')");
        try {
            mvc.perform(post(closePath()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("select status from release_window where id = ?", String.class, windowId))
                    .isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from run_item where window_key = ?", Integer.class, "RW-" + windowId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from run_plan where window_id = ?", Integer.class, windowId)).isZero();
        } finally {
            jdbc.execute("alter table run_plan drop constraint reject_cleanup_snapshot");
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void firstCloseOfFrozenWindowIsRejectedWithoutCreatingRunOrCallingGit() throws Exception {
        publishFixture();
        jdbc.update("update release_window set frozen = true where id = ?", windowId);
        mvc.perform(post(closePath()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from run_plan where window_id = ?", Integer.class, windowId)).isZero();
        assertThat(jdbc.queryForObject("select status from release_window where id = ?", String.class, windowId)).isEqualTo("PUBLISHED");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void runningOrchestrationPausedInGitPreventsCleanupAdmission() throws Exception {
        publishFixture();
        GitBranchPort git = orchestrationGit();
        var enteredGit = new CountDownLatch(1);
        var releaseGit = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            enteredGit.countDown();
            assertThat(releaseGit.await(20, TimeUnit.SECONDS)).isTrue();
            return GitBranchPort.MergeResult.success().withEvidence(orchestrationEvidence());
        }).when(git).tryMergeRequest(anyString(), anyString(), any(GitBranchPort.MrEvidence.class), anyString());
        String executionId = acceptRequest(requestKey, true);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var execution = pool.submit(() -> {
                driveTasks(executionId);
                return true;
            });
            try {
                assertThat(enteredGit.await(20, TimeUnit.SECONDS)).isTrue();
                assertThat(jdbc.queryForObject("select count(*) from run_plan p join run r on r.id = p.run_id where p.window_id = ? and r.execution_status = 'RUNNING'",
                        Integer.class, windowId)).isEqualTo(1);
                mvc.perform(post(closePath()).header("Authorization", "Bearer " + token))
                        .andExpect(status().isConflict());
                assertThat(jdbc.queryForObject("select status from release_window where id = ?", String.class, windowId)).isEqualTo("PUBLISHED");
                assertThat(jdbc.queryForObject("select count(*) from run_plan p join run r on r.id = p.run_id where p.window_id = ? and r.run_type = 'WINDOW_CLEANUP'",
                        Integer.class, windowId)).isZero();
            } finally {
                releaseGit.countDown();
            }
            assertThat(execution.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(runs.findById(executionId).orElseThrow().getExecutionStatus().name()).isEqualTo("DONE");
        }
        verify(git, times(1)).ensureMergeRequest(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(git, times(1)).tryMergeRequest(anyString(), anyString(), any(GitBranchPort.MrEvidence.class), anyString());
        verify(git, never()).mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(git, never()).readTag(anyString(), anyString(), anyString(), anyString());
        verify(git, never()).createTag(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(git, never()).triggerPipeline(anyString(), anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(gitLab, gitFiles);
    }

    @Test
    void unresolvedOrchestrationPreventsCloseBeforeAnyCleanupGit() throws Exception {
        publishFixture();
        var admission = planService.admit(windowId, List.of(repoId), List.of(iterationKey), true,
                "admin", UUID.randomUUID().toString());
        Run source = runs.findById(admission.runId()).orElseThrow();
        RunPort.TaskClaim claim = runs.claimNextTask(source.getId().value()).orElseThrow();
        var previousSettings = jdbc.queryForList("select * from system_settings where id='GLOBAL'");
        try {
            String base = "https://gitlab.invalid";
            String project = Long.toString(Long.parseLong(windowId.replace("-", "").substring(0, 13), 16) + 1000);
            String remote = GitOperationJournalPort.remoteKey("GITLAB", base, project);
            String operationId = UUID.randomUUID().toString();
            var repository = repos.findById(claim.repo()).orElseThrow();
            String branch = claim.metadata().get("plan.featureBranch");
            String ref = "a".repeat(40);
            var body = json.createObjectNode().put("branch", branch).put("ref", ref);
            var target = json.createObjectNode().put("method", "POST").put("suffix", "/repository/branches")
                    .put("branch", branch).put("ref", ref).put("sourceRef", claim.metadata().get("plan.defaultBranch"));
            settings.saveGitLab(new SettingsPort.SettingsGitLab(base, "isolated-plan-journal-token"));
            // This existing API guard fixture requires a dispatched write with an unknown response.
            journal.begin(new GitOperationJournalPort.Command(operationId, claim.repo().value(),
                    GitOperationJournalPort.mappingHash(repository.getGitProvider().name(), repository.getCloneUrl()),
                    remote, "GITLAB", base, project, "CREATE_BRANCH", target.toString(),
                    io.releasehub.infrastructure.git.GitLabGitBranchAdapter.branchRequestHash(remote, body),
                    claim.runId(), claim.operator(), claim.fence().claimToken(), claim.fence()));
            journal.sending(operationId, claim.fence());
            journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.UNKNOWN, "{}", "RESPONSE_UNCONFIRMED");
            Run unresolved = runs.completeTask(claim, new RunStep(claim.actionType(), RunItemResult.UNKNOWN,
                    claim.startedAt(), claim.startedAt().plusMillis(1), "fixture unknown result"), Map.of());
            assertThat(unresolved.getExecutionStatus().name()).isEqualTo("NEEDS_REVIEW");
            assertThat(runs.findTasks(source.getId().value()).getFirst().status()).isEqualTo("UNKNOWN");
            mvc.perform(post(closePath()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("select status from release_window where id = ?", String.class, windowId)).isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("select count(*) from run_plan p join run r on r.id = p.run_id where p.window_id = ? and r.run_type = 'WINDOW_CLEANUP'",
                    Integer.class, windowId)).isZero();
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            if (previousSettings.isEmpty()) {
                jdbc.update("delete from system_settings where id='GLOBAL'");
            } else {
                var previous = previousSettings.getFirst();
                jdbc.update("update system_settings set gitlab_base_url=?,gitlab_token=? where id='GLOBAL'",
                        previous.get("gitlab_base_url"), previous.get("gitlab_token"));
            }
            assertThat(jdbc.queryForList("select * from system_settings where id='GLOBAL'")).isEqualTo(previousSettings);
        }
    }

    @Test
    void readOnlyPreviewDoesNotBlockCloseOrBecomeAnExecution() throws Exception {
        publishFixture();
        JsonNode preview = create(requestKey, List.of(repoId), true);
        assertThat(preview.path("status").asText()).isEqualTo("PLANNED");
        GitBranchPort git = cleanupGit();
        JsonNode cleanup = closeRequest("{}");
        assertThat(cleanup.path("status").asText()).isEqualTo("DONE");
        assertThat(cleanup.path("id").asText()).isNotEqualTo(preview.path("runId").asText());
        assertThat(runs.findById(preview.path("runId").asText()).orElseThrow().getExecutionStatus().name()).isEqualTo("PLANNED");
        verify(git, times(1)).mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private void publishFixture() {
        jdbc.update("update release_window set status = 'PUBLISHED' where id = ?", windowId);
    }

    private String attachSharedRepositoryIteration() {
        String second = "ITER-" + UUID.randomUUID();
        Instant attachedAt = bindings.listByWindow(ReleaseWindowId.of(windowId)).getFirst().getAttachAt().plusSeconds(1);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            iterations.save(Iteration.rehydrate(IterationKey.of(second), "第二个参与迭代", "", null,
                    "G001", Set.of(RepoId.of(repoId)), IterationStatus.ACTIVE, attachedAt, attachedAt));
            versions.saveWithVersion(second, repoId, "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                    "feature/second", "SYSTEM", attachedAt, BranchCreationMode.AUTO);
            bindings.attach(ReleaseWindowId.of(windowId), IterationKey.of(second), attachedAt);
            bindings.updateReleaseBranch(windowId, second, "release/original", attachedAt);
        });
        return second;
    }

    private GitBranchPort cleanupGit() {
        // 此处验证真实 API/事务/数据库；远端结果由显式端口 fixture 提供，不宣称真实 GitLab E2E。
        GitBranchPort git = mock(GitBranchPort.class);
        when(gitBranches.getAdapter(GitProvider.GITLAB)).thenReturn(git);
        when(git.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.present("101", "a".repeat(40)));
        when(git.mergeBranch(anyString(), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            assertCleanupCommittedBeforeGit();
            return cleanupMerge();
        });
        when(git.readTag(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GitReadResult<>(GitReadResult.Status.NOT_FOUND, null));
        when(git.createTag(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> GitWriteResult.applied(new GitBranchPort.TagEvidence(
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4))));
        when(git.triggerPipeline(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> GitWriteResult.applied(new GitBranchPort.PipelineEvidence(
                        invocation.getArgument(2), "99", invocation.getArgument(3), invocation.getArgument(4),
                        "https://gitlab.invalid/acme/repo/-/pipelines/99")));
        return git;
    }

    private GitBranchPort.MergeResult cleanupMerge() {
        return GitBranchPort.MergeResult.success().withEvidence(new GitBranchPort.MrEvidence(
                "101", "release/original", "main", "7", "https://gitlab.invalid/acme/repo/-/merge_requests/7",
                "a".repeat(40), "b".repeat(40), GitBranchPort.MrDisposition.CREATED, null));
    }

    private void assertCleanupCommittedBeforeGit() {
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(jdbc.queryForObject("select status from release_window where id = ?", String.class, windowId)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("select count(*) from run_plan p join run r on r.id = p.run_id "
                + "where p.window_id = ? and r.run_type = 'WINDOW_CLEANUP'", Integer.class, windowId)).isEqualTo(1);
    }

    private JsonNode closeRequest(String body) throws Exception {
        var request = post(closePath()).header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private String closePath() { return "/api/v1/release-windows/" + windowId + "/close"; }

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
                            "feature/local", "AUTO", "release/local", "1.0.0", "1.1.0-SNAPSHOT", "1.1.0", List.of("TRY_MERGE"),
                            List.of("iteration"), null, false, null)));
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
