package io.releasehub.bootstrap.api;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunPort;
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

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
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
        var run = runs.findById(plans.admit(windowId, List.of(firstRepo), List.of(firstIteration), true, "operator", java.util.UUID.randomUUID().toString()).runId()).orElseThrow();
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
        assertThat(admission.created()).isTrue();
        assertThat(runs.findTasks(retry.getId().value()).stream().map(RunPort.TaskView::status))
                .containsExactly("INHERITED", "INHERITED", "INHERITED", "PENDING");
        assertThat(runs.findById(retry.getId().value()).orElseThrow().getItems().getFirst().getSteps()).isEmpty();
        var retryMerge = runs.claimNextTask(retry.getId().value()).orElseThrow();
        assertThat(retryMerge.actionType()).isEqualTo(ActionType.TRY_MERGE);
        assertThat(retryMerge.metadata()).containsEntry("git.projectId", "17")
                .containsEntry("git.mrIid", "11").containsEntry("git.sourceSha", "a".repeat(40));
        completeSuccess(retryMerge);
        assertThat(runs.findById(retry.getId().value()).orElseThrow().getItems().getFirst().getSteps()).hasSize(1);
        assertThat(plans.admitRetry(previous, List.of(merge.itemId()), "retry-operator", key).created()).isFalse();
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
