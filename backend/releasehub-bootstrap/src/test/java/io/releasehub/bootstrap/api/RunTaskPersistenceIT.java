package io.releasehub.bootstrap.api;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.run.RunAppService;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.run.plan.RunPlanAppService;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
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
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunExecutionStatus;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "releasehub.run.worker.enabled=false"})
@ActiveProfiles("e2e")
class RunTaskPersistenceIT {
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("run_task_test").withUsername("task_test").withPassword("isolated-task-db");
    static { DB.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }
    @AfterAll static void stopDatabase() { DB.stop(); }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ReleaseWindowPort windows;
    @Autowired IterationPort iterations;
    @Autowired IterationRepoPort versions;
    @Autowired CodeRepositoryPort repositories;
    @Autowired WindowIterationPort bindings;
    @Autowired RunPlanAppService plans;
    @Autowired RunPort runs;
    @Autowired RunAppService taskService;
    @Autowired GitOperationJournalPort journal;
    @Autowired SettingsPort settings;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @org.springframework.boot.test.mock.mockito.SpyBean java.time.Clock clock;
    @MockBean GitLabPort gitLab;
    @MockBean GitLabFilePort gitFiles;
    @MockBean GitBranchAdapterFactory gitBranches;
    private String windowId, firstRepo, secondRepo, firstIteration, secondIteration;

    @BeforeEach
    void fixtures() {
        windowId = UUID.randomUUID().toString();
        firstRepo = UUID.randomUUID().toString();
        secondRepo = UUID.randomUUID().toString();
        firstIteration = "ITER-" + UUID.randomUUID();
        secondIteration = "ITER-" + UUID.randomUUID();
        Instant now = Instant.now();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            windows.save(ReleaseWindow.rehydrate(ReleaseWindowId.of(windowId), "RW-" + windowId,
                    "持久任务测试", "", now, "G001", ReleaseWindowStatus.DRAFT, now, now, false, null));
            for (String id : List.of(firstRepo, secondRepo)) {
                repositories.save(CodeRepository.rehydrate(RepoId.of(id), "仓库-" + id,
                        "https://gitlab.invalid/acme/" + id + ".git", "main", "G001",
                        RepoType.SERVICE, GitProvider.GITLAB, "isolated-token", false,
                        0, 0, 0, 0, 0, 0, 0, null, now, now, 0));
            }
            int ordinal = 0;
            for (String key : List.of(firstIteration, secondIteration)) {
                iterations.save(Iteration.rehydrate(IterationKey.of(key), key, "", null, "G001",
                        Set.of(RepoId.of(firstRepo), RepoId.of(secondRepo)), IterationStatus.ACTIVE, now, now));
                for (String repo : List.of(firstRepo, secondRepo)) {
                    versions.saveWithVersion(key, repo, "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                            "feature/" + key, "SYSTEM", now, BranchCreationMode.AUTO);
                }
                bindings.attach(ReleaseWindowId.of(windowId), IterationKey.of(key), now.plusSeconds(ordinal++));
                bindings.updateReleaseBranch(windowId, key, "release/" + windowId, now);
            }
        });
    }

    @Test
    void admissionPersistsFourTaskIntentsInTheSameScopeWithoutGit() {
        String key = UUID.randomUUID().toString();
        var admission = plans.admit(windowId, List.of(firstRepo), List.of(firstIteration), true, "operator", key);
        var run = runs.findById(admission.runId()).orElseThrow();
        assertThat(plans.admit(windowId, List.of(firstRepo), List.of(firstIteration), true, "operator", key).runId())
                .isEqualTo(admission.runId());
        assertThat(jdbc.queryForObject("select count(*) from run_plan where operator=? and idempotency_key=?",
                Integer.class, "operator", key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from run_task where run_id=?", Integer.class, run.getId().value()))
                .isEqualTo(4);
        assertThat(jdbc.queryForList("select task_type from run_task where run_id=? order by task_order",
                String.class, run.getId().value()))
                .containsExactly("ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR", "TRY_MERGE");
        assertThat(jdbc.queryForList("select distinct target_id from run_task where run_id=?",
                String.class, run.getId().value())).containsExactly(run.getItems().getFirst().getId().value());
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void anActiveRepositoryBlocksAnotherIterationOfThatRepository() {
        plans.admit(windowId, List.of(firstRepo), List.of(firstIteration), true, "operator-a", java.util.UUID.randomUUID().toString());
        assertThatThrownBy(() -> plans.admit(windowId, List.of(firstRepo), List.of(secondIteration), true, "operator-b", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo("RUN_007"));
    }

    @Test
    void aDisjointRepositoryMayBeAdmittedWhileTheFirstRepositoryIsRunning() {
        var first = runs.findById(plans.admit(windowId, List.of(firstRepo), List.of(firstIteration), true, "operator-a", java.util.UUID.randomUUID().toString()).runId()).orElseThrow();
        var second = runs.findById(plans.admit(windowId, List.of(secondRepo), List.of(secondIteration), true, "operator-b", java.util.UUID.randomUUID().toString()).runId()).orElseThrow();
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getItems()).singleElement().satisfies(item -> assertThat(item.getRepo().value()).isEqualTo(secondRepo));
    }

    @Test
    void aPreviewDoesNotCreateTasksOrReserveARepository() {
        var preview = plans.create(windowId, List.of(firstRepo), List.of(firstIteration), true,
                "operator", UUID.randomUUID().toString());
        var run = runs.findById(plans.admit(windowId, List.of(firstRepo), List.of(firstIteration), true, "operator", java.util.UUID.randomUUID().toString()).runId()).orElseThrow();
        assertThat(jdbc.queryForObject("select count(*) from run_task where run_id=?", Integer.class, preview.runId())).isZero();
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(preview.runId());
        assertThat(jdbc.queryForObject("select count(*) from run_task where run_id=?", Integer.class, run.getId().value())).isEqualTo(4);
    }

    @Test
    void simultaneousAdmissionCannotCreateTwoActiveIntentsForOneRepository() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> admitAfter(start, firstIteration, "operator-a"));
            var second = pool.submit(() -> admitAfter(start, secondIteration, "operator-b"));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ADMITTED", "RUN_007");
        }
        assertThat(jdbc.queryForObject("select count(*) from run_plan where window_id=?", Integer.class, windowId)).isEqualTo(1);
    }

    @Test
    void concurrentClaimsKeepOneActiveTaskPerRepositoryAndAllowAnotherRepository() {
        Run run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration, secondIteration), true);
        var first = runs.claimNextTask(run.getId().value()).orElseThrow();
        var second = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(first.repo()).isNotEqualTo(second.repo());
        assertThat(first.actionType()).isEqualTo(ActionType.ENSURE_FEATURE);
        assertThat(second.actionType()).isEqualTo(ActionType.ENSURE_FEATURE);
        assertThat(runs.claimNextTask(run.getId().value())).isEmpty();

        completeSuccess(first);
        var next = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(next.itemId()).isEqualTo(first.itemId());
        assertThat(next.actionType()).isEqualTo(ActionType.ENSURE_RELEASE);
        assertThat(next.operator()).isEqualTo("operator");
        assertThat(next.metadata()).containsEntry("git.projectId", projectId(first));
        assertThat(runs.findById(run.getId().value()).orElseThrow().getItems().stream()
                .filter(item -> item.getId().value().equals(first.itemId())).findFirst().orElseThrow().getExecutedOrder()).isEqualTo(1);
    }

    @Test
    void concurrentResultsCannotOverwriteAnotherRepositoryProjection() throws Exception {
        Run run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration), true);
        var first = runs.claimNextTask(run.getId().value()).orElseThrow();
        var second = runs.claimNextTask(run.getId().value()).orElseThrow();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return completeSuccess(first); });
            var b = pool.submit(() -> { start.await(); return completeSuccess(second); });
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
        Run restored = runs.findById(run.getId().value()).orElseThrow();
        assertThat(restored.getItems()).allSatisfy(item -> assertThat(item.getSteps()).hasSize(1));
        assertThat(restored.getItems()).extracting(item -> item.getExecutedOrder()).containsExactlyInAnyOrder(1, 2);
        assertThat(runs.findTasks(run.getId().value()).stream().filter(task -> task.status().equals("COMPLETED"))).hasSize(2);
        assertThat(restored.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
    }

    @Test
    void fourCommittedActionsFinishTheRunAndRecordEachTaskOnlyOnce() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        for (var action : ActionType.orchestrationSequence()) {
            var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
            assertThat(claim.actionType()).isEqualTo(action);
            completeSuccess(claim);
        }
        Run restored = runs.findById(run.getId().value()).orElseThrow();
        assertThat(restored.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(restored.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGED);
            assertThat(item.getExecutedOrder()).isEqualTo(1);
            assertThat(item.getSteps()).hasSize(4);
        });
        assertThat(runs.claimNextTask(run.getId().value())).isEmpty();
        assertThat(runs.findTasks(run.getId().value())).allSatisfy(task -> assertThat(task.status()).isEqualTo("COMPLETED"));
        assertThat(jdbc.queryForObject("select count(distinct task_id) from run_step where run_item_id=?",
                Integer.class, restored.getItems().getFirst().getId().value())).isEqualTo(4);
        assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                .orElseThrow().getLastMergeAt()).isNotNull();
    }

    @Test
    void failFastSkipsOnlyTheLaterItemsOfTheSameRepository() {
        Run run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration, secondIteration), true);
        var first = runs.claimNextTask(run.getId().value()).orElseThrow();
        runs.completeTask(first, step(first, RunItemResult.FAILED), Map.of("git.blockReason", "PERMISSION_DENIED"));
        Run restored = runs.findById(run.getId().value()).orElseThrow();
        assertThat(restored.getItems().stream().filter(item -> item.getRepo().equals(first.repo())
                && !item.getId().value().equals(first.itemId()))).singleElement().satisfies(item -> {
                    assertThat(item.getFinalResult()).isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
                    assertThat(item.getExecutedOrder()).isZero();
                    assertThat(item.getMetadata()).containsEntry("blockedBy.itemId", first.itemId());
                });
        var next = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(next.repo()).isNotEqualTo(first.repo());
    }

    @Test
    void continueOnBlockAdvancesToTheNextItemButNeverTheFailedItemsLaterAction() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration, secondIteration), false);
        var first = runs.claimNextTask(run.getId().value()).orElseThrow();
        runs.completeTask(first, step(first, RunItemResult.FAILED), Map.of());
        var next = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(next.itemId()).isNotEqualTo(first.itemId());
        assertThat(next.actionType()).isEqualTo(ActionType.ENSURE_FEATURE);
        assertThat(runs.findTasks(run.getId().value()).stream().filter(task -> task.itemId().equals(first.itemId()))
                .map(RunPort.TaskView::status)).containsExactly("FAILED", "SKIPPED", "SKIPPED", "SKIPPED");
    }

    @Test
    void unknownStopsItsRepositoryEvenWhenContinueOnBlockWasChosen() {
        Run run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration, secondIteration), false);
        var first = runs.claimNextTask(run.getId().value()).orElseThrow();
        runs.completeTask(first, step(first, RunItemResult.UNKNOWN), Map.of("git.blockReason", "UNKNOWN"));
        var next = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(next.repo()).isNotEqualTo(first.repo());
        org.assertj.core.api.Assertions.assertThatCode(() -> completeSuccess(next)).doesNotThrowAnyException();
        assertThat(runs.findById(run.getId().value()).orElseThrow().getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
    }

    @Test
    void staleClaimsAndWholeAggregateWritesCannotOverwriteCommittedFacts() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
        completeSuccess(claim);
        assertThatThrownBy(() -> runs.completeTask(claim, step(claim, RunItemResult.FAILED), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.save(run)).isInstanceOf(BusinessException.class);
        assertThat(runs.findById(run.getId().value()).orElseThrow().getItems().getFirst().getSteps()).hasSize(1);
    }

    @Test
    void rollingBackTheResultTransactionKeepsTheTaskClaimedAndProjectionUnchanged() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            completeSuccess(claim);
            throw new IllegalStateException("projection rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(runs.findTasks(run.getId().value()).getFirst().status()).isEqualTo("RUNNING");
        assertThat(runs.findById(run.getId().value()).orElseThrow().getItems().getFirst().getSteps()).isEmpty();
        assertThat(runs.claimNextTask(run.getId().value())).isEmpty();
    }

    @Test
    void retryInheritsSuccessfulPrefixWithoutInventingNewStepsOrAnotherMergeRequest() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        for (int index = 0; index < 3; index++) completeSuccess(runs.claimNextTask(run.getId().value()).orElseThrow());
        var merge = runs.claimNextTask(run.getId().value()).orElseThrow();
        runs.completeTask(merge, step(merge, RunItemResult.MERGE_BLOCKED), Map.of("git.blockReason", "APPROVAL_REQUIRED"));
        Run previous = runs.findById(run.getId().value()).orElseThrow();
        String key = UUID.randomUUID().toString();
        var admission = plans.admitRetry(previous, List.of(merge.itemId()), "retry-operator", key);
        Run retry = runs.findById(admission.runId()).orElseThrow();
        assertThat(retry.getId()).isNotEqualTo(previous.getId());
        assertThat(runs.findTasks(retry.getId().value()).stream().map(RunPort.TaskView::status))
                .containsExactly("INHERITED", "INHERITED", "INHERITED", "PENDING");
        assertThat(runs.findById(retry.getId().value()).orElseThrow().getItems().getFirst().getSteps()).isEmpty();
        var retryMerge = runs.claimNextTask(retry.getId().value()).orElseThrow();
        assertThat(retryMerge.actionType()).isEqualTo(ActionType.TRY_MERGE);
        assertThat(retryMerge.metadata()).containsEntry("git.projectId", "17")
                .containsEntry("git.mrIid", "11").containsEntry("git.sourceSha", "a".repeat(40));
        completeSuccess(retryMerge);
        assertThat(runs.findById(retry.getId().value()).orElseThrow().getItems().getFirst().getSteps()).hasSize(1);
        assertThat(plans.admitRetry(previous, List.of(merge.itemId()), "retry-operator", key).runId())
                .isEqualTo(retry.getId().value());
        assertThat(jdbc.queryForObject("select count(*) from run_plan where operator=? and idempotency_key=?",
                Integer.class, "retry-operator", key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from run_task where run_id=?", Integer.class,
                retry.getId().value())).isEqualTo(4);
        assertThat(runs.findTasks(previous.getId().value()).getLast().status()).isEqualTo("FAILED");
    }

    @Test
    void aLaterItemReusesTheRepositoryIdentityAndCannotReplaceIt() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration, secondIteration), true);
        for (int index = 0; index < 4; index++) completeSuccess(runs.claimNextTask(run.getId().value()).orElseThrow());
        var next = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(next.actionType()).isEqualTo(ActionType.ENSURE_FEATURE);
        assertThat(next.metadata()).containsEntry("git.projectId", "17");
        assertThatThrownBy(() -> runs.completeTask(next, step(next, RunItemResult.ALREADY_EXISTS),
                Map.of("git.projectId", "18"))).isInstanceOf(BusinessException.class);
        assertThat(runs.findTasks(run.getId().value()).stream().filter(task -> task.taskId().equals(next.fence().taskId()))
                .findFirst().orElseThrow().status()).isEqualTo("RUNNING");
        assertThat(runs.findById(run.getId().value()).orElseThrow().getItems().stream()
                .filter(item -> item.getId().value().equals(next.itemId())).findFirst().orElseThrow().getSteps()).isEmpty();
    }

    @Test
    void tryMergeCannotEraseOrReplaceThePersistedMergeRequestIdentity() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        for (int index = 0; index < 3; index++) completeSuccess(runs.claimNextTask(run.getId().value()).orElseThrow());
        var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
        var clear = new java.util.LinkedHashMap<String, String>();
        clear.put("git.mrIid", null);
        assertThatThrownBy(() -> runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN), clear))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN), Map.of("git.sourceSha", "b".repeat(40))))
                .isInstanceOf(BusinessException.class);
        runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN), Map.of("git.blockReason", "UNKNOWN"));
        assertThat(runs.findById(run.getId().value()).orElseThrow().getItems().getFirst().getMetadata())
                .containsEntry("git.mrIid", "11").containsEntry("git.sourceSha", "a".repeat(40));
    }

    @Test
    void candidateDiscoveryRequiresCurrentWindowProtocolActiveStateAndPendingIntent() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        String id = run.getId().value();
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(id);

        // 有意构造历史/其他协议行，仅验证候选发现边界，不派发这些反例。
        for (int version : List.of(1, 2)) {
            jdbc.update("update run set execution_version=? where id=?", version, id);
            assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(id);
        }
        jdbc.update("update run set execution_version=? where id=?", Run.ORCHESTRATION_PROTOCOL_VERSION, id);
        for (String type : List.of("WINDOW_CLEANUP", "VERSION_UPDATE", "ATTACH_ITERATION")) {
            jdbc.update("update run set run_type=? where id=?", type, id);
            assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(id);
        }
        jdbc.update("update run set run_type='WINDOW_ORCHESTRATION' where id=?", id);
        for (String status : List.of("PLANNED", "DONE", "PARTIAL_FAILED", "FAILED")) {
            jdbc.update("update run set execution_status=? where id=?", status, id);
            assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(id);
        }
        jdbc.update("update run set execution_status='NEEDS_REVIEW' where id=?", id);
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(id);
        jdbc.update("update run_task set status='SKIPPED' where run_id=?", id);
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(id);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void candidatePagesAreBoundedStableAndDoNotClaimTasks() {
        var expected = new java.util.ArrayList<String>();
        for (int index = 0; index < 3; index++) {
            if (index > 0) fixtures();
            expected.add(admit(List.of(firstRepo), List.of(firstIteration), true).getId().value());
        }
        var discovered = new java.util.ArrayList<String>();
        String cursor = null;
        boolean ended = false;
        for (int page = 0; page < 1000; page++) {
            var candidates = runs.findTaskCandidateRunIds(cursor, 1);
            assertThat(candidates).hasSizeLessThanOrEqualTo(1);
            if (candidates.isEmpty()) { ended = true; break; }
            String next = candidates.getFirst();
            if (cursor != null) assertThat(next.compareTo(cursor)).isPositive();
            discovered.add(next);
            cursor = next;
        }
        assertThat(ended).as("bounded traversal must reach the end").isTrue();
        assertThat(discovered).isSorted().doesNotHaveDuplicates().containsAll(expected);
        for (String id : expected) {
            assertThat(runs.findTasks(id)).hasSize(4).allMatch(task -> task.status().equals("PENDING"));
            assertThat(jdbc.queryForObject("select count(*) from run_task where run_id=? and claim_token is not null",
                    Integer.class, id)).isZero();
        }
        assertThatThrownBy(() -> runs.findTaskCandidateRunIds(null, 0))
                .hasRootCauseInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 1000");
        assertThatThrownBy(() -> runs.findTaskCandidateRunIds(null, 1001))
                .hasRootCauseInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 1000");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void discoveryNeverReclaimsRunningOrUnknownTasks() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        String id = run.getId().value();
        var claim = runs.claimNextTask(id).orElseThrow();
        var runningTasks = runs.findTasks(id);
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(id);
        assertThat(runs.claimNextTask(id)).isEmpty();
        assertThat(runs.findTasks(id)).isEqualTo(runningTasks);
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                claim.fence().taskId())).isEqualTo(claim.fence().claimToken());

        runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN), Map.of("git.blockReason", "UNKNOWN"));
        var unresolved = runs.findTasks(id);
        assertThat(unresolved.getFirst().status()).isEqualTo("UNKNOWN");
        assertThat(runs.claimNextTask(id)).isEmpty();
        assertThat(runs.findTasks(id)).isEqualTo(unresolved);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                claim.fence().taskId())).isEqualTo(1);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }


    @Test
    void flywayCreatesNullableClaimExpiryWithoutReplacingFirstStart() {
        assertClaimExpiryColumn(jdbc.queryForObject("select current_schema()", String.class));
    }

    @Test
    void localHibernateUpdateAddsTheSameNullableClaimExpiry() {
        String schema = "lease_local_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        // local profile uses Hibernate update; start with its existing task table shape.
        jdbc.execute("create table " + schema + ".run_task (id varchar(255) primary key, started_at timestamptz)");
        var registry = new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.connection.url", DB.getJdbcUrl())
                .applySetting("hibernate.connection.username", DB.getUsername())
                .applySetting("hibernate.connection.password", DB.getPassword())
                .applySetting("hibernate.default_schema", schema)
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .build();
        try {
            try (var factory = new org.hibernate.boot.MetadataSources(registry)
                    .addAnnotatedClass(io.releasehub.infrastructure.persistence.run.RunTaskJpaEntity.class)
                    .buildMetadata().buildSessionFactory()) {
                assertClaimExpiryColumn(schema);
            }
        } finally {
            org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry);
            jdbc.execute("drop schema " + schema + " cascade");
        }
    }

    private void assertClaimExpiryColumn(String schema) {
        var columns = jdbc.queryForList("""
                select column_name, data_type, is_nullable from information_schema.columns
                where table_schema=? and table_name='run_task' and column_name in ('started_at', 'claim_expires_at')
                """, schema);
        assertThat(columns).extracting(column -> column.get("column_name"))
                .containsExactlyInAnyOrder("started_at", "claim_expires_at");
        assertThat(columns.stream().filter(column -> "claim_expires_at".equals(column.get("column_name"))))
                .singleElement().satisfies(column -> {
                    assertThat(column.get("data_type")).isEqualTo("timestamp with time zone");
                    assertThat(column.get("is_nullable")).isEqualTo("YES");
                });
    }


    @Test
    void claimDeadlineUsesDatabaseTimeWhenWorkerClockIsAhead() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        Instant workerTime = Instant.parse("2040-01-01T00:00:00Z");
        org.mockito.Mockito.doReturn(workerTime).when(clock).instant();
        Instant before = databaseNow();
        var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
        Instant after = databaseNow();

        assertThat(claim.startedAt()).isEqualTo(workerTime);
        assertThat(claimExpiry(claim)).isNotNull()
                .isBetween(before.plusSeconds(3600), after.plusSeconds(3600));
        assertThat(runs.findTasks(run.getId().value()).getFirst().startedAt()).isEqualTo(workerTime);
    }

    @Test
    void expiredLastTaskIsDiscoveredWithoutAnyPendingTask() {
        var claim = claimLastTask();
        assertThat(runs.findTasks(claim.runId()).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "RUNNING");
        expireClaim(claim);

        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(claim.runId());
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?",
                Integer.class, claim.fence().taskId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?",
                Integer.class, claim.fence().taskId())).isZero();
    }

    @Test
    void reclaimPreservesFirstStartAndPrefixWhileRejectingOldCompletion() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        for (int index = 0; index < 3; index++) completeSuccess(runs.claimNextTask(run.getId().value()).orElseThrow());
        var prefix = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id",
                run.getItems().getFirst().getId().value());
        int executedOrder = runs.findById(run.getId().value()).orElseThrow().getItems().getFirst().getExecutedOrder();
        Instant firstTime = Instant.parse("2040-01-01T00:00:00Z");
        org.mockito.Mockito.doReturn(firstTime).when(clock).instant();
        var previous = runs.claimNextTask(run.getId().value()).orElseThrow();
        expireClaim(previous);
        Instant currentTime = Instant.parse("2020-01-01T00:00:00Z");
        org.mockito.Mockito.doReturn(currentTime).when(clock).instant();

        var candidate = runs.claimNextTask(run.getId().value());
        assertThat(candidate).as("the original task with no write intent can be reclaimed").isPresent();
        var current = candidate.orElseThrow();
        assertThat(current.fence().taskId()).isEqualTo(previous.fence().taskId());
        assertThat(current.fence().claimToken()).isNotEqualTo(previous.fence().claimToken());
        assertThat(current.startedAt()).isEqualTo(currentTime);
        assertThat(current.metadata()).isEqualTo(previous.metadata());
        assertThat(runs.findTasks(current.runId()).getLast().startedAt()).isEqualTo(firstTime);
        assertThat(runs.findById(current.runId()).orElseThrow().getItems().getFirst().getExecutedOrder())
                .isEqualTo(executedOrder);
        assertThatThrownBy(() -> runs.completeTask(previous,
                new RunStep(ActionType.TRY_MERGE, RunItemResult.MERGED, firstTime, firstTime.plusMillis(1), "stale worker"),
                Map.of("git.mergeSha", "b".repeat(40), "git.mrDisposition", "MR_REUSED")))
                .isInstanceOf(BusinessException.class);

        Run completed = runs.completeTask(current,
                new RunStep(ActionType.TRY_MERGE, RunItemResult.MERGED, currentTime, currentTime.plusMillis(1), "current worker"),
                Map.of("git.mergeSha", "b".repeat(40), "git.mrDisposition", "MR_REUSED"));
        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? and task_id<>? order by task_id",
                current.itemId(), current.fence().taskId())).isEqualTo(prefix);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?",
                Integer.class, current.fence().taskId())).isEqualTo(1);
        assertThat(completed.getItems().getFirst().getSteps().getLast().startAt()).isEqualTo(currentTime);
    }

    @Test
    void twoWorkersCannotBothReclaimTheSameExpiredTask() throws Exception {
        var previous = claimLastTask();
        expireClaim(previous);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return runs.claimNextTask(previous.runId()); });
            var second = pool.submit(() -> { start.await(); return runs.claimNextTask(previous.runId()); });
            start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(java.util.Optional::isPresent)).hasSize(1);
            var current = results.stream().flatMap(java.util.Optional::stream).findFirst().orElseThrow();
            assertThat(current.fence().taskId()).isEqualTo(previous.fence().taskId());
            assertThat(current.fence().claimToken()).isNotEqualTo(previous.fence().claimToken());
            assertThat(claimExpiry(current)).isAfter(databaseNow());
            assertThat(runs.claimNextTask(previous.runId())).isEmpty();
        }
    }

    @Test
    void claimDeadlineStartsAfterWaitingForTheRunLock() throws Exception {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var waiterStarted = new CountDownLatch(1);
        var waiterPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    jdbc.queryForObject("select id from run where id=? for update", String.class, run.getId().value());
                    locked.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture lock was not released");
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    return null;
                }));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var waiter = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    waiterPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    waiterStarted.countDown();
                    return runs.claimNextTask(run.getId().value());
                }));
                assertThat(waiterStarted.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject(
                            "select count(*) from pg_stat_activity where pid=? and wait_event_type='Lock'",
                            Integer.class, waiterPid.get()) == 1;
                    if (waiting) break;
                    Thread.sleep(10);
                }
                assertThat(waiting).as("the claimant actually waited on the PostgreSQL row lock").isTrue();
                Instant earliestClaimTime = databaseNow();
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                var claim = waiter.get(10, TimeUnit.SECONDS).orElseThrow();
                assertThat(claimExpiry(claim)).isNotNull()
                        .isBetween(earliestClaimTime.plusSeconds(3600), databaseNow().plusSeconds(3600));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void nullFutureAndUnknownClaimsAreNotRecoveryCandidates() {
        var claim = claimLastTask();
        jdbc.update("update run_task set claim_expires_at=null where id=?", claim.fence().taskId());
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();

        jdbc.update("update run_task set claim_expires_at=clock_timestamp()+interval '1 day' where id=?",
                claim.fence().taskId());
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();

        runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN), Map.of("git.blockReason", "UNKNOWN"));
        expireClaim(claim);
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();
        assertThat(runs.findTasks(claim.runId()).getLast().status()).isEqualTo("UNKNOWN");
    }


    @Test
    void aCurrentTaskStepPreventsRecoveryButSuccessfulPrefixStepsDoNot() {
        var claim = claimLastTask();
        expireClaim(claim);
        // Isolated inconsistent result fixture: a committed Step cannot be silently replayed.
        jdbc.update("""
                insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id)
                values (?,?,?,?,?,?,?)
                """, claim.itemId(), ActionType.TRY_MERGE.name(), RunItemResult.MERGED.name(),
                java.sql.Timestamp.from(claim.startedAt()), java.sql.Timestamp.from(claim.startedAt().plusMillis(1)),
                "retained result evidence", claim.fence().taskId());
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?",
                Integer.class, claim.fence().taskId())).isEqualTo(1);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void incompleteRunningClaimsCannotRegainOwnership() {
        var claim = claimLastTask();
        expireClaim(claim);
        jdbc.update("update run_task set claim_token=null where id=?", claim.fence().taskId());
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();

        jdbc.update("update run_task set claim_token=?,started_at=null where id=?",
                claim.fence().claimToken(), claim.fence().taskId());
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();
        assertThat(runs.findTasks(claim.runId()).getLast().startedAt()).isNull();

        jdbc.update("update run_task set started_at=? where id=?",
                java.sql.Timestamp.from(claim.startedAt()), claim.fence().taskId());
        assertThat(jdbc.update("update run_item set executed_order=0 where id=?", claim.itemId())).isEqualTo(1);
        // Discovery only offers a candidate; the locked claim must reject incomplete execution facts.
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(claim.runId());
        String factsSql = """
                select t.claim_token,t.claim_expires_at,i.executed_order
                from run_task t join run_item i on i.id=t.target_id where t.id=?
                """;
        var before = jdbc.queryForMap(factsSql, claim.fence().taskId());
        var reclaimed = runs.claimNextTask(claim.runId());
        var after = jdbc.queryForMap(factsSql, claim.fence().taskId());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(reclaimed).as("missing execution order must not grant ownership").isEmpty();
            softly.assertThat(after.get("claim_token")).as("original token").isEqualTo(before.get("claim_token"));
            softly.assertThat(after.get("claim_expires_at")).as("original expiry").isEqualTo(before.get("claim_expires_at"));
            softly.assertThat(after.get("executed_order")).as("missing order remains unmodified").isEqualTo(0);
        });
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void anotherRepositoryCanBeReclaimedWhileTheRunNeedsReview() {
        Run run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration), true);
        var unknown = runs.claimNextTask(run.getId().value()).orElseThrow();
        runs.completeTask(unknown, step(unknown, RunItemResult.UNKNOWN), Map.of("git.blockReason", "UNKNOWN"));
        var other = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(other.repo()).isNotEqualTo(unknown.repo());
        expireClaim(other);
        assertThat(runs.findById(run.getId().value()).orElseThrow().getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(run.getId().value());

        var candidate = runs.claimNextTask(run.getId().value());
        assertThat(candidate).isPresent();
        var current = candidate.orElseThrow();
        assertThat(current.fence().taskId()).isEqualTo(other.fence().taskId());
        assertThat(current.fence().claimToken()).isNotEqualTo(other.fence().claimToken());
        assertThat(current.repo()).isEqualTo(other.repo());
        completeSuccess(current);
        assertThat(runs.findTasks(run.getId().value()).stream()
                .filter(task -> task.taskId().equals(unknown.fence().taskId()))).singleElement()
                .satisfies(task -> assertThat(task.status()).isEqualTo("UNKNOWN"));
        assertThat(runs.findById(run.getId().value()).orElseThrow().getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
    }

    @Test
    void expiryDoesNotReleaseTheActiveRunCleanupBarrier() {
        var claim = claimLastTask();
        var window = windows.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        window.publish(Instant.now());
        windows.save(window);
        expireClaim(claim);

        int priorCleanupRuns = jdbc.queryForObject("select count(*) from run where run_type='WINDOW_CLEANUP'", Integer.class);
        assertThatThrownBy(() -> plans.admitCleanup(windowId, "cleanup-operator", false))
                .isInstanceOf(BusinessException.class);
        assertThat(windows.findById(ReleaseWindowId.of(windowId)).orElseThrow().getStatus())
                .isEqualTo(ReleaseWindowStatus.PUBLISHED);
        assertThat(jdbc.queryForObject("select count(*) from run where run_type='WINDOW_CLEANUP'",
                Integer.class)).isEqualTo(priorCleanupRuns);

        completeSuccess(claim);
        assertThat(plans.admitCleanup(windowId, "cleanup-operator", false).run().getRunType())
                .isEqualTo(io.releasehub.domain.run.RunType.WINDOW_CLEANUP);
        assertThat(windows.findById(ReleaseWindowId.of(windowId)).orElseThrow().getStatus())
                .isEqualTo(ReleaseWindowStatus.CLOSED);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ActionType.class, names = {"ENSURE_FEATURE", "ENSURE_RELEASE"})
    void appliedBranchIsProjectedBySingleStepWithoutGitAndWithoutRewritingHistory(ActionType action) {
        Instant firstStart = Instant.parse("2024-01-01T00:00:00Z");
        Instant httpAt = Instant.parse("2024-02-01T00:00:00Z");
        Instant projectionAt = Instant.parse("2023-12-01T00:00:00Z");
        org.mockito.Mockito.doReturn(firstStart).when(clock).instant();
        var claim = claimBranchTask(action);
        org.mockito.Mockito.doReturn(httpAt).when(clock).instant();
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        Instant expiry = claimExpiry(claim);
        var operationBefore = jdbc.queryForMap("select * from git_operation where id=?", operationId);
        var eventsBefore = jdbc.queryForList(
                "select * from git_operation_event where operation_id=? order by event_sequence", operationId);
        var prefixBefore = jdbc.queryForList(
                "select * from run_step where run_item_id=? and task_id<>? order by action_type",
                claim.itemId(), claim.fence().taskId());
        var itemBefore = runs.findById(claim.runId()).orElseThrow().getItems().getFirst();
        org.mockito.Mockito.doReturn(projectionAt).when(clock).instant();

        assertThat(taskService.executeNextTask(claim.runId())).isTrue();

        var task = runs.findTasks(claim.runId()).stream()
                .filter(value -> value.taskId().equals(claim.fence().taskId())).findFirst().orElseThrow();
        assertThat(task.status()).isEqualTo("COMPLETED");
        assertThat(task.startedAt()).isEqualTo(firstStart);
        assertThat(claimExpiry(claim)).isEqualTo(expiry);
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
        var restored = runs.findById(claim.runId()).orElseThrow();
        assertThat(restored.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
        var item = restored.getItems().getFirst();
        assertThat(item.getId()).isEqualTo(itemBefore.getId());
        assertThat(item.getExecutedOrder()).isEqualTo(itemBefore.getExecutedOrder());
        assertThat(item.getMetadata()).containsAllEntriesOf(itemBefore.getMetadata())
                .containsEntry("git.projectId", projectionProjectId());
        assertThat(item.getSteps().stream().filter(value -> value.actionType() == action))
                .singleElement().satisfies(value -> {
                    assertThat(value.result()).isEqualTo(RunItemResult.CREATED);
                    assertThat(value.startAt()).isEqualTo(projectionAt);
                    assertThat(value.endAt()).isEqualTo(projectionAt);
                });
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? and task_id<>? order by action_type",
                claim.itemId(), claim.fence().taskId())).isEqualTo(prefixBefore);
        assertThat(runs.findTasks(claim.runId()).stream()
                .filter(value -> value.taskOrder() > task.taskOrder()).map(RunPort.TaskView::status))
                .containsOnly("PENDING");
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(operationBefore);
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(eventsBefore);
        var guard = projectionGuard(operationId);
        assertThat(guard).containsEntry("session_id", null).containsEntry("operation_id", null)
                .containsEntry("reason", null).containsEntry("generation", operationBefore.get("generation"));
        if (action == ActionType.ENSURE_RELEASE) {
            var binding = bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId),
                    IterationKey.of(firstIteration)).orElseThrow();
            assertThat(binding.getReleaseBranch()).isEqualTo(claim.metadata().get("plan.releaseBranch"));
            assertThat(binding.getBranchCreated()).isTrue();
        }
        assertThatThrownBy(() -> runs.completeTask(claim, step(claim, RunItemResult.CREATED),
                Map.of("git.projectId", projectionProjectId()))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                claim.fence().taskId())).isEqualTo(1);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void appliedBranchProjectionRollsBackTogetherWithItsGuard() {
        var claim = claimBranchTask(ActionType.ENSURE_RELEASE);
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        var before = branchProjectionFacts(claim, operationId);

        Boolean projected = new TransactionTemplate(transactions).execute(tx -> {
            boolean progressed = taskService.executeNextTask(claim.runId());
            if (progressed) {
                assertThat(projectionGuard(operationId)).containsEntry("session_id", null)
                        .containsEntry("operation_id", null);
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                        claim.fence().taskId())).isEqualTo(1);
            }
            tx.setRollbackOnly();
            return progressed;
        });

        assertThat(projected).isTrue();
        assertThat(branchProjectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "MISSING_PROOF", "LEGACY_SUMMARY", "STRING_STATUS", "WRONG_COMMIT_ALIAS", "WRONG_ATTEMPT", "WRONG_GUARD",
            "WRONG_SOURCE_REF", "WRONG_FROZEN_SHA", "WRONG_BRANCH", "NON_BRANCH_ACTION",
            "PERSISTED_PROJECT_MISMATCH", "NOT_EXPIRED", "MISSING_FIRST_START", "MISSING_ORDER", "CURRENT_STEP"
    })
    void appliedBranchProjectionRejectsUnprovenOrMismatchedFacts(String contradiction) {
        var claim = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        String branch = claim.metadata().get("plan.featureBranch");
        String sha = "a".repeat(40);
        int changed = switch (contradiction) {
            case "MISSING_PROOF" -> jdbc.update(
                    "delete from git_operation_event where operation_id=? and event_sequence=2", operationId);
            case "LEGACY_SUMMARY" -> jdbc.update(
                    "update git_operation_event set evidence_json=? where operation_id=? and event_sequence=2",
                    json.valueToTree(Map.of("httpStatus", 201, "name", branch, "commitSha", sha)).toString(), operationId);
            case "STRING_STATUS" -> jdbc.update(
                    "update git_operation_event set evidence_json=? where operation_id=? and event_sequence=2",
                    json.valueToTree(Map.of("httpStatus", "201", "name", branch, "commit", Map.of("id", sha))).toString(),
                    operationId);
            case "WRONG_COMMIT_ALIAS" -> jdbc.update(
                    "update git_operation_event set evidence_json=? where operation_id=? and event_sequence=2",
                    json.valueToTree(Map.of("httpStatus", 201, "name", branch,
                            "commit", Map.of("id", "b".repeat(40), "sha", sha), "object", Map.of("id", sha))).toString(),
                    operationId);
            case "WRONG_ATTEMPT" -> jdbc.update(
                    "update git_operation_event set attempt_id=? where operation_id=? and event_sequence=2",
                    UUID.randomUUID().toString(), operationId);
            case "WRONG_GUARD" -> jdbc.update(
                    "update repo_execution_guard set generation=generation+1 where operation_id=?", operationId);
            case "WRONG_SOURCE_REF" -> jdbc.update("update git_operation set target_json=? where id=?",
                    branchTarget(branch, "different-base", sha), operationId);
            case "WRONG_FROZEN_SHA" -> jdbc.update("update git_operation set target_json=? where id=?",
                    branchTarget(branch, claim.metadata().get("plan.defaultBranch"), "b".repeat(40)), operationId);
            case "WRONG_BRANCH" -> {
                String different = branch + "-different";
                assertThat(jdbc.update(
                        "update git_operation_event set evidence_json=? where operation_id=? and event_sequence=2",
                        branchProof(different, sha), operationId)).isEqualTo(1);
                yield jdbc.update("update git_operation set target_json=? where id=?",
                        branchTarget(different, claim.metadata().get("plan.defaultBranch"), sha), operationId);
            }
            case "NON_BRANCH_ACTION" -> jdbc.update(
                    "update git_operation set action='CREATE_MR' where id=?", operationId);
            case "PERSISTED_PROJECT_MISMATCH" -> jdbc.update(
                    "insert into run_item_metadata(run_item_id,metadata_key,metadata_value) values (?,'git.projectId',?)",
                    claim.itemId(), projectionProjectId() + "1");
            case "NOT_EXPIRED" -> jdbc.update(
                    "update run_task set claim_expires_at=clock_timestamp()+interval '1 hour' where id=?",
                    claim.fence().taskId());
            case "MISSING_FIRST_START" -> jdbc.update(
                    "update run_task set started_at=null where id=?", claim.fence().taskId());
            case "MISSING_ORDER" -> jdbc.update(
                    "update run_item set executed_order=0 where id=?", claim.itemId());
            case "CURRENT_STEP" -> jdbc.update(
                    "insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) values (?,?,?,?,?,?,?)",
                    claim.itemId(), claim.actionType().name(), RunItemResult.CREATED.name(),
                    java.sql.Timestamp.from(claim.startedAt()), java.sql.Timestamp.from(claim.startedAt()),
                    "isolated pre-existing projection", claim.fence().taskId());
            default -> throw new AssertionError(contradiction);
        };
        assertThat(changed).isEqualTo(1);
        var before = branchProjectionFacts(claim, operationId);

        assertThat(taskService.executeNextTask(claim.runId())).isFalse();

        assertThat(branchProjectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void unknownOwnerResultCannotBeReplacedByAppliedBranchProjection() {
        var claim = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN),
                Map.of("git.projectId", projectionProjectId(), "git.blockReason", "UNKNOWN"));
        var before = branchProjectionFacts(claim, operationId);
        assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId))
                .isEqualTo("NEEDS_REVIEW");

        assertThat(taskService.executeNextTask(claim.runId())).isFalse();

        assertThat(branchProjectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void twoProjectionWorkersCommitExactlyOneBranchResult() throws Exception {
        var claim = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        Instant expiry = claimExpiry(claim);
        var operationBefore = jdbc.queryForMap("select * from git_operation where id=?", operationId);
        var eventsBefore = jdbc.queryForList(
                "select * from git_operation_event where operation_id=? order by event_sequence", operationId);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return runs.projectNextAppliedBranchTask(claim.runId());
            });
            var second = pool.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return runs.projectNextAppliedBranchTask(claim.runId());
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(runs.findTasks(claim.runId()).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "PENDING", "PENDING", "PENDING");
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                claim.fence().taskId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
        assertThat(claimExpiry(claim)).isEqualTo(expiry);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(operationBefore);
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(eventsBefore);
        var after = branchProjectionFacts(claim, operationId);
        assertThat(runs.projectNextAppliedBranchTask(claim.runId())).isFalse();
        assertThat(branchProjectionFacts(claim, operationId)).isEqualTo(after);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void successfulOwnerWinsBeforeBranchProjectionAndKeepsItsResult() {
        var claim = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        var ownerStep = new RunStep(claim.actionType(), RunItemResult.CREATED,
                claim.startedAt(), Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS), "original owner result");
        runs.completeTask(claim, ownerStep, Map.of("git.projectId", projectionProjectId()));
        var before = branchProjectionFacts(claim, operationId);

        assertThat(runs.projectNextAppliedBranchTask(claim.runId())).isFalse();

        assertThat(branchProjectionFacts(claim, operationId)).isEqualTo(before);
        assertThat(runs.findById(claim.runId()).orElseThrow().getItems().getFirst().getSteps())
                .containsExactly(ownerStep);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void appliedBranchProofRequiresTheExistingResultTransactionAndDoesNotChangeFacts() {
        var claim = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        var before = branchProjectionFacts(claim, operationId);

        assertThatThrownBy(() -> journal.confirmedAppliedBranchProject(claim))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var project = new TransactionTemplate(transactions).execute(tx -> journal.confirmedAppliedBranchProject(claim));

        assertThat(project).contains(projectionProjectId());
        assertThat(branchProjectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void anotherItemPersistedProjectCannotBeIgnoredByBranchProjection() {
        var run = admit(List.of(firstRepo), List.of(firstIteration, secondIteration), true);
        var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        String otherItem = run.getItems().stream().map(item -> item.getId().value())
                .filter(id -> !id.equals(claim.itemId())).findFirst().orElseThrow();
        assertThat(jdbc.update(
                "insert into run_item_metadata(run_item_id,metadata_key,metadata_value) values (?,'git.projectId',?)",
                otherItem, projectionProjectId() + "1")).isEqualTo(1);
        var before = branchProjectionFacts(claim, operationId);
        var otherBefore = jdbc.queryForList(
                "select * from run_item_metadata where run_item_id=? order by metadata_key", otherItem);

        assertThat(taskService.executeNextTask(claim.runId())).isFalse();

        assertThat(branchProjectionFacts(claim, operationId)).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from run_item_metadata where run_item_id=? order by metadata_key",
                otherItem)).isEqualTo(otherBefore);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    private RunPort.TaskClaim claimBranchTask(ActionType action) {
        var run = admit(List.of(firstRepo), List.of(firstIteration), true);
        var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
        if (action == ActionType.ENSURE_RELEASE) {
            runs.completeTask(claim, step(claim, RunItemResult.ALREADY_EXISTS),
                    Map.of("git.projectId", projectionProjectId()));
            claim = runs.claimNextTask(run.getId().value()).orElseThrow();
            // The frozen plan survives before the release binding's result projection is committed.
            assertThat(jdbc.update(
                    "update window_iteration set release_branch=null,branch_created=false where window_id=? and iteration_key=?",
                    windowId, firstIteration)).isEqualTo(1);
        }
        assertThat(claim.actionType()).isEqualTo(action);
        return claim;
    }

    private String recordAppliedBranch(RunPort.TaskClaim claim) {
        String baseUrl = "https://gitlab.invalid";
        settings.saveGitLab(new SettingsPort.SettingsGitLab(baseUrl, "isolated-projection-token"));
        var repo = repositories.findById(claim.repo()).orElseThrow();
        String branch = claim.metadata().get(claim.actionType() == ActionType.ENSURE_FEATURE
                ? "plan.featureBranch" : "plan.releaseBranch");
        String sha = "a".repeat(40);
        String target = branchTarget(branch, claim.metadata().get("plan.defaultBranch"), sha);
        String operationId = journal.begin(new GitOperationJournalPort.Command(UUID.randomUUID().toString(),
                claim.repo().value(), GitOperationJournalPort.mappingHash("GITLAB", repo.getCloneUrl()),
                GitOperationJournalPort.remoteKey("GITLAB", baseUrl, projectionProjectId()), "GITLAB", baseUrl,
                projectionProjectId(), "CREATE_BRANCH", target, GitOperationJournalPort.mappingHash("CREATE_BRANCH", target),
                claim.runId(), claim.operator(), claim.fence().claimToken(), claim.fence()));
        journal.sending(operationId);
        // Case-insensitive SHA identity follows strict SHA validation; branch and sourceRef remain exact.
        journal.finish(operationId, GitWriteResult.Outcome.APPLIED,
                branchProof(branch, sha.toUpperCase(java.util.Locale.ROOT)), null);
        return operationId;
    }

    private String projectionProjectId() {
        // Independent numeric remote identity avoids sharing a retained guard with another isolated fixture.
        return Long.toString(Long.parseLong(windowId.replace("-", "").substring(0, 13), 16) + 1000);
    }

    private String branchTarget(String branch, String sourceRef, String sha) {
        return json.valueToTree(Map.of("branch", branch, "sourceRef", sourceRef, "ref", sha)).toString();
    }

    private String branchProof(String branch, String sha) {
        return json.valueToTree(Map.of("httpStatus", 201, "name", branch, "commit", Map.of("id", sha))).toString();
    }

    private Map<String, Object> projectionGuard(String operationId) {
        return jdbc.queryForMap("select * from repo_execution_guard where remote_key="
                + "(select remote_key from git_operation where id=?)", operationId);
    }

    private Map<String, Object> branchProjectionFacts(RunPort.TaskClaim claim, String operationId) {
        return Map.of(
                "run", jdbc.queryForMap("select * from run where id=?", claim.runId()),
                "tasks", jdbc.queryForList("select * from run_task where run_id=? order by task_order", claim.runId()),
                "item", jdbc.queryForMap("select * from run_item where id=?", claim.itemId()),
                "steps", jdbc.queryForList("select * from run_step where run_item_id=? order by action_type", claim.itemId()),
                "metadata", jdbc.queryForList(
                        "select * from run_item_metadata where run_item_id=? order by metadata_key", claim.itemId()),
                "operation", jdbc.queryForMap("select * from git_operation where id=?", operationId),
                "events", jdbc.queryForList(
                        "select * from git_operation_event where operation_id=? order by event_sequence", operationId),
                "guard", projectionGuard(operationId),
                "binding", jdbc.queryForMap("select * from window_iteration where window_id=? and iteration_key=?",
                        windowId, firstIteration));
    }

    private RunPort.TaskClaim claimLastTask() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        for (int index = 0; index < 3; index++) completeSuccess(runs.claimNextTask(run.getId().value()).orElseThrow());
        return runs.claimNextTask(run.getId().value()).orElseThrow();
    }

    private void expireClaim(RunPort.TaskClaim claim) {
        // Targeted failure injection in the isolated database; no production sleep or status rewrite.
        assertThat(jdbc.update("update run_task set claim_expires_at=clock_timestamp()-interval '1 second' where id=?",
                claim.fence().taskId())).isEqualTo(1);
    }

    private Instant claimExpiry(RunPort.TaskClaim claim) {
        return jdbc.queryForObject("select claim_expires_at from run_task where id=?", (row, index) -> {
            var value = row.getObject(1, java.time.OffsetDateTime.class);
            return value == null ? null : value.toInstant();
        }, claim.fence().taskId());
    }

    private Instant databaseNow() {
        return jdbc.queryForObject("select clock_timestamp()", (row, index) ->
                row.getObject(1, java.time.OffsetDateTime.class).toInstant());
    }

    private Run admit(List<String> repos, List<String> iterations, boolean failFast) {
        return runs.findById(plans.admit(windowId, repos, iterations, failFast, "operator", UUID.randomUUID().toString()).runId()).orElseThrow();
    }

    private RunStep step(RunPort.TaskClaim claim, RunItemResult result) {
        return new RunStep(claim.actionType(), result, claim.startedAt(), Instant.now(), result.name());
    }

    private String projectId(RunPort.TaskClaim claim) {
        return claim.repo().value().equals(firstRepo) ? "17" : "18";
    }

    private Run completeSuccess(RunPort.TaskClaim claim) {
        RunItemResult result;
        Map<String, String> facts;
        switch (claim.actionType()) {
            case ENSURE_FEATURE, ENSURE_RELEASE -> {
                result = RunItemResult.ALREADY_EXISTS;
                facts = Map.of("git.projectId", projectId(claim));
            }
            case ENSURE_MR -> {
                result = RunItemResult.MR_REUSED;
                facts = Map.of("git.projectId", projectId(claim), "git.sourceBranch", claim.metadata().get("plan.featureBranch"),
                        "git.targetBranch", claim.metadata().get("plan.releaseBranch"), "git.mrIid", "11",
                        "git.mrUrl", "https://gitlab.invalid/project/-/merge_requests/11", "git.sourceSha", "a".repeat(40),
                        "git.mrDisposition", "MR_REUSED");
            }
            case TRY_MERGE -> {
                result = RunItemResult.MERGED;
                facts = Map.of("git.mergeSha", "b".repeat(40), "git.mrDisposition", "MR_REUSED");
            }
            default -> throw new AssertionError(claim.actionType());
        }
        return runs.completeTask(claim, step(claim, result), facts);
    }

    private String admitAfter(CountDownLatch start, String iteration, String operator) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        try {
            plans.admit(windowId, List.of(firstRepo), List.of(iteration), true, operator, java.util.UUID.randomUUID().toString());
            return "ADMITTED";
        } catch (BusinessException error) {
            return error.getCode();
        }
    }
}
