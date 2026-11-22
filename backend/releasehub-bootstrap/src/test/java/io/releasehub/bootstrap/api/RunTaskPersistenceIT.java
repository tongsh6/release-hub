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
    @Autowired jakarta.persistence.EntityManager entityManager;
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
        String operationId = beginBranchIntent(first, 40);
        journal.sending(operationId, first.fence());
        journal.finish(operationId, first.fence(), GitWriteResult.Outcome.UNKNOWN,
                "{}", "RESPONSE_UNCONFIRMED");
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

        String operationId = beginBranchIntent(claim, 40);
        journal.sending(operationId, claim.fence());
        journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.UNKNOWN,
                "{}", "RESPONSE_UNCONFIRMED");
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
        var claim = claimMergedMrTask("MR_REUSED", 40);
        jdbc.update("update run_task set claim_expires_at=null where id=?", claim.fence().taskId());
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();

        jdbc.update("update run_task set claim_expires_at=clock_timestamp()+interval '1 day' where id=?",
                claim.fence().taskId());
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();

        String operationId = beginMergeIntent(claim);
        journal.sending(operationId, claim.fence());
        journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.UNKNOWN,
                "{}", "RESPONSE_UNCONFIRMED");
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
        String operationId = beginBranchIntent(unknown, 40);
        journal.sending(operationId, unknown.fence());
        journal.finish(operationId, unknown.fence(), GitWriteResult.Outcome.UNKNOWN,
                "{}", "RESPONSE_UNCONFIRMED");
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
        var before = projectionFacts(claim, operationId);

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
        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
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
        var before = projectionFacts(claim, operationId);

        assertThat(taskService.executeNextTask(claim.runId())).isFalse();

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void unknownOwnerResultCannotBeReplacedByAppliedBranchProjection() {
        var claim = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN),
                Map.of("git.projectId", projectionProjectId(), "git.blockReason", "UNKNOWN"));
        var before = projectionFacts(claim, operationId);
        assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId))
                .isEqualTo("NEEDS_REVIEW");

        assertThat(taskService.executeNextTask(claim.runId())).isFalse();

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
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
                return runs.projectNextAppliedTask(claim.runId());
            });
            var second = pool.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return runs.projectNextAppliedTask(claim.runId());
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
        var after = projectionFacts(claim, operationId);
        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();
        assertThat(projectionFacts(claim, operationId)).isEqualTo(after);
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
        var before = projectionFacts(claim, operationId);

        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        assertThat(runs.findById(claim.runId()).orElseThrow().getItems().getFirst().getSteps())
                .containsExactly(ownerStep);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void appliedBranchProofRequiresTheExistingResultTransactionAndDoesNotChangeFacts() {
        var claim = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = recordAppliedBranch(claim);
        expireClaim(claim);
        var before = projectionFacts(claim, operationId);

        assertThatThrownBy(() -> journal.confirmedAppliedBranchProject(claim))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var project = new TransactionTemplate(transactions).execute(tx -> journal.confirmedAppliedBranchProject(claim));

        assertThat(project).contains(projectionProjectId());
        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
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
        var before = projectionFacts(claim, operationId);
        var otherBefore = jdbc.queryForList(
                "select * from run_item_metadata where run_item_id=? order by metadata_key", otherItem);

        assertThat(taskService.executeNextTask(claim.runId())).isFalse();

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from run_item_metadata where run_item_id=? order by metadata_key",
                otherItem)).isEqualTo(otherBefore);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void appliedCreatedMrRestoresOnlyItsCheckpointWithoutGitOrRewritingHistory() {
        Instant firstStart = Instant.parse("2024-01-01T00:00:00Z");
        Instant httpAt = Instant.parse("2024-02-01T00:00:00Z");
        Instant projectionAt = Instant.parse("2023-12-01T00:00:00Z");
        org.mockito.Mockito.doReturn(firstStart).when(clock).instant();
        var claim = claimMrTask();
        org.mockito.Mockito.doReturn(httpAt).when(clock).instant();
        String operationId = recordAppliedCreatedMr(claim);
        expireClaim(claim);
        var before = projectionFacts(claim, operationId);
        var taskHistory = new java.util.LinkedHashMap<>(
                jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId()));
        taskHistory.keySet().removeAll(Set.of("status", "finished_at", "error_message"));
        var otherTasks = jdbc.queryForList(
                "select * from run_task where run_id=? and id<>? order by task_order",
                claim.runId(), claim.fence().taskId());
        var prefix = jdbc.queryForList(
                "select * from run_step where run_item_id=? order by action_type", claim.itemId());
        var itemBefore = runs.findById(claim.runId()).orElseThrow().getItems().getFirst();
        var expectedMetadata = new java.util.LinkedHashMap<>(itemBefore.getMetadata());
        expectedMetadata.putAll(createdMrCheckpoint(claim));
        expectedMetadata.keySet().removeAll(Set.of("git.mergeSha", "git.blockReason", "git.requestId"));
        org.mockito.Mockito.doReturn(projectionAt).when(clock).instant();

        assertThat(taskService.executeNextTask(claim.runId())).isTrue();

        assertThat(jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId()))
                .containsAllEntriesOf(taskHistory)
                .containsEntry("status", "COMPLETED").containsEntry("error_message", null);
        var task = runs.findTasks(claim.runId()).stream()
                .filter(value -> value.taskId().equals(claim.fence().taskId())).findFirst().orElseThrow();
        assertThat(task.startedAt()).isEqualTo(firstStart);
        assertThat(task.finishedAt()).isEqualTo(projectionAt);
        assertThat(jdbc.queryForList("select * from run_task where run_id=? and id<>? order by task_order",
                claim.runId(), claim.fence().taskId())).isEqualTo(otherTasks);
        var restored = runs.findById(claim.runId()).orElseThrow();
        assertThat(restored.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
        var item = restored.getItems().getFirst();
        assertThat(item.getId()).isEqualTo(itemBefore.getId());
        assertThat(item.getExecutedOrder()).isEqualTo(itemBefore.getExecutedOrder());
        assertThat(item.getMetadata()).isEqualTo(expectedMetadata);
        assertThat(item.getSteps().stream().filter(value -> value.actionType() == ActionType.ENSURE_MR))
                .singleElement().satisfies(value -> {
                    assertThat(value.result()).isEqualTo(RunItemResult.CREATED);
                    assertThat(value.startAt()).isEqualTo(projectionAt);
                    assertThat(value.endAt()).isEqualTo(projectionAt);
                });
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? and task_id<>? order by action_type",
                claim.itemId(), claim.fence().taskId())).isEqualTo(prefix);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId))
                .isEqualTo(before.get("operation"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(jdbc.queryForMap("select * from window_iteration where window_id=? and iteration_key=?",
                windowId, firstIteration)).isEqualTo(before.get("binding"));
        assertThat(projectionGuard(operationId)).containsEntry("session_id", null)
                .containsEntry("operation_id", null).containsEntry("reason", null);

        var projected = projectionFacts(claim, operationId);
        assertThatThrownBy(() -> runs.completeTask(claim, step(claim, RunItemResult.CREATED),
                createdMrCheckpoint(claim))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN),
                Map.of("git.blockReason", "UNKNOWN"))).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(claim, operationId)).isEqualTo(projected);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void appliedCreatedMrCheckpointAndGuardRollBackTogether() {
        var claim = claimMrTask();
        String operationId = recordAppliedCreatedMr(claim);
        expireClaim(claim);
        var before = projectionFacts(claim, operationId);

        Boolean projected = new TransactionTemplate(transactions).execute(tx -> {
            boolean progressed = taskService.executeNextTask(claim.runId());
            if (progressed) {
                assertThat(projectionGuard(operationId)).containsEntry("session_id", null)
                        .containsEntry("operation_id", null);
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                        claim.fence().taskId())).isEqualTo(1);
                assertThat(runs.findById(claim.runId()).orElseThrow().getItems().getFirst().getMetadata())
                        .containsAllEntriesOf(createdMrCheckpoint(claim))
                        .doesNotContainKeys("git.mergeSha", "git.blockReason", "git.requestId");
            }
            tx.setRollbackOnly();
            return progressed;
        });

        assertThat(projected).isTrue();
        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "MISSING_PROOF", "LEGACY_SUMMARY", "STRING_STATUS", "STATUS_200", "STATUS_202",
            "STRING_PROJECT", "STRING_SOURCE_PROJECT", "STRING_TARGET_PROJECT", "STRING_IID",
            "WRONG_PROJECT", "FORK_SOURCE_PROJECT", "WRONG_TARGET_PROJECT", "GLOBAL_ID_ONLY", "ZERO_IID",
            "MISSING_URL", "INVALID_URL", "MISSING_SHA", "WRONG_SHA", "WRONG_SOURCE_BRANCH", "WRONG_TARGET_BRANCH",
            "CLOSED_MR", "WRONG_ATTEMPT", "WRONG_GUARD", "WRONG_FROZEN_SHA", "WRONG_PLANNED_BRANCH",
            "NON_CREATE_ACTION", "PERSISTED_PROJECT_MISMATCH", "NOT_EXPIRED",
            "MISSING_FIRST_START", "MISSING_TOKEN", "MISSING_EXPIRY", "MISSING_ORDER", "CURRENT_STEP"
    })
    void appliedCreatedMrProjectionRejectsIncompleteOrContradictoryFacts(String contradiction) {
        var claim = claimMrTask();
        String operationId = recordAppliedCreatedMr(claim);
        expireClaim(claim);
        var proof = createdMrProof(claim);
        int changed = switch (contradiction) {
            case "MISSING_PROOF" -> jdbc.update(
                    "delete from git_operation_event where operation_id=? and event_sequence=2", operationId);
            case "WRONG_ATTEMPT" -> jdbc.update(
                    "update git_operation_event set attempt_id=? where operation_id=? and event_sequence=2",
                    UUID.randomUUID().toString(), operationId);
            case "WRONG_GUARD" -> jdbc.update(
                    "update repo_execution_guard set generation=generation+1 where operation_id=?", operationId);
            case "WRONG_FROZEN_SHA" -> jdbc.update(
                    "update git_operation set target_json=? where id=?",
                    createdMrTarget(claim).put("sha", "b".repeat(40)).toString(), operationId);
            case "WRONG_PLANNED_BRANCH" -> {
                String different = claim.metadata().get("plan.featureBranch") + "-different";
                assertThat(jdbc.update(
                        "update git_operation_event set evidence_json=? where operation_id=? and event_sequence=2",
                        proof.put("source_branch", different).toString(), operationId)).isEqualTo(1);
                yield jdbc.update("update git_operation set target_json=? where id=?",
                        createdMrTarget(claim).put("source_branch", different).toString(), operationId);
            }
            case "NON_CREATE_ACTION" -> jdbc.update(
                    "update git_operation set action='MERGE_MR' where id=?", operationId);
            case "PERSISTED_PROJECT_MISMATCH" -> jdbc.update(
                    "update run_item_metadata set metadata_value=? where run_item_id=? and metadata_key='git.projectId'",
                    projectionProjectId() + "1", claim.itemId());
            case "NOT_EXPIRED" -> jdbc.update(
                    "update run_task set claim_expires_at=clock_timestamp()+interval '1 hour' where id=?",
                    claim.fence().taskId());
            case "MISSING_FIRST_START" -> jdbc.update(
                    "update run_task set started_at=null where id=?", claim.fence().taskId());
            case "MISSING_TOKEN" -> jdbc.update(
                    "update run_task set claim_token=null where id=?", claim.fence().taskId());
            case "MISSING_EXPIRY" -> jdbc.update(
                    "update run_task set claim_expires_at=null where id=?", claim.fence().taskId());
            case "MISSING_ORDER" -> jdbc.update(
                    "update run_item set executed_order=0 where id=?", claim.itemId());
            case "CURRENT_STEP" -> jdbc.update(
                    "insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) values (?,?,?,?,?,?,?)",
                    claim.itemId(), claim.actionType().name(), RunItemResult.CREATED.name(),
                    java.sql.Timestamp.from(claim.startedAt()), java.sql.Timestamp.from(claim.startedAt()),
                    "isolated pre-existing MR result", claim.fence().taskId());
            default -> {
                long otherProject = Long.parseLong(projectionProjectId()) + 1;
                switch (contradiction) {
                    case "LEGACY_SUMMARY" -> {
                        proof.removeAll();
                        proof.put("httpStatus", 201).put("iid", 11).put("sha", "a".repeat(40)).put("state", "opened");
                    }
                    case "STRING_STATUS" -> proof.put("httpStatus", "201");
                    case "STATUS_200" -> proof.put("httpStatus", 200);
                    case "STATUS_202" -> proof.put("httpStatus", 202);
                    case "STRING_PROJECT" -> proof.put("project_id", projectionProjectId());
                    case "STRING_SOURCE_PROJECT" -> proof.put("source_project_id", projectionProjectId());
                    case "STRING_TARGET_PROJECT" -> proof.put("target_project_id", projectionProjectId());
                    case "STRING_IID" -> proof.put("iid", "11");
                    case "WRONG_PROJECT" -> proof.put("project_id", otherProject);
                    case "FORK_SOURCE_PROJECT" -> proof.put("source_project_id", otherProject);
                    case "WRONG_TARGET_PROJECT" -> proof.put("target_project_id", otherProject);
                    case "GLOBAL_ID_ONLY" -> { proof.remove("iid"); proof.put("id", 11); }
                    case "ZERO_IID" -> proof.put("iid", 0);
                    case "MISSING_URL" -> proof.remove("web_url");
                    case "INVALID_URL" -> proof.put("web_url", "/merge_requests/11");
                    case "MISSING_SHA" -> proof.remove("sha");
                    case "WRONG_SHA" -> proof.put("sha", "b".repeat(40));
                    case "WRONG_SOURCE_BRANCH" -> proof.put("source_branch", "feature/different");
                    case "WRONG_TARGET_BRANCH" -> proof.put("target_branch", "release/different");
                    case "CLOSED_MR" -> proof.put("state", "closed");
                    default -> throw new AssertionError(contradiction);
                }
                yield jdbc.update(
                        "update git_operation_event set evidence_json=? where operation_id=? and event_sequence=2",
                        proof.toString(), operationId);
            }
        };
        assertThat(changed).isEqualTo(1);
        var before = projectionFacts(claim, operationId);

        assertThat(taskService.executeNextTask(claim.runId())).isFalse();

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void unknownOwnerResultCannotBeReplacedByCreatedMrProjection() {
        var claim = claimMrTask();
        String operationId = recordAppliedCreatedMr(claim);
        expireClaim(claim);
        runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN),
                Map.of("git.blockReason", "UNKNOWN"));
        var before = projectionFacts(claim, operationId);
        assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId))
                .isEqualTo("NEEDS_REVIEW");

        assertThat(taskService.executeNextTask(claim.runId())).isFalse();

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void twoProjectionWorkersCommitExactlyOneCreatedMrCheckpoint() throws Exception {
        var claim = claimMrTask();
        String operationId = recordAppliedCreatedMr(claim);
        expireClaim(claim);
        var before = projectionFacts(claim, operationId);
        Instant expiry = claimExpiry(claim);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return runs.projectNextAppliedTask(claim.runId());
            });
            var second = pool.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return runs.projectNextAppliedTask(claim.runId());
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(runs.findTasks(claim.runId()).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "PENDING");
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                claim.fence().taskId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
        assertThat(claimExpiry(claim)).isEqualTo(expiry);
        assertThat(runs.findById(claim.runId()).orElseThrow().getItems().getFirst().getMetadata())
                .containsAllEntriesOf(createdMrCheckpoint(claim))
                .doesNotContainKeys("git.mergeSha", "git.blockReason", "git.requestId");
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId))
                .isEqualTo(before.get("operation"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(projectionGuard(operationId)).containsEntry("session_id", null).containsEntry("operation_id", null);
        var after = projectionFacts(claim, operationId);
        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();
        assertThat(projectionFacts(claim, operationId)).isEqualTo(after);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void successfulOwnerWinsBeforeCreatedMrProjectionAndKeepsItsCheckpoint() {
        var claim = claimMrTask();
        String operationId = recordAppliedCreatedMr(claim);
        expireClaim(claim);
        var ownerStep = new RunStep(claim.actionType(), RunItemResult.CREATED,
                claim.startedAt(), Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS), "original MR owner result");
        var ownerPatch = new java.util.LinkedHashMap<>(createdMrCheckpoint(claim));
        ownerPatch.put("git.mergeSha", null);
        ownerPatch.put("git.blockReason", null);
        ownerPatch.put("git.requestId", null);
        runs.completeTask(claim, ownerStep, ownerPatch);
        var before = projectionFacts(claim, operationId);

        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        assertThat(runs.findById(claim.runId()).orElseThrow().getItems().getFirst().getSteps().stream()
                .filter(value -> value.actionType() == ActionType.ENSURE_MR)).containsExactly(ownerStep);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void appliedCreatedMrProofRequiresTheExistingResultTransactionAndDoesNotChangeFacts() {
        var claim = claimMrTask();
        String operationId = recordAppliedCreatedMr(claim);
        expireClaim(claim);
        var before = projectionFacts(claim, operationId);

        assertThatThrownBy(() -> journal.confirmedAppliedCreatedMr(claim))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var evidence = new TransactionTemplate(transactions).execute(tx -> journal.confirmedAppliedCreatedMr(claim));

        assertThat(evidence).contains(new io.releasehub.application.port.out.GitBranchPort.MrEvidence(
                projectionProjectId(), claim.metadata().get("plan.featureBranch"), claim.metadata().get("plan.releaseBranch"),
                "11", "https://gitlab.invalid/project/-/merge_requests/11", "a".repeat(40), null,
                io.releasehub.application.port.out.GitBranchPort.MrDisposition.CREATED, null));
        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "CREATED,40,true", "CREATED,64,false", "MR_REUSED,40,false", "MR_REUSED,64,true"
    })
    void mrMergeTerminalAppliedIsDiscoveredAndProjectedWithoutChangingIdentityOrHistory(
            String disposition, int shaLength, boolean nullMergeSha) {
        Instant firstStart = Instant.parse("2024-01-01T00:00:00Z");
        Instant httpAt = Instant.parse("2024-02-01T00:00:00Z");
        Instant projectionAt = Instant.parse("2023-12-01T00:00:00Z");
        org.mockito.Mockito.doReturn(firstStart).when(clock).instant();
        var claim = claimMergedMrTask(disposition, shaLength);
        String mergeSha = nullMergeSha ? null : "B".repeat(shaLength);
        org.mockito.Mockito.doReturn(httpAt).when(clock).instant();
        String operationId = recordAppliedMergedMr(claim, mergeSha);
        expireClaim(claim);
        assertThat(jdbc.queryForList("select metadata_key,metadata_value from run_item_metadata where run_item_id=? "
                + "and metadata_key in ('git.mergeSha','git.blockReason','git.requestId')", claim.itemId()))
                .containsExactlyInAnyOrder(
                        Map.of("metadata_key", "git.mergeSha", "metadata_value", "b".repeat(40)),
                        Map.of("metadata_key", "git.blockReason", "metadata_value", "PIPELINE_PENDING"),
                        Map.of("metadata_key", "git.requestId", "metadata_value", "previous-request"));
        var before = projectionFacts(claim, operationId);
        var taskHistory = new java.util.LinkedHashMap<>(
                jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId()));
        taskHistory.keySet().removeAll(Set.of("status", "finished_at", "error_message"));
        var prefixTasks = jdbc.queryForList("select * from run_task where run_id=? and id<>? order by task_order",
                claim.runId(), claim.fence().taskId());
        var prefixSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by action_type", claim.itemId());
        var itemBefore = runs.findById(claim.runId()).orElseThrow().getItems().getFirst();
        var expectedMetadata = new java.util.LinkedHashMap<>(itemBefore.getMetadata());
        expectedMetadata.keySet().removeAll(Set.of("git.mergeSha", "git.blockReason", "git.requestId"));
        if (mergeSha != null) expectedMetadata.put("git.mergeSha", mergeSha);
        assertThat(runs.findTasks(claim.runId()).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "RUNNING");

        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(claim.runId());
        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        org.mockito.Mockito.doReturn(projectionAt).when(clock).instant();
        assertThat(runs.projectNextAppliedTask(claim.runId())).isTrue();

        assertThat(jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId()))
                .containsAllEntriesOf(taskHistory).containsEntry("status", "COMPLETED").containsEntry("error_message", null);
        var task = runs.findTasks(claim.runId()).getLast();
        assertThat(task.startedAt()).isEqualTo(firstStart);
        assertThat(task.finishedAt()).isEqualTo(projectionAt);
        assertThat(jdbc.queryForList("select * from run_task where run_id=? and id<>? order by task_order",
                claim.runId(), claim.fence().taskId())).isEqualTo(prefixTasks);
        var restored = runs.findById(claim.runId()).orElseThrow();
        assertThat(restored.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(restored.getFinishedAt()).isEqualTo(projectionAt);
        var item = restored.getItems().getFirst();
        assertThat(item.getId()).isEqualTo(itemBefore.getId());
        assertThat(item.getExecutedOrder()).isEqualTo(itemBefore.getExecutedOrder());
        assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGED);
        assertThat(item.getMetadata()).isEqualTo(expectedMetadata)
                .containsEntry("git.sourceSha", "A".repeat(shaLength)).containsEntry("git.mrDisposition", disposition);
        assertThat(item.getSteps().stream().filter(value -> value.actionType() == ActionType.TRY_MERGE))
                .singleElement().satisfies(value -> {
                    assertThat(value.result()).isEqualTo(RunItemResult.MERGED);
                    assertThat(value.startAt()).isEqualTo(projectionAt);
                    assertThat(value.endAt()).isEqualTo(projectionAt);
                });
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? and task_id<>? order by action_type",
                claim.itemId(), claim.fence().taskId())).isEqualTo(prefixSteps);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(before.get("operation"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                .orElseThrow().getLastMergeAt()).isEqualTo(projectionAt);
        assertThat(projectionGuard(operationId)).containsEntry("session_id", null)
                .containsEntry("operation_id", null).containsEntry("reason", null);

        var after = projectionFacts(claim, operationId);
        assertThatThrownBy(() -> runs.completeTask(claim, step(claim, RunItemResult.MERGED),
                mergedMrResultPatch(disposition, mergeSha))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN),
                Map.of("git.blockReason", "UNKNOWN"))).isInstanceOf(BusinessException.class);
        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        assertThat(projectionFacts(claim, operationId)).isEqualTo(after);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mrMergeResultTransactionRollsBackTaskItemRunMetadataBindingAndGuardTogether() {
        var claim = claimMergedMrTask("MR_REUSED", 64);
        String operationId = recordAppliedMergedMr(claim, null);
        expireClaim(claim);
        assertThat(jdbc.queryForList("select metadata_key,metadata_value from run_item_metadata where run_item_id=? "
                + "and metadata_key in ('git.mergeSha','git.blockReason','git.requestId')", claim.itemId()))
                .containsExactlyInAnyOrder(
                        Map.of("metadata_key", "git.mergeSha", "metadata_value", "b".repeat(40)),
                        Map.of("metadata_key", "git.blockReason", "metadata_value", "PIPELINE_PENDING"),
                        Map.of("metadata_key", "git.requestId", "metadata_value", "previous-request"));
        var before = projectionFacts(claim, operationId);
        Instant projectionAt = Instant.parse("2024-03-01T00:00:00Z");
        org.mockito.Mockito.doReturn(projectionAt).when(clock).instant();

        Boolean projected = new TransactionTemplate(transactions).execute(tx -> {
            boolean progressed = runs.projectNextAppliedTask(claim.runId());
            if (progressed) {
                assertThat(projectionGuard(operationId)).containsEntry("session_id", null).containsEntry("operation_id", null);
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                        claim.fence().taskId())).isEqualTo(1);
                var run = runs.findById(claim.runId()).orElseThrow();
                assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
                assertThat(run.getFinishedAt()).isEqualTo(projectionAt);
                assertThat(run.getItems().getFirst().getFinalResult()).isEqualTo(RunItemResult.MERGED);
                assertThat(run.getItems().getFirst().getMetadata())
                        .containsEntry("git.sourceSha", "A".repeat(64)).containsEntry("git.mrDisposition", "MR_REUSED")
                        .doesNotContainKeys("git.mergeSha", "git.blockReason", "git.requestId");
                assertThat(runs.findTasks(claim.runId()).getLast().status()).isEqualTo("COMPLETED");
                assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                        .orElseThrow().getLastMergeAt()).isEqualTo(projectionAt);
            }
            tx.setRollbackOnly();
            return progressed;
        });

        assertThat(projected).isTrue();
        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "WRONG_ATTEMPT", "WRONG_SEQUENCE", "WRONG_GUARD", "WRONG_OPERATION_TOKEN", "WRONG_OPERATION_SESSION",
            "MISSING_METHOD", "WRONG_METHOD", "WRONG_SUFFIX", "WRONG_FROZEN_SHA", "NON_MERGE_ACTION",
            "CHECKPOINT_PROJECT", "CHECKPOINT_SOURCE", "CHECKPOINT_TARGET", "CHECKPOINT_IID", "CHECKPOINT_SHA",
            "ALREADY_MERGED_CHECKPOINT", "MISSING_PROOF", "MISSING_MERGE_SHA", "WRONG_RESPONSE_TARGET", "WRONG_RESPONSE_IID",
            "NOT_EXPIRED", "MISSING_FIRST_START", "MISSING_TOKEN", "MISSING_EXPIRY", "MISSING_ORDER", "CURRENT_STEP"
    })
    void mrMergeProjectionRejectsUnprovenOrUnauthorizedOriginalFacts(String contradiction) {
        var claim = claimMergedMrTask("CREATED", 40);
        String operationId = recordAppliedMergedMr(claim, null);
        expireClaim(claim);
        var target = mergedMrTarget(claim);
        var proof = mergedMrProof(claim, null);
        int changed = switch (contradiction) {
            case "WRONG_ATTEMPT" -> jdbc.update(
                    "update git_operation_event set attempt_id=? where operation_id=? and event_sequence=2",
                    UUID.randomUUID().toString(), operationId);
            case "WRONG_SEQUENCE" -> jdbc.update(
                    "update git_operation_event set event_sequence=7 where operation_id=? and event_sequence=2", operationId);
            case "WRONG_GUARD" -> jdbc.update(
                    "update repo_execution_guard set generation=generation+1 where operation_id=?", operationId);
            case "WRONG_OPERATION_TOKEN" -> jdbc.update(
                    "update git_operation set claim_token=? where id=?", UUID.randomUUID().toString(), operationId);
            case "WRONG_OPERATION_SESSION" -> jdbc.update(
                    "update git_operation set session_id=? where id=?", UUID.randomUUID().toString(), operationId);
            case "NON_MERGE_ACTION" -> jdbc.update("update git_operation set action='CREATE_MR' where id=?", operationId);
            case "MISSING_PROOF" -> jdbc.update(
                    "delete from git_operation_event where operation_id=? and event_sequence=2", operationId);
            case "NOT_EXPIRED" -> jdbc.update(
                    "update run_task set claim_expires_at=clock_timestamp()+interval '1 hour' where id=?", claim.fence().taskId());
            case "MISSING_FIRST_START" -> jdbc.update("update run_task set started_at=null where id=?", claim.fence().taskId());
            case "MISSING_TOKEN" -> jdbc.update("update run_task set claim_token=null where id=?", claim.fence().taskId());
            case "MISSING_EXPIRY" -> jdbc.update("update run_task set claim_expires_at=null where id=?", claim.fence().taskId());
            case "MISSING_ORDER" -> jdbc.update("update run_item set executed_order=0 where id=?", claim.itemId());
            case "CURRENT_STEP" -> jdbc.update(
                    "insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) values (?,?,?,?,?,?,?)",
                    claim.itemId(), ActionType.TRY_MERGE.name(), RunItemResult.MERGED.name(),
                    java.sql.Timestamp.from(claim.startedAt()), java.sql.Timestamp.from(claim.startedAt()),
                    "isolated pre-existing merge result", claim.fence().taskId());
            default -> {
                if (contradiction.startsWith("CHECKPOINT_") || contradiction.equals("ALREADY_MERGED_CHECKPOINT")) {
                    String key = switch (contradiction) {
                        case "CHECKPOINT_PROJECT" -> "git.projectId";
                        case "CHECKPOINT_SOURCE" -> "git.sourceBranch";
                        case "CHECKPOINT_TARGET" -> "git.targetBranch";
                        case "CHECKPOINT_IID" -> "git.mrIid";
                        case "CHECKPOINT_SHA" -> "git.sourceSha";
                        case "ALREADY_MERGED_CHECKPOINT" -> "git.mrDisposition";
                        default -> throw new AssertionError(contradiction);
                    };
                    String value = switch (contradiction) {
                        case "CHECKPOINT_PROJECT" -> claim.metadata().get(key) + "1";
                        case "CHECKPOINT_SOURCE", "CHECKPOINT_TARGET" -> claim.metadata().get(key) + "-different";
                        case "CHECKPOINT_IID" -> "12";
                        case "CHECKPOINT_SHA" -> "b".repeat(40);
                        case "ALREADY_MERGED_CHECKPOINT" -> "ALREADY_MERGED";
                        default -> throw new AssertionError(contradiction);
                    };
                    yield jdbc.update("update run_item_metadata set metadata_value=? where run_item_id=? and metadata_key=?",
                            value, claim.itemId(), key);
                }
                if (Set.of("MISSING_METHOD", "WRONG_METHOD", "WRONG_SUFFIX", "WRONG_FROZEN_SHA").contains(contradiction)) {
                    switch (contradiction) {
                        case "MISSING_METHOD" -> target.remove("method");
                        case "WRONG_METHOD" -> target.put("method", "POST");
                        case "WRONG_SUFFIX" -> target.put("suffix", "/merge_requests/12/merge");
                        case "WRONG_FROZEN_SHA" -> target.put("sha", "b".repeat(40));
                        default -> throw new AssertionError(contradiction);
                    }
                    yield jdbc.update("update git_operation set target_json=? where id=?", target.toString(), operationId);
                }
                switch (contradiction) {
                    case "MISSING_MERGE_SHA" -> proof.remove("merge_commit_sha");
                    case "WRONG_RESPONSE_TARGET" -> proof.put("target_branch", claim.metadata().get("git.targetBranch") + "-different");
                    case "WRONG_RESPONSE_IID" -> proof.put("iid", 12);
                    default -> throw new AssertionError(contradiction);
                }
                yield jdbc.update("update git_operation_event set evidence_json=? where operation_id=? and event_sequence=2",
                        proof.toString(), operationId);
            }
        };
        assertThat(changed).isEqualTo(1);
        var before = projectionFacts(claim, operationId);

        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();
        if (Set.of("NON_MERGE_ACTION", "NOT_EXPIRED", "MISSING_FIRST_START", "MISSING_TOKEN", "MISSING_EXPIRY", "CURRENT_STEP")
                .contains(contradiction)) {
            assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        }

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"INTENT", "SEND", "UNKNOWN"})
    void mrMergeLegacyIntentMayBeDiscoveredButUnresolvedFactsCannotBeClaimedOrProjected(String stage) {
        var claim = claimMergedMrTask("MR_REUSED", 40);
        String operationId = beginMergedMrOperation(claim);
        if (!stage.equals("INTENT")) journal.sending(operationId, claim.fence());
        if (stage.equals("UNKNOWN")) journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.UNKNOWN, "{}", "UNKNOWN");
        expireClaim(claim);
        var before = projectionFacts(claim, operationId);

        if (stage.equals("INTENT")) {
            assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(claim.runId());
        } else {
            assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
        }
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();
        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mrMergeTwoRecoverersCommitOneOriginalResult() throws Exception {
        var claim = claimMergedMrTask("MR_REUSED", 40);
        String operationId = recordAppliedMergedMr(claim, null);
        expireClaim(claim);
        var before = projectionFacts(claim, operationId);
        Instant expiry = claimExpiry(claim);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return runs.projectNextAppliedTask(claim.runId());
            });
            var second = pool.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return runs.projectNextAppliedTask(claim.runId());
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(runs.findTasks(claim.runId())).allSatisfy(task -> assertThat(task.status()).isEqualTo("COMPLETED"));
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                claim.fence().taskId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
        assertThat(claimExpiry(claim)).isEqualTo(expiry);
        assertThat(runs.findById(claim.runId()).orElseThrow().getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(before.get("operation"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(projectionGuard(operationId)).containsEntry("session_id", null).containsEntry("operation_id", null);
        var after = projectionFacts(claim, operationId);
        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();
        assertThat(projectionFacts(claim, operationId)).isEqualTo(after);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RunItemResult.class, names = {"MERGED", "UNKNOWN"})
    void mrMergeOriginalOwnerResultWinsBeforeRecovery(RunItemResult ownerResult) {
        var claim = claimMergedMrTask("CREATED", 40);
        String operationId = recordAppliedMergedMr(claim, null);
        expireClaim(claim);
        var ownerStep = new RunStep(ActionType.TRY_MERGE, ownerResult, claim.startedAt(),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS), "original merge owner result");
        runs.completeTask(claim, ownerStep, ownerResult == RunItemResult.MERGED
                ? mergedMrResultPatch("CREATED", null) : Map.of("git.blockReason", "UNKNOWN"));
        var before = projectionFacts(claim, operationId);

        assertThat(runs.projectNextAppliedTask(claim.runId())).isFalse();
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());

        var restored = runs.findById(claim.runId()).orElseThrow();
        assertThat(restored.getExecutionStatus()).isEqualTo(ownerResult == RunItemResult.MERGED
                ? RunExecutionStatus.DONE : RunExecutionStatus.NEEDS_REVIEW);
        assertThat(restored.getItems().getFirst().getSteps().stream()
                .filter(value -> value.actionType() == ActionType.TRY_MERGE)).containsExactly(ownerStep);
        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mrMergeRecoveryKeepsRepositoryOrderWhileAnotherRepositoryProgresses() {
        Run run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration, secondIteration), true);
        var first = claimMergedMrTask(run.getId().value(), "CREATED", 40);
        recordAppliedMergedMr(first, null);
        expireClaim(first);
        var parallel = runs.claimNextTask(first.runId()).orElseThrow();
        assertThat(parallel.repo()).isNotEqualTo(first.repo());
        assertThat(parallel.actionType()).isEqualTo(ActionType.ENSURE_FEATURE);
        completeMergedMrFixtureTask(parallel, "MR_REUSED", 40);
        var parallelSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by action_type", parallel.itemId());
        var laterSameRepo = runs.findById(first.runId()).orElseThrow().getItems().stream()
                .filter(item -> item.getRepo().equals(first.repo()) && !item.getId().value().equals(first.itemId()))
                .findFirst().orElseThrow();
        assertThat(laterSameRepo.getExecutedOrder()).isZero();
        assertThat(runs.findTasks(first.runId()).stream().filter(task -> task.itemId().equals(laterSameRepo.getId().value())))
                .allSatisfy(task -> assertThat(task.status()).isEqualTo("PENDING"));

        assertThat(runs.projectNextAppliedTask(first.runId())).isTrue();

        var intermediate = runs.findById(first.runId()).orElseThrow();
        assertThat(intermediate.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
        assertThat(intermediate.getFinishedAt()).isNull();
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by action_type", parallel.itemId()))
                .isEqualTo(parallelSteps);
        for (int index = 0; index < 16; index++) {
            var next = runs.claimNextTask(first.runId());
            if (next.isEmpty()) break;
            completeMergedMrFixtureTask(next.orElseThrow(), "MR_REUSED", 40);
        }
        var completed = runs.findById(first.runId()).orElseThrow();
        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(completed.getItems()).hasSize(4).allSatisfy(item -> {
            assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGED);
            assertThat(item.getSteps()).hasSize(4);
        });
        for (String repo : List.of(firstRepo, secondRepo)) {
            var actualOrder = completed.getItems().stream().filter(item -> item.getRepo().value().equals(repo))
                    .collect(java.util.stream.Collectors.toMap(
                            item -> item.getIterationKey().value(), item -> item.getExecutedOrder()));
            // The fixture attaches firstIteration before secondIteration; loaded Item positions are not ordered.
            assertThat(actualOrder).containsOnlyKeys(firstIteration, secondIteration);
            assertThat(actualOrder.get(firstIteration)).isPositive().isLessThan(actualOrder.get(secondIteration));
        }
        assertThat(runs.findTasks(first.runId())).allSatisfy(task -> assertThat(task.status()).isEqualTo("COMPLETED"));
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RunItemResult.class, names = {"MERGED", "FAILED", "UNKNOWN"})
    void mrMergeTerminalProjectionAggregatesTheOtherRepositoryResult(RunItemResult otherResult) {
        Run run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration), true);
        var first = claimMergedMrTask(run.getId().value(), "MR_REUSED", 40);
        recordAppliedMergedMr(first, null);
        expireClaim(first);
        var second = claimMergedMrTask(first.runId(), "CREATED", 64);
        assertThat(second.repo()).isNotEqualTo(first.repo());
        if (otherResult == RunItemResult.UNKNOWN) {
            String operationId = beginMergeIntent(second);
            journal.sending(operationId, second.fence());
            journal.finish(operationId, second.fence(), GitWriteResult.Outcome.UNKNOWN,
                    "{}", "RESPONSE_UNCONFIRMED");
        }
        runs.completeTask(second, step(second, otherResult), otherResult == RunItemResult.MERGED
                ? mergedMrResultPatch("CREATED", "b".repeat(64)) : Map.of("git.blockReason", "UNKNOWN"));
        var otherItem = jdbc.queryForMap("select * from run_item where id=?", second.itemId());
        var otherSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by action_type", second.itemId());
        Instant projectionAt = Instant.parse("2024-04-01T00:00:00Z");
        org.mockito.Mockito.doReturn(projectionAt).when(clock).instant();
        assertThat(runs.findTasks(first.runId()).stream().map(RunPort.TaskView::status)).doesNotContain("PENDING");

        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(first.runId());
        assertThat(runs.projectNextAppliedTask(first.runId())).isTrue();

        var restored = runs.findById(first.runId()).orElseThrow();
        assertThat(restored.getExecutionStatus()).isEqualTo(switch (otherResult) {
            case MERGED -> RunExecutionStatus.DONE;
            case FAILED -> RunExecutionStatus.PARTIAL_FAILED;
            case UNKNOWN -> RunExecutionStatus.NEEDS_REVIEW;
            default -> throw new AssertionError(otherResult);
        });
        assertThat(restored.getFinishedAt()).isEqualTo(otherResult == RunItemResult.UNKNOWN ? null : projectionAt);
        assertThat(restored.getItems().stream().filter(item -> item.getId().value().equals(first.itemId())))
                .singleElement().satisfies(item -> assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGED));
        assertThat(jdbc.queryForMap("select * from run_item where id=?", second.itemId())).isEqualTo(otherItem);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by action_type", second.itemId()))
                .isEqualTo(otherSteps);
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(first.runId());
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mrMergeLaterIterationSuccessDoesNotMakeItsFailedRepositoryComplete() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration, secondIteration), false);
        var failed = claimMergedMrTask(run.getId().value(), "CREATED", 40);
        runs.completeTask(failed, step(failed, RunItemResult.FAILED), Map.of("git.blockReason", "PERMISSION_DENIED"));
        var later = claimMergedMrTask(run.getId().value(), "MR_REUSED", 64);
        assertThat(later.itemId()).isNotEqualTo(failed.itemId());
        recordAppliedMergedMr(later, null);
        expireClaim(later);
        Instant projectionAt = Instant.parse("2024-05-01T00:00:00Z");
        org.mockito.Mockito.doReturn(projectionAt).when(clock).instant();

        assertThat(runs.projectNextAppliedTask(later.runId())).isTrue();

        var restored = runs.findById(later.runId()).orElseThrow();
        assertThat(restored.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
        assertThat(restored.getFinishedAt()).isEqualTo(projectionAt);
        assertThat(restored.getItems().stream().collect(java.util.stream.Collectors.toMap(
                item -> item.getId().value(), item -> item.getFinalResult())))
                .isEqualTo(Map.of(failed.itemId(), RunItemResult.FAILED, later.itemId(), RunItemResult.MERGED));
        assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                .orElseThrow().getLastMergeAt()).isNull();
        assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(secondIteration))
                .orElseThrow().getLastMergeAt()).isEqualTo(projectionAt);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mrMergeRecoveryCannotJumpAnUnresolvedEarlierIterationOfItsRepository() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration, secondIteration), true);
        var earlier = claimMergedMrTask(run.getId().value(), "CREATED", 40);
        completeMergedMrFixtureTask(earlier, "CREATED", 40);
        var later = claimMergedMrTask(run.getId().value(), "MR_REUSED", 40);
        String operationId = recordAppliedMergedMr(later, null);
        expireClaim(later);
        // Isolated contradictory history: lock-time eligibility must still protect the target queue.
        assertThat(jdbc.update("update run_task set status='RUNNING',finished_at=null where id=?",
                earlier.fence().taskId())).isEqualTo(1);
        var before = projectionFacts(later, operationId);
        var bindingsBefore = jdbc.queryForList("select * from window_iteration where window_id=? order by iteration_key", windowId);

        assertThat(runs.projectNextAppliedTask(later.runId())).isFalse();

        assertThat(projectionFacts(later, operationId)).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from window_iteration where window_id=? order by iteration_key", windowId))
                .isEqualTo(bindingsBefore);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mrMergeInvalidFrontPagesDoNotHideTheLaterTerminalCandidateOrClaimTasks() {
        var claims = new java.util.ArrayList<RunPort.TaskClaim>();
        var operations = new java.util.LinkedHashMap<String, String>();
        for (int index = 0; index < 3; index++) {
            if (index > 0) fixtures();
            var claim = claimMergedMrTask("MR_REUSED", 40);
            operations.put(claim.runId(), recordAppliedMergedMr(claim, null));
            expireClaim(claim);
            claims.add(claim);
        }
        claims.sort(java.util.Comparator.comparing(RunPort.TaskClaim::runId));
        var badFirst = claims.get(0);
        var badSecond = claims.get(1);
        var validLast = claims.get(2);
        var incomplete = mergedMrProof(badFirst, null);
        incomplete.remove("merge_commit_sha");
        assertThat(jdbc.update("update git_operation_event set evidence_json=? where operation_id=? and event_sequence=2",
                incomplete.toString(), operations.get(badFirst.runId()))).isEqualTo(1);
        assertThat(jdbc.update("update repo_execution_guard set generation=generation+1 where operation_id=?",
                operations.get(badSecond.runId()))).isEqualTo(1);
        var before = new java.util.LinkedHashMap<String, Object>();
        for (var claim : claims) {
            before.put(claim.runId(), List.of(
                    jdbc.queryForList("select * from run_task where run_id=? order by task_order", claim.runId()),
                    jdbc.queryForMap("select * from git_operation where id=?", operations.get(claim.runId())),
                    jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                            operations.get(claim.runId()))));
        }

        var candidates = runs.findTaskCandidateRunIds(null, 1000);
        assertThat(candidates).contains(badFirst.runId(), badSecond.runId(), validLast.runId());
        int firstIndex = candidates.indexOf(badFirst.runId());
        String cursor = firstIndex == 0 ? null : candidates.get(firstIndex - 1);
        var visited = new java.util.ArrayList<String>();
        for (int page = 0; page <= candidates.size(); page++) {
            var next = runs.findTaskCandidateRunIds(cursor, 1);
            assertThat(next).hasSize(1);
            String candidate = next.getFirst();
            if (cursor != null) assertThat(candidate.compareTo(cursor)).isPositive();
            visited.add(candidate);
            cursor = candidate;
            if (candidate.equals(validLast.runId())) break;
            if (candidate.equals(badFirst.runId()) || candidate.equals(badSecond.runId())) {
                assertThat(runs.projectNextAppliedTask(candidate)).isFalse();
            }
        }
        assertThat(visited).doesNotHaveDuplicates().containsSubsequence(badFirst.runId(), badSecond.runId(), validLast.runId());
        for (var claim : claims) {
            assertThat(List.of(
                    jdbc.queryForList("select * from run_task where run_id=? order by task_order", claim.runId()),
                    jdbc.queryForMap("select * from git_operation where id=?", operations.get(claim.runId())),
                    jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                            operations.get(claim.runId())))).isEqualTo(before.get(claim.runId()));
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                    claim.fence().taskId())).isZero();
        }

        assertThat(runs.projectNextAppliedTask(validLast.runId())).isTrue();
        assertThat(runs.findById(validLast.runId()).orElseThrow().getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"CREATED,40", "MR_REUSED,64"})
    void mergeIntentClaimChangesCurrentOwnerButPreservesOriginalReservationAndHistory(String disposition, int shaLength) {
        var original = claimMergedMrTask(disposition, shaLength);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var before = projectionFacts(original, operationId);
        var taskHistory = new java.util.LinkedHashMap<>(
                jdbc.queryForMap("select * from run_task where id=?", original.fence().taskId()));
        taskHistory.keySet().removeAll(Set.of("claim_token", "claim_expires_at"));
        var opHistory = new java.util.LinkedHashMap<>(
                jdbc.queryForMap("select * from git_operation where id=?", operationId));
        opHistory.keySet().removeAll(Set.of("state_version", "updated_at"));
        long oldVersion = mergeIntentVersion(operationId);
        assertThat(runs.findTasks(original.runId()).stream().map(RunPort.TaskView::status)).doesNotContain("PENDING");

        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(original.runId());
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        Instant earliest = databaseNow();
        var current = reclaimMergeIntent(original);

        assertThat(current.fence().taskId()).isEqualTo(original.fence().taskId());
        assertThat(current.fence().claimToken()).isNotEqualTo(original.fence().claimToken());
        assertThat(current.metadata()).isEqualTo(original.metadata());
        assertThat(claimExpiry(current)).isBetween(earliest.plusSeconds(3600), databaseNow().plusSeconds(3600));
        assertThat(jdbc.queryForMap("select * from run_task where id=?", original.fence().taskId()))
                .containsAllEntriesOf(taskHistory);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).containsAllEntriesOf(opHistory);
        assertThat(mergeIntentVersion(operationId)).isGreaterThan(oldVersion);
        assertThat(projectionGuard(operationId)).isEqualTo(before.get("guard"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        var after = projectionFacts(current, operationId);
        for (String key : List.of("run", "item", "steps", "metadata", "binding")) {
            assertThat(after.get(key)).as(key).isEqualTo(before.get(key));
        }

        long firstTransferVersion = mergeIntentVersion(operationId);
        expireClaim(current);
        var again = reclaimMergeIntent(current);
        assertThat(again.fence().taskId()).isEqualTo(original.fence().taskId());
        assertThat(again.fence().claimToken()).isNotIn(original.fence().claimToken(), current.fence().claimToken());
        assertThat(mergeIntentVersion(operationId)).isGreaterThan(firstTransferVersion);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).containsAllEntriesOf(opHistory);
        assertThat(projectionGuard(operationId)).isEqualTo(before.get("guard"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                original.fence().taskId())).isEqualTo(1);
        assertThat(jdbc.queryForMap("select * from run_task where id=?", original.fence().taskId()))
                .containsAllEntriesOf(taskHistory);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void mergeIntentActualSenderReusesOperationAndCanAcknowledgeOrProjectAppliedResult(boolean projectResult) throws Exception {
        var original = claimMergedMrTask("MR_REUSED", 64);
        String operationId = beginMergeIntent(original);
        String attempt = journal.find(operationId).orElseThrow().events().getFirst().attemptId();
        expireClaim(original);
        var current = reclaimMergeIntent(original);
        var transferred = projectionFacts(current, operationId);

        assertThat(journal.begin(mergeIntentCommand(current, mergeIntentTarget(current))).id()).isEqualTo(operationId);
        assertThatThrownBy(() -> mergeIntentSending(operationId, original)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(original, step(original, RunItemResult.UNKNOWN),
                Map.of("git.blockReason", "UNKNOWN"))).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(transferred);

        mergeIntentSending(operationId, current);
        var sent = projectionFacts(current, operationId);
        assertThatThrownBy(() -> mergeIntentFinish(operationId, original, GitWriteResult.Outcome.APPLIED,
                mergedMrProof(original, null).toString())).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(sent);
        mergeIntentFinish(operationId, current, GitWriteResult.Outcome.APPLIED, mergedMrProof(current, null).toString());
        var detail = journal.find(operationId).orElseThrow();
        assertThat(detail.events()).hasSize(3).allSatisfy(event -> assertThat(event.attemptId()).isEqualTo(attempt));
        var sender = json.readTree(detail.events().get(1).evidenceJson());
        assertThat(sender.path("taskId").asText()).isEqualTo(current.fence().taskId());
        assertThat(sender.path("claimToken").asText()).isEqualTo(current.fence().claimToken());
        var appliedOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
        var appliedEvents = jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId);
        assertThat(appliedOperation).containsEntry("claim_token", original.fence().claimToken())
                .containsEntry("session_id", original.fence().claimToken());

        if (projectResult) {
            expireClaim(current);
            assertThat(runs.projectNextAppliedTask(current.runId())).isTrue();
        } else {
            runs.completeTask(current, step(current, RunItemResult.MERGED), mergedMrResultPatch("MR_REUSED", null));
        }
        var completed = runs.findById(current.runId()).orElseThrow();
        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(completed.getItems().getFirst().getMetadata())
                .containsEntry("git.sourceSha", "A".repeat(64)).containsEntry("git.mrDisposition", "MR_REUSED")
                .doesNotContainKeys("git.mergeSha", "git.blockReason", "git.requestId");
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isEqualTo(1);
        assertThat(projectionGuard(operationId)).containsEntry("session_id", null).containsEntry("operation_id", null);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(appliedOperation);
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(appliedEvents);
        assertThatThrownBy(() -> runs.completeTask(original, step(original, RunItemResult.UNKNOWN),
                Map.of("git.blockReason", "UNKNOWN"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"sending,STALE", "sending,ABSENT", "finish,STALE", "finish,ABSENT"})
    void mergeIntentExplicitCallerFenceCannotBorrowTheOriginalOwnersOperation(String action, String callerKind) {
        var original = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(original);
        RunPort.TaskClaim caller = callerKind.equals("ABSENT") ? null
                : new RunPort.TaskClaim(new RunPort.TaskFence(original.fence().taskId(), UUID.randomUUID().toString()),
                        original.runId(), original.itemId(), original.repo(), original.actionType(),
                        original.startedAt(), original.operator(), original.metadata(), original.unsentOperation());
        if (action.equals("finish")) mergeIntentSending(operationId, original);
        var before = projectionFacts(original, operationId);

        if (action.equals("sending")) {
            assertThatThrownBy(() -> mergeIntentSending(operationId, caller)).isInstanceOf(BusinessException.class);
        } else {
            assertThatThrownBy(() -> mergeIntentFinish(operationId, caller, GitWriteResult.Outcome.APPLIED,
                    mergedMrProof(original, null).toString())).isInstanceOf(BusinessException.class);
        }
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentClaimCasRejectionRollsBackPreparedVersion() {
        var original = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        long originalVersion = mergeIntentVersion(operationId);
        var before = projectionFacts(original, operationId);
        var highestVersion = new java.util.concurrent.atomic.AtomicLong(originalVersion);
        String trigger = "b5_claim_cas_" + UUID.randomUUID().toString().replace("-", "");
        try {
            jdbc.execute("create function " + trigger + "() returns trigger language plpgsql "
                    + "as 'begin return null; end;'");
            jdbc.execute("create trigger " + trigger + " before update of claim_token on run_task "
                    + "for each row when (old.id='" + original.fence().taskId()
                    + "' and new.claim_token is distinct from old.claim_token) execute function " + trigger + "()");
            org.mockito.Mockito.doAnswer(invocation -> {
                // The first now() is before prepare; the final startedAt sample sees its transactional version.
                highestVersion.accumulateAndGet(mergeIntentVersion(operationId), Math::max);
                return Instant.now();
            }).when(clock).instant();

            assertThatThrownBy(() -> runs.claimNextTask(original.runId())).isInstanceOf(BusinessException.class);

            assertThat(highestVersion.get()).as("prepare must really increment the version before the rejected CAS")
                    .isGreaterThan(originalVersion);
            assertThat(projectionFacts(original, operationId)).isEqualTo(before);
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on run_task");
            jdbc.execute("drop function if exists " + trigger + "()");
        }
    }

    @Test
    void mergeIntentClaimRollbackRestoresTokenLeaseVersionAndReservation() {
        var original = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var before = projectionFacts(original, operationId);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var current = reclaimMergeIntent(original);
            assertThat(current.fence().claimToken()).isNotEqualTo(original.fence().claimToken());
            assertThat(mergeIntentVersion(operationId)).isGreaterThan(
                    ((Number) ((Map<?, ?>) before.get("operation")).get("state_version")).longValue());
            tx.setRollbackOnly();
        });

        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentResultRollbackKeepsSenderFactsAndRollsBackTaskItemRunBindingAndGuard() {
        var original = claimMergedMrTask("MR_REUSED", 64);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var current = reclaimMergeIntent(original);
        mergeIntentSending(operationId, current);
        mergeIntentFinish(operationId, current, GitWriteResult.Outcome.APPLIED, mergedMrProof(current, null).toString());
        var before = projectionFacts(current, operationId);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var completed = runs.completeTask(current, step(current, RunItemResult.MERGED),
                    mergedMrResultPatch("MR_REUSED", null));
            assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
            assertThat(completed.getItems().getFirst().getFinalResult()).isEqualTo(RunItemResult.MERGED);
            assertThat(completed.getItems().getFirst().getMetadata())
                    .doesNotContainKeys("git.mergeSha", "git.blockReason", "git.requestId");
            assertThat(runs.findTasks(current.runId()).getLast().status()).isEqualTo("COMPLETED");
            assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
            assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                    .orElseThrow().getLastMergeAt()).isNotNull();
            tx.setRollbackOnly();
        });

        assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void mergeIntentPreSendUnknownRecordsNotSentAndKeepsReviewState(boolean afterTransfer) throws Exception {
        var original = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(original);
        RunPort.TaskClaim current = original;
        if (afterTransfer) {
            expireClaim(original);
            current = reclaimMergeIntent(original);
        }
        var before = journal.find(operationId).orElseThrow();
        var reservation = new java.util.LinkedHashMap<>(projectionGuard(operationId));
        reservation.remove("updated_at");
        reservation.put("operation_id", null);
        reservation.put("session_id", null);
        reservation.put("reason", null);
        var patch = Map.of("git.blockReason", "UNKNOWN");

        var unknown = runs.completeTask(current, step(current, RunItemResult.UNKNOWN), patch);

        assertThat(unknown.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        assertThat(runs.findTasks(current.runId()).getLast().status()).isEqualTo("UNKNOWN");
        var after = journal.find(operationId).orElseThrow();
        assertThat(after.operation().state()).isEqualTo("NOT_SENT");
        assertThat(after.events()).hasSize(2);
        assertThat(after.events().getFirst()).isEqualTo(before.events().getFirst());
        var decision = after.events().getLast();
        assertThat(decision.attemptId()).isEqualTo(before.events().getFirst().attemptId());
        assertThat(decision.sequence()).isEqualTo(1);
        assertThat(decision.type()).isEqualTo("NOT_SENT");
        assertThat(decision.reason()).isEqualTo("UNKNOWN");
        var evidence = json.readTree(decision.evidenceJson());
        assertThat(evidence.path("taskId").textValue()).isEqualTo(current.fence().taskId());
        assertThat(evidence.path("claimToken").textValue()).isEqualTo(current.fence().claimToken());
        assertThat(evidence.path("result").textValue()).isEqualTo("UNKNOWN");
        assertThat(evidence.get("metadata")).isEqualTo(json.valueToTree(patch));
        assertThat(projectionGuard(operationId)).containsAllEntriesOf(reservation);
        expireClaim(current);
        assertThat(runs.claimNextTask(current.runId())).isEmpty();
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(current.runId());
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentTransferInvalidatesOldObservationButKeepsHistoricalObservationAndMappingProtection() {
        var original = claimMergedMrTask("CREATED", 40);
        var command = mergeIntentCommand(original, mergeIntentTarget(original));
        settings.saveGitLab(new SettingsPort.SettingsGitLab("https://gitlab.invalid", "isolated-projection-token"));
        String operationId = journal.begin(command).id();
        long oldVersion = mergeIntentVersion(operationId);
        expireClaim(original);
        var current = reclaimMergeIntent(original);
        long newVersion = mergeIntentVersion(operationId);
        var guard = projectionGuard(operationId);
        var events = journal.find(operationId).orElseThrow().events();

        assertThatThrownBy(() -> journal.observe(operationId, "reviewer", "before-transfer", oldVersion,
                io.releasehub.application.gitoperation.GitReadResult.Status.UNAVAILABLE, "{}"))
                .isInstanceOf(BusinessException.class);
        assertThat(journal.observation(operationId, "reviewer", "before-transfer")).isEmpty();
        var observation = journal.observe(operationId, "reviewer", "after-transfer", newVersion,
                io.releasehub.application.gitoperation.GitReadResult.Status.UNAVAILABLE, "{\"read\":\"unavailable\"}");
        assertThat(observation.expectedVersion()).isEqualTo(newVersion);
        assertThat(journal.observation(operationId, "reviewer", "after-transfer")).hasValueSatisfying(saved -> {
            assertThat(saved.id()).isEqualTo(observation.id());
            assertThat(saved.expectedVersion()).isEqualTo(newVersion);
            assertThat(saved.evidenceJson()).isEqualTo(observation.evidenceJson());
        });
        assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(events);
        assertThat(projectionGuard(operationId)).isEqualTo(guard);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId))
                .containsEntry("claim_token", original.fence().claimToken()).containsEntry("session_id", original.fence().claimToken());
        assertThat(current.fence().claimToken()).isNotEqualTo(original.fence().claimToken());

        String changedProject = command.projectId() + "1";
        String changedRemote = GitOperationJournalPort.remoteKey("GITLAB", command.baseUrl(), changedProject);
        var changed = new GitOperationJournalPort.Command(UUID.randomUUID().toString(), command.repoId(),
                command.repositoryMapping(), changedRemote, command.provider(), command.baseUrl(), changedProject,
                command.action(), command.targetJson(), mergeIntentHash(changedRemote, mergeIntentTarget(original)),
                null, command.operator(), UUID.randomUUID().toString(), null);
        assertThatThrownBy(() -> journal.begin(changed)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from git_operation where remote_key=?", Integer.class,
                changedRemote)).isZero();
        assertThat(projectionGuard(operationId)).isEqualTo(guard);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentOldSendWinsObservedPostgresLockAndItsLateResponseRemainsWritable() throws Exception {
        var original = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var taskBefore = jdbc.queryForMap("select * from run_task where id=?", original.fence().taskId());
        var sendLocked = new CountDownLatch(1);
        var releaseSend = new CountDownLatch(1);
        var claimantStarted = new CountDownLatch(1);
        var senderThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var senderPid = new java.util.concurrent.atomic.AtomicInteger();
        var claimantPid = new java.util.concurrent.atomic.AtomicInteger();
        var pauseOnce = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (Thread.currentThread() == senderThread.get() && pauseOnce.compareAndSet(false, true)) {
                senderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                sendLocked.countDown();
                awaitMergeIntentLatch(releaseSend);
            }
            return Instant.now();
        }).when(clock).instant();

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var sender = pool.submit(() -> {
                    senderThread.set(Thread.currentThread());
                    mergeIntentSending(operationId, original);
                });
                assertThat(sendLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var claimant = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    claimantPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    claimantStarted.countDown();
                    return runs.claimNextTask(original.runId());
                }));
                assertThat(claimantStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(claimantPid.get(), Set.of(senderPid.get()));
                releaseSend.countDown();
                sender.get(10, TimeUnit.SECONDS);
                assertThat(claimant.get(10, TimeUnit.SECONDS)).isEmpty();
            } finally {
                releaseSend.countDown();
            }
        }
        assertThat(jdbc.queryForMap("select * from run_task where id=?", original.fence().taskId())).isEqualTo(taskBefore);
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
        var sent = projectionFacts(original, operationId);
        assertThat(runs.claimNextTask(original.runId())).isEmpty();
        assertThat(projectionFacts(original, operationId)).isEqualTo(sent);
        mergeIntentFinish(operationId, original, GitWriteResult.Outcome.APPLIED, mergedMrProof(original, null).toString());
        assertThat(runs.completeTask(original, step(original, RunItemResult.MERGED), mergedMrResultPatch("CREATED", null))
                .getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNewOwnerWinsObservedPostgresLockAndOldSenderCannotCommitUnknown() throws Exception {
        var original = claimMergedMrTask("MR_REUSED", 40);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var transferLocked = new CountDownLatch(1);
        var releaseTransfer = new CountDownLatch(1);
        var oldStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        var transferred = new java.util.concurrent.atomic.AtomicReference<RunPort.TaskClaim>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var owner = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    var current = reclaimMergeIntent(original);
                    transferred.set(current);
                    transferLocked.countDown();
                    awaitMergeIntentLatch(releaseTransfer);
                    return current;
                }));
                boolean transferReached = transferLocked.await(5, TimeUnit.SECONDS);
                if (!transferReached && owner.isDone()) {
                    try {
                        owner.get(1, TimeUnit.SECONDS);
                    } catch (java.util.concurrent.ExecutionException failed) {
                        throw new AssertionError("the original unsent intent must be claimed before testing the losing sender",
                                failed.getCause());
                    }
                }
                assertThat(transferReached).as("the new owner must hold its actual claim transaction").isTrue();
                var oldSender = pool.submit(() -> {
                    oldStarted.countDown();
                    try {
                        mergeIntentSending(operationId, original);
                        return (RuntimeException) null;
                    } catch (RuntimeException error) {
                        return error;
                    }
                });
                assertThat(oldStarted.await(5, TimeUnit.SECONDS)).isTrue();
                int oldPid = awaitMergeIntentWaiter(holderPid.get());
                awaitMergeIntentBlockers(oldPid, Set.of(holderPid.get()));
                releaseTransfer.countDown();
                assertThat(owner.get(10, TimeUnit.SECONDS)).isEqualTo(transferred.get());
                assertThat(oldSender.get(10, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class);
            } finally {
                releaseTransfer.countDown();
            }
        }
        var current = transferred.get();
        assertThat(current).isNotNull();
        var beforeUnknown = projectionFacts(current, operationId);
        assertThatThrownBy(() -> runs.completeTask(original, step(original, RunItemResult.UNKNOWN),
                Map.of("git.blockReason", "UNKNOWN"))).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(beforeUnknown);
        assertThat(runs.findTasks(current.runId()).getLast().status()).isEqualTo("RUNNING");
        assertThat(journal.find(operationId).orElseThrow().events()).hasSize(1);
        mergeIntentSending(operationId, current);
        mergeIntentFinish(operationId, current, GitWriteResult.Outcome.APPLIED, mergedMrProof(current, null).toString());
        assertThat(runs.completeTask(current, step(current, RunItemResult.MERGED), mergedMrResultPatch("MR_REUSED", null))
                .getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isEqualTo(1);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentTwoClaimantsActuallyWaitOnPostgresAndOnlyOneReceivesOwnership() throws Exception {
        var original = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var before = mergeIntentHistory(original, operationId);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstStarted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        var firstPid = new java.util.concurrent.atomic.AtomicInteger();
        var secondPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(3)) {
            try {
                var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    jdbc.queryForObject("select id from run where id=? for update", String.class, original.runId());
                    jdbc.queryForObject("select id from run_task where id=? for update", String.class, original.fence().taskId());
                    locked.countDown();
                    awaitMergeIntentLatch(release);
                    return null;
                }));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var first = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    firstPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    firstStarted.countDown();
                    return runs.claimNextTask(original.runId());
                }));
                assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(firstPid.get(), Set.of(holderPid.get()));
                var second = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    secondStarted.countDown();
                    return runs.claimNextTask(original.runId());
                }));
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(secondPid.get(), Set.of(holderPid.get(), firstPid.get()));
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                var owners = java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                        .flatMap(java.util.Optional::stream).toList();
                assertThat(owners).hasSize(1);
                assertThat(owners.getFirst().fence().claimToken()).isNotEqualTo(original.fence().claimToken());
                assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                        original.fence().taskId())).isEqualTo(owners.getFirst().fence().claimToken());
            } finally {
                release.countDown();
            }
        }
        assertThat(projectionGuard(operationId)).isEqualTo(before.get("guard"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("INTENT_RECORDED");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "NOT_EXPIRED", "MISSING_EXPIRY", "MISSING_TOKEN", "MISSING_FIRST_START", "MISSING_ORDER",
            "CURRENT_STEP", "OWNER_UNKNOWN", "WRONG_PROTOCOL", "WRONG_TASK_TYPE", "NON_MERGE_ACTION",
            "WRONG_ATTEMPT", "WRONG_SEQUENCE", "WRONG_EVENT_TYPE", "MISSING_INTENT", "EXTRA_ATTEMPT",
            "HIDDEN_SEND", "FOREIGN_ATTEMPT_SEND", "RESPONSE_WITHOUT_SEND", "SEND_STARTED", "HTTP_UNKNOWN",
            "WRONG_GUARD_GENERATION", "WRONG_GUARD_SESSION", "WRONG_GUARD_OPERATION", "WRONG_GUARD_REASON",
            "WRONG_OPERATION_TOKEN", "WRONG_OPERATION_SESSION", "WRONG_OPERATION_RUN", "WRONG_OPERATOR",
            "CHECKPOINT_PROJECT", "CHECKPOINT_SOURCE", "CHECKPOINT_TARGET", "CHECKPOINT_IID", "CHECKPOINT_SHA",
            "ALREADY_MERGED_CHECKPOINT"
    })
    void mergeIntentRejectsIncompleteOrContradictoryOriginalQualificationWithoutChangingFacts(String contradiction) {
        var original = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        int changed = switch (contradiction) {
            case "NOT_EXPIRED" -> jdbc.update(
                    "update run_task set claim_expires_at=clock_timestamp()+interval '1 hour' where id=?", original.fence().taskId());
            case "MISSING_EXPIRY" -> jdbc.update("update run_task set claim_expires_at=null where id=?", original.fence().taskId());
            case "MISSING_TOKEN" -> jdbc.update("update run_task set claim_token=null where id=?", original.fence().taskId());
            case "MISSING_FIRST_START" -> jdbc.update("update run_task set started_at=null where id=?", original.fence().taskId());
            case "MISSING_ORDER" -> jdbc.update("update run_item set executed_order=0 where id=?", original.itemId());
            case "CURRENT_STEP" -> jdbc.update(
                    "insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) values (?,?,?,?,?,?,?)",
                    original.itemId(), "TRY_MERGE", "UNKNOWN", java.sql.Timestamp.from(original.startedAt()),
                    java.sql.Timestamp.from(original.startedAt()), "isolated existing owner result", original.fence().taskId());
            case "OWNER_UNKNOWN" -> {
                runs.completeTask(original, step(original, RunItemResult.UNKNOWN), Map.of("git.blockReason", "UNKNOWN"));
                yield 1;
            }
            case "WRONG_PROTOCOL" -> jdbc.update("update run set execution_version=0 where id=?", original.runId());
            case "WRONG_TASK_TYPE" -> jdbc.update("update run_task set task_type='UPDATE_VERSION' where id=?", original.fence().taskId());
            case "NON_MERGE_ACTION" -> jdbc.update("update git_operation set action='CREATE_MR' where id=?", operationId);
            case "WRONG_ATTEMPT" -> jdbc.update(
                    "update git_operation_event set attempt_id=? where operation_id=? and event_sequence=0",
                    UUID.randomUUID().toString(), operationId);
            case "WRONG_SEQUENCE" -> jdbc.update(
                    "update git_operation_event set event_sequence=7 where operation_id=? and event_sequence=0", operationId);
            case "WRONG_EVENT_TYPE" -> jdbc.update(
                    "update git_operation_event set event_type='SEND_STARTED' where operation_id=? and event_sequence=0", operationId);
            case "MISSING_INTENT" -> jdbc.update("delete from git_operation_event where operation_id=?", operationId);
            case "EXTRA_ATTEMPT", "FOREIGN_ATTEMPT_SEND", "RESPONSE_WITHOUT_SEND" -> {
                String attempt = contradiction.equals("RESPONSE_WITHOUT_SEND")
                        ? journal.find(operationId).orElseThrow().events().getFirst().attemptId() : UUID.randomUUID().toString();
                int sequence = contradiction.equals("EXTRA_ATTEMPT") ? 0 : contradiction.equals("FOREIGN_ATTEMPT_SEND") ? 1 : 2;
                String type = sequence == 0 ? "INTENT_RECORDED" : sequence == 1 ? "SEND_STARTED" : "APPLIED";
                yield jdbc.update("insert into git_operation_event(id,operation_id,attempt_id,event_sequence,event_type,"
                                + "occurred_at,evidence_json,reason) values (?,?,?,?,?,clock_timestamp(),?,null)",
                        UUID.randomUUID().toString(), operationId, attempt, sequence, type, "{}");
            }
            case "HIDDEN_SEND", "SEND_STARTED", "HTTP_UNKNOWN" -> {
                mergeIntentSending(operationId, original);
                if (contradiction.equals("HTTP_UNKNOWN")) {
                    mergeIntentFinish(operationId, original, GitWriteResult.Outcome.UNKNOWN, "{}");
                } else if (contradiction.equals("HIDDEN_SEND")) {
                    assertThat(jdbc.update("update git_operation set state='INTENT_RECORDED' where id=?", operationId)).isEqualTo(1);
                    assertThat(jdbc.update("update repo_execution_guard set reason='INTENT_PENDING' where operation_id=?",
                            operationId)).isEqualTo(1);
                }
                yield 1;
            }
            case "WRONG_GUARD_GENERATION" -> jdbc.update(
                    "update repo_execution_guard set generation=generation+1 where operation_id=?", operationId);
            case "WRONG_GUARD_SESSION" -> jdbc.update(
                    "update repo_execution_guard set session_id=? where operation_id=?", UUID.randomUUID().toString(), operationId);
            case "WRONG_GUARD_OPERATION" -> jdbc.update(
                    "update repo_execution_guard set operation_id=? where operation_id=?", UUID.randomUUID().toString(), operationId);
            case "WRONG_GUARD_REASON" -> jdbc.update(
                    "update repo_execution_guard set reason='REQUEST_IN_FLIGHT' where operation_id=?", operationId);
            case "WRONG_OPERATION_TOKEN" -> jdbc.update(
                    "update git_operation set claim_token=? where id=?", UUID.randomUUID().toString(), operationId);
            case "WRONG_OPERATION_SESSION" -> jdbc.update(
                    "update git_operation set session_id=? where id=?", UUID.randomUUID().toString(), operationId);
            case "WRONG_OPERATION_RUN" -> jdbc.update(
                    "update git_operation set run_id=? where id=?", UUID.randomUUID().toString(), operationId);
            case "WRONG_OPERATOR" -> jdbc.update("update git_operation set operator='another-operator' where id=?", operationId);
            default -> {
                String key = switch (contradiction) {
                    case "CHECKPOINT_PROJECT" -> "git.projectId";
                    case "CHECKPOINT_SOURCE" -> "git.sourceBranch";
                    case "CHECKPOINT_TARGET" -> "git.targetBranch";
                    case "CHECKPOINT_IID" -> "git.mrIid";
                    case "CHECKPOINT_SHA" -> "git.sourceSha";
                    case "ALREADY_MERGED_CHECKPOINT" -> "git.mrDisposition";
                    default -> throw new AssertionError(contradiction);
                };
                String value = switch (contradiction) {
                    case "CHECKPOINT_PROJECT" -> original.metadata().get(key) + "1";
                    case "CHECKPOINT_SOURCE", "CHECKPOINT_TARGET" -> original.metadata().get(key) + "-changed";
                    case "CHECKPOINT_IID" -> "12";
                    case "CHECKPOINT_SHA" -> "b".repeat(40);
                    case "ALREADY_MERGED_CHECKPOINT" -> "ALREADY_MERGED";
                    default -> throw new AssertionError(contradiction);
                };
                yield jdbc.update("update run_item_metadata set metadata_value=? where run_item_id=? and metadata_key=?",
                        value, original.itemId(), key);
            }
        };
        assertThat(changed).isEqualTo(1);
        var before = projectionFacts(original, operationId);

        assertMergeIntentClaimRejected(original.runId());

        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "LEGACY_SUMMARY", "LEGACY_HASH", "HASH_MISMATCH", "MISSING_METHOD", "WRONG_METHOD", "WRONG_SUFFIX",
            "MISSING_SHA", "NUMERIC_SHA", "WRONG_SHA", "MISSING_MESSAGE", "NUMERIC_MESSAGE", "CUSTOM_MESSAGE",
            "MISSING_REMOVE_FLAG", "TEXT_REMOVE_FLAG", "TRUE_REMOVE_FLAG", "UNKNOWN_FIELD", "QUERY", "EMPTY_QUERY"
    })
    void mergeIntentRejectsIncompleteTypedRequestsOrNoncanonicalFingerprintsWithoutRepair(String contradiction) {
        var original = claimMergedMrTask("MR_REUSED", 64);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var target = mergeIntentTarget(original);
        switch (contradiction) {
            case "LEGACY_SUMMARY" -> {
                target.remove("merge_commit_message");
                target.remove("should_remove_source_branch");
            }
            case "MISSING_METHOD" -> target.remove("method");
            case "WRONG_METHOD" -> target.put("method", "POST");
            case "WRONG_SUFFIX" -> target.put("suffix", "/merge_requests/12/merge");
            case "MISSING_SHA" -> target.remove("sha");
            case "NUMERIC_SHA" -> target.put("sha", 123);
            case "WRONG_SHA" -> target.put("sha", "B".repeat(64));
            case "MISSING_MESSAGE" -> target.remove("merge_commit_message");
            case "NUMERIC_MESSAGE" -> target.put("merge_commit_message", 123);
            case "CUSTOM_MESSAGE" -> target.put("merge_commit_message", "a valid custom message outside this task plan");
            case "MISSING_REMOVE_FLAG" -> target.remove("should_remove_source_branch");
            case "TEXT_REMOVE_FLAG" -> target.put("should_remove_source_branch", "false");
            case "TRUE_REMOVE_FLAG" -> target.put("should_remove_source_branch", true);
            case "UNKNOWN_FIELD" -> target.put("unrecognized_request_field", "do not discard");
            case "QUERY" -> target.put("query", "merge_when_pipeline_succeeds=true");
            case "EMPTY_QUERY" -> target.put("query", "");
            case "LEGACY_HASH", "HASH_MISMATCH" -> { }
            default -> throw new AssertionError(contradiction);
        }
        String remote = journal.find(operationId).orElseThrow().operation().remoteKey();
        String fingerprint = Set.of("LEGACY_SUMMARY", "LEGACY_HASH").contains(contradiction)
                ? GitOperationJournalPort.mappingHash("MERGE_MR", target.toString())
                : contradiction.equals("HASH_MISMATCH") ? "0".repeat(64) : mergeIntentHash(remote, target);
        assertThat(jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                target.toString(), fingerprint, operationId)).isEqualTo(1);
        var before = projectionFacts(original, operationId);

        assertMergeIntentClaimRejected(original.runId());

        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentInvalidFrontPagesDoNotHideAValidTerminalIntent() {
        var claims = new java.util.ArrayList<RunPort.TaskClaim>();
        var operations = new java.util.HashMap<String, String>();
        for (int index = 0; index < 3; index++) {
            if (index > 0) fixtures();
            var claim = claimMergedMrTask("CREATED", 40);
            operations.put(claim.runId(), beginMergeIntent(claim));
            expireClaim(claim);
            claims.add(claim);
        }
        claims.sort(java.util.Comparator.comparing(RunPort.TaskClaim::runId));
        var badFirst = claims.get(0);
        var badSecond = claims.get(1);
        var validLast = claims.get(2);
        assertThat(jdbc.update("update git_operation set request_hash=? where id=?", "0".repeat(64),
                operations.get(badFirst.runId()))).isEqualTo(1);
        assertThat(jdbc.update("update repo_execution_guard set generation=generation+1 where operation_id=?",
                operations.get(badSecond.runId()))).isEqualTo(1);
        var before = new java.util.HashMap<String, Map<String, Object>>();
        for (var claim : claims) before.put(claim.runId(), mergeIntentHistory(claim, operations.get(claim.runId())));
        var candidates = runs.findTaskCandidateRunIds(null, 1000);
        assertThat(candidates).contains(badFirst.runId(), badSecond.runId(), validLast.runId());
        int firstIndex = candidates.indexOf(badFirst.runId());
        String cursor = firstIndex == 0 ? null : candidates.get(firstIndex - 1);
        var visited = new java.util.ArrayList<String>();

        for (int page = 0; page <= candidates.size(); page++) {
            var next = runs.findTaskCandidateRunIds(cursor, 1);
            assertThat(next).hasSize(1);
            String candidate = next.getFirst();
            if (cursor != null) assertThat(candidate.compareTo(cursor)).isPositive();
            cursor = candidate;
            visited.add(candidate);
            if (candidate.equals(validLast.runId())) break;
            if (candidate.equals(badFirst.runId()) || candidate.equals(badSecond.runId())) {
                assertMergeIntentClaimRejected(candidate);
            }
        }

        assertThat(visited).doesNotHaveDuplicates().containsSubsequence(badFirst.runId(), badSecond.runId(), validLast.runId());
        for (var claim : claims) {
            assertThat(mergeIntentHistory(claim, operations.get(claim.runId()))).isEqualTo(before.get(claim.runId()));
        }
        assertThat(reclaimMergeIntent(validLast).fence().taskId()).isEqualTo(validLast.fence().taskId());
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentRecoveryKeepsSameRepositoryOrderWhileAnotherRepositoryAdvances() {
        var run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration, secondIteration), true);
        var original = claimMergedMrTask(run.getId().value(), "CREATED", 40);
        String operationId = beginMergeIntent(original);
        var parallel = runs.claimNextTask(original.runId()).orElseThrow();
        assertThat(parallel.repo()).isNotEqualTo(original.repo());
        var parallelTask = jdbc.queryForMap("select * from run_task where id=?", parallel.fence().taskId());
        var later = runs.findById(original.runId()).orElseThrow().getItems().stream()
                .filter(item -> item.getRepo().equals(original.repo()) && !item.getId().value().equals(original.itemId()))
                .findFirst().orElseThrow();
        expireClaim(original);

        var current = reclaimMergeIntent(original);

        assertThat(current.fence().taskId()).isEqualTo(original.fence().taskId());
        assertThat(jdbc.queryForMap("select * from run_task where id=?", parallel.fence().taskId())).isEqualTo(parallelTask);
        assertThat(runs.findTasks(original.runId()).stream().filter(task -> task.itemId().equals(later.getId().value())))
                .allSatisfy(task -> assertThat(task.status()).isEqualTo("PENDING"));
        assertThat(later.getExecutedOrder()).isZero();
        mergeIntentSending(operationId, current);
        mergeIntentFinish(operationId, current, GitWriteResult.Outcome.APPLIED, mergedMrProof(current, null).toString());
        assertThat(runs.completeTask(current, step(current, RunItemResult.MERGED), mergedMrResultPatch("CREATED", null))
                .getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
        completeMergedMrFixtureTask(parallel, "MR_REUSED", 40);
        for (int index = 0; index < 16; index++) {
            var next = runs.claimNextTask(original.runId());
            if (next.isEmpty()) break;
            completeMergedMrFixtureTask(next.orElseThrow(), "MR_REUSED", 40);
        }
        var completed = runs.findById(original.runId()).orElseThrow();
        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(completed.getItems()).hasSize(4)
                .allSatisfy(item -> assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGED));
        for (String repo : List.of(firstRepo, secondRepo)) {
            var order = completed.getItems().stream().filter(item -> item.getRepo().value().equals(repo))
                    .collect(java.util.stream.Collectors.toMap(item -> item.getIterationKey().value(), item -> item.getExecutedOrder()));
            assertThat(order).containsOnlyKeys(firstIteration, secondIteration);
            assertThat(order.get(firstIteration)).isPositive().isLessThan(order.get(secondIteration));
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentCannotReclaimAcrossAnUnresolvedEarlierIteration() {
        var run = admit(List.of(firstRepo), List.of(firstIteration, secondIteration), true);
        var earlier = claimMergedMrTask(run.getId().value(), "CREATED", 40);
        completeMergedMrFixtureTask(earlier, "CREATED", 40);
        var later = claimMergedMrTask(run.getId().value(), "MR_REUSED", 40);
        String operationId = beginMergeIntent(later);
        expireClaim(later);
        assertThat(jdbc.update("update run_task set status='RUNNING',finished_at=null where id=?",
                earlier.fence().taskId())).isEqualTo(1);
        var before = projectionFacts(later, operationId);

        assertMergeIntentClaimRejected(later.runId());

        assertThat(projectionFacts(later, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "ALREADY_MERGED,false,CREATED,40", "ALREADY_MERGED,true,MR_REUSED,64",
            "MERGE_BLOCKED,false,MR_REUSED,40", "MERGE_BLOCKED,true,CREATED,64",
            "UNKNOWN,false,CREATED,64", "UNKNOWN,true,MR_REUSED,40"
    })
    void mergeIntentNotSentCommitsLocalResultAndOriginalHistory(String resultName, boolean transferred,
            String disposition, int shaLength) throws Exception {
        var original = claimMergedMrTask(disposition, shaLength);
        String operationId = beginMergeIntent(original);
        var operationHistory = new java.util.LinkedHashMap<>(jdbc.queryForMap(
                "select * from git_operation where id=?", operationId));
        operationHistory.keySet().removeAll(Set.of("state", "state_version", "reason", "updated_at"));
        var originalEvent = jdbc.queryForMap(
                "select * from git_operation_event where operation_id=?", operationId);
        var current = original;
        if (transferred) {
            expireClaim(original);
            current = reclaimMergeIntent(original);
            assertThat(current.fence().claimToken()).isNotEqualTo(original.fence().claimToken());
        }
        assertThat(claimExpiry(current)).as("local completion does not wait for another expiry").isAfter(databaseNow());
        var before = projectionFacts(current, operationId);
        var taskHistory = new java.util.LinkedHashMap<>(jdbc.queryForMap(
                "select * from run_task where id=?", current.fence().taskId()));
        taskHistory.keySet().removeAll(Set.of("status", "finished_at", "error_message"));
        long version = mergeIntentVersion(operationId);
        RunItemResult result = RunItemResult.valueOf(resultName);
        var patch = notSentPatch(result);
        if (result == RunItemResult.ALREADY_MERGED && transferred) patch.put("git.mergeSha", "b".repeat(shaLength));

        var completed = completeNotSent(current, result, patch);

        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId))
                .containsAllEntriesOf(operationHistory).containsEntry("state", "NOT_SENT")
                .containsEntry("reason", patch.get("git.blockReason"));
        assertThat(mergeIntentVersion(operationId)).isGreaterThan(version);
        var events = jdbc.queryForList(
                "select * from git_operation_event where operation_id=? order by event_sequence", operationId);
        assertThat(events).hasSize(2);
        assertThat(events.getFirst()).isEqualTo(originalEvent);
        assertThat(events.getLast()).containsEntry("operation_id", operationId)
                .containsEntry("attempt_id", originalEvent.get("attempt_id"))
                .containsEntry("event_sequence", 1).containsEntry("event_type", "NOT_SENT")
                .containsEntry("reason", patch.get("git.blockReason"));
        var decision = json.readTree((String) events.getLast().get("evidence_json"));
        assertThat(decision.path("taskId").textValue()).isEqualTo(current.fence().taskId());
        assertThat(decision.path("claimToken").textValue()).isEqualTo(current.fence().claimToken());
        assertThat(decision.path("result").textValue()).isEqualTo(resultName);
        assertThat(decision.get("metadata")).isEqualTo(json.valueToTree(patch));
        assertThat(decision.has("httpStatus")).isFalse();
        assertThat(decision.has("response")).isFalse();
        assertThat(decision.has("target")).isFalse();
        assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId()))
                .containsAllEntriesOf(taskHistory);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isEqualTo(1);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? and task_id<>? order by action_type",
                current.itemId(), current.fence().taskId())).isEqualTo(before.get("steps"));
        var expectedMetadata = new java.util.LinkedHashMap<>(current.metadata());
        patch.forEach((key, value) -> { if (value == null) expectedMetadata.remove(key); else expectedMetadata.put(key, value); });
        var item = completed.getItems().getFirst();
        assertThat(item.getMetadata()).isEqualTo(expectedMetadata);
        assertThat(item.getFinalResult()).isEqualTo(result);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null)
                .containsEntry("session_id", null).containsEntry("reason", null)
                .containsEntry("generation", ((Map<?, ?>) before.get("guard")).get("generation"));
        String taskStatus = result == RunItemResult.UNKNOWN ? "UNKNOWN"
                : result == RunItemResult.MERGE_BLOCKED ? "FAILED" : "COMPLETED";
        assertThat(runs.findTasks(current.runId()).getLast().status()).isEqualTo(taskStatus);
        assertThat(completed.getExecutionStatus()).isEqualTo(result == RunItemResult.UNKNOWN
                ? RunExecutionStatus.NEEDS_REVIEW : result == RunItemResult.MERGE_BLOCKED
                ? RunExecutionStatus.FAILED : RunExecutionStatus.DONE);
        if (result == RunItemResult.ALREADY_MERGED) {
            assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration)).orElseThrow().getLastMergeAt()).isNotNull();
        } else {
            assertThat(jdbc.queryForMap("select * from window_iteration where window_id=? and iteration_key=?",
                    windowId, firstIteration)).isEqualTo(before.get("binding"));
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNotSentUnknownKeepsRepositoryQueueAndRetryClosed() {
        var run = admit(List.of(firstRepo, secondRepo), List.of(firstIteration, secondIteration), false);
        var original = claimMergedMrTask(run.getId().value(), "CREATED", 40);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var current = reclaimMergeIntent(original);

        var completed = completeNotSent(current, RunItemResult.UNKNOWN, notSentPatch(RunItemResult.UNKNOWN));

        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("NOT_SENT");
        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
        var other = runs.claimNextTask(current.runId()).orElseThrow();
        assertThat(other.repo()).isNotEqualTo(current.repo());
        completeMergedMrFixtureTask(other, "MR_REUSED", 40);
        var skipped = jdbc.queryForList("select status,claim_token,started_at from run_task where run_id=? and target_id in "
                + "(select id from run_item where run_id=? and repo_id=? and id<>?) order by task_order",
                current.runId(), current.runId(), current.repo().value(), current.itemId());
        assertThat(skipped).hasSize(4).allSatisfy(task -> assertThat(task)
                .containsEntry("status", "SKIPPED").containsEntry("claim_token", null).containsEntry("started_at", null));
        assertThat(jdbc.queryForObject("select count(*) from run_step s join run_item i on i.id=s.run_item_id "
                + "where i.run_id=? and i.repo_id=? and i.id<>?", Integer.class,
                current.runId(), current.repo().value(), current.itemId())).isZero();
        assertThatThrownBy(() -> plans.admitRetry(runs.findById(current.runId()).orElseThrow(),
                List.of(current.itemId()), current.operator(), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        assertThat(runs.findTasks(current.runId()).stream()
                .filter(task -> task.taskId().equals(current.fence().taskId())).findFirst().orElseThrow().status())
                .isEqualTo("UNKNOWN");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void mergeIntentNotSentBlockedRetainsQueuePolicyAndReusesMrOnRetry(boolean failFast) {
        var run = admit(List.of(firstRepo), List.of(firstIteration, secondIteration), failFast);
        var current = claimMergedMrTask(run.getId().value(), "MR_REUSED", 40);
        String operationId = beginMergeIntent(current);

        completeNotSent(current, RunItemResult.MERGE_BLOCKED, notSentPatch(RunItemResult.MERGE_BLOCKED));

        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("NOT_SENT");
        var next = runs.claimNextTask(current.runId());
        if (failFast) {
            assertThat(next).isEmpty();
            assertThat(runs.findById(current.runId()).orElseThrow().getItems().stream()
                    .filter(item -> !item.getId().value().equals(current.itemId())).findFirst().orElseThrow().getFinalResult())
                    .isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
        } else {
            assertThat(next).isPresent();
            var later = next.orElseThrow();
            assertThat(later.itemId()).isNotEqualTo(current.itemId());
            assertThat(later.actionType()).isEqualTo(ActionType.ENSURE_FEATURE);
            for (int index = 0; index < 4; index++) {
                completeMergedMrFixtureTask(later, "CREATED", 40);
                if (index < 3) later = runs.claimNextTask(current.runId()).orElseThrow();
            }
        }
        var previous = runs.findById(current.runId()).orElseThrow();
        var retry = plans.admitRetry(previous, List.of(current.itemId()), current.operator(), UUID.randomUUID().toString());
        var retried = runs.findById(retry.runId()).orElseThrow();
        assertThat(retried.getItems()).hasSize(1);
        var retryItem = retried.getItems().getFirst();
        for (String key : List.of("git.projectId", "git.sourceBranch", "git.targetBranch", "git.mrIid",
                "git.mrUrl", "git.sourceSha", "git.mrDisposition")) {
            assertThat(retryItem.getMetadata().get(key)).isEqualTo(current.metadata().get(key));
        }
        assertThat(runs.findTasks(retry.runId()).stream().filter(task -> task.status().equals("PENDING"))
                .map(RunPort.TaskView::actionType)).containsExactly(ActionType.TRY_MERGE);
        assertThat(jdbc.queryForObject("select count(*) from run_step where run_item_id=?", Integer.class,
                retryItem.getId().value())).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "MERGED", "FAILED", "ALREADY_MISSING_DISPOSITION", "ALREADY_WRONG_DISPOSITION", "ALREADY_BAD_SHA",
            "ALREADY_HTTP_REQUEST_ID", "BLOCKED_MERGE_SHA", "BLOCKED_DISPOSITION",
            "UNKNOWN_MERGE_SHA", "UNKNOWN_DISPOSITION", "UNKNOWN_HTTP_REQUEST_ID", "BLOCKED_HTTP_REQUEST_ID"
    })
    void mergeIntentNotSentRejectsResultOrPatchThatClaimsUnperformedWork(String contradiction) {
        var current = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(current);
        RunItemResult result = contradiction.startsWith("ALREADY") ? RunItemResult.ALREADY_MERGED
                : contradiction.startsWith("BLOCKED") ? RunItemResult.MERGE_BLOCKED
                : contradiction.equals("MERGED") ? RunItemResult.MERGED
                : contradiction.equals("FAILED") ? RunItemResult.FAILED : RunItemResult.UNKNOWN;
        var patch = notSentPatch(result);
        switch (contradiction) {
            case "ALREADY_MISSING_DISPOSITION" -> patch.remove("git.mrDisposition");
            case "ALREADY_WRONG_DISPOSITION" -> patch.put("git.mrDisposition", "CREATED");
            case "ALREADY_BAD_SHA" -> patch.put("git.mergeSha", "not-a-commit");
            case "BLOCKED_MERGE_SHA", "UNKNOWN_MERGE_SHA" -> patch.put("git.mergeSha", "b".repeat(40));
            case "BLOCKED_DISPOSITION", "UNKNOWN_DISPOSITION" -> patch.put("git.mrDisposition", "ALREADY_MERGED");
            case "ALREADY_HTTP_REQUEST_ID", "BLOCKED_HTTP_REQUEST_ID", "UNKNOWN_HTTP_REQUEST_ID" ->
                    patch.put("git.requestId", "never-sent-request");
            default -> { }
        }
        var before = projectionFacts(current, operationId);

        assertThatThrownBy(() -> runs.completeTask(current, step(current, result), patch))
                .isInstanceOf(BusinessException.class);

        assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "STALE_OWNER", "LEGACY_HASH", "MISSING_MESSAGE", "STRING_REMOVE", "EXTRA_FIELD", "WRONG_ATTEMPT",
            "EXTRA_EVENT", "GUARD_GENERATION", "GUARD_SESSION", "GUARD_OPERATION", "CHECKPOINT_SHA", "CURRENT_STEP"
    })
    void mergeIntentNotSentRejectsBrokenOriginalQualification(String contradiction) {
        var original = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(original);
        var target = mergeIntentTarget(original);
        switch (contradiction) {
            case "STALE_OWNER" -> {
                expireClaim(original);
                reclaimMergeIntent(original);
            }
            case "LEGACY_HASH" -> jdbc.update("update git_operation set request_hash=? where id=?",
                    GitOperationJournalPort.mappingHash("MERGE_MR", target.toString()), operationId);
            case "MISSING_MESSAGE", "STRING_REMOVE", "EXTRA_FIELD" -> {
                if (contradiction.equals("MISSING_MESSAGE")) target.remove("merge_commit_message");
                else if (contradiction.equals("STRING_REMOVE")) target.put("should_remove_source_branch", "false");
                else target.put("squash", true);
                String remote = jdbc.queryForObject("select remote_key from git_operation where id=?", String.class, operationId);
                jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                        target.toString(), mergeIntentHash(remote, target), operationId);
            }
            case "WRONG_ATTEMPT" -> jdbc.update("update git_operation_event set attempt_id=? where operation_id=?",
                    UUID.randomUUID().toString(), operationId);
            case "EXTRA_EVENT" -> jdbc.update("insert into git_operation_event(id,operation_id,attempt_id,event_sequence,"
                            + "event_type,occurred_at,evidence_json,reason) values (?,?,?,1,'SEND_STARTED',clock_timestamp(),'{}',null)",
                    UUID.randomUUID().toString(), operationId, UUID.randomUUID().toString());
            case "GUARD_GENERATION" -> jdbc.update("update repo_execution_guard set generation=generation+1 where operation_id=?", operationId);
            case "GUARD_SESSION" -> jdbc.update("update repo_execution_guard set session_id=? where operation_id=?",
                    UUID.randomUUID().toString(), operationId);
            case "GUARD_OPERATION" -> jdbc.update("update repo_execution_guard set operation_id=? where operation_id=?",
                    UUID.randomUUID().toString(), operationId);
            case "CHECKPOINT_SHA" -> jdbc.update("update run_item_metadata set metadata_value=? where run_item_id=? and metadata_key='git.sourceSha'",
                    "c".repeat(40), original.itemId());
            case "CURRENT_STEP" -> jdbc.update("insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) "
                    + "values (?,'TRY_MERGE','UNKNOWN',clock_timestamp(),clock_timestamp(),'existing local fact',?)",
                    original.itemId(), original.fence().taskId());
            default -> throw new AssertionError(contradiction);
        }
        var before = projectionFacts(original, operationId);

        assertThatThrownBy(() -> runs.completeTask(original, step(original, RunItemResult.UNKNOWN),
                notSentPatch(RunItemResult.UNKNOWN))).isInstanceOf(BusinessException.class);

        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNotSentLocalResultRollsBackWithOuterTransaction() {
        var original = claimMergedMrTask("MR_REUSED", 64);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var current = reclaimMergeIntent(original);
        var before = projectionFacts(current, operationId);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var completed = completeNotSent(current, RunItemResult.ALREADY_MERGED, notSentPatch(RunItemResult.ALREADY_MERGED));
            assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
            assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId)).isEqualTo("NOT_SENT");
            assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                    current.fence().taskId())).isEqualTo(1);
            assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                    .orElseThrow().getLastMergeAt()).isNotNull();
            tx.setRollbackOnly();
        });

        assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNotSentRejectedTaskCasRollsBackCompletedLocalDecision() {
        var current = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(current);
        var before = projectionFacts(current, operationId);
        String trigger = "b6_not_sent_" + UUID.randomUUID().toString().replace("-", "");
        String seen = trigger + "_seen";
        try {
            jdbc.execute("create sequence " + seen);
            // nextval survives rollback; it proves the final CAS saw the local decision in this same transaction.
            jdbc.execute("create function " + trigger + "() returns trigger language plpgsql as $$ begin "
                    + "if exists(select 1 from git_operation where id='" + operationId + "' and state='NOT_SENT') "
                    + "and exists(select 1 from git_operation_event where operation_id='" + operationId
                    + "' and event_sequence=1 and event_type='NOT_SENT') "
                    + "and exists(select 1 from repo_execution_guard where remote_key=(select remote_key from git_operation where id='"
                    + operationId + "') and operation_id is null and session_id is null and reason is null) "
                    + "and exists(select 1 from run_step where task_id=old.id) then perform nextval('" + seen
                    + "'); end if; return null; end; $$");
            jdbc.execute("create trigger " + trigger + " before update of status on run_task for each row "
                    + "when (old.id='" + current.fence().taskId() + "' and old.status='RUNNING' and new.status='UNKNOWN') "
                    + "execute function " + trigger + "()");

            assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN),
                    notSentPatch(RunItemResult.UNKNOWN))).isInstanceOf(BusinessException.class);

            assertThat(jdbc.queryForObject("select is_called from " + seen, Boolean.class))
                    .as("the actual rejected Task UPDATE must observe NOT_SENT, its event, Step and released guard").isTrue();
            assertThat(projectionFacts(current, operationId)).isEqualTo(before);
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on run_task");
            jdbc.execute("drop function if exists " + trigger + "()");
            jdbc.execute("drop sequence if exists " + seen);
        }
    }

    @Test
    void mergeIntentNotSentSendWinsActualLockAndUnknownCannotSealSender() throws Exception {
        var current = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(current);
        var taskBefore = jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId());
        var sendLocked = new CountDownLatch(1);
        var releaseSend = new CountDownLatch(1);
        var completionStarted = new CountDownLatch(1);
        var senderThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var senderPid = new java.util.concurrent.atomic.AtomicInteger();
        var completerPid = new java.util.concurrent.atomic.AtomicInteger();
        var pauseOnce = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (Thread.currentThread() == senderThread.get() && pauseOnce.compareAndSet(false, true)) {
                senderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                sendLocked.countDown();
                awaitMergeIntentLatch(releaseSend);
            }
            return Instant.now();
        }).when(clock).instant();

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var sender = pool.submit(() -> {
                    senderThread.set(Thread.currentThread());
                    mergeIntentSending(operationId, current);
                });
                assertThat(sendLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var completion = pool.submit(() -> {
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                            completerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                            completionStarted.countDown();
                            runs.completeTask(current, step(current, RunItemResult.UNKNOWN), notSentPatch(RunItemResult.UNKNOWN));
                        });
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                });
                assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(completerPid.get(), Set.of(senderPid.get()));
                releaseSend.countDown();
                sender.get(10, TimeUnit.SECONDS);
                assertThat(completion.get(10, TimeUnit.SECONDS))
                        .as("a preflight UNKNOWN must not close the Task after durable SEND won")
                        .isInstanceOf(BusinessException.class);
            } finally {
                releaseSend.countDown();
            }
        }

        assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId())).isEqualTo(taskBefore);
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
        assertThat(journal.find(operationId).orElseThrow().events()).hasSize(2);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId).containsEntry("reason", "REQUEST_IN_FLIGHT");
        mergeIntentFinish(operationId, current, GitWriteResult.Outcome.APPLIED, mergedMrProof(current, null).toString());
        assertThat(runs.completeTask(current, step(current, RunItemResult.MERGED), mergedMrResultPatch("CREATED", null))
                .getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(jdbc.queryForObject("select count(*) from git_operation_event where operation_id=? and event_type='NOT_SENT'",
                Integer.class, operationId)).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNotSentTerminalDecisionWinsActualLockAgainstLateSend() throws Exception {
        var original = claimMergedMrTask("MR_REUSED", 40);
        String operationId = beginMergeIntent(original);
        expireClaim(original);
        var current = reclaimMergeIntent(original);
        var decisionLocked = new CountDownLatch(1);
        var releaseDecision = new CountDownLatch(1);
        var senderStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var completion = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    var completed = completeNotSent(current, RunItemResult.ALREADY_MERGED, notSentPatch(RunItemResult.ALREADY_MERGED));
                    assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId)).isEqualTo("NOT_SENT");
                    decisionLocked.countDown();
                    awaitMergeIntentLatch(releaseDecision);
                    return completed;
                }));
                assertThat(decisionLocked.await(5, TimeUnit.SECONDS))
                        .as("the actual result transaction must hold its terminal unsent decision").isTrue();
                var lateSender = pool.submit(() -> {
                    senderStarted.countDown();
                    try {
                        mergeIntentSending(operationId, current);
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                });
                assertThat(senderStarted.await(5, TimeUnit.SECONDS)).isTrue();
                int waiter = awaitMergeIntentWaiter(holderPid.get());
                awaitMergeIntentBlockers(waiter, Set.of(holderPid.get()));
                releaseDecision.countDown();
                assertThat(completion.get(10, TimeUnit.SECONDS).getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
                assertThat(lateSender.get(10, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class);
            } finally {
                releaseDecision.countDown();
            }
        }
        var terminal = projectionFacts(current, operationId);
        for (var late : List.of(original, current)) {
            assertThatThrownBy(() -> mergeIntentSending(operationId, late)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> mergeIntentFinish(operationId, late, GitWriteResult.Outcome.APPLIED,
                    mergedMrProof(late, null).toString())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> runs.completeTask(late, step(late, RunItemResult.UNKNOWN),
                    notSentPatch(RunItemResult.UNKNOWN))).isInstanceOf(BusinessException.class);
        }
        assertThat(projectionFacts(current, operationId)).isEqualTo(terminal);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNotSentTwoLocalCompletersWaitOnPostgresAndCommitOnce() throws Exception {
        var current = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(current);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstStarted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        var firstPid = new java.util.concurrent.atomic.AtomicInteger();
        var secondPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(3)) {
            try {
                var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    jdbc.queryForObject("select id from run where id=? for update", String.class, current.runId());
                    jdbc.queryForObject("select id from run_task where id=? for update", String.class, current.fence().taskId());
                    locked.countDown();
                    awaitMergeIntentLatch(release);
                    return null;
                }));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var first = pool.submit(() -> notSentConcurrentCompletion(current, firstPid, firstStarted));
                assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(firstPid.get(), Set.of(holderPid.get()));
                var second = pool.submit(() -> notSentConcurrentCompletion(current, secondPid, secondStarted));
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(secondPid.get(), Set.of(holderPid.get(), firstPid.get()));
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
            } finally {
                release.countDown();
            }
        }
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("NOT_SENT");
        assertThat(journal.find(operationId).orElseThrow().events()).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isEqualTo(1);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNotSentDoesNotReplaceRealResponseUnknown() {
        var current = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(current);
        mergeIntentSending(operationId, current);
        mergeIntentFinish(operationId, current, GitWriteResult.Outcome.UNKNOWN, "{\"httpStatus\":502}");
        var operation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
        var events = journal.find(operationId).orElseThrow().events();

        var completed = completeNotSent(current, RunItemResult.UNKNOWN, notSentPatch(RunItemResult.UNKNOWN));

        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        assertThat(runs.findTasks(current.runId()).getLast().status()).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(operation);
        assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(events).hasSize(3);
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("UNKNOWN");
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId)
                .containsEntry("session_id", current.fence().claimToken()).containsEntry("reason", "REMOTE_RESULT_UNKNOWN");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNotSentInvalidatesOldObservationAndKeepsNewObservationReadOnly() {
        var current = claimMergedMrTask("MR_REUSED", 64);
        String operationId = beginMergeIntent(current);
        long beforeVersion = mergeIntentVersion(operationId);

        completeNotSent(current, RunItemResult.UNKNOWN, notSentPatch(RunItemResult.UNKNOWN));

        long terminalVersion = mergeIntentVersion(operationId);
        assertThat(terminalVersion).isGreaterThan(beforeVersion);
        var terminal = projectionFacts(current, operationId);
        assertThatThrownBy(() -> journal.observe(operationId, "reviewer", "before-local-completion", beforeVersion,
                io.releasehub.application.gitoperation.GitReadResult.Status.UNAVAILABLE, "{}"))
                .isInstanceOf(BusinessException.class);
        assertThat(journal.observation(operationId, "reviewer", "before-local-completion")).isEmpty();
        var observation = journal.observe(operationId, "reviewer", "after-local-completion", terminalVersion,
                io.releasehub.application.gitoperation.GitReadResult.Status.UNAVAILABLE, "{\"read\":\"unavailable\"}");
        assertThat(observation.expectedVersion()).isEqualTo(terminalVersion);
        assertThat(journal.observation(operationId, "reviewer", "after-local-completion")).hasValueSatisfying(saved -> {
            assertThat(saved.id()).isEqualTo(observation.id());
            assertThat(saved.evidenceJson()).isEqualTo(observation.evidenceJson());
        });
        assertThat(mergeIntentVersion(operationId)).isGreaterThan(terminalVersion);
        assertThat(withoutOperationObservationAudit(projectionFacts(current, operationId)))
                .isEqualTo(withoutOperationObservationAudit(terminal));
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("NOT_SENT");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void mergeIntentNotSentLaterReservationKeepsHistoricalBlockedMeaning() {
        var current = claimMergedMrTask("CREATED", 40);
        String operationId = beginMergeIntent(current);
        completeNotSent(current, RunItemResult.ALREADY_MERGED, notSentPatch(RunItemResult.ALREADY_MERGED));
        assertThat(journal.find(operationId).orElseThrow().operation().blocked()).isFalse();
        var originalEvents = journal.find(operationId).orElseThrow().events();
        var template = mergeIntentCommand(current, mergeIntentTarget(current));
        var next = new GitOperationJournalPort.Command(UUID.randomUUID().toString(), template.repoId(),
                template.repositoryMapping(), template.remoteKey(), template.provider(), template.baseUrl(), template.projectId(),
                template.action(), template.targetJson(), template.requestHash(), null, template.operator(),
                UUID.randomUUID().toString(), null);

        String laterOperationId = journal.begin(next).id();

        assertThat(laterOperationId).isNotEqualTo(operationId);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", laterOperationId)
                .containsEntry("session_id", next.sessionId());
        var history = journal.find(operationId).orElseThrow();
        assertThat(history.operation().state()).isEqualTo("NOT_SENT");
        assertThat(history.operation().blocked()).as("blocked is the current remote reservation, not this operation's ownership").isTrue();
        assertThat(history.events()).isEqualTo(originalEvents);
        var held = projectionFacts(current, operationId);
        journal.observe(operationId, "reviewer", "later-reservation", history.operation().version(),
                io.releasehub.application.gitoperation.GitReadResult.Status.UNAVAILABLE, "{}");
        String changedProject = template.projectId() + "1";
        String changedRemote = GitOperationJournalPort.remoteKey("GITLAB", template.baseUrl(), changedProject);
        var changed = new GitOperationJournalPort.Command(UUID.randomUUID().toString(), template.repoId(),
                template.repositoryMapping(), changedRemote, template.provider(), template.baseUrl(), changedProject,
                template.action(), template.targetJson(), mergeIntentHash(changedRemote, mergeIntentTarget(current)), null,
                template.operator(), UUID.randomUUID().toString(), null);
        assertThatThrownBy(() -> journal.begin(changed)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from git_operation where remote_key=?", Integer.class, changedRemote)).isZero();
        assertThat(mergeIntentVersion(operationId)).isGreaterThan(history.operation().version());
        assertThat(withoutOperationObservationAudit(projectionFacts(current, operationId)))
                .isEqualTo(withoutOperationObservationAudit(held));
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    private Map<String, Object> withoutOperationObservationAudit(Map<String, Object> facts) {
        var execution = new java.util.LinkedHashMap<>(facts);
        var operation = new java.util.LinkedHashMap<>((Map<?, ?>) facts.get("operation"));
        operation.keySet().removeAll(Set.of("updated_at", "state_version"));
        execution.put("operation", operation);
        return execution;
    }

    private java.util.LinkedHashMap<String, String> notSentPatch(RunItemResult result) {
        var patch = new java.util.LinkedHashMap<String, String>();
        if (result == RunItemResult.ALREADY_MERGED || result == RunItemResult.MERGED) {
            patch.putAll(mergedMrResultPatch(result == RunItemResult.ALREADY_MERGED ? "ALREADY_MERGED" : "CREATED", null));
        } else {
            patch.put("git.blockReason", result == RunItemResult.MERGE_BLOCKED ? "PERMISSION_DENIED" : "UNKNOWN");
            if (result == RunItemResult.MERGE_BLOCKED) patch.put("git.requestId", null);
        }
        return patch;
    }

    private Run completeNotSent(RunPort.TaskClaim current, RunItemResult result, Map<String, String> patch) {
        var completed = new java.util.concurrent.atomic.AtomicReference<Run>();
        org.assertj.core.api.Assertions.assertThatCode(() ->
                completed.set(runs.completeTask(current, step(current, result), patch)))
                .as("the current owner must commit its real local result without another lease expiry")
                .doesNotThrowAnyException();
        return completed.get();
    }

    private boolean notSentConcurrentCompletion(RunPort.TaskClaim current,
            java.util.concurrent.atomic.AtomicInteger pid, CountDownLatch started) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                pid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                started.countDown();
                runs.completeTask(current, step(current, RunItemResult.UNKNOWN), notSentPatch(RunItemResult.UNKNOWN));
            });
            return true;
        } catch (BusinessException rejected) {
            return false;
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "ENSURE_FEATURE,40,AUTO", "ENSURE_FEATURE,64,NAMED",
            "ENSURE_RELEASE,40,AUTO", "ENSURE_RELEASE,64,AUTO"
    })
    void branchIntentClaimPreservesOriginalRequestAndHistory(ActionType action, int shaLength, String mode) {
        if (mode.equals("NAMED")) {
            versions.saveWithVersion(firstIteration, firstRepo, "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                    "feature/named-" + windowId, "SYSTEM", Instant.now(), BranchCreationMode.NAMED);
        }
        var original = claimBranchTask(action);
        String operationId = beginBranchIntent(original, shaLength);
        var unexpired = projectionFacts(original, operationId);
        assertThat(claimExpiry(original)).isAfter(databaseNow());
        assertThat(runs.claimNextTask(original.runId())).as("an unexpired branch owner cannot be replaced").isEmpty();
        assertThat(projectionFacts(original, operationId)).isEqualTo(unexpired);
        expireClaim(original);
        var before = projectionFacts(original, operationId);
        var firstStart = jdbc.queryForObject("select started_at from run_task where id=?",
                java.sql.Timestamp.class, original.fence().taskId());
        var operationHistory = new java.util.LinkedHashMap<>(jdbc.queryForMap("select * from git_operation where id=?", operationId));
        operationHistory.keySet().removeAll(Set.of("state_version", "updated_at"));

        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(original.runId());
        var current = reclaimBranchIntent(original);

        assertThat(current.fence().taskId()).isEqualTo(original.fence().taskId());
        assertThat(current.fence().claimToken()).isNotEqualTo(original.fence().claimToken());
        assertThat(current.metadata()).as("recovery must not inject derived operation facts into frozen metadata")
                .isEqualTo(original.metadata());
        assertThat(jdbc.queryForObject("select started_at from run_task where id=?",
                java.sql.Timestamp.class, current.fence().taskId())).isEqualTo(firstStart);
        assertThat(claimExpiry(current)).isAfter(databaseNow());
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId))
                .containsAllEntriesOf(operationHistory)
                .containsEntry("claim_token", original.fence().claimToken())
                .containsEntry("session_id", original.fence().claimToken());
        assertThat(mergeIntentVersion(operationId)).isGreaterThan(
                ((Number) ((Map<?, ?>) before.get("operation")).get("state_version")).longValue());
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(projectionGuard(operationId)).isEqualTo(before.get("guard"));
        assertThat(jdbc.queryForList("select * from run_item_metadata where run_item_id=? order by metadata_key",
                current.itemId())).isEqualTo(before.get("metadata"));
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by action_type",
                current.itemId())).isEqualTo(before.get("steps"));
        var after = projectionFacts(current, operationId);
        assertThatThrownBy(() -> journal.sending(operationId, original.fence())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(original, step(original, RunItemResult.UNKNOWN), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(after);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "ENSURE_FEATURE,false,ALREADY_EXISTS,PROJECT,40", "ENSURE_RELEASE,true,ALREADY_EXISTS,EMPTY,64",
            "ENSURE_FEATURE,true,FAILED,EMPTY,64", "ENSURE_RELEASE,false,FAILED,PROJECT_REASON,40",
            "ENSURE_FEATURE,false,UNKNOWN,EMPTY,64", "ENSURE_RELEASE,true,UNKNOWN,REASON,40"
    })
    void branchIntentLocalResultsKeepTheirActualPatchAndOriginalScope(ActionType action, boolean transferred,
            RunItemResult result, String patchShape, int shaLength) throws Exception {
        var original = claimBranchTask(action);
        String operationId = beginBranchIntent(original, shaLength);
        var originalEvent = jdbc.queryForMap("select * from git_operation_event where operation_id=?", operationId);
        var current = original;
        if (transferred) {
            expireClaim(original);
            current = reclaimBranchIntent(original);
        }
        assertThat(claimExpiry(current)).isAfter(databaseNow());
        var before = projectionFacts(current, operationId);
        var operationHistory = new java.util.LinkedHashMap<>(jdbc.queryForMap("select * from git_operation where id=?", operationId));
        operationHistory.keySet().removeAll(Set.of("state", "state_version", "updated_at", "reason"));
        var taskHistory = new java.util.LinkedHashMap<>(jdbc.queryForMap(
                "select * from run_task where id=?", current.fence().taskId()));
        taskHistory.keySet().removeAll(Set.of("status", "finished_at", "error_message"));
        var patch = branchIntentPatch(current, patchShape);

        var completed = completeNotSent(current, result, patch);

        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId))
                .containsAllEntriesOf(operationHistory).containsEntry("state", "NOT_SENT")
                .containsEntry("reason", patch.get("git.blockReason"));
        var events = jdbc.queryForList(
                "select * from git_operation_event where operation_id=? order by event_sequence", operationId);
        assertThat(events).hasSize(2);
        assertThat(events.getFirst()).isEqualTo(originalEvent);
        assertThat(events.getLast()).containsEntry("attempt_id", originalEvent.get("attempt_id"))
                .containsEntry("event_sequence", 1).containsEntry("event_type", "NOT_SENT")
                .containsEntry("reason", patch.get("git.blockReason"));
        var evidence = json.readTree((String) events.getLast().get("evidence_json"));
        assertThat(evidence.path("taskId").textValue()).isEqualTo(current.fence().taskId());
        assertThat(evidence.path("claimToken").textValue()).isEqualTo(current.fence().claimToken());
        assertThat(evidence.path("result").textValue()).isEqualTo(result.name());
        assertThat(evidence.get("metadata")).isEqualTo(json.valueToTree(patch));
        assertThat(evidence.has("httpStatus")).isFalse();
        assertThat(evidence.has("response")).isFalse();
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null)
                .containsEntry("session_id", null).containsEntry("reason", null)
                .containsEntry("generation", ((Map<?, ?>) before.get("guard")).get("generation"));
        assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId()))
                .containsAllEntriesOf(taskHistory).containsEntry("status",
                        result == RunItemResult.ALREADY_EXISTS ? "COMPLETED" : result == RunItemResult.UNKNOWN ? "UNKNOWN" : "FAILED");
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isEqualTo(1);
        var expectedMetadata = new java.util.LinkedHashMap<>(current.metadata());
        patch.forEach((key, value) -> { if (value == null) expectedMetadata.remove(key); else expectedMetadata.put(key, value); });
        assertThat(completed.getItems().getFirst().getMetadata()).isEqualTo(expectedMetadata);
        assertThat(completed.getItems().getFirst().getSteps().stream().filter(value -> value.actionType() == action))
                .singleElement().satisfies(value -> assertThat(value.result()).isEqualTo(result));
        assertThat(completed.getExecutionStatus()).isEqualTo(result == RunItemResult.ALREADY_EXISTS
                ? RunExecutionStatus.RUNNING : result == RunItemResult.UNKNOWN ? RunExecutionStatus.NEEDS_REVIEW : RunExecutionStatus.FAILED);
        if (result == RunItemResult.UNKNOWN) {
            var successors = jdbc.queryForList("select status,claim_token,started_at from run_task where run_id=? "
                    + "and task_order>(select task_order from run_task where id=?) order by task_order",
                    current.runId(), current.fence().taskId());
            assertThat(successors).hasSize(action == ActionType.ENSURE_FEATURE ? 3 : 2)
                    .allSatisfy(task -> assertThat(task).containsEntry("status", "SKIPPED")
                            .containsEntry("claim_token", null).containsEntry("started_at", null));
            assertThat(jdbc.queryForObject("select count(*) from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? and t.task_order>(select task_order from run_task where id=?)", Integer.class,
                    current.runId(), current.fence().taskId())).isZero();
            assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(current.runId());
            assertThat(runs.claimNextTask(current.runId())).isEmpty();
            assertThatThrownBy(() -> plans.admitRetry(completed, List.of(original.itemId()),
                    original.operator(), UUID.randomUUID().toString())).isInstanceOf(BusinessException.class);
        }
        if (result == RunItemResult.ALREADY_EXISTS && action == ActionType.ENSURE_RELEASE) {
            var binding = bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration)).orElseThrow();
            assertThat(binding.getReleaseBranch()).isEqualTo(current.metadata().get("plan.releaseBranch"));
            assertThat(binding.getBranchCreated()).isTrue();
        } else {
            assertThat(jdbc.queryForMap("select * from window_iteration where window_id=? and iteration_key=?",
                    windowId, firstIteration)).isEqualTo(before.get("binding"));
        }
        var terminal = projectionFacts(current, operationId);
        for (var late : List.of(original, current)) {
            assertThatThrownBy(() -> journal.sending(operationId, late.fence())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> runs.completeTask(late, step(late, RunItemResult.UNKNOWN), Map.of()))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(projectionFacts(current, operationId)).isEqualTo(terminal);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "CREATED", "FOREIGN_PROJECT", "NULL_PROJECT", "MR_IID", "MERGE_SHA", "HTTP_REQUEST_ID"
    })
    void branchIntentRejectsInventedCreationAndForeignResultFields(String contradiction) {
        var current = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = beginBranchIntent(current, 40);
        var patch = new java.util.LinkedHashMap<String, String>();
        RunItemResult result = contradiction.equals("CREATED") ? RunItemResult.CREATED : RunItemResult.UNKNOWN;
        switch (contradiction) {
            case "CREATED" -> patch.put("git.projectId", branchIntentProject(current));
            case "FOREIGN_PROJECT" -> patch.put("git.projectId", branchIntentProject(current) + "1");
            case "NULL_PROJECT" -> patch.put("git.projectId", null);
            case "MR_IID" -> patch.put("git.mrIid", "11");
            case "MERGE_SHA" -> patch.put("git.mergeSha", "b".repeat(40));
            case "HTTP_REQUEST_ID" -> patch.put("git.requestId", "never-sent");
            default -> throw new AssertionError(contradiction);
        }
        var before = projectionFacts(current, operationId);

        assertThatThrownBy(() -> runs.completeTask(current, step(current, result), patch))
                .isInstanceOf(BusinessException.class);

        assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "MISSING_METHOD", "QUERY_SUFFIX", "NUMERIC_REF", "EXTRA_FIELD", "WRONG_BRANCH", "WRONG_SOURCE_REF",
            "WRONG_REF", "WRONG_HASH", "EXISTING_MODE", "NON_BRANCH_ACTION", "GUARD_SESSION",
            "GUARD_GENERATION", "GUARD_OPERATION", "GUARD_REASON", "WRONG_ATTEMPT", "EXTRA_EVENT", "CURRENT_STEP", "FOREIGN_PROJECT"
    })
    void branchIntentBrokenOriginalFactsCannotClaimBeginSendOrComplete(String contradiction) {
        var original = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = beginBranchIntent(original, 40);
        var requested = branchIntentTarget(original, 40);
        var candidate = branchIntentCommand(original, requested);
        expireClaim(original);
        var damaged = requested.deepCopy();
        switch (contradiction) {
            case "MISSING_METHOD", "QUERY_SUFFIX", "NUMERIC_REF", "EXTRA_FIELD", "WRONG_BRANCH", "WRONG_SOURCE_REF", "WRONG_REF" -> {
                switch (contradiction) {
                    case "MISSING_METHOD" -> damaged.remove("method");
                    case "QUERY_SUFFIX" -> damaged.put("suffix", "/repository/branches?dry_run=true");
                    case "NUMERIC_REF" -> damaged.put("ref", new java.math.BigInteger("1".repeat(40)));
                    case "EXTRA_FIELD" -> damaged.put("unexpected", true);
                    case "WRONG_BRANCH" -> damaged.put("branch", "feature/another");
                    case "WRONG_SOURCE_REF" -> damaged.put("sourceRef", "other-base");
                    case "WRONG_REF" -> damaged.put("ref", "b".repeat(40));
                    default -> throw new AssertionError(contradiction);
                }
                String fingerprint = contradiction.equals("WRONG_REF") ? candidate.requestHash()
                        : branchIntentHash(candidate.remoteKey(), damaged);
                jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                        damaged.toString(), fingerprint, operationId);
            }
            case "WRONG_HASH" -> {
                var reverse = json.createObjectNode().put("ref", requested.path("ref").textValue())
                        .put("branch", requested.path("branch").textValue());
                String rawOrderHash = mergeIntentSha256("POST|" + candidate.remoteKey()
                        + "|/repository/branches||" + mergeIntentSha256(reverse.toString()) + "|");
                assertThat(rawOrderHash).isNotEqualTo(candidate.requestHash());
                jdbc.update("update git_operation set request_hash=? where id=?", rawOrderHash, operationId);
            }
            case "EXISTING_MODE" -> jdbc.update(
                    "update run_item_metadata set metadata_value='EXISTING' where run_item_id=? and metadata_key='plan.featureBranchMode'",
                    original.itemId());
            case "NON_BRANCH_ACTION" -> jdbc.update("update git_operation set action='CREATE_MR' where id=?", operationId);
            case "GUARD_SESSION" -> jdbc.update("update repo_execution_guard set session_id=? where operation_id=?",
                    UUID.randomUUID().toString(), operationId);
            case "GUARD_GENERATION" -> jdbc.update("update repo_execution_guard set generation=generation+1 where operation_id=?", operationId);
            case "GUARD_OPERATION" -> jdbc.update("update repo_execution_guard set operation_id=? where operation_id=?",
                    UUID.randomUUID().toString(), operationId);
            case "GUARD_REASON" -> jdbc.update("update repo_execution_guard set reason='REQUEST_IN_FLIGHT' where operation_id=?", operationId);
            case "WRONG_ATTEMPT" -> jdbc.update("update git_operation_event set attempt_id=? where operation_id=?",
                    UUID.randomUUID().toString(), operationId);
            case "EXTRA_EVENT" -> jdbc.update("insert into git_operation_event(id,operation_id,attempt_id,event_sequence,event_type,"
                            + "occurred_at,evidence_json,reason) values (?,?,?,1,'SEND_STARTED',clock_timestamp(),'{}',null)",
                    UUID.randomUUID().toString(), operationId, UUID.randomUUID().toString());
            case "CURRENT_STEP" -> jdbc.update("insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) "
                            + "values (?,'ENSURE_FEATURE','UNKNOWN',clock_timestamp(),clock_timestamp(),'retained local fact',?)",
                    original.itemId(), original.fence().taskId());
            case "FOREIGN_PROJECT" -> jdbc.update(
                    "insert into run_item_metadata(run_item_id,metadata_key,metadata_value) values (?,'git.projectId',?)",
                    original.itemId(), branchIntentProject(original) + "1");
            default -> throw new AssertionError(contradiction);
        }
        var before = projectionFacts(original, operationId);

        assertMergeIntentClaimRejected(original.runId());
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        assertThatThrownBy(() -> journal.begin(candidate)).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        assertThatThrownBy(() -> journal.sending(operationId, original.fence())).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        assertThatThrownBy(() -> runs.completeTask(original, step(original, RunItemResult.UNKNOWN), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentClaimCasFailureRollsBackThePreparedVersion() {
        var original = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = beginBranchIntent(original, 40);
        expireClaim(original);
        long version = mergeIntentVersion(operationId);
        var before = projectionFacts(original, operationId);
        String trigger = "b7_branch_claim_" + UUID.randomUUID().toString().replace("-", "");
        String seen = trigger + "_seen";
        try {
            jdbc.execute("create sequence " + seen);
            jdbc.execute("create function " + trigger + "() returns trigger language plpgsql as $$ begin "
                    + "if exists(select 1 from git_operation where id='" + operationId + "' and state_version>" + version
                    + ") then perform nextval('" + seen + "'); end if; return null; end; $$");
            jdbc.execute("create trigger " + trigger + " before update of claim_token on run_task for each row "
                    + "when (old.id='" + original.fence().taskId()
                    + "' and new.claim_token is distinct from old.claim_token) execute function " + trigger + "()");

            assertThatThrownBy(() -> runs.claimNextTask(original.runId())).isInstanceOf(BusinessException.class);

            assertThat(jdbc.queryForObject("select is_called from " + seen, Boolean.class))
                    .as("the rejected Task CAS must actually observe the prepared operation version").isTrue();
            assertThat(projectionFacts(original, operationId)).isEqualTo(before);
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on run_task");
            jdbc.execute("drop function if exists " + trigger + "()");
            jdbc.execute("drop sequence if exists " + seen);
        }
    }

    @Test
    void branchIntentClaimOuterRollbackRestoresReservationAndRejectsLeakedFence() {
        var original = claimBranchTask(ActionType.ENSURE_RELEASE);
        String operationId = beginBranchIntent(original, 64);
        expireClaim(original);
        var before = projectionFacts(original, operationId);
        var leaked = new java.util.concurrent.atomic.AtomicReference<RunPort.TaskClaim>();

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var current = reclaimBranchIntent(original);
            leaked.set(current);
            assertThat(current.fence().claimToken()).isNotEqualTo(original.fence().claimToken());
            assertThat(mergeIntentVersion(operationId)).isGreaterThan(
                    ((Number) ((Map<?, ?>) before.get("operation")).get("state_version")).longValue());
            tx.setRollbackOnly();
        });

        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        assertThatThrownBy(() -> journal.sending(operationId, leaked.get().fence())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(leaked.get(), step(leaked.get(), RunItemResult.UNKNOWN), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentResultCasFailureRollsBackActualLocalDecisionAndBinding() {
        var current = claimBranchTask(ActionType.ENSURE_RELEASE);
        String operationId = beginBranchIntent(current, 40);
        var before = projectionFacts(current, operationId);
        String trigger = "b7_branch_result_" + UUID.randomUUID().toString().replace("-", "");
        String seen = trigger + "_seen";
        try {
            jdbc.execute("create sequence " + seen);
            jdbc.execute("create function " + trigger + "() returns trigger language plpgsql as $$ begin "
                    + "if exists(select 1 from git_operation where id='" + operationId + "' and state='NOT_SENT') "
                    + "and exists(select 1 from git_operation_event where operation_id='" + operationId
                    + "' and event_sequence=1 and event_type='NOT_SENT') "
                    + "and exists(select 1 from repo_execution_guard where remote_key=(select remote_key from git_operation where id='"
                    + operationId + "') and operation_id is null and session_id is null and reason is null) "
                    + "and exists(select 1 from run_step where task_id=old.id) then perform nextval('" + seen
                    + "'); end if; return null; end; $$");
            jdbc.execute("create trigger " + trigger + " before update of status on run_task for each row "
                    + "when (old.id='" + current.fence().taskId() + "' and old.status='RUNNING' and new.status='COMPLETED') "
                    + "execute function " + trigger + "()");

            assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.ALREADY_EXISTS),
                    branchIntentPatch(current, "PROJECT"))).isInstanceOf(BusinessException.class);

            assertThat(jdbc.queryForObject("select is_called from " + seen, Boolean.class))
                    .as("actual Task CAS=0 must occur after NOT_SENT, its event, Step and guard release").isTrue();
            assertThat(projectionFacts(current, operationId)).isEqualTo(before);
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on run_task");
            jdbc.execute("drop function if exists " + trigger + "()");
            jdbc.execute("drop sequence if exists " + seen);
        }
    }

    @Test
    void branchIntentResultOuterRollbackRestoresTaskItemOperationGuardAndBinding() {
        var current = claimBranchTask(ActionType.ENSURE_RELEASE);
        String operationId = beginBranchIntent(current, 64);
        var before = projectionFacts(current, operationId);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var completed = completeNotSent(current, RunItemResult.ALREADY_EXISTS, branchIntentPatch(current, "PROJECT"));
            assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
            assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId))
                    .isEqualTo("NOT_SENT");
            assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                    current.fence().taskId())).isEqualTo(1);
            assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                    .orElseThrow().getBranchCreated()).isTrue();
            tx.setRollbackOnly();
        });

        assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentSendWinsActualPostgresLockAndLocalUnknownCannotSealSender() throws Exception {
        var current = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = beginBranchIntent(current, 40);
        var target = branchIntentTarget(current, 40);
        var taskBefore = jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId());
        var sendLocked = new CountDownLatch(1);
        var releaseSend = new CountDownLatch(1);
        var completionStarted = new CountDownLatch(1);
        var senderThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var senderPid = new java.util.concurrent.atomic.AtomicInteger();
        var completerPid = new java.util.concurrent.atomic.AtomicInteger();
        var pauseOnce = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (Thread.currentThread() == senderThread.get() && pauseOnce.compareAndSet(false, true)) {
                senderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                sendLocked.countDown();
                awaitMergeIntentLatch(releaseSend);
            }
            return Instant.now();
        }).when(clock).instant();

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var sender = pool.submit(() -> {
                    senderThread.set(Thread.currentThread());
                    journal.sending(operationId, current.fence());
                });
                assertThat(sendLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var completion = pool.submit(() -> {
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                            completerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                            completionStarted.countDown();
                            runs.completeTask(current, step(current, RunItemResult.UNKNOWN), Map.of());
                        });
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                });
                assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(completerPid.get(), Set.of(senderPid.get()));
                releaseSend.countDown();
                sender.get(10, TimeUnit.SECONDS);
                assertThat(completion.get(10, TimeUnit.SECONDS))
                        .as("local UNKNOWN cannot end a Task after the real sender acquired durable SEND")
                        .isInstanceOf(BusinessException.class);
            } finally {
                releaseSend.countDown();
            }
        }

        assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId())).isEqualTo(taskBefore);
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
        assertThat(journal.find(operationId).orElseThrow().events()).hasSize(2);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId).containsEntry("reason", "REQUEST_IN_FLIGHT");
        journal.finish(operationId, current.fence(), GitWriteResult.Outcome.APPLIED,
                branchProof(target.path("branch").textValue(), target.path("ref").textValue()), null);
        completeNotSent(current, RunItemResult.CREATED, branchIntentPatch(current, "PROJECT"));
        assertThat(jdbc.queryForObject("select status from run_task where id=?", String.class,
                current.fence().taskId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from git_operation_event where operation_id=? and event_type='NOT_SENT'",
                Integer.class, operationId)).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentNotSentWinsActualPostgresLockAndRejectsLateSendAndFinish() throws Exception {
        var current = claimBranchTask(ActionType.ENSURE_RELEASE);
        String operationId = beginBranchIntent(current, 40);
        var target = branchIntentTarget(current, 40);
        var decisionLocked = new CountDownLatch(1);
        var releaseDecision = new CountDownLatch(1);
        var senderStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var completion = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    var completed = completeNotSent(current, RunItemResult.ALREADY_EXISTS, Map.of());
                    assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId))
                            .isEqualTo("NOT_SENT");
                    decisionLocked.countDown();
                    awaitMergeIntentLatch(releaseDecision);
                    return completed;
                }));
                assertThat(decisionLocked.await(5, TimeUnit.SECONDS))
                        .as("the actual branch result transaction must hold NOT_SENT before racing SEND").isTrue();
                var lateSender = pool.submit(() -> {
                    senderStarted.countDown();
                    try {
                        journal.sending(operationId, current.fence());
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                });
                assertThat(senderStarted.await(5, TimeUnit.SECONDS)).isTrue();
                int waiter = awaitMergeIntentWaiter(holderPid.get());
                awaitMergeIntentBlockers(waiter, Set.of(holderPid.get()));
                releaseDecision.countDown();
                assertThat(completion.get(10, TimeUnit.SECONDS).getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
                assertThat(lateSender.get(10, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class);
            } finally {
                releaseDecision.countDown();
            }
        }
        var terminal = projectionFacts(current, operationId);
        assertThatThrownBy(() -> journal.finish(operationId, current.fence(), GitWriteResult.Outcome.APPLIED,
                branchProof(target.path("branch").textValue(), target.path("ref").textValue()), null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(terminal);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentTwoLocalCompletersWaitOnPostgresAndCommitOnce() throws Exception {
        var current = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = beginBranchIntent(current, 40);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstStarted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        var firstPid = new java.util.concurrent.atomic.AtomicInteger();
        var secondPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(3)) {
            try {
                var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    jdbc.queryForObject("select id from run where id=? for update", String.class, current.runId());
                    jdbc.queryForObject("select id from run_task where id=? for update", String.class, current.fence().taskId());
                    locked.countDown();
                    awaitMergeIntentLatch(release);
                    return null;
                }));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var first = pool.submit(() -> notSentConcurrentCompletion(current, firstPid, firstStarted));
                assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(firstPid.get(), Set.of(holderPid.get()));
                var second = pool.submit(() -> notSentConcurrentCompletion(current, secondPid, secondStarted));
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(secondPid.get(), Set.of(holderPid.get(), firstPid.get()));
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
            } finally {
                release.countDown();
            }
        }
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("NOT_SENT");
        assertThat(journal.find(operationId).orElseThrow().events()).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isEqualTo(1);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentTwoClaimantsWaitOnPostgresAndOnlyOneOwnsTheOriginalIntent() throws Exception {
        var original = claimBranchTask(ActionType.ENSURE_RELEASE);
        String operationId = beginBranchIntent(original, 64);
        expireClaim(original);
        var before = projectionFacts(original, operationId);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstStarted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        var firstPid = new java.util.concurrent.atomic.AtomicInteger();
        var secondPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(3)) {
            try {
                var holder = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    jdbc.queryForObject("select id from run where id=? for update", String.class, original.runId());
                    jdbc.queryForObject("select id from run_task where id=? for update", String.class, original.fence().taskId());
                    locked.countDown();
                    awaitMergeIntentLatch(release);
                    return null;
                }));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var first = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    firstPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    firstStarted.countDown();
                    return runs.claimNextTask(original.runId());
                }));
                assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(firstPid.get(), Set.of(holderPid.get()));
                var second = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    secondStarted.countDown();
                    return runs.claimNextTask(original.runId());
                }));
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(secondPid.get(), Set.of(holderPid.get(), firstPid.get()));
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                var owners = java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                        .flatMap(java.util.Optional::stream).toList();
                assertThat(owners).hasSize(1);
                assertThat(owners.getFirst().fence().claimToken()).isNotEqualTo(original.fence().claimToken());
                assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                        original.fence().taskId())).isEqualTo(owners.getFirst().fence().claimToken());
            } finally {
                release.countDown();
            }
        }
        assertThat(projectionGuard(operationId)).isEqualTo(before.get("guard"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by action_type",
                original.itemId())).isEqualTo(before.get("steps"));
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("INTENT_RECORDED");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentRealResponseUnknownRemainsUnknownAndRetainsItsGuard() {
        var current = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = beginBranchIntent(current, 40);
        journal.sending(operationId, current.fence());
        journal.finish(operationId, current.fence(), GitWriteResult.Outcome.UNKNOWN, "{\"httpStatus\":502}", "UNKNOWN");
        var operation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
        var events = journal.find(operationId).orElseThrow().events();

        var completed = completeNotSent(current, RunItemResult.UNKNOWN, Map.of());

        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        assertThat(jdbc.queryForObject("select status from run_task where id=?", String.class, current.fence().taskId()))
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(operation);
        assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(events).hasSize(3);
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("UNKNOWN");
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId)
                .containsEntry("session_id", current.fence().claimToken()).containsEntry("reason", "REMOTE_RESULT_UNKNOWN");
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(current.runId());
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ActionType.class, names = {"ENSURE_FEATURE", "ENSURE_RELEASE"})
    void branchIntentSecondRecoveryProjectsTheActualSenderWithoutRotatingHistory(ActionType action) throws Exception {
        var original = claimBranchTask(action);
        int length = action == ActionType.ENSURE_FEATURE ? 40 : 64;
        String operationId = beginBranchIntent(original, length);
        var originalTarget = branchIntentTarget(original, length);
        String attempt = journal.find(operationId).orElseThrow().events().getFirst().attemptId();
        expireClaim(original);
        var current = reclaimBranchIntent(original);
        var prefix = jdbc.queryForList("select * from run_step where run_item_id=? order by action_type", current.itemId());
        var replayed = new java.util.concurrent.atomic.AtomicReference<String>();
        org.assertj.core.api.Assertions.assertThatCode(() ->
                replayed.set(journal.begin(branchIntentCommand(current, originalTarget)).id())).doesNotThrowAnyException();
        assertThat(replayed.get()).isEqualTo(operationId);
        assertThatThrownBy(() -> journal.sending(operationId, original.fence())).isInstanceOf(BusinessException.class);
        org.assertj.core.api.Assertions.assertThatCode(() -> journal.sending(operationId, current.fence()))
                .doesNotThrowAnyException();
        var sent = projectionFacts(current, operationId);
        assertThatThrownBy(() -> journal.finish(operationId, original.fence(), GitWriteResult.Outcome.APPLIED,
                branchProof(originalTarget.path("branch").textValue(), originalTarget.path("ref").textValue()), null))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(sent);
        org.assertj.core.api.Assertions.assertThatCode(() -> journal.finish(operationId, current.fence(),
                GitWriteResult.Outcome.APPLIED, branchProof(originalTarget.path("branch").textValue(),
                        originalTarget.path("ref").textValue().toUpperCase(java.util.Locale.ROOT)), null))
                .doesNotThrowAnyException();
        expireClaim(current);
        var appliedOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
        var appliedEvents = journal.find(operationId).orElseThrow().events();
        var expiry = claimExpiry(current);
        assertThat(appliedEvents).hasSize(3).allSatisfy(event -> assertThat(event.attemptId()).isEqualTo(attempt));
        var sender = json.readTree(appliedEvents.get(1).evidenceJson());
        assertThat(sender.path("taskId").textValue()).isEqualTo(current.fence().taskId());
        assertThat(sender.path("claimToken").textValue()).isEqualTo(current.fence().claimToken());
        assertThat(appliedOperation).containsEntry("claim_token", original.fence().claimToken())
                .containsEntry("session_id", original.fence().claimToken());
        assertThat(runs.findTaskCandidateRunIds(null, 1000)).contains(current.runId());

        assertThat(taskService.executeNextTask(current.runId()))
                .as("the existing worker entry must project the confirmed second owner without another Git call").isTrue();

        assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId()))
                .containsEntry("status", "COMPLETED").containsEntry("claim_token", current.fence().claimToken());
        assertThat(claimExpiry(current)).isEqualTo(expiry);
        assertThat(jdbc.queryForObject("select result from run_step where task_id=?", String.class,
                current.fence().taskId())).isEqualTo("CREATED");
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? and task_id<>? order by action_type",
                current.itemId(), current.fence().taskId())).isEqualTo(prefix);
        assertThat(runs.findById(current.runId()).orElseThrow().getItems().getFirst().getMetadata())
                .containsEntry("git.projectId", branchIntentProject(current));
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(appliedOperation);
        assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(appliedEvents);
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                current.fence().taskId())).isEqualTo(1);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
        if (action == ActionType.ENSURE_RELEASE) {
            assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                    .orElseThrow().getBranchCreated()).isTrue();
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentAppliedSummaryDoesNotAcquireUnsentRequestHashRequirements() {
        var current = claimBranchTask(ActionType.ENSURE_RELEASE);
        String operationId = beginBranchIntent(current, 40);
        var target = branchIntentTarget(current, 40);
        journal.sending(operationId, current.fence());
        journal.finish(operationId, current.fence(), GitWriteResult.Outcome.APPLIED,
                branchProof(target.path("branch").textValue(), target.path("ref").textValue()), null);
        // Historical APPLIED proof binds the original scope and actual response, not the new unsent request format.
        jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                branchTarget(target.path("branch").textValue(), target.path("sourceRef").textValue(),
                        target.path("ref").textValue()), "0".repeat(64), operationId);
        expireClaim(current);
        var operation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
        var events = journal.find(operationId).orElseThrow().events();
        var expiry = claimExpiry(current);

        assertThat(taskService.executeNextTask(current.runId())).isTrue();

        assertThat(jdbc.queryForObject("select result from run_step where task_id=?", String.class,
                current.fence().taskId())).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                current.fence().taskId())).isEqualTo(current.fence().claimToken());
        assertThat(claimExpiry(current)).isEqualTo(expiry);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(operation);
        assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(events);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentCannotPassAnUnresolvedEarlierIterationInTheRepositoryQueue() {
        var run = admit(List.of(firstRepo), List.of(firstIteration, secondIteration), true);
        RunPort.TaskClaim earlier = null;
        for (int index = 0; index < 4; index++) {
            earlier = runs.claimNextTask(run.getId().value()).orElseThrow();
            completeSuccess(earlier);
        }
        var later = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(later.actionType()).isEqualTo(ActionType.ENSURE_FEATURE);
        String operationId = beginBranchIntent(later, 40);
        expireClaim(later);
        assertThat(jdbc.update("update run_task set status='RUNNING',finished_at=null where id=?",
                earlier.fence().taskId())).isEqualTo(1);
        var before = projectionFacts(later, operationId);

        assertMergeIntentClaimRejected(later.runId());

        assertThat(projectionFacts(later, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void branchIntentObservationAdvancesAuditVersionWithoutRevokingLiveExecutionFacts() {
        var original = claimBranchTask(ActionType.ENSURE_FEATURE);
        String operationId = beginBranchIntent(original, 64);
        var target = branchIntentTarget(original, 64);
        expireClaim(original);
        var current = reclaimBranchIntent(original);
        long expectedVersion = mergeIntentVersion(operationId);
        var before = projectionFacts(current, operationId);
        var observed = journal.observe(operationId, "reviewer", "branch-audit", expectedVersion,
                io.releasehub.application.gitoperation.GitReadResult.Status.UNAVAILABLE, "{}");
        assertThat(observed.expectedVersion()).isEqualTo(expectedVersion);
        assertThat(mergeIntentVersion(operationId)).isGreaterThan(expectedVersion);
        assertThat(withoutOperationObservationAudit(projectionFacts(current, operationId)))
                .isEqualTo(withoutOperationObservationAudit(before));
        assertThatThrownBy(() -> journal.observe(operationId, "reviewer", "stale-branch-audit", expectedVersion,
                io.releasehub.application.gitoperation.GitReadResult.Status.UNAVAILABLE, "{}"))
                .isInstanceOf(BusinessException.class);
        assertThat(journal.observation(operationId, "reviewer", "stale-branch-audit")).isEmpty();

        org.assertj.core.api.Assertions.assertThatCode(() -> {
            assertThat(journal.begin(branchIntentCommand(current, target)).id()).isEqualTo(operationId);
            journal.sending(operationId, current.fence());
        }).as("an audit-only version increment is not an execution revocation").doesNotThrowAnyException();

        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
        assertThat(jdbc.queryForObject("select target_json from git_operation where id=?", String.class, operationId))
                .isEqualTo(target.toString());
        assertThat(jdbc.queryForObject("select claim_token from git_operation where id=?", String.class, operationId))
                .isEqualTo(original.fence().claimToken());
        assertThat(jdbc.queryForObject("select count(*) from git_operation_event where operation_id=?", Integer.class,
                operationId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,40", "false,64"})
    void createMrRequestNormalTaskAndNonTaskPreserveTheCompleteCommand(boolean taskOwned, int shaLength) throws Exception {
        var claim = taskOwned ? claimMrTask() : null;
        String operationId = taskOwned ? UUID.randomUUID().toString() : " cleanup-" + UUID.randomUUID().toString().substring(0, 8) + " ";
        var target = createMrRequestTarget(claim, operationId, shaLength);
        var command = createMrRequestCommand(claim, operationId, target, projectionProjectId());

        var admitted = journal.begin(command);

        assertThat(admitted.id()).isEqualTo(operationId);
        assertThat(admitted.action()).isEqualTo("CREATE_MR");
        assertThat(admitted.state()).isEqualTo("INTENT_RECORDED");
        assertThat(admitted.reason()).isNull();
        assertThat(admitted.blocked()).isTrue();
        assertThat(json.readTree(admitted.targetJson())).isEqualTo(target);
        assertThat(jdbc.queryForMap("select target_json,request_hash,session_id,claim_token from git_operation where id=?", operationId))
                .containsEntry("target_json", target.toString()).containsEntry("request_hash", command.requestHash())
                .containsEntry("session_id", command.sessionId())
                .containsEntry("claim_token", claim == null ? null : claim.fence().claimToken());
        assertThat(target.path("description").textValue().lines().filter(
                ("ReleaseHub-Operation: " + operationId)::equals)).hasSize(1);

        journal.sending(operationId, command.taskFence());
        var sender = json.readTree(journal.find(operationId).orElseThrow().events().get(1).evidenceJson());
        if (claim == null) assertThat(sender.size()).isZero();
        else {
            assertThat(sender.path("taskId").textValue()).isEqualTo(claim.fence().taskId());
            assertThat(sender.path("claimToken").textValue()).isEqualTo(claim.fence().claimToken());
        }
        journal.finish(operationId, command.taskFence(), GitWriteResult.Outcome.APPLIED,
                createMrRequestProof(target, command.projectId()).toString(), null);
        if (claim == null) {
            journal.completeSession(command.sessionId(), true);
        } else {
            var checkpoint = new java.util.LinkedHashMap<>(createdMrCheckpoint(claim));
            checkpoint.put("git.sourceSha", target.path("sha").textValue());
            checkpoint.put("git.mergeSha", null);
            checkpoint.put("git.blockReason", null);
            checkpoint.put("git.requestId", null);
            runs.completeTask(claim, step(claim, RunItemResult.CREATED), checkpoint);
            assertThat(runs.claimNextTask(claim.runId()).orElseThrow().metadata())
                    .containsEntry("git.sourceSha", target.path("sha").textValue()).containsEntry("git.mrIid", "11");
        }
        assertThat(journal.find(operationId).orElseThrow().events())
                .extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("APPLIED");
        assertThat(projectionGuard(operationId)).containsEntry("session_id", null).containsEntry("reason", null);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "MISSING_METHOD", "QUERY_SUFFIX", "MISSING_TITLE", "TITLE_TYPE", "BLANK_TITLE",
            "MISSING_DESCRIPTION", "NULL_DESCRIPTION", "DESCRIPTION_TYPE", "MISSING_REMOVE_SOURCE",
            "STRING_REMOVE_SOURCE", "TRUE_REMOVE_SOURCE", "SHA_TYPE", "EXTRA_FIELD",
            "HASH_DIFFERENT", "HASH_REORDERED_BODY", "HASH_LOWERCASED_SHA",
            "MARKER_MISSING", "MARKER_FOREIGN", "MARKER_DUPLICATE", "MARKER_NOT_FINAL", "MARKER_NOT_STANDALONE",
            "EMPTY_ID", "BLANK_ID", "CR_ID", "LF_ID", "CURRENT_STEP"
    })
    void createMrRequestBeginRejectsMalformedTypedRequestsWithoutPersistingIntent(String contradiction) {
        var claim = claimMrTask();
        String operationId = switch (contradiction) {
            case "EMPTY_ID" -> "";
            case "BLANK_ID" -> "   ";
            case "CR_ID" -> "b8\roperation";
            case "LF_ID" -> "b8\noperation";
            default -> UUID.randomUUID().toString();
        };
        var target = createMrRequestTarget(claim, operationId, 40);
        switch (contradiction) {
            case "MISSING_METHOD" -> target.remove("method");
            case "QUERY_SUFFIX" -> target.put("suffix", "/merge_requests?");
            case "MISSING_TITLE" -> target.remove("title");
            case "TITLE_TYPE" -> target.put("title", 17);
            case "BLANK_TITLE" -> target.put("title", " \t");
            case "MISSING_DESCRIPTION" -> target.remove("description");
            case "NULL_DESCRIPTION" -> target.putNull("description");
            case "DESCRIPTION_TYPE" -> target.set("description", json.createArrayNode());
            case "MISSING_REMOVE_SOURCE" -> target.remove("remove_source_branch");
            case "STRING_REMOVE_SOURCE" -> target.put("remove_source_branch", "false");
            case "TRUE_REMOVE_SOURCE" -> target.put("remove_source_branch", true);
            case "SHA_TYPE" -> target.put("sha", new java.math.BigInteger("1".repeat(40)));
            case "EXTRA_FIELD" -> target.put("unrelated_private_body", "must-not-be-stored");
            case "MARKER_MISSING" -> target.put("description", "ordinary text");
            case "MARKER_FOREIGN" -> target.put("description", "\nReleaseHub-Operation: another-operation");
            case "MARKER_DUPLICATE" -> target.put("description", "ReleaseHub-Operation: earlier\nReleaseHub-Operation: " + operationId);
            case "MARKER_NOT_FINAL" -> target.put("description", "\nReleaseHub-Operation: " + operationId + "\ntrailing text");
            case "MARKER_NOT_STANDALONE" -> target.put("description", "embedded ReleaseHub-Operation: " + operationId);
            default -> { }
        }
        if (contradiction.equals("CURRENT_STEP")) {
            assertThat(jdbc.update("insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) "
                            + "values (?,'ENSURE_MR','UNKNOWN',clock_timestamp(),clock_timestamp(),'retained original result',?)",
                    claim.itemId(), claim.fence().taskId())).isEqualTo(1);
        }
        var base = createMrRequestCommand(claim, operationId, target, projectionProjectId());
        String hash = base.requestHash();
        if (contradiction.equals("HASH_DIFFERENT")) hash = "0".repeat(64);
        if (contradiction.equals("HASH_LOWERCASED_SHA")) {
            hash = createMrRequestHash(base.remoteKey(), target.deepCopy().put("sha", target.path("sha").textValue().toLowerCase(java.util.Locale.ROOT)));
        }
        if (contradiction.equals("HASH_REORDERED_BODY")) {
            var reverse = json.createObjectNode();
            for (String key : List.of("remove_source_branch", "description", "title", "target_branch", "source_branch")) {
                reverse.set(key, target.get(key));
            }
            hash = mergeIntentSha256("POST|" + base.remoteKey() + "|/merge_requests||"
                    + mergeIntentSha256(reverse.toString()) + "|" + target.path("sha").textValue());
        }
        var command = new GitOperationJournalPort.Command(base.operationId(), base.repoId(), base.repositoryMapping(),
                base.remoteKey(), base.provider(), base.baseUrl(), base.projectId(), base.action(), base.targetJson(),
                hash, base.runId(), base.operator(), base.sessionId(), base.taskFence());
        var tasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", claim.runId());

        assertThatThrownBy(() -> journal.begin(command)).as(contradiction).isInstanceOf(BusinessException.class);

        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                claim.fence().taskId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from git_operation_event where operation_id=?", Integer.class, operationId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from repo_execution_guard where remote_key=?", Integer.class, command.remoteKey())).isZero();
        assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", claim.runId())).isEqualTo(tasks);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "TITLE_TYPE", "HASH_DIFFERENT", "MARKER_FOREIGN", "SOURCE_TYPE", "TARGET_BLANK",
            "PROJECT_ZERO", "PROJECT_NEGATIVE", "PROJECT_TEXT", "PROJECT_LEADING_ZERO"
    })
    void createMrRequestNonTaskAdmissionUsesTheSameProofAndNumericIdentity(String contradiction) {
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(null, operationId, 64);
        String project = switch (contradiction) {
            case "PROJECT_ZERO" -> "0";
            case "PROJECT_NEGATIVE" -> "-1";
            case "PROJECT_TEXT" -> "acme/project";
            case "PROJECT_LEADING_ZERO" -> "0" + projectionProjectId();
            default -> projectionProjectId();
        };
        switch (contradiction) {
            case "TITLE_TYPE" -> target.put("title", false);
            case "MARKER_FOREIGN" -> target.put("description", "\nReleaseHub-Operation: another-operation");
            case "SOURCE_TYPE" -> target.put("source_branch", 17);
            case "TARGET_BLANK" -> target.put("target_branch", " ");
            default -> { }
        }
        var base = createMrRequestCommand(null, operationId, target, project);
        var command = new GitOperationJournalPort.Command(base.operationId(), base.repoId(), base.repositoryMapping(),
                base.remoteKey(), base.provider(), base.baseUrl(), base.projectId(), base.action(), base.targetJson(),
                contradiction.equals("HASH_DIFFERENT") ? "0".repeat(64) : base.requestHash(),
                base.runId(), base.operator(), base.sessionId(), null);

        assertThatThrownBy(() -> journal.begin(command)).as(contradiction).isInstanceOf(BusinessException.class);

        assertThat(jdbc.queryForObject("select count(*) from git_operation where id=?", Integer.class, operationId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from git_operation_event where operation_id=?", Integer.class, operationId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from repo_execution_guard where remote_key=?", Integer.class, command.remoteKey())).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "TITLE_REMOVED", "MARKER_FOREIGN", "REMOVE_SOURCE_TRUE", "SHA_TYPE", "HASH_CHANGED",
            "PLAN_SOURCE_CHANGED", "PLAN_TARGET_CHANGED", "FROZEN_PROJECT_CHANGED",
            "PROJECT_ZERO", "REMOTE_IDENTITY_MISMATCH", "PROVIDER_CHANGED", "BASE_NOT_CANONICAL", "REASON",
            "MISSING_CLAIM_EXPIRY", "MISSING_FIRST_START", "CURRENT_STEP", "WRONG_EVENT_ATTEMPT", "WRONG_GUARD", "OWNER_CHANGED"
    })
    void createMrRequestCurrentOwnerSendingRechecksDamagedPersistedProof(String contradiction) {
        var claim = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(claim, operationId, 40);
        var command = createMrRequestCommand(claim, operationId, target, projectionProjectId());
        journal.begin(command);
        int changed = switch (contradiction) {
            case "TITLE_REMOVED", "MARKER_FOREIGN", "REMOVE_SOURCE_TRUE", "SHA_TYPE" -> {
                switch (contradiction) {
                    case "TITLE_REMOVED" -> target.remove("title");
                    case "MARKER_FOREIGN" -> target.put("description", "\nReleaseHub-Operation: another-operation");
                    case "REMOVE_SOURCE_TRUE" -> target.put("remove_source_branch", true);
                    case "SHA_TYPE" -> target.put("sha", new java.math.BigInteger("1".repeat(40)));
                    default -> throw new AssertionError(contradiction);
                }
                yield jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                        target.toString(), createMrRequestHash(command.remoteKey(), target), operationId);
            }
            case "HASH_CHANGED" -> jdbc.update("update git_operation set request_hash=? where id=?", "0".repeat(64), operationId);
            case "PLAN_SOURCE_CHANGED", "PLAN_TARGET_CHANGED", "FROZEN_PROJECT_CHANGED" -> {
                String key = contradiction.equals("PLAN_SOURCE_CHANGED") ? "plan.featureBranch"
                        : contradiction.equals("PLAN_TARGET_CHANGED") ? "plan.releaseBranch" : "git.projectId";
                yield jdbc.update("update run_item_metadata set metadata_value=? where run_item_id=? and metadata_key=?",
                        claim.metadata().get(key) + "1", claim.itemId(), key);
            }
            case "PROJECT_ZERO" -> jdbc.update("update git_operation set project_id='0' where id=?", operationId);
            case "REMOTE_IDENTITY_MISMATCH" -> jdbc.update("update git_operation set project_id=? where id=?",
                    command.projectId() + "1", operationId);
            case "PROVIDER_CHANGED" -> jdbc.update("update git_operation set provider='GITHUB' where id=?", operationId);
            case "BASE_NOT_CANONICAL" -> jdbc.update("update git_operation set base_url=? where id=?", command.baseUrl() + "/", operationId);
            case "REASON" -> jdbc.update("update git_operation set reason='UNCONFIRMED' where id=?", operationId);
            case "MISSING_CLAIM_EXPIRY" -> jdbc.update("update run_task set claim_expires_at=null where id=?", claim.fence().taskId());
            case "MISSING_FIRST_START" -> jdbc.update("update run_task set started_at=null where id=?", claim.fence().taskId());
            case "CURRENT_STEP" -> jdbc.update("insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) "
                            + "values (?,'ENSURE_MR','UNKNOWN',clock_timestamp(),clock_timestamp(),'retained original result',?)",
                    claim.itemId(), claim.fence().taskId());
            case "WRONG_EVENT_ATTEMPT" -> jdbc.update("update git_operation_event set attempt_id=? where operation_id=?",
                    UUID.randomUUID().toString(), operationId);
            case "WRONG_GUARD" -> jdbc.update("update repo_execution_guard set generation=generation+1 where operation_id=?", operationId);
            case "OWNER_CHANGED" -> jdbc.update("update run_task set claim_token=? where id=?",
                    UUID.randomUUID().toString(), claim.fence().taskId());
            default -> throw new AssertionError(contradiction);
        };
        assertThat(changed).isEqualTo(1);
        var before = projectionFacts(claim, operationId);

        assertThatThrownBy(() -> journal.sending(operationId, claim.fence())).as(contradiction).isInstanceOf(BusinessException.class);

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("INTENT_RECORDED");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void createMrRequestRepeatedAdmissionRejectsDamagedExistingIntent() {
        var claim = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(claim, operationId, 40);
        var command = createMrRequestCommand(claim, operationId, target, projectionProjectId());
        journal.begin(command);
        target.remove("description");
        assertThat(jdbc.update("update git_operation set target_json=? where id=?", target.toString(), operationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select request_hash from git_operation where id=?", String.class, operationId))
                .isEqualTo(command.requestHash());
        var before = projectionFacts(claim, operationId);

        assertThatThrownBy(() -> journal.begin(command))
                .as("the existing-id path must validate the stored request, even when the original Command and hash match")
                .isInstanceOf(BusinessException.class);

        assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
        assertThat(journal.find(operationId).orElseThrow().events())
                .extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"HASH", "MARKER", "PROJECT"})
    void createMrRequestNonTaskSendingAlsoRechecksPersistedProof(String contradiction) {
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(null, operationId, 64);
        var command = createMrRequestCommand(null, operationId, target, projectionProjectId());
        journal.begin(command);
        switch (contradiction) {
            case "HASH" -> jdbc.update("update git_operation set request_hash=? where id=?", "0".repeat(64), operationId);
            case "MARKER" -> {
                target.put("description", "\nReleaseHub-Operation: another-operation");
                jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                        target.toString(), createMrRequestHash(command.remoteKey(), target), operationId);
            }
            case "PROJECT" -> jdbc.update("update git_operation set project_id=? where id=?", command.projectId() + "1", operationId);
            default -> throw new AssertionError(contradiction);
        }
        var before = createMrRequestNonTaskFacts(operationId);

        assertThatThrownBy(() -> journal.sending(operationId, null)).as(contradiction).isInstanceOf(BusinessException.class);

        assertThat(createMrRequestNonTaskFacts(operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void createMrRequestExpiredCompleteIntentTransfersOnlyTheCurrentClaim() {
        var original = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(original, operationId, 40);
        journal.begin(createMrRequestCommand(original, operationId, target, projectionProjectId()));
        expireClaim(original);
        var before = projectionFacts(original, operationId);

        var next = runs.claimNextTask(original.runId());
        assertThat(next).isPresent();
        var current = next.orElseThrow();
        assertThat(current.fence().taskId()).isEqualTo(original.fence().taskId());
        assertThat(current.fence().claimToken()).isNotEqualTo(original.fence().claimToken());
        assertThat(current.unsentOperation()).isNotNull();
        assertThat(current.unsentOperation().id()).isEqualTo(operationId);
        assertThat(jdbc.queryForObject("select status from run_task where id=?", String.class,
                current.fence().taskId())).isEqualTo("RUNNING");
        var originalFacts = new java.util.LinkedHashMap<>((Map<?, ?>) before.get("operation"));
        var currentFacts = new java.util.LinkedHashMap<>(jdbc.queryForMap("select * from git_operation where id=?", operationId));
        originalFacts.remove("state_version");
        currentFacts.remove("state_version");
        assertThat(currentFacts).isEqualTo(originalFacts);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                operationId)).isEqualTo(before.get("events"));
        assertThat(projectionGuard(operationId)).isEqualTo(before.get("guard"));
        assertThatThrownBy(() -> journal.sending(operationId, original.fence())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void createMrRequestOriginalOwnerCommitsTrueNotSentWithoutLosingUnknownSemantics() {
        var claim = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        journal.begin(createMrRequestCommand(claim, operationId,
                createMrRequestTarget(claim, operationId, 40), projectionProjectId()));
        var originalEvent = jdbc.queryForMap("select * from git_operation_event where operation_id=?", operationId);

        runs.completeTask(claim, step(claim, RunItemResult.UNKNOWN), Map.of("git.blockReason", "UNKNOWN"));

        assertThat(jdbc.queryForObject("select status from run_task where id=?", String.class,
                claim.fence().taskId())).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForList("select result from run_step where task_id=?", String.class,
                claim.fence().taskId())).containsExactly("UNKNOWN");
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("NOT_SENT");
        assertThat(journal.find(operationId).orElseThrow().events())
                .extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED", "NOT_SENT");
        assertThat(jdbc.queryForMap("select * from git_operation_event where operation_id=? and event_sequence=0",
                operationId)).isEqualTo(originalEvent);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId))
                .containsEntry("session_id", claim.fence().claimToken()).containsEntry("claim_token", claim.fence().claimToken());
        assertThat(projectionGuard(operationId)).containsEntry("session_id", null)
                .containsEntry("operation_id", null).containsEntry("reason", null);
        assertThat(runs.findById(claim.runId()).orElseThrow().getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        assertThat(runs.claimNextTask(claim.runId())).isEmpty();
        assertThat(taskService.executeNextTask(claim.runId())).isFalse();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }
    @Test
    void createMrRequestHistoricalAppliedSummaryDoesNotAcquireTheNewSendingRequirements() {
        var claim = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(claim, operationId, 40).put("sha", "a".repeat(40));
        var command = createMrRequestCommand(claim, operationId, target, projectionProjectId());
        journal.begin(command);
        journal.sending(operationId, claim.fence());
        journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.APPLIED, createdMrProof(claim).toString(), null);
        // Reconstruct historical APPLIED evidence only after a valid fresh write; no legacy INTENT is admitted.
        assertThat(jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                createdMrTarget(claim).toString(), "historical-request-summary", operationId)).isEqualTo(1);
        expireClaim(claim);
        var before = projectionFacts(claim, operationId);

        assertThat(taskService.executeNextTask(claim.runId())).isTrue();

        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(before.get("operation"));
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence", operationId))
                .isEqualTo(before.get("events"));
        assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class, claim.fence().taskId()))
                .isEqualTo(claim.fence().claimToken());
        assertThat(jdbc.queryForObject("select result from run_step where task_id=?", String.class, claim.fence().taskId()))
                .isEqualTo("CREATED");
        assertThat(projectionGuard(operationId)).containsEntry("session_id", null).containsEntry("reason", null);
        var next = runs.claimNextTask(claim.runId()).orElseThrow();
        assertThat(next.actionType()).isEqualTo(ActionType.TRY_MERGE);
        assertThat(next.metadata()).containsAllEntriesOf(createdMrCheckpoint(claim))
                .doesNotContainKeys("git.mergeSha", "git.blockReason", "git.requestId");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void createMrRequestAdmissionTransactionRollsBackAfterIntentAndGuardWereWritten() {
        var claim = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        var command = createMrRequestCommand(claim, operationId, createMrRequestTarget(claim, operationId, 40), projectionProjectId());
        var tasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", claim.runId());
        String probe = createMrRequestInstallRollbackProbe(operationId, 0);
        try {
            assertThatThrownBy(() -> journal.begin(command)).isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining("B8_ROLLBACK_AFTER_INTENT_RECORDED");
            assertThat(jdbc.queryForObject("select is_called from " + probe + "_reached", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from git_operation where id=?", Integer.class, operationId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from git_operation_event where operation_id=?", Integer.class, operationId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from repo_execution_guard where remote_key=?", Integer.class, command.remoteKey())).isZero();
            assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", claim.runId())).isEqualTo(tasks);
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            createMrRequestRemoveRollbackProbe(probe);
        }
    }

    @Test
    void createMrRequestSendingTransactionRollsBackAfterSendAndGuardWereWritten() {
        var claim = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        journal.begin(createMrRequestCommand(claim, operationId, createMrRequestTarget(claim, operationId, 40), projectionProjectId()));
        var before = projectionFacts(claim, operationId);
        String probe = createMrRequestInstallRollbackProbe(operationId, 1);
        try {
            assertThatThrownBy(() -> journal.sending(operationId, claim.fence())).isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining("B8_ROLLBACK_AFTER_SEND_STARTED");
            assertThat(jdbc.queryForObject("select is_called from " + probe + "_reached", Boolean.class)).isTrue();
            assertThat(projectionFacts(claim, operationId)).isEqualTo(before);
            assertThat(journal.find(operationId).orElseThrow().events())
                    .extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED");
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            createMrRequestRemoveRollbackProbe(probe);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"INTENT_RECORDED", "SEND_STARTED", "APPLIED"})
    void createMrRequestOuterTaskRollbackRetainsIndependentFactsAndOriginalProjection(String stage) {
        var claim = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(claim, operationId, 40).put("sha", "a".repeat(40));
        var command = createMrRequestCommand(claim, operationId, target, projectionProjectId());
        var committed = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            journal.begin(command);
            if (!stage.equals("INTENT_RECORDED")) journal.sending(operationId, claim.fence());
            if (stage.equals("APPLIED")) {
                journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.APPLIED, createdMrProof(claim).toString(), null);
            }
            committed.set(projectionFacts(claim, operationId));
            if (stage.equals("APPLIED")) {
                var checkpoint = new java.util.LinkedHashMap<>(createdMrCheckpoint(claim));
                checkpoint.put("git.mergeSha", null);
                checkpoint.put("git.blockReason", null);
                checkpoint.put("git.requestId", null);
                runs.completeTask(claim, step(claim, RunItemResult.CREATED), checkpoint);
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                        claim.fence().taskId())).isEqualTo(1);
                assertThat(projectionGuard(operationId)).containsEntry("session_id", null);
            } else {
                assertThat(jdbc.update("update run_item set executed_order=executed_order+100 where id=?", claim.itemId())).isEqualTo(1);
            }
            tx.setRollbackOnly();
        });

        assertThat(projectionFacts(claim, operationId)).isEqualTo(committed.get());
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo(stage);
        assertThat(journal.find(operationId).orElseThrow().events()).hasSize(
                stage.equals("INTENT_RECORDED") ? 1 : stage.equals("SEND_STARTED") ? 2 : 3);
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class, claim.fence().taskId())).isZero();
        if (stage.equals("APPLIED")) {
            var events = committed.get().get("events");
            expireClaim(claim);
            assertThat(taskService.executeNextTask(claim.runId())).isTrue();
            assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("APPLIED");
            assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence", operationId))
                    .isEqualTo(events);
            assertThat(runs.claimNextTask(claim.runId()).orElseThrow().metadata()).containsAllEntriesOf(createdMrCheckpoint(claim));
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void createMrRequestNonTaskRollbackKeepsAppliedEvidenceThroughSessionClosure() {
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(null, operationId, 40);
        var command = createMrRequestCommand(null, operationId, target, projectionProjectId());
        var binding = jdbc.queryForMap("select * from window_iteration where window_id=? and iteration_key=?", windowId, firstIteration);
        var committed = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            journal.begin(command);
            journal.sending(operationId, null);
            journal.finish(operationId, null, GitWriteResult.Outcome.APPLIED,
                    createMrRequestProof(target, command.projectId()).toString(), null);
            committed.set(createMrRequestNonTaskFacts(operationId));
            assertThat(jdbc.update("update window_iteration set branch_created=not branch_created where window_id=? and iteration_key=?",
                    windowId, firstIteration)).isEqualTo(1);
            tx.setRollbackOnly();
        });

        assertThat(createMrRequestNonTaskFacts(operationId)).isEqualTo(committed.get());
        assertThat(jdbc.queryForMap("select * from window_iteration where window_id=? and iteration_key=?", windowId, firstIteration))
                .isEqualTo(binding);
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("APPLIED");
        journal.completeSession(command.sessionId(), false);
        var detail = journal.find(operationId).orElseThrow();
        assertThat(detail.operation().state()).isEqualTo("NEEDS_REVIEW");
        assertThat(detail.operation().reason()).isEqualTo("LOCAL_RESULT_UNCONFIRMED");
        assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED", "LOCAL_RESULT_UNCONFIRMED");
        assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? and event_sequence<=2 order by event_sequence", operationId))
                .isEqualTo(committed.get().get("events"));
        assertThat(projectionGuard(operationId)).containsEntry("session_id", command.sessionId())
                .containsEntry("operation_id", operationId).containsEntry("reason", "LOCAL_RESULT_UNCONFIRMED");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void createMrRequestSenderRechecksTheProofAfterActuallyWaitingOnPostgres() throws Exception {
        var claim = claimMrTask();
        String operationId = UUID.randomUUID().toString();
        var target = createMrRequestTarget(claim, operationId, 40);
        var command = createMrRequestCommand(claim, operationId, target, projectionProjectId());
        journal.begin(command);
        var damaged = target.deepCopy().put("title", " ");
        var holderReady = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        var damagedFacts = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    jdbc.queryForObject("select id from run where id=? for update", String.class, claim.runId());
                    assertThat(jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                            damaged.toString(), createMrRequestHash(command.remoteKey(), damaged), operationId)).isEqualTo(1);
                    damagedFacts.set(projectionFacts(claim, operationId));
                    holderReady.countDown();
                    awaitMergeIntentLatch(releaseHolder);
                }));
                assertThat(holderReady.await(5, TimeUnit.SECONDS)).isTrue();
                var sender = pool.submit(() -> {
                    try {
                        journal.sending(operationId, claim.fence());
                        return (RuntimeException) null;
                    } catch (RuntimeException rejected) {
                        return rejected;
                    }
                });
                int senderPid = awaitMergeIntentWaiter(holderPid.get());
                awaitMergeIntentBlockers(senderPid, Set.of(holderPid.get()));
                releaseHolder.countDown();
                holder.get(10, TimeUnit.SECONDS);
                assertThat(sender.get(10, TimeUnit.SECONDS))
                        .as("the current owner must recheck the newly committed request after the real row-lock wait")
                        .isInstanceOf(BusinessException.class);
            } finally {
                releaseHolder.countDown();
            }
        }
        assertThat(projectionFacts(claim, operationId)).isEqualTo(damagedFacts.get());
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }


    // B9 independent contract: a complete original CREATE_MR is not a fresh branch-name command.
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 64})
    void createMrIntentClaimPreservesOriginalFactsAcrossRepeatedOwnershipChanges(int shaLength) {
        var original = claimMrTask();
        String operationId = beginCreateMrIntent(original, shaLength);
        var before = projectionFacts(original, operationId);
        assertThat(runs.claimNextTask(original.runId())).isEmpty();
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        var history = new java.util.LinkedHashMap<>(jdbc.queryForMap("select * from git_operation where id=?", operationId));
        history.keySet().removeAll(Set.of("state_version", "updated_at"));
        var current = original;
        for (int transfer = 0; transfer < 2; transfer++) {
            expireClaim(current);
            var previous = current;
            long version = mergeIntentVersion(operationId);
            current = reclaimCreateMrIntent(current);
            assertThat(current.metadata()).isEqualTo(original.metadata());
            assertThat(current.fence().taskId()).isEqualTo(original.fence().taskId());
            assertThat(current.fence().claimToken()).isNotEqualTo(previous.fence().claimToken());
            assertThat(mergeIntentVersion(operationId)).isGreaterThan(version);
            assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).containsAllEntriesOf(history);
            assertThat(projectionGuard(operationId)).isEqualTo(before.get("guard"));
            assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence",
                    operationId)).isEqualTo(before.get("events"));
            assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by action_type",
                    current.itemId())).isEqualTo(before.get("steps"));
            var accepted = journal.begin(createMrRequestCommand(current, operationId,
                    createMrRequestTarget(current, operationId, shaLength), projectionProjectId()));
            assertThat(accepted.id()).isEqualTo(operationId);
            assertThat(accepted.targetJson()).isEqualTo(history.get("target_json"));
            var stable = projectionFacts(current, operationId);
            assertThatThrownBy(() -> journal.sending(operationId, previous.fence())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> journal.begin(createMrRequestCommand(previous, operationId,
                    createMrRequestTarget(previous, operationId, shaLength), projectionProjectId())))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> journal.finish(operationId, previous.fence(), GitWriteResult.Outcome.UNKNOWN, "{}", "UNKNOWN"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> runs.completeTask(previous, step(previous, RunItemResult.UNKNOWN), Map.of()))
                    .isInstanceOf(BusinessException.class);
            assertThat(projectionFacts(current, operationId)).isEqualTo(stable);
        }
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                original.fence().taskId())).isEqualTo(1);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void createMrIntentNewSenderAppliedProjectionPreservesOriginalHistory(boolean historicalSummary) throws Exception {
        var original = claimMrTask();
        String operationId = beginCreateMrIntent(original, 64);
        var target = createMrRequestTarget(original, operationId, 64);
        var originalEvent = jdbc.queryForMap("select * from git_operation_event where operation_id=?", operationId);
        expireClaim(original);
        var current = reclaimCreateMrIntent(original);
        assertThat(journal.begin(createMrRequestCommand(current, operationId, target, projectionProjectId())).id())
                .isEqualTo(operationId);
        journal.sending(operationId, current.fence());
        var sent = projectionFacts(current, operationId);
        assertThatThrownBy(() -> journal.finish(operationId, original.fence(), GitWriteResult.Outcome.APPLIED,
                createMrRequestProof(target, projectionProjectId()).toString(), null)).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(sent);
        journal.finish(operationId, current.fence(), GitWriteResult.Outcome.APPLIED,
                createMrRequestProof(target, projectionProjectId()).toString(), null);
        if (historicalSummary) {
            var summary = json.createObjectNode().put("source_branch", target.path("source_branch").textValue())
                    .put("target_branch", target.path("target_branch").textValue()).put("sha", target.path("sha").textValue());
            assertThat(jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                    summary.toString(), "historical-response-proof-only", operationId)).isEqualTo(1);
        }
        expireClaim(current);
        var applied = jdbc.queryForMap("select * from git_operation where id=?", operationId);
        var events = journal.find(operationId).orElseThrow().events();
        var expiry = claimExpiry(current);
        assertThat(events).hasSize(3);
        assertThat(jdbc.queryForMap("select * from git_operation_event where operation_id=? and event_sequence=0",
                operationId)).isEqualTo(originalEvent);
        var sender = json.readTree(events.get(1).evidenceJson());
        assertThat(sender.path("taskId").textValue()).isEqualTo(current.fence().taskId());
        assertThat(sender.path("claimToken").textValue()).isEqualTo(current.fence().claimToken());
        assertThat(applied).containsEntry("claim_token", original.fence().claimToken())
                .containsEntry("session_id", original.fence().claimToken());

        assertThat(taskService.executeNextTask(current.runId())).as("the real worker projects the actual sender without Git").isTrue();

        assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId()))
                .containsEntry("status", "COMPLETED").containsEntry("claim_token", current.fence().claimToken());
        assertThat(claimExpiry(current)).isEqualTo(expiry);
        assertThat(jdbc.queryForObject("select result from run_step where task_id=?", String.class,
                current.fence().taskId())).isEqualTo("CREATED");
        var metadata = runs.findById(current.runId()).orElseThrow().getItems().getFirst().getMetadata();
        assertThat(metadata).containsEntry("git.projectId", projectionProjectId()).containsEntry("git.mrIid", "11")
                .containsEntry("git.mrDisposition", "CREATED");
        assertThat(metadata.get("git.sourceSha")).isEqualToIgnoringCase(target.path("sha").textValue());
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(applied);
        assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(events);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
        assertThat(runs.claimNextTask(current.runId())).get().extracting(RunPort.TaskClaim::actionType).isEqualTo(ActionType.TRY_MERGE);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "MR_REUSED,false,40,FULL", "ALREADY_MERGED,true,64,FULL",
            "UNKNOWN,false,40,MINIMAL", "UNKNOWN,true,64,FULL",
            "MERGE_BLOCKED,false,40,MINIMAL", "FAILED,true,40,MINIMAL"
    })
    void createMrIntentLocalResultAtomicallyRecordsNotSentAndRetainsItsRealMeaning(
            RunItemResult result, boolean transferred, int shaLength, String shape) throws Exception {
        var original = claimMrTask();
        String operationId = beginCreateMrIntent(original, shaLength);
        var originalEvent = jdbc.queryForMap("select * from git_operation_event where operation_id=?", operationId);
        if (transferred) expireClaim(original);
        var current = transferred ? reclaimCreateMrIntent(original) : original;
        var before = projectionFacts(current, operationId);
        var patch = shape.equals("FULL") ? createMrIntentPatch(current, operationId, result)
                : new java.util.LinkedHashMap<String, String>(Map.of("git.blockReason",
                    result == RunItemResult.UNKNOWN || result == RunItemResult.FAILED ? "UNKNOWN" : "PERMISSION_DENIED"));

        var completed = completeNotSent(current, result, patch);

        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("NOT_SENT");
        var events = jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence", operationId);
        assertThat(events).hasSize(2);
        assertThat(events.getFirst()).isEqualTo(originalEvent);
        assertThat(events.getLast()).containsEntry("event_sequence", 1).containsEntry("event_type", "NOT_SENT")
                .containsEntry("attempt_id", originalEvent.get("attempt_id"));
        var evidence = json.readTree((String) events.getLast().get("evidence_json"));
        assertThat(evidence.path("taskId").textValue()).isEqualTo(current.fence().taskId());
        assertThat(evidence.path("claimToken").textValue()).isEqualTo(current.fence().claimToken());
        assertThat(evidence.path("result").textValue()).isEqualTo(result.name());
        assertThat(evidence.get("metadata")).isEqualTo(json.valueToTree(patch));
        assertThat(evidence.has("httpStatus")).isFalse();
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null)
                .containsEntry("reason", null).containsEntry("generation", ((Map<?, ?>) before.get("guard")).get("generation"));
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select result from run_step where task_id=?", String.class,
                current.fence().taskId())).isEqualTo(result.name());
        var expected = new java.util.LinkedHashMap<>(current.metadata());
        patch.forEach((key, value) -> { if (value == null) expected.remove(key); else expected.put(key, value); });
        assertThat(completed.getItems().getFirst().getMetadata()).isEqualTo(expected);
        boolean success = result == RunItemResult.MR_REUSED || result == RunItemResult.ALREADY_MERGED;
        assertThat(completed.getExecutionStatus()).isEqualTo(success ? RunExecutionStatus.RUNNING
                : result == RunItemResult.UNKNOWN ? RunExecutionStatus.NEEDS_REVIEW : RunExecutionStatus.FAILED);
        if (result == RunItemResult.UNKNOWN) {
            assertThat(runs.claimNextTask(current.runId())).isEmpty();
            assertThat(runs.findTaskCandidateRunIds(null, 1000)).doesNotContain(current.runId());
            assertThatThrownBy(() -> plans.admitRetry(completed, List.of(current.itemId()),
                    current.operator(), UUID.randomUUID().toString())).isInstanceOf(BusinessException.class);
            assertThat(jdbc.queryForObject("select count(*) from run_step where run_item_id=? and action_type='TRY_MERGE'",
                    Integer.class, current.itemId())).isZero();
        }
        var terminal = projectionFacts(current, operationId);
        assertThatThrownBy(() -> journal.sending(operationId, current.fence())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(terminal);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "LEGACY_TARGET", "HASH", "MARKER", "REMOVE_SOURCE", "PLAN_SOURCE", "PROJECT", "SESSION",
            "GUARD_GENERATION", "GUARD_REASON", "ATTEMPT", "EXTRA_EVENT", "CURRENT_STEP", "REASON", "NO_EXPIRY", "NO_START"
    })
    void createMrIntentBrokenOriginalProofCannotClaimBeginSendOrComplete(String damage) {
        var original = claimMrTask();
        String operationId = beginCreateMrIntent(original, 40);
        var target = createMrRequestTarget(original, operationId, 40);
        var command = createMrRequestCommand(original, operationId, target, projectionProjectId());
        expireClaim(original);
        switch (damage) {
            case "LEGACY_TARGET" -> jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                    createdMrTarget(original).toString(), "historical-summary-hash", operationId);
            case "HASH" -> jdbc.update("update git_operation set request_hash=? where id=?", "0".repeat(64), operationId);
            case "MARKER", "REMOVE_SOURCE" -> {
                if (damage.equals("MARKER")) target.put("description", "\nReleaseHub-Operation: foreign");
                else target.put("remove_source_branch", true);
                jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                        target.toString(), createMrRequestHash(command.remoteKey(), target), operationId);
            }
            case "PLAN_SOURCE" -> jdbc.update("update run_item_metadata set metadata_value='feature/foreign' "
                    + "where run_item_id=? and metadata_key='plan.featureBranch'", original.itemId());
            case "PROJECT" -> jdbc.update("update git_operation set project_id=? where id=?", command.projectId() + "1", operationId);
            case "SESSION" -> jdbc.update("update git_operation set session_id=? where id=?", UUID.randomUUID().toString(), operationId);
            case "GUARD_GENERATION" -> jdbc.update("update repo_execution_guard set generation=generation+1 where operation_id=?", operationId);
            case "GUARD_REASON" -> jdbc.update("update repo_execution_guard set reason='REQUEST_IN_FLIGHT' where operation_id=?", operationId);
            case "ATTEMPT" -> jdbc.update("update git_operation_event set attempt_id=? where operation_id=?",
                    UUID.randomUUID().toString(), operationId);
            case "EXTRA_EVENT" -> jdbc.update("insert into git_operation_event(id,operation_id,attempt_id,event_sequence,event_type,"
                    + "occurred_at,evidence_json,reason) values (?,?,?,1,'SEND_STARTED',clock_timestamp(),'{}',null)",
                    UUID.randomUUID().toString(), operationId, UUID.randomUUID().toString());
            case "CURRENT_STEP" -> jdbc.update("insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) "
                    + "values (?,'ENSURE_MR','UNKNOWN',clock_timestamp(),clock_timestamp(),'retained fact',?)",
                    original.itemId(), original.fence().taskId());
            case "REASON" -> jdbc.update("update git_operation set reason='UNCONFIRMED' where id=?", operationId);
            case "NO_EXPIRY" -> jdbc.update("update run_task set claim_expires_at=null where id=?", original.fence().taskId());
            case "NO_START" -> jdbc.update("update run_task set started_at=null where id=?", original.fence().taskId());
            default -> throw new AssertionError(damage);
        }
        var before = projectionFacts(original, operationId);
        assertMergeIntentClaimRejected(original.runId());
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        assertThatThrownBy(() -> journal.begin(command)).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        assertThatThrownBy(() -> journal.sending(operationId, original.fence())).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        assertThatThrownBy(() -> runs.completeTask(original, step(original, RunItemResult.UNKNOWN), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "CREATED", "REQUEST_ID", "PROJECT", "SOURCE", "TARGET", "SHA", "MISSING_IID",
            "MISSING_DISPOSITION", "INVALID_URL", "FOREIGN_FIELD", "UNKNOWN_DISPOSITION", "UNKNOWN_MERGE_SHA"
    })
    void createMrIntentLocalResultCannotClaimUnperformedOrForeignWork(String damage) {
        var current = claimMrTask();
        String operationId = beginCreateMrIntent(current, 40);
        RunItemResult result = damage.equals("CREATED") ? RunItemResult.CREATED
                : damage.startsWith("UNKNOWN") ? RunItemResult.UNKNOWN : RunItemResult.MR_REUSED;
        var patch = createMrIntentPatch(current, operationId, result);
        switch (damage) {
            case "REQUEST_ID" -> patch.put("git.requestId", "never-sent-request");
            case "PROJECT" -> patch.put("git.projectId", projectionProjectId() + "1");
            case "SOURCE" -> patch.put("git.sourceBranch", "feature/foreign");
            case "TARGET" -> patch.put("git.targetBranch", "release/foreign");
            case "SHA" -> patch.put("git.sourceSha", "b".repeat(40));
            case "MISSING_IID" -> patch.put("git.mrIid", null);
            case "MISSING_DISPOSITION" -> patch.put("git.mrDisposition", null);
            case "INVALID_URL" -> patch.put("git.mrUrl", "not-a-url");
            case "FOREIGN_FIELD" -> patch.put("plan.featureBranch", "feature/foreign");
            case "UNKNOWN_DISPOSITION" -> patch.put("git.mrDisposition", "MR_REUSED");
            case "UNKNOWN_MERGE_SHA" -> patch.put("git.mergeSha", "b".repeat(40));
            default -> { }
        }
        var before = projectionFacts(current, operationId);
        assertThatThrownBy(() -> runs.completeTask(current, step(current, result), patch)).isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void createMrIntentCommittedSendRequiresItsActualResponseBeforeAnyLocalResult(boolean hasResponse) {
        var current = claimMrTask();
        String operationId = beginCreateMrIntent(current, 40);
        journal.sending(operationId, current.fence());
        if (hasResponse) journal.finish(operationId, current.fence(), GitWriteResult.Outcome.UNKNOWN, "{}", "UNKNOWN");
        var before = projectionFacts(current, operationId);
        if (hasResponse) {
            runs.completeTask(current, step(current, RunItemResult.UNKNOWN), createMrIntentPatch(current, operationId, RunItemResult.UNKNOWN));
            assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("UNKNOWN");
            assertThat(journal.find(operationId).orElseThrow().events()).hasSize(3);
            assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId).containsEntry("reason", "REMOTE_RESULT_UNKNOWN");
        } else {
            assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN), Map.of()))
                    .isInstanceOf(BusinessException.class);
            assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        }
        expireClaim(current);
        var stable = projectionFacts(current, operationId);
        assertMergeIntentClaimRejected(current.runId());
        assertThat(projectionFacts(current, operationId)).isEqualTo(stable);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "FAILED,PERMISSION_DENIED", "MERGE_BLOCKED,UNKNOWN", "MERGE_BLOCKED,NULL"
    })
    void createMrIntentRejectsInconsistentLocalResultReason(RunItemResult result, String reason) {
        var current = claimMrTask();
        String operationId = beginCreateMrIntent(current, 40);
        Map<String, String> patch = new java.util.LinkedHashMap<>();
        patch.put("git.blockReason", reason.equals("NULL") ? null : reason);
        var before = projectionFacts(current, operationId);

        assertThatThrownBy(() -> runs.completeTask(current, step(current, result), patch))
                .as("a local result must retain the same classification as the existing ENSURE_MR consumer")
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    private String beginCreateMrIntent(RunPort.TaskClaim claim, int shaLength) {
        String id = UUID.randomUUID().toString();
        journal.begin(createMrRequestCommand(claim, id, createMrRequestTarget(claim, id, shaLength), projectionProjectId()));
        return id;
    }

    private RunPort.TaskClaim reclaimCreateMrIntent(RunPort.TaskClaim original) {
        var next = runs.claimNextTask(original.runId());
        assertThat(next).as("a complete original CREATE_MR INTENT must be reclaimable by the existing Task").isPresent();
        var current = next.orElseThrow();
        assertThat(current.actionType()).isEqualTo(ActionType.ENSURE_MR);
        assertThat(current.unsentOperation()).isNotNull();
        assertThat(current.unsentOperation().action()).isEqualTo("CREATE_MR");
        return current;
    }

    private Map<String, String> createMrIntentPatch(RunPort.TaskClaim claim, String operationId, RunItemResult result) {
        com.fasterxml.jackson.databind.JsonNode target;
        try { target = json.readTree(journal.find(operationId).orElseThrow().operation().targetJson()); }
        catch (java.io.IOException error) { throw new AssertionError(error); }
        boolean checkpoint = result == RunItemResult.CREATED || result == RunItemResult.MR_REUSED || result == RunItemResult.ALREADY_MERGED;
        boolean identifiedMr = result == RunItemResult.CREATED || result == RunItemResult.MR_REUSED;
        Map<String, String> patch = new java.util.LinkedHashMap<>();
        patch.put("git.requestId", null);
        patch.put("git.blockReason", result == RunItemResult.UNKNOWN || result == RunItemResult.FAILED
                ? "UNKNOWN" : checkpoint ? null : "PERMISSION_DENIED");
        patch.put("git.projectId", projectionProjectId());
        patch.put("git.sourceBranch", claim.metadata().get("plan.featureBranch"));
        patch.put("git.targetBranch", claim.metadata().get("plan.releaseBranch"));
        patch.put("git.sourceSha", target.path("sha").textValue());
        patch.put("git.mrIid", identifiedMr ? "11" : null);
        patch.put("git.mrUrl", identifiedMr ? "https://gitlab.invalid/project/-/merge_requests/11" : null);
        patch.put("git.mergeSha", null);
        patch.put("git.mrDisposition", checkpoint ? result.name() : null);
        return patch;
    }

    @Test
    void createMrIntentClaimCasFailureRollsBackThePreparedVersion() {
        var original = claimMrTask();
        String operationId = beginCreateMrIntent(original, 40);
        expireClaim(original);
        long version = mergeIntentVersion(operationId);
        var before = projectionFacts(original, operationId);
        String trigger = "b9_mr_claim_" + UUID.randomUUID().toString().replace("-", "");
        String seen = trigger + "_seen";
        try {
            jdbc.execute("create sequence " + seen);
            jdbc.execute("create function " + trigger + "() returns trigger language plpgsql as $$ begin "
                    + "if exists(select 1 from git_operation where id='" + operationId + "' and state_version>" + version
                    + ") then perform nextval('" + seen + "'); end if; return null; end; $$");
            jdbc.execute("create trigger " + trigger + " before update of claim_token on run_task for each row "
                    + "when (old.id='" + original.fence().taskId()
                    + "' and new.claim_token is distinct from old.claim_token) execute function " + trigger + "()");

            assertThatThrownBy(() -> runs.claimNextTask(original.runId())).isInstanceOf(BusinessException.class);

            assertThat(jdbc.queryForObject("select is_called from " + seen, Boolean.class))
                    .as("the rejected Task CAS must actually observe the prepared operation version").isTrue();
            assertThat(projectionFacts(original, operationId)).isEqualTo(before);
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on run_task");
            jdbc.execute("drop function if exists " + trigger + "()");
            jdbc.execute("drop sequence if exists " + seen);
        }
    }


    @Test
    void createMrIntentClaimOuterRollbackRestoresReservationAndRejectsLeakedFence() {
        var original = claimMrTask();
        String operationId = beginCreateMrIntent(original, 64);
        expireClaim(original);
        var before = projectionFacts(original, operationId);
        var leaked = new java.util.concurrent.atomic.AtomicReference<RunPort.TaskClaim>();

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var current = reclaimCreateMrIntent(original);
            leaked.set(current);
            assertThat(current.fence().claimToken()).isNotEqualTo(original.fence().claimToken());
            assertThat(mergeIntentVersion(operationId)).isGreaterThan(
                    ((Number) ((Map<?, ?>) before.get("operation")).get("state_version")).longValue());
            tx.setRollbackOnly();
        });

        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        assertThatThrownBy(() -> journal.sending(operationId, leaked.get().fence())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(leaked.get(), step(leaked.get(), RunItemResult.UNKNOWN), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(original, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }


    @Test
    void createMrIntentResultCasFailureRollsBackActualLocalDecisionAndBinding() {
        var current = claimMrTask();
        String operationId = beginCreateMrIntent(current, 40);
        var before = projectionFacts(current, operationId);
        String trigger = "b9_mr_result_" + UUID.randomUUID().toString().replace("-", "");
        String seen = trigger + "_seen";
        try {
            jdbc.execute("create sequence " + seen);
            jdbc.execute("create function " + trigger + "() returns trigger language plpgsql as $$ begin "
                    + "if exists(select 1 from git_operation where id='" + operationId + "' and state='NOT_SENT') "
                    + "and exists(select 1 from git_operation_event where operation_id='" + operationId
                    + "' and event_sequence=1 and event_type='NOT_SENT') "
                    + "and exists(select 1 from repo_execution_guard where remote_key=(select remote_key from git_operation where id='"
                    + operationId + "') and operation_id is null and session_id is null and reason is null) "
                    + "and exists(select 1 from run_step where task_id=old.id) then perform nextval('" + seen
                    + "'); end if; return null; end; $$");
            jdbc.execute("create trigger " + trigger + " before update of status on run_task for each row "
                    + "when (old.id='" + current.fence().taskId() + "' and old.status='RUNNING' and new.status='COMPLETED') "
                    + "execute function " + trigger + "()");

            assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.MR_REUSED),
                    createMrIntentPatch(current, operationId, RunItemResult.MR_REUSED))).isInstanceOf(BusinessException.class);

            assertThat(jdbc.queryForObject("select is_called from " + seen, Boolean.class))
                    .as("actual Task CAS=0 must occur after NOT_SENT, its event, Step and guard release").isTrue();
            assertThat(projectionFacts(current, operationId)).isEqualTo(before);
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on run_task");
            jdbc.execute("drop function if exists " + trigger + "()");
            jdbc.execute("drop sequence if exists " + seen);
        }
    }


    @Test
    void createMrIntentResultOuterRollbackRestoresTaskItemOperationGuardAndBinding() {
        var current = claimMrTask();
        String operationId = beginCreateMrIntent(current, 64);
        var before = projectionFacts(current, operationId);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var completed = completeNotSent(current, RunItemResult.MR_REUSED, createMrIntentPatch(current, operationId, RunItemResult.MR_REUSED));
            assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
            assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId))
                    .isEqualTo("NOT_SENT");
            assertThat(projectionGuard(operationId)).containsEntry("operation_id", null).containsEntry("session_id", null);
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                    current.fence().taskId())).isEqualTo(1);
            assertThat(bindings.findByWindowIdAndIterationKey(ReleaseWindowId.of(windowId), IterationKey.of(firstIteration))
                    .orElseThrow().getBranchCreated()).isTrue();
            tx.setRollbackOnly();
        });

        assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }


    @Test
    void createMrIntentSendWinsActualPostgresLockAndLocalUnknownCannotSealSender() throws Exception {
        var current = claimMrTask();
        String operationId = beginCreateMrIntent(current, 40);
        var target = createMrRequestTarget(current, operationId, 40);
        var taskBefore = jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId());
        var sendLocked = new CountDownLatch(1);
        var releaseSend = new CountDownLatch(1);
        var completionStarted = new CountDownLatch(1);
        var senderThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var senderPid = new java.util.concurrent.atomic.AtomicInteger();
        var completerPid = new java.util.concurrent.atomic.AtomicInteger();
        var pauseOnce = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (Thread.currentThread() == senderThread.get() && pauseOnce.compareAndSet(false, true)) {
                senderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                sendLocked.countDown();
                awaitMergeIntentLatch(releaseSend);
            }
            return Instant.now();
        }).when(clock).instant();

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var sender = pool.submit(() -> {
                    senderThread.set(Thread.currentThread());
                    journal.sending(operationId, current.fence());
                });
                assertThat(sendLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var completion = pool.submit(() -> {
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                            completerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                            completionStarted.countDown();
                            runs.completeTask(current, step(current, RunItemResult.UNKNOWN), Map.of());
                        });
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                });
                assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(completerPid.get(), Set.of(senderPid.get()));
                releaseSend.countDown();
                sender.get(10, TimeUnit.SECONDS);
                assertThat(completion.get(10, TimeUnit.SECONDS))
                        .as("local UNKNOWN cannot end a Task after the real sender acquired durable SEND")
                        .isInstanceOf(BusinessException.class);
            } finally {
                releaseSend.countDown();
            }
        }

        assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId())).isEqualTo(taskBefore);
        assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
        assertThat(journal.find(operationId).orElseThrow().events()).hasSize(2);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId).containsEntry("reason", "REQUEST_IN_FLIGHT");
        journal.finish(operationId, current.fence(), GitWriteResult.Outcome.APPLIED,
                createMrRequestProof(target, projectionProjectId()).toString(), null);
        completeNotSent(current, RunItemResult.CREATED, createMrIntentPatch(current, operationId, RunItemResult.CREATED));
        assertThat(jdbc.queryForObject("select status from run_task where id=?", String.class,
                current.fence().taskId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from git_operation_event where operation_id=? and event_type='NOT_SENT'",
                Integer.class, operationId)).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }


    @Test
    void createMrIntentNotSentWinsActualPostgresLockAndRejectsLateSendAndFinish() throws Exception {
        var current = claimMrTask();
        String operationId = beginCreateMrIntent(current, 40);
        var target = createMrRequestTarget(current, operationId, 40);
        var decisionLocked = new CountDownLatch(1);
        var releaseDecision = new CountDownLatch(1);
        var senderStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var completion = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    var completed = completeNotSent(current, RunItemResult.ALREADY_MERGED, createMrIntentPatch(current, operationId, RunItemResult.ALREADY_MERGED));
                    assertThat(jdbc.queryForObject("select state from git_operation where id=?", String.class, operationId))
                            .isEqualTo("NOT_SENT");
                    decisionLocked.countDown();
                    awaitMergeIntentLatch(releaseDecision);
                    return completed;
                }));
                assertThat(decisionLocked.await(5, TimeUnit.SECONDS))
                        .as("the actual MR result transaction must hold NOT_SENT before racing SEND").isTrue();
                var lateSender = pool.submit(() -> {
                    senderStarted.countDown();
                    try {
                        journal.sending(operationId, current.fence());
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                });
                assertThat(senderStarted.await(5, TimeUnit.SECONDS)).isTrue();
                int waiter = awaitMergeIntentWaiter(holderPid.get());
                awaitMergeIntentBlockers(waiter, Set.of(holderPid.get()));
                releaseDecision.countDown();
                assertThat(completion.get(10, TimeUnit.SECONDS).getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
                assertThat(lateSender.get(10, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class);
            } finally {
                releaseDecision.countDown();
            }
        }
        var terminal = projectionFacts(current, operationId);
        assertThatThrownBy(() -> journal.finish(operationId, current.fence(), GitWriteResult.Outcome.APPLIED,
                createMrRequestProof(target, projectionProjectId()).toString(), null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThat(projectionFacts(current, operationId)).isEqualTo(terminal);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }


    // End B9 independent contract.
    // Durable Step order regression: the 13-suite failure exposed an unordered loaded collection.
    // These fixtures control the loaded Entity permutation; they do not claim PostgreSQL returns it.
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SAME_TIME", "REVERSED_TIME"})
    void runStepOrderUsesTaskOrderDespiteControlledLoadedPermutationAndResultTimes(String timing) {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        Instant base = Instant.parse("2026-01-02T03:04:05Z");
        var expected = runStepOrderCompleteBranchTasks(run,
                "SAME_TIME".equals(timing) ? base : base.plusSeconds(10), base);

        assertRunStepOrderAcrossPersistentReads(run.getId().value(), run.getItems().getFirst().getId().value(),
                expected, List.of("task-B", "task-A"));
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void runStepOrderRetainsRepeatedCleanupActionsWithoutInventingTasks() {
        var window = windows.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        window.publish(Instant.now());
        windows.save(window);
        Run run = plans.admitCleanup(windowId, "cleanup-order-operator", false).run();
        var item = run.getItems().stream().filter(value -> value.getRepo().value().equals(firstRepo))
                .findFirst().orElseThrow();
        Instant base = Instant.parse("2026-01-02T03:04:05Z");
        var expected = List.of(
                new RunStep(ActionType.MERGE_TO_MASTER, RunItemResult.MERGED,
                        base, base.plusSeconds(1), "cleanup-A"),
                new RunStep(ActionType.MERGE_TO_MASTER, RunItemResult.ALREADY_MERGED,
                        base.plusSeconds(5), base.plusSeconds(6), "cleanup-B"),
                new RunStep(ActionType.MERGE_TO_MASTER, RunItemResult.MERGED,
                        base.plusSeconds(5), base.plusSeconds(7), "cleanup-C"));
        expected.forEach(item::addStep);
        runs.save(run);
        assertThat(runs.findTasks(run.getId().value())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from run_step where run_item_id=? and task_id is not null",
                Integer.class, item.getId().value())).isZero();

        assertRunStepOrderAcrossPersistentReads(run.getId().value(), item.getId().value(),
                expected, List.of("cleanup-C", "cleanup-B", "cleanup-A"));
        assertThat(runs.findTasks(run.getId().value())).isEmpty();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void runStepOrderKeepsForeignLinkedFactsAfterKnownTasksWithoutBorrowingTheirOrder() {
        Run run = admit(List.of(firstRepo), List.of(firstIteration), true);
        Instant base = Instant.parse("2026-01-02T03:04:05Z");
        var expected = new java.util.ArrayList<>(runStepOrderCompleteBranchTasks(run, base.plusSeconds(10), base));
        String itemId = run.getItems().getFirst().getId().value();
        Run foreignRun = admit(List.of(secondRepo), List.of(firstIteration), true);
        var foreignTask = runs.findTasks(foreignRun.getId().value()).getFirst();
        assertThat(foreignTask.runId()).isNotEqualTo(run.getId().value());
        assertThat(foreignTask.itemId()).isNotEqualTo(itemId);
        assertThat(foreignTask.taskOrder()).isEqualTo(1);
        var unlinked = new RunStep(foreignTask.actionType(), RunItemResult.ALREADY_EXISTS,
                base.plusSeconds(5), base.plusSeconds(6), "foreign-U");
        // The FK exists, but it does not prove this Run/Item owns that Task's ordering authority.
        assertThat(jdbc.update("insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id)"
                        + " values (?,?,?,?,?,?,?)", itemId, unlinked.actionType().name(), unlinked.result().name(),
                java.sql.Timestamp.from(unlinked.startAt()), java.sql.Timestamp.from(unlinked.endAt()),
                unlinked.message(), foreignTask.taskId())).isEqualTo(1);
        expected.add(unlinked);

        // A(order 1,time 10), B(order 2,time 0), U(time 5) defeats pairwise task-or-time comparison.
        // Known/unlinked grouping is a display rule, not a claim about U's actual execution causality.
        assertRunStepOrderAcrossPersistentReads(run.getId().value(), itemId,
                expected, List.of("task-B", "foreign-U", "task-A"));
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    private List<RunStep> runStepOrderCompleteBranchTasks(Run run, Instant firstAt, Instant secondAt) {
        var expected = List.of(
                new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.ALREADY_EXISTS,
                        firstAt, firstAt.plusSeconds(1), "task-A"),
                new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.ALREADY_EXISTS,
                        secondAt, secondAt.plusSeconds(1), "task-B"));
        for (var step : expected) {
            var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
            assertThat(claim.actionType()).isEqualTo(step.actionType());
            assertThat(claim.itemId()).isEqualTo(run.getItems().getFirst().getId().value());
            runs.completeTask(claim, step, Map.of("git.projectId", projectId(claim)));
        }
        assertThat(jdbc.queryForList("select t.task_order from run_task t join run_step s on s.task_id=t.id"
                + " where t.run_id=? order by t.task_order", Integer.class, run.getId().value())).containsExactly(1, 2);
        return expected;
    }

    private void assertRunStepOrderAcrossPersistentReads(String runId, String itemId,
            List<RunStep> expected, List<String> loadedPermutation) {
        var stepsBefore = jdbc.queryForList("select * from run_step where run_item_id=?"
                + " order by start_at,end_at,message,task_id", itemId);
        var tasksBefore = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            entityManager.clear();
            // The real persisted collection is loaded first. Suppress auto-flush of the test-only permutation.
            entityManager.setFlushMode(jakarta.persistence.FlushModeType.COMMIT);
            var stored = entityManager.find(
                    io.releasehub.infrastructure.persistence.run.RunItemJpaEntity.class, itemId);
            assertThat(stored.getRun().getId()).isEqualTo(runId);
            assertThat(stored.getSteps()).hasSize(expected.size());
            stored.getSteps().sort(java.util.Comparator.comparingInt(step -> loadedPermutation.indexOf(step.getMessage())));
            assertThat(stored.getSteps().stream().map(
                    io.releasehub.infrastructure.persistence.run.RunStepJpaEmbeddable::getMessage))
                    .containsExactlyElementsOf(loadedPermutation);

            var reads = new java.util.LinkedHashMap<String, Run>();
            reads.put("findById", runs.findById(runId).orElseThrow());
            reads.put("findAll", runs.findAll().stream().filter(value -> value.getId().value().equals(runId))
                    .findFirst().orElseThrow());
            reads.put("findPaged", runs.findPaged(null, null, stored.getWindowKey(), null, null, null, null, 1, 20)
                    .items().stream().filter(value -> value.getId().value().equals(runId)).findFirst().orElseThrow());
            org.assertj.core.api.SoftAssertions.assertSoftly(softly -> reads.forEach((name, restored) -> {
                var steps = restored.getItems().stream().filter(value -> value.getId().value().equals(itemId))
                        .findFirst().orElseThrow().getSteps();
                softly.assertThat(steps).as("%s preserves all Step facts in display order", name)
                        .containsExactlyElementsOf(expected);
                softly.assertThat(steps.getLast()).as("%s exposes the final displayed Step", name)
                        .isEqualTo(expected.getLast());
            }));
        });
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=?"
                + " order by start_at,end_at,message,task_id", itemId)).isEqualTo(stepsBefore);
        assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId))
                .isEqualTo(tasksBefore);
    }

    // B12 independent contract: classify a first UNKNOWN as FAILED only when its Task has no own operation or Step.
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ActionType.class,
            names = {"ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR", "TRY_MERGE"})
    void noOperationUnknownClosesEachActionAsFailed(ActionType action) {
        String priorOperation = null;
        RunPort.TaskClaim current;
        if (action == ActionType.TRY_MERGE) {
            var ensured = claimMrTask();
            priorOperation = recordAppliedCreatedMr(ensured);
            runs.completeTask(ensured, step(ensured, RunItemResult.CREATED), createdMrCheckpoint(ensured));
            current = runs.claimNextTask(ensured.runId()).orElseThrow();
        } else {
            current = action == ActionType.ENSURE_MR ? claimMrTask() : claimBranchTask(action);
        }
        assertThat(current.actionType()).isEqualTo(action);
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        var priorDetail = priorOperation == null ? null : journal.find(priorOperation).orElseThrow();
        var priorGuard = priorOperation == null ? Map.<String, Object>of() : projectionGuard(priorOperation);
        var beforeTask = new java.util.LinkedHashMap<>(jdbc.queryForMap(
                "select * from run_task where id=?", current.fence().taskId()));
        int taskOrder = ((Number) beforeTask.get("task_order")).intValue();
        beforeTask.keySet().removeAll(Set.of("status", "finished_at", "error_message"));
        var bindingBefore = jdbc.queryForList("select * from window_iteration where window_id=? order by iteration_key", windowId);
        var prefix = runs.findById(current.runId()).orElseThrow().getItems().getFirst().getSteps();
        String message = "READ_UNCONFIRMED: preserve the original diagnostic";
        var input = new RunStep(action, RunItemResult.UNKNOWN, current.startedAt(),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS), message);
        var patch = new java.util.LinkedHashMap<String, String>();
        patch.put("git.blockReason", "UNKNOWN");
        patch.put("git.requestId", null);
        var expectedMetadata = new java.util.LinkedHashMap<>(current.metadata());
        expectedMetadata.put("git.blockReason", "UNKNOWN");
        expectedMetadata.remove("git.requestId");

        var completed = runs.completeTask(current, input, patch);

        var item = completed.getItems().getFirst();
        assertThat(item.getSteps()).hasSize(prefix.size() + 1);
        assertThat(item.getSteps().subList(0, prefix.size())).containsExactlyElementsOf(prefix);
        assertThat(item.getSteps().getLast()).isEqualTo(new RunStep(action, RunItemResult.FAILED,
                input.startAt(), input.endAt(), message));
        assertThat(item.getFinalResult()).isEqualTo(RunItemResult.FAILED);
        assertThat(item.getMetadata()).isEqualTo(expectedMetadata);
        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
        assertThat(completed.getFinishedAt()).isNotNull();
        var taskAfter = new java.util.LinkedHashMap<>(jdbc.queryForMap(
                "select * from run_task where id=?", current.fence().taskId()));
        assertThat(taskAfter).containsEntry("status", "FAILED").containsEntry("error_message", message);
        assertThat(taskAfter.get("finished_at")).isNotNull();
        taskAfter.keySet().removeAll(Set.of("status", "finished_at", "error_message"));
        assertThat(taskAfter).isEqualTo(beforeTask);
        assertThat(runs.findTasks(current.runId()).stream().filter(task -> task.taskOrder() > taskOrder))
                .allSatisfy(task -> assertThat(task.status()).isEqualTo("SKIPPED"));
        assertThat(jdbc.queryForList("select * from window_iteration where window_id=? order by iteration_key", windowId))
                .isEqualTo(bindingBefore);
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        if (priorOperation != null) {
            assertThat(journal.find(priorOperation).orElseThrow()).isEqualTo(priorDetail);
            assertThat(projectionGuard(priorOperation)).isEqualTo(priorGuard);
        }
        var terminal = noOperationUnknownFacts(current);
        assertThatThrownBy(() -> runs.completeTask(current, input, patch)).isInstanceOf(BusinessException.class);
        assertThat(noOperationUnknownFacts(current)).isEqualTo(terminal);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "ENSURE_MR,MR_REUSED", "TRY_MERGE,MERGE_BLOCKED", "ENSURE_FEATURE,FAILED"
    })
    void noOperationUnknownKeepsOtherResults(ActionType action, RunItemResult result) {
        var current = action == ActionType.TRY_MERGE ? claimMergedMrTask("MR_REUSED", 40)
                : action == ActionType.ENSURE_MR ? claimMrTask() : claimBranchTask(action);
        var patch = new java.util.LinkedHashMap<String, String>();
        if (result == RunItemResult.MR_REUSED) {
            patch.putAll(createdMrCheckpoint(current));
            patch.put("git.mrDisposition", "MR_REUSED");
        } else {
            patch.put("git.blockReason", result == RunItemResult.MERGE_BLOCKED ? "PIPELINE_PENDING" : "UNKNOWN");
        }
        var expectedMetadata = new java.util.LinkedHashMap<>(current.metadata());
        expectedMetadata.putAll(patch);
        var input = new RunStep(action, result, current.startedAt(),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS), "preserve " + result);

        var completed = runs.completeTask(current, input, patch);

        assertThat(completed.getItems().getFirst().getSteps().getLast()).isEqualTo(input);
        assertThat(completed.getItems().getFirst().getMetadata()).isEqualTo(expectedMetadata);
        assertThat(runs.findTasks(current.runId()).stream().filter(task -> task.taskId().equals(current.fence().taskId())))
                .singleElement().satisfies(task -> assertThat(task.status())
                        .isEqualTo(result.isFailure() ? "FAILED" : "COMPLETED"));
        assertThat(completed.getExecutionStatus()).isEqualTo(result.isFailure()
                ? RunExecutionStatus.FAILED : RunExecutionStatus.RUNNING);
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"INTENT", "SEND_STARTED", "UNKNOWN_IO", "UNKNOWN_HTTP_200", "APPLIED"})
    void noOperationUnknownDoesNotOverrideAnyOwnOperation(String stage) {
        var current = claimMergedMrTask("MR_REUSED", 40);
        String operationId = beginMergeIntent(current);
        var intent = journal.find(operationId).orElseThrow().events().getFirst();
        if (!stage.equals("INTENT")) journal.sending(operationId, current.fence());
        if (stage.startsWith("UNKNOWN")) {
            journal.finish(operationId, current.fence(), GitWriteResult.Outcome.UNKNOWN,
                    stage.equals("UNKNOWN_HTTP_200") ? "{\"httpStatus\":200}" : "{}", "RESPONSE_UNCONFIRMED");
        } else if (stage.equals("APPLIED")) {
            journal.finish(operationId, current.fence(), GitWriteResult.Outcome.APPLIED,
                    mergedMrProof(current, null).toString(), null);
        }
        var before = projectionFacts(current, operationId);
        var eventsBefore = journal.find(operationId).orElseThrow().events();

        if (stage.equals("SEND_STARTED")) {
            assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN),
                    notSentPatch(RunItemResult.UNKNOWN))).isInstanceOf(BusinessException.class);
            assertThat(projectionFacts(current, operationId)).isEqualTo(before);
        } else {
            var completed = runs.completeTask(current, step(current, RunItemResult.UNKNOWN),
                    notSentPatch(RunItemResult.UNKNOWN));
            assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
            assertThat(completed.getItems().getFirst().getFinalResult()).isEqualTo(RunItemResult.UNKNOWN);
            assertThat(completed.getItems().getFirst().getSteps().getLast().result()).isEqualTo(RunItemResult.UNKNOWN);
            assertThat(runs.findTasks(current.runId()).getLast().status()).isEqualTo("UNKNOWN");
            var detail = journal.find(operationId).orElseThrow();
            assertThat(detail.events().getFirst()).isEqualTo(intent);
            if (stage.equals("INTENT")) {
                assertThat(detail.operation().state()).isEqualTo("NOT_SENT");
                assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "NOT_SENT");
                assertThat(projectionGuard(operationId)).containsEntry("session_id", null)
                        .containsEntry("operation_id", null).containsEntry("reason", null);
            } else if (stage.equals("APPLIED")) {
                assertThat(detail.operation().state()).isEqualTo("NEEDS_REVIEW");
                assertThat(detail.events().subList(0, 3)).containsExactlyElementsOf(eventsBefore);
                assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED", "LOCAL_RESULT_UNCONFIRMED");
                assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId)
                        .containsEntry("reason", "LOCAL_RESULT_UNCONFIRMED");
            } else {
                assertThat(detail.operation().state()).isEqualTo("UNKNOWN");
                assertThat(detail.events()).containsExactlyElementsOf(eventsBefore);
                assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId)
                        .containsEntry("reason", "REMOTE_RESULT_UNKNOWN");
            }
            assertThatThrownBy(() -> taskService.retry(current.runId(), List.of(current.itemId()),
                    "operator", UUID.randomUUID().toString())).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void noOperationUnknownBeginWinsTheActualRunTaskLock() throws Exception {
        var current = claimMergedMrTask("MR_REUSED", 40);
        settings.saveGitLab(new SettingsPort.SettingsGitLab("https://gitlab.invalid", "isolated-projection-token"));
        var command = mergeIntentCommand(current, mergeIntentTarget(current));
        var beginLocked = new CountDownLatch(1);
        var releaseBegin = new CountDownLatch(1);
        var completionStarted = new CountDownLatch(1);
        var beginThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var beginPid = new java.util.concurrent.atomic.AtomicInteger();
        var completionPid = new java.util.concurrent.atomic.AtomicInteger();
        var pauseOnce = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (Thread.currentThread() == beginThread.get() && pauseOnce.compareAndSet(false, true)) {
                beginPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                beginLocked.countDown();
                awaitMergeIntentLatch(releaseBegin);
            }
            return Instant.now();
        }).when(clock).instant();

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var beginning = pool.submit(() -> {
                    beginThread.set(Thread.currentThread());
                    return journal.begin(command);
                });
                assertThat(beginLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var completing = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    completionPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    completionStarted.countDown();
                    return runs.completeTask(current, step(current, RunItemResult.UNKNOWN),
                            notSentPatch(RunItemResult.UNKNOWN));
                }));
                assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitMergeIntentBlockers(completionPid.get(), Set.of(beginPid.get()));
                releaseBegin.countDown();
                assertThat(beginning.get(10, TimeUnit.SECONDS).id()).isEqualTo(command.operationId());
                assertThat(completing.get(10, TimeUnit.SECONDS).getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
            } finally {
                releaseBegin.countDown();
            }
        }
        assertThat(runs.findTasks(current.runId()).getLast().status()).isEqualTo("UNKNOWN");
        assertThat(runs.findById(current.runId()).orElseThrow().getItems().getFirst().getSteps().getLast().result())
                .isEqualTo(RunItemResult.UNKNOWN);
        var detail = journal.find(command.operationId()).orElseThrow();
        assertThat(detail.operation().targetJson()).isEqualTo(command.targetJson());
        assertThat(detail.operation().state()).isEqualTo("NOT_SENT");
        assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "NOT_SENT");
        assertThat(detail.events().getFirst().evidenceJson()).isEqualTo("{}");
        assertThat(projectionGuard(command.operationId())).containsEntry("session_id", null)
                .containsEntry("operation_id", null).containsEntry("reason", null);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void noOperationUnknownResultWinsBeforeLateBegin() throws Exception {
        var current = claimMergedMrTask("MR_REUSED", 40);
        settings.saveGitLab(new SettingsPort.SettingsGitLab("https://gitlab.invalid", "isolated-projection-token"));
        var command = mergeIntentCommand(current, mergeIntentTarget(current));
        var resultWritten = new CountDownLatch(1);
        var releaseResult = new CountDownLatch(1);
        var beginStarted = new CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicInteger();
        Run completed;
        RuntimeException rejected;
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var completing = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                    holderPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    var restored = runs.completeTask(current, step(current, RunItemResult.UNKNOWN),
                            notSentPatch(RunItemResult.UNKNOWN));
                    // Both baseline and target reach real result writes before the race; assert the new result after release.
                    assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                            current.fence().taskId())).isEqualTo(1);
                    assertThat(jdbc.queryForObject("select status from run_task where id=?", String.class,
                            current.fence().taskId())).isIn("UNKNOWN", "FAILED");
                    assertThat(jdbc.queryForObject("select metadata_value from run_item_metadata"
                            + " where run_item_id=? and metadata_key='git.blockReason'", String.class, current.itemId()))
                            .isEqualTo("UNKNOWN");
                    resultWritten.countDown();
                    awaitMergeIntentLatch(releaseResult);
                    return restored;
                }));
                assertThat(resultWritten.await(5, TimeUnit.SECONDS)).isTrue();
                var beginning = pool.submit(() -> {
                    beginStarted.countDown();
                    try {
                        journal.begin(command);
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                });
                assertThat(beginStarted.await(5, TimeUnit.SECONDS)).isTrue();
                int waiter = awaitMergeIntentWaiter(holderPid.get());
                awaitMergeIntentBlockers(waiter, Set.of(holderPid.get()));
                releaseResult.countDown();
                completed = completing.get(10, TimeUnit.SECONDS);
                rejected = beginning.get(10, TimeUnit.SECONDS);
            } finally {
                releaseResult.countDown();
            }
        }

        assertThat(rejected).isInstanceOf(BusinessException.class);
        assertThat(journal.find(command.operationId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from repo_execution_guard where remote_key=?", Integer.class,
                command.remoteKey())).isZero();
        assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
        assertThat(completed.getItems().getFirst().getSteps().getLast().result()).isEqualTo(RunItemResult.FAILED);
        assertThat(runs.findTasks(current.runId()).getLast().status()).isEqualTo("FAILED");
        var terminal = noOperationUnknownFacts(current);
        assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN),
                notSentPatch(RunItemResult.UNKNOWN))).isInstanceOf(BusinessException.class);
        assertThat(noOperationUnknownFacts(current)).isEqualTo(terminal);
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void noOperationUnknownTaskCasRollsBackActualFailedProjection() {
        var current = claimMrTask();
        var before = noOperationUnknownFacts(current);
        var patch = new java.util.LinkedHashMap<String, String>();
        patch.put("git.blockReason", "UNKNOWN");
        patch.put("git.requestId", null);
        String trigger = "b12_result_" + UUID.randomUUID().toString().replace("-", "");
        String seen = trigger + "_seen";
        try {
            jdbc.execute("create sequence " + seen);
            jdbc.execute("create function " + trigger + "() returns trigger language plpgsql as $$ begin "
                    + "if exists(select 1 from run_step where task_id=old.id and result='FAILED') "
                    + "and exists(select 1 from run_item where id=old.target_id and final_result='FAILED') "
                    + "and exists(select 1 from run_item_metadata where run_item_id=old.target_id "
                    + "and metadata_key='git.blockReason' and metadata_value='UNKNOWN') "
                    + "and not exists(select 1 from run_item_metadata where run_item_id=old.target_id "
                    + "and metadata_key='git.requestId') "
                    + "and exists(select 1 from run_task where run_id=old.run_id and target_id=old.target_id "
                    + "and task_type='TRY_MERGE' and status='SKIPPED') "
                    + "and not exists(select 1 from git_operation where task_id=old.id) "
                    + "then perform nextval('" + seen + "'); end if; return null; end; $$");
            jdbc.execute("create trigger " + trigger + " before update of status on run_task for each row "
                    + "when (old.id='" + current.fence().taskId() + "' and old.status='RUNNING' and new.status='FAILED') "
                    + "execute function " + trigger + "()");

            assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN), patch))
                    .isInstanceOf(BusinessException.class);

            assertThat(jdbc.queryForObject("select is_called from " + seen, Boolean.class))
                    .as("the failing Task CAS follows actual FAILED Step, Item, metadata and successor writes").isTrue();
            assertThat(noOperationUnknownFacts(current)).isEqualTo(before);
            verifyNoInteractions(gitLab, gitFiles, gitBranches);
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on run_task");
            jdbc.execute("drop function if exists " + trigger + "()");
            jdbc.execute("drop sequence if exists " + seen);
        }
    }

    @Test
    void noOperationUnknownOuterRollbackRestoresAllLocalFacts() {
        var current = claimMrTask();
        var before = noOperationUnknownFacts(current);
        var patch = new java.util.LinkedHashMap<String, String>();
        patch.put("git.blockReason", "UNKNOWN");
        patch.put("git.requestId", null);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var completed = runs.completeTask(current, step(current, RunItemResult.UNKNOWN), patch);
            assertThat(completed.getItems().getFirst().getSteps().getLast().result()).isEqualTo(RunItemResult.FAILED);
            assertThat(completed.getItems().getFirst().getFinalResult()).isEqualTo(RunItemResult.FAILED);
            assertThat(completed.getItems().getFirst().getMetadata()).containsEntry("git.blockReason", "UNKNOWN")
                    .doesNotContainKey("git.requestId");
            assertThat(completed.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
            assertThat(completed.getFinishedAt()).isNotNull();
            assertThat(jdbc.queryForObject("select result from run_step where task_id=?", String.class,
                    current.fence().taskId())).isEqualTo("FAILED");
            assertThat(jdbc.queryForMap("select status,finished_at,error_message from run_task where id=?",
                    current.fence().taskId())).containsEntry("status", "FAILED").containsEntry("error_message", "UNKNOWN");
            assertThat(jdbc.queryForObject("select status from run_task where run_id=? and task_type='TRY_MERGE'",
                    String.class, current.runId())).isEqualTo("SKIPPED");
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, current.runId()))
                    .isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                    current.fence().taskId())).isZero();
            tx.setRollbackOnly();
        });

        assertThat(noOperationUnknownFacts(current)).isEqualTo(before);
        String operationId = beginCreateMrIntent(current, 40);
        var intent = journal.find(operationId).orElseThrow();
        assertThat(jdbc.queryForMap("select status,claim_token from run_task where id=?", current.fence().taskId()))
                .containsEntry("status", "RUNNING").containsEntry("claim_token", current.fence().claimToken());
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        assertThat(intent.operation().state()).isEqualTo("INTENT_RECORDED");
        assertThat(intent.operation().projectId()).isEqualTo(current.metadata().get("git.projectId"));
        assertThat(intent.operation().targetJson()).isEqualTo(createMrRequestTarget(current, operationId, 40).toString());
        assertThat(intent.events()).extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED");
        assertThat(intent.events()).extracting(GitOperationJournalPort.Event::sequence).containsExactly(0);
        assertThat(projectionGuard(operationId)).containsEntry("operation_id", operationId)
                .containsEntry("session_id", current.fence().claimToken()).containsEntry("reason", "INTENT_PENDING");
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void noOperationUnknownUsesTheSelectedRepositoryPolicy(boolean failFast) {
        Run admitted = admit(List.of(firstRepo, secondRepo), List.of(firstIteration, secondIteration), failFast);
        String runId = admitted.getId().value();
        var current = runs.claimNextTask(runId).orElseThrow();
        var otherFeature = runs.claimNextTask(runId).orElseThrow();
        assertThat(current.actionType()).isEqualTo(ActionType.ENSURE_FEATURE);
        assertThat(otherFeature.repo()).isNotEqualTo(current.repo());
        completeMergedMrFixtureTask(otherFeature, "MR_REUSED", 40);
        var otherRelease = runs.claimNextTask(runId).orElseThrow();
        assertThat(otherRelease.repo()).isEqualTo(otherFeature.repo());
        assertThat(otherRelease.actionType()).isEqualTo(ActionType.ENSURE_RELEASE);
        String otherOperation = beginBranchIntent(otherRelease, 40);
        var otherHistory = mergeIntentHistory(otherRelease, otherOperation);
        String laterItem = admitted.getItems().stream().filter(item -> item.getRepo().equals(current.repo())
                && !item.getId().value().equals(current.itemId())).findFirst().orElseThrow().getId().value();

        var completed = runs.completeTask(current, step(current, RunItemResult.UNKNOWN), Map.of("git.blockReason", "UNKNOWN"));

        assertThat(mergeIntentHistory(otherRelease, otherOperation)).isEqualTo(otherHistory);
        assertThat(runs.findTasks(runId).stream().filter(task -> task.itemId().equals(current.itemId()))
                .map(RunPort.TaskView::status)).containsExactly("FAILED", "SKIPPED", "SKIPPED", "SKIPPED");
        var later = completed.getItems().stream().filter(item -> item.getId().value().equals(laterItem))
                .findFirst().orElseThrow();
        assertThat(runs.findTasks(runId).stream().filter(task -> task.itemId().equals(laterItem))
                .map(RunPort.TaskView::status)).containsExactlyElementsOf(
                        java.util.Collections.nCopies(4, failFast ? "SKIPPED" : "PENDING"));
        if (failFast) {
            assertThat(later.getFinalResult()).isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
            assertThat(later.getMetadata()).containsEntry("blockedBy.itemId", current.itemId());
            assertThat(later.getExecutedOrder()).isZero();
        } else {
            assertThat(later.getFinalResult()).isNull();
            assertThat(later.getMetadata()).doesNotContainKey("blockedBy.itemId");
        }
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                current.fence().taskId())).isZero();

        completeMergedMrFixtureTask(otherRelease, "MR_REUSED", 40);
        int remaining = failFast ? 6 : 10;
        for (int index = 0; index < remaining; index++) {
            var next = runs.claimNextTask(runId);
            assertThat(next).as("each unaffected eligible Task must remain claimable").isPresent();
            completeMergedMrFixtureTask(next.orElseThrow(), "MR_REUSED", 40);
        }
        assertThat(runs.claimNextTask(runId)).isEmpty();
        var terminal = runs.findById(runId).orElseThrow();
        assertThat(terminal.getExecutionStatus()).isEqualTo(RunExecutionStatus.PARTIAL_FAILED);
        assertThat(terminal.getFinishedAt()).isNotNull();
        assertThat(terminal.getItems().stream().filter(item -> item.getRepo().equals(otherFeature.repo())))
                .hasSize(2).allSatisfy(item -> assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGED));
        assertThat(terminal.getItems().stream().filter(item -> item.getId().value().equals(laterItem)))
                .singleElement().satisfies(item -> assertThat(item.getFinalResult())
                        .isEqualTo(failFast ? RunItemResult.SKIPPED_DUE_TO_BLOCK : RunItemResult.MERGED));
        assertThat(runs.findTasks(runId)).noneSatisfy(task -> assertThat(task.status()).isEqualTo("UNKNOWN"));
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void noOperationUnknownDoesNotRewriteHistoricalUnknown() {
        var current = claimMergedMrTask("MR_REUSED", 40);
        runs.completeTask(current, new RunStep(current.actionType(), RunItemResult.FAILED,
                current.startedAt(), Instant.now(), "historical READ_UNCONFIRMED"), Map.of("git.blockReason", "UNKNOWN"));
        // An explicit historical row fixture: B12 does not migrate or reopen an already stored UNKNOWN outcome.
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThat(jdbc.update("update run_step set result='UNKNOWN' where task_id=?", current.fence().taskId()))
                    .isEqualTo(1);
            assertThat(jdbc.update("update run_task set status='UNKNOWN' where id=?", current.fence().taskId()))
                    .isEqualTo(1);
            assertThat(jdbc.update("update run_item set final_result='UNKNOWN' where id=?", current.itemId()))
                    .isEqualTo(1);
            assertThat(jdbc.update("update run set execution_status='NEEDS_REVIEW',finished_at=null where id=?",
                    current.runId())).isEqualTo(1);
        });
        var before = noOperationUnknownFacts(current);

        assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN),
                notSentPatch(RunItemResult.UNKNOWN))).isInstanceOf(BusinessException.class);
        assertThat(runs.claimNextTask(current.runId())).isEmpty();
        assertThat(runs.projectNextAppliedTask(current.runId())).isFalse();
        assertThat(taskService.executeNextTask(current.runId())).isFalse();
        assertThatThrownBy(() -> taskService.retry(current.runId(), List.of(current.itemId()),
                "operator", UUID.randomUUID().toString())).isInstanceOf(BusinessException.class);

        assertThat(noOperationUnknownFacts(current)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    @Test
    void noOperationUnknownRejectsAnExistingCurrentStep() {
        var current = claimMergedMrTask("MR_REUSED", 40);
        assertThat(jdbc.update("insert into run_step(run_item_id,action_type,result,start_at,end_at,message,task_id) "
                + "values (?,?,?,?,?,?,?)", current.itemId(), current.actionType().name(), "UNKNOWN",
                java.sql.Timestamp.from(current.startedAt()), java.sql.Timestamp.from(Instant.now()),
                "pre-existing Step must not be reinterpreted", current.fence().taskId())).isEqualTo(1);
        var before = noOperationUnknownFacts(current);

        // Baseline's unique Step constraint and B12's earlier stale rejection must both preserve the existing fact.
        assertThatThrownBy(() -> runs.completeTask(current, step(current, RunItemResult.UNKNOWN),
                notSentPatch(RunItemResult.UNKNOWN))).isInstanceOf(RuntimeException.class);

        assertThat(noOperationUnknownFacts(current)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from run_task where id=?", String.class,
                current.fence().taskId())).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                current.fence().taskId())).isZero();
        verifyNoInteractions(gitLab, gitFiles, gitBranches);
    }

    private Map<String, Object> noOperationUnknownFacts(RunPort.TaskClaim claim) {
        String candidateRemote = GitOperationJournalPort.remoteKey("GITLAB", "https://gitlab.invalid",
                claim.metadata().getOrDefault("git.projectId", projectionProjectId()));
        return Map.of(
                "run", jdbc.queryForMap("select * from run where id=?", claim.runId()),
                "tasks", jdbc.queryForList("select * from run_task where run_id=? order by task_order", claim.runId()),
                "items", jdbc.queryForList("select * from run_item where run_id=? order by id", claim.runId()),
                "steps", jdbc.queryForList("select s.* from run_step s join run_item i on i.id=s.run_item_id "
                        + "where i.run_id=? order by s.run_item_id,s.task_id,s.action_type,s.start_at,s.end_at,s.message", claim.runId()),
                "metadata", jdbc.queryForList("select m.* from run_item_metadata m join run_item i on i.id=m.run_item_id "
                        + "where i.run_id=? order by m.run_item_id,m.metadata_key", claim.runId()),
                "operations", jdbc.queryForList("select * from git_operation where run_id=? order by id", claim.runId()),
                "events", jdbc.queryForList("select e.* from git_operation_event e join git_operation o on o.id=e.operation_id "
                        + "where o.run_id=? order by e.operation_id,e.event_sequence", claim.runId()),
                "guards", jdbc.queryForList("select * from repo_execution_guard where remote_key=? "
                        + "or remote_key in (select remote_key from git_operation where run_id=?) order by remote_key",
                        candidateRemote, claim.runId()),
                "bindings", jdbc.queryForList("select * from window_iteration where window_id=? order by iteration_key", windowId));
    }

    // End B12 independent contract.

    private com.fasterxml.jackson.databind.node.ObjectNode createMrRequestTarget(RunPort.TaskClaim claim,
            String operationId, int shaLength) {
        String source = claim == null ? "feature/" + firstIteration : claim.metadata().get("plan.featureBranch");
        String target = claim == null ? "release/" + windowId : claim.metadata().get("plan.releaseBranch");
        return json.createObjectNode().put("method", "POST").put("suffix", "/merge_requests")
                .put("source_branch", source).put("target_branch", target)
                .put("title", " Merge \"" + source + "\" → " + target + " \n验证 ")
                .put("description", "说明 \"原值\"\n第二行\nReleaseHub-Operation: " + operationId)
                .put("remove_source_branch", false).put("sha", "aA".repeat(shaLength / 2));
    }

    private GitOperationJournalPort.Command createMrRequestCommand(RunPort.TaskClaim claim, String operationId,
            com.fasterxml.jackson.databind.node.ObjectNode target, String project) {
        String base = "https://gitlab.invalid";
        settings.saveGitLab(new SettingsPort.SettingsGitLab(base, "isolated-projection-token"));
        String repoId = claim == null ? firstRepo : claim.repo().value();
        var repository = repositories.findById(RepoId.of(repoId)).orElseThrow();
        String remote = GitOperationJournalPort.remoteKey("GITLAB", base, project);
        return new GitOperationJournalPort.Command(operationId, repoId,
                GitOperationJournalPort.mappingHash("GITLAB", repository.getCloneUrl()), remote, "GITLAB", base, project,
                "CREATE_MR", target.toString(), createMrRequestHash(remote, target),
                claim == null ? "cleanup-" + windowId : claim.runId(),
                claim == null ? "cleanup-operator" : claim.operator(),
                claim == null ? UUID.randomUUID().toString() : claim.fence().claimToken(),
                claim == null ? null : claim.fence());
    }

    private String createMrRequestHash(String remote, com.fasterxml.jackson.databind.node.ObjectNode target) {
        // Independent protocol oracle: no production canonicalizer, validation helper, or repaired missing fields.
        var body = json.createObjectNode();
        for (String key : List.of("source_branch", "target_branch", "title", "description", "remove_source_branch")) {
            if (target.has(key)) body.set(key, target.get(key));
        }
        return mergeIntentSha256("POST|" + remote + "|/merge_requests||"
                + mergeIntentSha256(body.toString()) + "|" + target.path("sha").asText());
    }

    private com.fasterxml.jackson.databind.node.ObjectNode createMrRequestProof(
            com.fasterxml.jackson.databind.node.ObjectNode target, String project) {
        long numericProject = Long.parseLong(project);
        return json.createObjectNode().put("httpStatus", 201).put("project_id", numericProject)
                .put("source_project_id", numericProject).put("target_project_id", numericProject)
                .put("iid", 11).put("source_branch", target.path("source_branch").textValue())
                .put("target_branch", target.path("target_branch").textValue())
                .put("sha", target.path("sha").textValue()).put("state", "opened")
                .put("web_url", "https://gitlab.invalid/project/-/merge_requests/11");
    }

    private Map<String, Object> createMrRequestNonTaskFacts(String operationId) {
        return Map.of("operation", jdbc.queryForMap("select * from git_operation where id=?", operationId),
                "events", jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence", operationId),
                "guard", projectionGuard(operationId));
    }

    private String createMrRequestInstallRollbackProbe(String operationId, int sequence) {
        // Deferred PG trigger verifies staged rows before aborting; sequence calls survive rollback as a reachability witness.
        String probe = "b8_mr_abort_" + UUID.randomUUID().toString().replace("-", "");
        String state = sequence == 0 ? "INTENT_RECORDED" : "SEND_STARTED";
        String reason = sequence == 0 ? "INTENT_PENDING" : "REQUEST_IN_FLIGHT";
        jdbc.execute("create sequence " + probe + "_reached");
        jdbc.execute("create function " + probe + "() returns trigger language plpgsql as $$ begin "
                + "if not exists (select 1 from git_operation o join repo_execution_guard g on g.remote_key=o.remote_key "
                + "where o.id=new.operation_id and o.state='" + state + "' and g.operation_id=o.id "
                + "and g.session_id=o.session_id and g.generation=o.generation and g.reason='" + reason + "' "
                + "and new.attempt_id=o.attempt_id and new.event_type='" + state + "') then "
                + "raise exception 'B8_PROBE_BEFORE_EXPECTED_WRITES'; end if; "
                + "perform nextval('" + probe + "_reached'); raise exception 'B8_ROLLBACK_AFTER_" + state + "'; end; $$");
        jdbc.execute("create constraint trigger " + probe + " after insert on git_operation_event "
                + "deferrable initially deferred for each row when (new.operation_id='" + operationId
                + "' and new.event_sequence=" + sequence + ") execute function " + probe + "()");
        return probe;
    }

    private void createMrRequestRemoveRollbackProbe(String probe) {
        jdbc.execute("drop trigger if exists " + probe + " on git_operation_event");
        jdbc.execute("drop function if exists " + probe + "()");
        jdbc.execute("drop sequence if exists " + probe + "_reached");
    }

    private String beginBranchIntent(RunPort.TaskClaim claim, int shaLength) {
        settings.saveGitLab(new SettingsPort.SettingsGitLab("https://gitlab.invalid", "isolated-projection-token"));
        return journal.begin(branchIntentCommand(claim, branchIntentTarget(claim, shaLength))).id();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode branchIntentTarget(RunPort.TaskClaim claim, int shaLength) {
        String branch = claim.metadata().get(claim.actionType() == ActionType.ENSURE_FEATURE
                ? "plan.featureBranch" : "plan.releaseBranch");
        return json.createObjectNode().put("method", "POST").put("suffix", "/repository/branches")
                .put("branch", branch).put("ref", "aA".repeat(shaLength / 2))
                .put("sourceRef", claim.metadata().get("plan.defaultBranch"));
    }

    private String branchIntentProject(RunPort.TaskClaim claim) {
        return claim.metadata().getOrDefault("git.projectId", projectionProjectId());
    }

    private GitOperationJournalPort.Command branchIntentCommand(RunPort.TaskClaim claim,
            com.fasterxml.jackson.databind.node.ObjectNode target) {
        String base = "https://gitlab.invalid";
        String project = branchIntentProject(claim);
        String remote = GitOperationJournalPort.remoteKey("GITLAB", base, project);
        var repository = repositories.findById(claim.repo()).orElseThrow();
        return new GitOperationJournalPort.Command(UUID.randomUUID().toString(), claim.repo().value(),
                GitOperationJournalPort.mappingHash("GITLAB", repository.getCloneUrl()), remote, "GITLAB", base, project,
                "CREATE_BRANCH", target.toString(), branchIntentHash(remote, target),
                claim.runId(), claim.operator(), claim.fence().claimToken(), claim.fence());
    }

    private String branchIntentHash(String remote, com.fasterxml.jackson.databind.node.ObjectNode target) {
        // Independent canonical request fixture: preserve supplied types and raw ref case; no production helper.
        var body = json.createObjectNode();
        for (String key : List.of("branch", "ref")) {
            if (target.has(key)) body.set(key, target.get(key));
        }
        return mergeIntentSha256("POST|" + remote + "|/repository/branches||"
                + mergeIntentSha256(body.toString()) + "|");
    }

    private RunPort.TaskClaim reclaimBranchIntent(RunPort.TaskClaim original) {
        var next = runs.claimNextTask(original.runId());
        assertThat(next).as("a complete expired unsent branch intent must yield its original Task").isPresent();
        return next.orElseThrow();
    }

    private java.util.LinkedHashMap<String, String> branchIntentPatch(RunPort.TaskClaim claim, String shape) {
        var patch = new java.util.LinkedHashMap<String, String>();
        if (shape.contains("PROJECT")) patch.put("git.projectId", branchIntentProject(claim));
        if (shape.contains("REASON")) patch.put("git.blockReason", "UNKNOWN");
        if (shape.equals("PROJECT_REASON")) patch.put("git.requestId", null);
        return patch;
    }

    private String beginMergeIntent(RunPort.TaskClaim claim) {
        settings.saveGitLab(new SettingsPort.SettingsGitLab("https://gitlab.invalid", "isolated-projection-token"));
        return journal.begin(mergeIntentCommand(claim, mergeIntentTarget(claim))).id();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode mergeIntentTarget(RunPort.TaskClaim claim) {
        return mergedMrTarget(claim)
                .put("merge_commit_message", "Merge " + claim.metadata().get("plan.featureBranch")
                        + " into " + claim.metadata().get("plan.releaseBranch"))
                .put("should_remove_source_branch", false);
    }

    private GitOperationJournalPort.Command mergeIntentCommand(RunPort.TaskClaim claim,
            com.fasterxml.jackson.databind.node.ObjectNode target) {
        String base = "https://gitlab.invalid";
        String project = claim.metadata().get("git.projectId");
        String remote = GitOperationJournalPort.remoteKey("GITLAB", base, project);
        var repository = repositories.findById(claim.repo()).orElseThrow();
        return new GitOperationJournalPort.Command(UUID.randomUUID().toString(), claim.repo().value(),
                GitOperationJournalPort.mappingHash("GITLAB", repository.getCloneUrl()), remote, "GITLAB", base, project,
                "MERGE_MR", target.toString(), mergeIntentHash(remote, target),
                claim.runId(), claim.operator(), claim.fence().claimToken(), claim.fence());
    }

    private String mergeIntentHash(String remote, com.fasterxml.jackson.databind.node.ObjectNode target) {
        // Independent fixture construction: retain supplied JSON types; never repair missing or legacy fields.
        var body = json.createObjectNode();
        for (String key : List.of("sha", "merge_commit_message", "should_remove_source_branch")) {
            if (target.has(key)) body.set(key, target.get(key));
        }
        return mergeIntentSha256(target.path("method").asText() + "|" + remote + "|"
                + target.path("suffix").asText() + "|null|" + mergeIntentSha256(body.toString()) + "|"
                + target.path("sha").asText());
    }

    private String mergeIntentSha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private RunPort.TaskClaim reclaimMergeIntent(RunPort.TaskClaim original) {
        var next = runs.claimNextTask(original.runId());
        assertThat(next).as("a complete expired unsent MERGE intent must yield its original task").isPresent();
        return next.orElseThrow();
    }

    private long mergeIntentVersion(String operationId) {
        return jdbc.queryForObject("select state_version from git_operation where id=?", Long.class, operationId);
    }

    private Map<String, Object> mergeIntentHistory(RunPort.TaskClaim claim, String operationId) {
        return Map.of(
                "task", jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId()),
                "operation", jdbc.queryForMap("select * from git_operation where id=?", operationId),
                "events", jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence", operationId),
                "guard", projectionGuard(operationId));
    }

    private void assertMergeIntentClaimRejected(String runId) {
        try {
            assertThat(runs.claimNextTask(runId)).as("invalid persisted qualification must not gain ownership").isEmpty();
        } catch (BusinessException rejected) {
            // Invalid protocol/identity may reject before the candidate loop; all persisted facts are checked by the caller.
            assertThat(rejected.getCode()).isNotBlank();
        }
    }

    private void mergeIntentSending(String operationId, RunPort.TaskClaim caller) {
        journal.sending(operationId, caller == null ? null : caller.fence());
    }

    private void mergeIntentFinish(String operationId, RunPort.TaskClaim caller,
            GitWriteResult.Outcome outcome, String evidence) {
        journal.finish(operationId, caller == null ? null : caller.fence(), outcome, evidence,
                outcome == GitWriteResult.Outcome.UNKNOWN ? "UNKNOWN" : null);
    }

    private void awaitMergeIntentLatch(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("merge intent transaction was not released");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private void awaitMergeIntentBlockers(int waiter, Set<Integer> expectedBlockers) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<Integer> blockers = List.of();
        while (System.nanoTime() < deadline) {
            blockers = jdbc.queryForList("select unnest(pg_blocking_pids(?))", Integer.class, waiter);
            if (blockers.stream().anyMatch(expectedBlockers::contains)) break;
            Thread.sleep(10);
        }
        assertThat(blockers.stream().anyMatch(expectedBlockers::contains))
                .as("PostgreSQL must report waiter %s blocked by %s, actual %s", waiter, expectedBlockers, blockers).isTrue();
        System.out.printf("[merge-intent-pg-lock] waiter=%d blockers=%s expected=%s%n", waiter, blockers, expectedBlockers);
    }

    private int awaitMergeIntentWaiter(int holder) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<Integer> waiters = List.of();
        while (System.nanoTime() < deadline) {
            waiters = jdbc.queryForList("select pid from pg_stat_activity where ? = any(pg_blocking_pids(pid))",
                    Integer.class, holder);
            if (!waiters.isEmpty()) break;
            Thread.sleep(10);
        }
        assertThat(waiters).as("the old sender must actually wait for the new owner's PostgreSQL transaction").hasSize(1);
        return waiters.getFirst();
    }

    private RunPort.TaskClaim claimMergedMrTask(String disposition, int shaLength) {
        var ensure = claimMrTask();
        completeMergedMrFixtureTask(ensure, disposition, shaLength);
        var claim = runs.claimNextTask(ensure.runId()).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        return claim;
    }

    private RunPort.TaskClaim claimMergedMrTask(String runId, String disposition, int shaLength) {
        var claim = runs.claimNextTask(runId).orElseThrow();
        String itemId = claim.itemId();
        for (int index = 0; index < 3 && claim.actionType() != ActionType.TRY_MERGE; index++) {
            completeMergedMrFixtureTask(claim, disposition, shaLength);
            claim = runs.claimNextTask(runId).orElseThrow();
            assertThat(claim.itemId()).isEqualTo(itemId);
        }
        assertThat(claim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        return claim;
    }

    private void completeMergedMrFixtureTask(RunPort.TaskClaim claim, String disposition, int shaLength) {
        if (claim.actionType() == ActionType.ENSURE_FEATURE || claim.actionType() == ActionType.ENSURE_RELEASE) {
            String project = claim.metadata().getOrDefault("git.projectId",
                    Long.toString(Long.parseLong(projectionProjectId()) + (claim.repo().value().equals(firstRepo) ? 0 : 1)));
            runs.completeTask(claim, step(claim, RunItemResult.ALREADY_EXISTS), Map.of("git.projectId", project));
        } else if (claim.actionType() == ActionType.ENSURE_MR) {
            var checkpoint = new java.util.LinkedHashMap<>(createdMrCheckpoint(claim));
            checkpoint.put("git.projectId", claim.metadata().get("git.projectId"));
            checkpoint.put("git.sourceSha", "A".repeat(shaLength));
            String iid = claim.metadata().get("plan.featureBranch").equals("feature/" + secondIteration) ? "12" : "11";
            checkpoint.put("git.mrIid", iid);
            checkpoint.put("git.mrUrl", "https://gitlab.invalid/project/-/merge_requests/" + iid);
            checkpoint.put("git.mrDisposition", disposition);
            runs.completeTask(claim, step(claim, RunItemResult.valueOf(disposition)), checkpoint);
        } else if (claim.actionType() == ActionType.TRY_MERGE) {
            runs.completeTask(claim, step(claim, RunItemResult.MERGED),
                    mergedMrResultPatch(claim.metadata().get("git.mrDisposition"), "b".repeat(shaLength)));
        } else {
            throw new AssertionError(claim.actionType());
        }
    }

    private String beginMergedMrOperation(RunPort.TaskClaim claim) {
        String baseUrl = "https://gitlab.invalid";
        settings.saveGitLab(new SettingsPort.SettingsGitLab(baseUrl, "isolated-projection-token"));
        var repo = repositories.findById(claim.repo()).orElseThrow();
        String project = claim.metadata().get("git.projectId");
        String target = mergedMrTarget(claim).toString();
        return journal.begin(new GitOperationJournalPort.Command(UUID.randomUUID().toString(),
                claim.repo().value(), GitOperationJournalPort.mappingHash("GITLAB", repo.getCloneUrl()),
                GitOperationJournalPort.remoteKey("GITLAB", baseUrl, project), "GITLAB", baseUrl,
                project, "MERGE_MR", target, GitOperationJournalPort.mappingHash("MERGE_MR", target),
                claim.runId(), claim.operator(), claim.fence().claimToken(), claim.fence())).id();
    }

    private String recordAppliedMergedMr(RunPort.TaskClaim claim, String mergeSha) {
        String operationId = beginMergedMrOperation(claim);
        journal.sending(operationId, claim.fence());
        journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.APPLIED, mergedMrProof(claim, mergeSha).toString(), null);
        return operationId;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode mergedMrTarget(RunPort.TaskClaim claim) {
        return json.createObjectNode().put("method", "PUT")
                .put("suffix", "/merge_requests/" + claim.metadata().get("git.mrIid") + "/merge")
                .put("sha", claim.metadata().get("git.sourceSha"));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode mergedMrProof(RunPort.TaskClaim claim, String mergeSha) {
        long project = Long.parseLong(claim.metadata().get("git.projectId"));
        var proof = json.createObjectNode().put("httpStatus", 200)
                .put("project_id", project).put("source_project_id", project).put("target_project_id", project)
                .put("iid", Long.parseLong(claim.metadata().get("git.mrIid")))
                .put("source_branch", claim.metadata().get("git.sourceBranch"))
                .put("target_branch", claim.metadata().get("git.targetBranch"))
                .put("sha", claim.metadata().get("git.sourceSha").toLowerCase(java.util.Locale.ROOT))
                .put("state", "merged").put("web_url", claim.metadata().get("git.mrUrl"));
        if (mergeSha == null) proof.putNull("merge_commit_sha");
        else proof.put("merge_commit_sha", mergeSha);
        return proof;
    }

    private Map<String, String> mergedMrResultPatch(String disposition, String mergeSha) {
        var patch = new java.util.LinkedHashMap<String, String>();
        patch.put("git.mrDisposition", disposition);
        patch.put("git.mergeSha", mergeSha);
        patch.put("git.blockReason", null);
        patch.put("git.requestId", null);
        return patch;
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
        var target = branchIntentTarget(claim, 40).put("ref", sha);
        String operationId = journal.begin(new GitOperationJournalPort.Command(UUID.randomUUID().toString(),
                claim.repo().value(), GitOperationJournalPort.mappingHash("GITLAB", repo.getCloneUrl()),
                GitOperationJournalPort.remoteKey("GITLAB", baseUrl, projectionProjectId()), "GITLAB", baseUrl,
                projectionProjectId(), "CREATE_BRANCH", target.toString(),
                branchIntentHash(GitOperationJournalPort.remoteKey("GITLAB", baseUrl, projectionProjectId()), target),
                claim.runId(), claim.operator(), claim.fence().claimToken(), claim.fence())).id();
        journal.sending(operationId, claim.fence());
        // Case-insensitive SHA identity follows strict SHA validation; branch and sourceRef remain exact.
        journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.APPLIED,
                branchProof(branch, sha.toUpperCase(java.util.Locale.ROOT)), null);
        return operationId;
    }

    private RunPort.TaskClaim claimMrTask() {
        var run = admit(List.of(firstRepo), List.of(firstIteration), true);
        for (ActionType action : List.of(ActionType.ENSURE_FEATURE, ActionType.ENSURE_RELEASE)) {
            var prefix = runs.claimNextTask(run.getId().value()).orElseThrow();
            assertThat(prefix.actionType()).isEqualTo(action);
            runs.completeTask(prefix, step(prefix, RunItemResult.ALREADY_EXISTS),
                    Map.of("git.projectId", projectionProjectId()));
        }
        String itemId = run.getItems().getFirst().getId().value();
        // Isolated stale result fields must be removed by the same null patch as normal ENSURE_MR.
        for (var entry : Map.of("git.mergeSha", "b".repeat(40), "git.blockReason", "PIPELINE_PENDING",
                "git.requestId", "previous-request").entrySet()) {
            assertThat(jdbc.update(
                    "insert into run_item_metadata(run_item_id,metadata_key,metadata_value) values (?,?,?)",
                    itemId, entry.getKey(), entry.getValue())).isEqualTo(1);
        }
        var claim = runs.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.ENSURE_MR);
        return claim;
    }

    private String recordAppliedCreatedMr(RunPort.TaskClaim claim) {
        String operationId = UUID.randomUUID().toString();
        var request = createMrRequestTarget(claim, operationId, 40).put("sha", "a".repeat(40));
        journal.begin(createMrRequestCommand(claim, operationId, request, projectionProjectId()));
        journal.sending(operationId, claim.fence());
        journal.finish(operationId, claim.fence(), GitWriteResult.Outcome.APPLIED, createdMrProof(claim).toString(), null);
        // Historical B3 projection keeps its original summary/hash; only fresh sending requires the complete request.
        String target = createdMrTarget(claim).toString();
        assertThat(jdbc.update("update git_operation set target_json=?,request_hash=? where id=?",
                target, GitOperationJournalPort.mappingHash("CREATE_MR", target), operationId)).isEqualTo(1);
        return operationId;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode createdMrTarget(RunPort.TaskClaim claim) {
        return json.createObjectNode().put("source_branch", claim.metadata().get("plan.featureBranch"))
                .put("target_branch", claim.metadata().get("plan.releaseBranch")).put("sha", "a".repeat(40));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode createdMrProof(RunPort.TaskClaim claim) {
        long projectId = Long.parseLong(projectionProjectId());
        return json.createObjectNode().put("httpStatus", 201)
                .put("project_id", projectId).put("source_project_id", projectId).put("target_project_id", projectId)
                .put("iid", 11).put("source_branch", claim.metadata().get("plan.featureBranch"))
                .put("target_branch", claim.metadata().get("plan.releaseBranch"))
                .put("sha", "a".repeat(40)).put("state", "opened")
                .put("web_url", "https://gitlab.invalid/project/-/merge_requests/11");
    }

    private Map<String, String> createdMrCheckpoint(RunPort.TaskClaim claim) {
        return Map.of("git.projectId", projectionProjectId(),
                "git.sourceBranch", claim.metadata().get("plan.featureBranch"),
                "git.targetBranch", claim.metadata().get("plan.releaseBranch"),
                "git.mrIid", "11", "git.mrUrl", "https://gitlab.invalid/project/-/merge_requests/11",
                "git.sourceSha", "a".repeat(40), "git.mrDisposition", "CREATED");
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

    private Map<String, Object> projectionFacts(RunPort.TaskClaim claim, String operationId) {
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
