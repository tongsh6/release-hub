package io.releasehub.application.run;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.conflict.ConflictDetectionAppService;
import io.releasehub.application.gitoperation.GitOperationContext;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.plan.RunPlanAppService;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.version.VersionUpdateAppService;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.run.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只验证协调器的动作边界；任务顺序、失败传播和 CAS 由真实存储测试负责。 */
class RunOrchestrationTest {
    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");
    private static final String WINDOW = "window-1";
    private static final String KEY = "orchestrate-intent-1";
    private static final String URL = "https://gitlab.example/group/repository.git";
    private static final String SOURCE = "feature/I1";
    private static final String TARGET = "release/W1";
    private static final String SHA = "a".repeat(40);
    private final RunPort runs = mock(RunPort.class);
    private final RunPlanAppService planner = mock(RunPlanAppService.class);
    private final CodeRepositoryPort repositories = mock(CodeRepositoryPort.class);
    private final GitBranchAdapterFactory adapters = mock(GitBranchAdapterFactory.class);
    private final GitBranchPort git = mock(GitBranchPort.class);
    private final BranchRuleUseCase rules = mock(BranchRuleUseCase.class);
    private final Map<String, String> facts = new LinkedHashMap<>();
    private final Deque<ActionType> pending = new ArrayDeque<>();
    private final List<RunStep> committed = new ArrayList<>();
    private final Map<ActionType, RunPort.TaskFence> fences = new EnumMap<>(ActionType.class);
    private Run run;
    private RunItem item;
    private RunAppService service;
    private boolean stopped;

    @BeforeEach
    void setUp() {
        item = RunItem.create("W1", RepoId.of("r1"), IterationKey.of("I1"), 1, NOW);
        run = Run.rehydrate(RunId.generate(RunType.WINDOW_ORCHESTRATION, NOW),
                RunType.WINDOW_ORCHESTRATION, "admitted-actor", NOW, null,
                List.of(item), NOW, NOW, 0L, 3, RunExecutionStatus.RUNNING);
        service = new RunAppService(runs, mock(ReleaseWindowPort.class), mock(WindowIterationPort.class),
                repositories, adapters, mock(VersionUpdateAppService.class),
                mock(ConflictDetectionAppService.class), mock(SettingsPort.class),
                Clock.fixed(NOW, ZoneOffset.UTC), planner, rules);
        when(planner.admit(WINDOW, List.of("r1"), List.of("I1"), true, "admitted-actor", KEY))
                .thenReturn(new RunPlanAppService.OrchestrationAdmission(run.getId().value()));
        when(runs.findById(run.getId().value())).thenReturn(Optional.of(run));
        facts.putAll(Map.of("plan.windowId", WINDOW, "plan.featureBranch", SOURCE,
                "plan.releaseBranch", TARGET, "plan.defaultBranch", "main",
                "plan.featureBranchMode", "AUTO", "plan.cloneUrl", URL,
                "plan.gitProvider", "GITLAB", "plan.failFast", "true"));
        pending.addAll(List.of(ActionType.ENSURE_FEATURE, ActionType.ENSURE_RELEASE,
                ActionType.ENSURE_MR, ActionType.TRY_MERGE));
        for (ActionType action : pending) fences.put(action,
                new RunPort.TaskFence(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        when(runs.claimNextTask(run.getId().value())).thenAnswer(invocation -> {
            if (stopped || pending.isEmpty()) return Optional.empty();
            var action = pending.removeFirst();
            return Optional.of(new RunPort.TaskClaim(fences.get(action), run.getId().value(),
                    item.getId().value(), item.getRepo(), action, NOW, run.getOperator(), Map.copyOf(facts)));
        });
        when(runs.completeTask(any(), any(), any())).thenAnswer(invocation -> commit(
                invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(repositories.findById(item.getRepo())).thenReturn(Optional.of(CodeRepository.rehydrate(
                item.getRepo(), "repository", URL, "main", "G1", RepoType.SERVICE, GitProvider.GITLAB,
                "token", false, 0, 0, 0, 0, 0, 0, 0, null, NOW, NOW, 0L)));
        when(adapters.getAdapter(GitProvider.GITLAB)).thenReturn(git);
        when(rules.isCompliant(anyString(), anyString(), anyString())).thenReturn(true);
        when(git.getBranchStatus(any(), any(), any())).thenReturn(GitBranchPort.BranchStatus.present("17", SHA));
        when(git.ensureMergeRequest(any(), any(), any(), any(), any()))
                .thenReturn(GitWriteResult.applied(evidence(GitBranchPort.MrDisposition.CREATED)));
        when(git.tryMergeRequest(any(), any(), any(), any()))
                .thenReturn(GitBranchPort.MergeResult.success()
                        .withEvidence(mergedEvidence()));
    }

    @Test
    void aFreshAdmissionReturnsItsReceiptWithoutClaimingOrCallingGit() {
        assertThat(service.startOrchestrate(WINDOW, List.of("r1"), List.of("I1"),
                true, "admitted-actor", KEY)).isSameAs(run);
        assertThat(committed).isEmpty();
        verify(runs, never()).claimNextTask(any());
        verifyNoInteractions(git);
    }

    @Test
    void aRetryAdmissionReturnsItsReceiptWithoutClaimingOrCallingGit() {
        var retryRun = Run.rehydrate(RunId.generate(RunType.WINDOW_ORCHESTRATION, NOW),
                RunType.WINDOW_ORCHESTRATION, "admitted-actor", NOW, null,
                List.of(), NOW, NOW, 0L, 3, RunExecutionStatus.RUNNING);
        var selected = List.of(item.getId().value());
        when(planner.admitRetry(run, selected, "admitted-actor", "retry-intent"))
                .thenReturn(new RunPlanAppService.OrchestrationAdmission(retryRun.getId().value()));
        when(runs.findById(retryRun.getId().value())).thenReturn(Optional.of(retryRun));

        assertThat(service.retry(run.getId().value(), selected, "admitted-actor", "retry-intent"))
                .isSameAs(retryRun);
        verify(runs, never()).claimNextTask(any());
        verifyNoInteractions(git);
    }

    @Test
    void anAppliedProjectionIsTheOnlyTaskCompletedByAnInvocation() {
        when(runs.projectNextAppliedTask(run.getId().value())).thenReturn(true);

        assertThat(service.executeNextTask(run.getId().value())).isTrue();

        verify(runs).projectNextAppliedTask(run.getId().value());
        verify(runs, never()).claimNextTask(any());
        verify(runs, never()).completeTask(any(), any(), any());
        verifyNoInteractions(repositories, adapters, git);
        assertThat(pending).hasSize(4);
    }

    @Test
    void projectionFailureCannotFallThroughToAClaimOrGitExecution() {
        var failure = new IllegalStateException("projection transaction failed");
        when(runs.projectNextAppliedTask(run.getId().value())).thenThrow(failure);

        assertThatThrownBy(() -> service.executeNextTask(run.getId().value())).isSameAs(failure);

        verify(runs, never()).claimNextTask(any());
        verify(runs, never()).completeTask(any(), any(), any());
        verifyNoInteractions(repositories, adapters, git);
        assertThat(pending).hasSize(4);
    }

    @Test
    void oneInvocationCommitsOneTaskAndLeavesTheRestUnclaimed() {
        assertThat(service.executeNextTask(run.getId().value())).isTrue();
        assertThat(committed).extracting(RunStep::actionType).containsExactly(ActionType.ENSURE_FEATURE);
        assertThat(pending).containsExactly(ActionType.ENSURE_RELEASE, ActionType.ENSURE_MR, ActionType.TRY_MERGE);
        verify(runs, times(1)).claimNextTask(run.getId().value());
        verify(git, never()).ensureMergeRequest(any(), any(), any(), any(), any());
        verify(git, never()).tryMergeRequest(any(), any(), any(), any());
        assertThat(GitOperationContext.taskFence()).isNull();
    }

    @Test
    void noEligibleTaskReturnsFalseWithoutReconstructingThePlan() {
        pending.clear();
        assertThat(service.executeNextTask(run.getId().value())).isFalse();
        verifyNoInteractions(git, planner);
        verify(runs, never()).completeTask(any(), any(), any());
    }

    @Test
    void commitsEachActionBeforeTheNextAndNeverUsesTheCompoundMergePath() {
        when(git.tryMergeRequest(any(), any(), any(), any())).thenAnswer(invocation -> {
            assertThat(committed).extracting(RunStep::actionType)
                    .containsExactly(ActionType.ENSURE_FEATURE, ActionType.ENSURE_RELEASE, ActionType.ENSURE_MR);
            assertThat(invocation.<GitBranchPort.MrEvidence>getArgument(2).iid()).isEqualTo("7");
            assertThat(GitOperationContext.operator()).isEqualTo("admitted-actor");
            assertThat(GitOperationContext.taskFence()).isEqualTo(fences.get(ActionType.TRY_MERGE));
            return GitBranchPort.MergeResult.success().withEvidence(mergedEvidence());
        });

        execute();

        assertThat(committed).extracting(RunStep::actionType).containsExactly(
                ActionType.ENSURE_FEATURE, ActionType.ENSURE_RELEASE, ActionType.ENSURE_MR, ActionType.TRY_MERGE);
        assertThat(committed).extracting(RunStep::result).containsExactly(
                RunItemResult.ALREADY_EXISTS, RunItemResult.ALREADY_EXISTS, RunItemResult.CREATED, RunItemResult.MERGED);
        verify(git, never()).mergeBranch(any(), any(), any(), any(), any());
        verify(runs, never()).save(any());
        assertThat(GitOperationContext.runId()).isNull();
        assertThat(GitOperationContext.taskFence()).isNull();
    }

    @Test
    void aRepeatedAdmissionReturnsItsReceiptWithoutClaimingOrCallingGit() {
        when(planner.admit(WINDOW, List.of("r1"), List.of("I1"), true, "admitted-actor", KEY))
                .thenReturn(new RunPlanAppService.OrchestrationAdmission(run.getId().value()));
        assertThat(service.startOrchestrate(WINDOW, List.of("r1"), List.of("I1"),
                true, "admitted-actor", KEY)).isSameAs(run);
        verify(runs).findById(run.getId().value());
        verify(runs, never()).claimNextTask(any());
        verifyNoInteractions(git);
    }

    @Test
    void aFailedMrCheckpointCommitStopsBeforeTryMergeAndLeavesTheFailureVisible() {
        doAnswer(invocation -> {
            RunPort.TaskClaim claim = invocation.getArgument(0);
            if (claim.actionType() == ActionType.ENSURE_MR) throw new IllegalStateException("checkpoint commit failed");
            return commit(claim, invocation.getArgument(1), invocation.getArgument(2));
        }).when(runs).completeTask(any(), any(), any());

        assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class)
                .hasMessage("checkpoint commit failed");

        verify(git, times(1)).ensureMergeRequest(any(), any(), any(), any(), any());
        verify(git, never()).tryMergeRequest(any(), any(), any(), any());
        assertThat(committed).extracting(RunStep::actionType)
                .containsExactly(ActionType.ENSURE_FEATURE, ActionType.ENSURE_RELEASE);
        assertThat(GitOperationContext.taskFence()).isNull();
    }

    @Test
    void aFailedBranchCheckpointCommitStopsBeforeAnyMrAction() {
        doThrow(new IllegalStateException("database unavailable")).when(runs).completeTask(any(), any(), any());
        assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class);
        verify(git, never()).ensureMergeRequest(any(), any(), any(), any(), any());
        verify(git, never()).tryMergeRequest(any(), any(), any(), any());
    }

    @Test
    void tryMergeConsumesThePersistedCheckpointWithoutRecreatingTheMr() {
        pending.clear();
        pending.add(ActionType.TRY_MERGE);
        checkpoint(evidence(GitBranchPort.MrDisposition.MR_REUSED));

        execute();

        verify(git, never()).ensureMergeRequest(any(), any(), any(), any(), any());
        verify(git).tryMergeRequest(eq(URL), eq("token"),
                eq(evidence(GitBranchPort.MrDisposition.MR_REUSED)), anyString());
        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.MERGED);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 64})
    void tryMergeAcceptsEquivalentSourceShaWithoutReplacingTheCheckpoint(int length) {
        pending.clear();
        pending.add(ActionType.TRY_MERGE);
        String originalSha = "ABCD".repeat(length / 4);
        String observedSha = originalSha.toLowerCase(Locale.ROOT);
        String mergeSha = "e".repeat(length);
        String mrUrl = "https://gitlab.example/group/repository/-/merge_requests/7";
        var original = new GitBranchPort.MrEvidence("17", SOURCE, TARGET, "7", mrUrl,
                originalSha, null, GitBranchPort.MrDisposition.CREATED, null);
        checkpoint(original);
        var expectedFacts = new LinkedHashMap<>(facts);
        expectedFacts.put("git.mergeSha", mergeSha);
        when(git.tryMergeRequest(any(), any(), any(), any())).thenReturn(GitBranchPort.MergeResult.success()
                .withEvidence(new GitBranchPort.MrEvidence("17", SOURCE, TARGET, "7", mrUrl,
                        observedSha, mergeSha, GitBranchPort.MrDisposition.CREATED, null)));

        execute();

        assertThat(committed).singleElement().satisfies(step -> {
            assertThat(step.actionType()).isEqualTo(ActionType.TRY_MERGE);
            assertThat(step.result()).isEqualTo(RunItemResult.MERGED);
        });
        assertThat(facts).isEqualTo(expectedFacts);
        verify(git).tryMergeRequest(eq(URL), eq("token"), eq(original), anyString());
        verify(git, never()).ensureMergeRequest(any(), any(), any(), any(), any());
        verify(git, never()).mergeBranch(any(), any(), any(), any(), any());
    }

    @Test
    void anIncompletePersistedMrCheckpointCannotAuthorizeMerge() {
        pending.clear();
        pending.add(ActionType.TRY_MERGE);
        checkpoint(evidence(GitBranchPort.MrDisposition.MR_REUSED));
        facts.remove("git.sourceSha");

        execute();

        verify(git, never()).tryMergeRequest(any(), any(), any(), any());
        verify(git, never()).ensureMergeRequest(any(), any(), any(), any(), any());
        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.UNKNOWN);
    }

    @Test
    void incompleteEnsureEvidenceIsUnknownAndCannotAuthorizeTryMerge() {
        when(git.ensureMergeRequest(any(), any(), any(), any(), any()))
                .thenReturn(GitWriteResult.applied(new GitBranchPort.MrEvidence(
                        "17", SOURCE, TARGET, "7", null, SHA, null, GitBranchPort.MrDisposition.CREATED, null)));

        execute();

        assertThat(committed.getLast().result()).isEqualTo(RunItemResult.UNKNOWN);
        verify(git, never()).tryMergeRequest(any(), any(), any(), any());
    }

    @Test
    void anExceptionAfterMrCreationIsUnknownAndNeverContinues() {
        when(git.ensureMergeRequest(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("journal failed after remote acceptance"));

        execute();

        assertThat(committed.getLast().result()).isEqualTo(RunItemResult.UNKNOWN);
        verify(git, never()).tryMergeRequest(any(), any(), any(), any());
    }

    @Test
    void anUnknownTryResponsePreservesThePersistedMrIdentity() {
        pending.clear();
        pending.add(ActionType.TRY_MERGE);
        checkpoint(evidence(GitBranchPort.MrDisposition.MR_REUSED));
        when(git.tryMergeRequest(any(), any(), any(), any()))
                .thenReturn(GitBranchPort.MergeResult.unknown("response lost"));

        execute();

        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.UNKNOWN);
        assertThat(facts).containsEntry("git.projectId", "17").containsEntry("git.mrIid", "7")
                .containsEntry("git.sourceSha", SHA).containsEntry("git.blockReason", "UNKNOWN");
    }

    @Test
    void successForAnotherMrCannotReplaceThePersistedIdentity() {
        pending.clear();
        pending.add(ActionType.TRY_MERGE);
        checkpoint(evidence(GitBranchPort.MrDisposition.MR_REUSED));
        when(git.tryMergeRequest(any(), any(), any(), any())).thenReturn(GitBranchPort.MergeResult.success()
                .withEvidence(new GitBranchPort.MrEvidence("17", SOURCE, TARGET, "8",
                        "https://gitlab.example/group/repository/-/merge_requests/8",
                        SHA, "b".repeat(40), GitBranchPort.MrDisposition.MR_REUSED, null)));

        execute();

        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.UNKNOWN);
        assertThat(facts).containsEntry("git.projectId", "17").containsEntry("git.mrIid", "7");
    }

    @Test
    void containmentIsConfirmedByTryAndDoesNotInventAMergeRequest() {
        var contained = new GitBranchPort.MrEvidence("17", SOURCE, TARGET, null, null, SHA, null,
                GitBranchPort.MrDisposition.ALREADY_MERGED, null);
        when(git.ensureMergeRequest(any(), any(), any(), any(), any())).thenReturn(GitWriteResult.applied(contained));
        when(git.tryMergeRequest(any(), any(), any(), any()))
                .thenReturn(GitBranchPort.MergeResult.success().withEvidence(contained));

        execute();

        assertThat(committed.get(2).result()).isEqualTo(RunItemResult.ALREADY_MERGED);
        assertThat(committed.get(3).result()).isEqualTo(RunItemResult.ALREADY_MERGED);
        verify(git).tryMergeRequest(eq(URL), eq("token"), eq(contained), anyString());
    }

    @Test
    void aDeletedInheritedFeatureIsNotRecreatedWhileEnsuringRelease() {
        pending.clear();
        pending.add(ActionType.ENSURE_RELEASE);
        facts.put("retry.sourceRunId", "previous-run");
        when(git.getBranchStatus(URL, "token", SOURCE)).thenReturn(GitBranchPort.BranchStatus.missing("17"));

        execute();

        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.FAILED);
        verify(git, never()).createBranch(any(), any(), any(), any());
    }

    @Test
    void existingFeatureOwnershipNeverAuthorizesRecreation() {
        facts.put("plan.featureBranchMode", "EXISTING");
        when(git.getBranchStatus(URL, "token", SOURCE)).thenReturn(GitBranchPort.BranchStatus.missing("17"));

        execute();

        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.FAILED);
        verify(git, never()).createBranch(any(), any(), any(), any());
    }

    @Test
    void aChangedRepositoryMappingCannotWriteGit() {
        facts.put("plan.cloneUrl", "https://gitlab.example/other/repository.git");
        execute();
        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.FAILED);
        verifyNoInteractions(git);
    }

    @Test
    void aMissingClaimDoesNotReplayThePlanFromMemory() {
        pending.clear();
        assertThat(execute()).isSameAs(run);
        verifyNoInteractions(git);
        verify(runs, never()).save(any());
    }

    @Test
    void aReadOnlyFeaturePinsTheProjectBeforeTheNextTaskCanWrite() {
        when(git.getBranchStatus(URL, "token", SOURCE))
                .thenReturn(GitBranchPort.BranchStatus.present("17", SHA),
                        GitBranchPort.BranchStatus.present("18", SHA));

        execute();

        assertThat(committed).extracting(RunStep::result)
                .containsExactly(RunItemResult.ALREADY_EXISTS, RunItemResult.FAILED);
        assertThat(facts).containsEntry("git.projectId", "17");
        verify(git, never()).createBranch(any(), any(), any(), any());
        verify(git, never()).ensureMergeRequest(any(), any(), any(), any(), any());
    }

    @Test
    void aMissingBranchWithoutProjectIdentityCannotAuthorizeCreate() {
        pending.clear();
        pending.add(ActionType.ENSURE_FEATURE);
        when(git.getBranchStatus(URL, "token", SOURCE)).thenReturn(GitBranchPort.BranchStatus.missing(null));

        execute();

        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.FAILED);
        verify(git, never()).createBranch(any(), any(), any(), any());
        assertThat(facts).doesNotContainKey("git.projectId");
    }

    @Test
    void aMissingBranchPinsItsProjectBeforeTheFirstWrite() {
        pending.clear();
        pending.add(ActionType.ENSURE_FEATURE);
        when(git.getBranchStatus(URL, "token", SOURCE)).thenReturn(GitBranchPort.BranchStatus.missing("17"));
        when(git.createBranch(URL, "token", SOURCE, "main")).thenAnswer(invocation -> {
            assertThat(GitOperationContext.expectedProjectId()).isEqualTo("17");
            return true;
        });

        execute();

        assertThat(committed).singleElement().extracting(RunStep::result).isEqualTo(RunItemResult.CREATED);
        assertThat(facts).containsEntry("git.projectId", "17");
        assertThat(GitOperationContext.expectedProjectId()).isNull();
    }

    @Test
    void anMrFromAnotherProjectCannotReplaceThePriorReadEvidence() {
        when(git.ensureMergeRequest(any(), any(), any(), any(), any()))
                .thenReturn(GitWriteResult.applied(new GitBranchPort.MrEvidence(
                        "18", SOURCE, TARGET, "7", "https://gitlab.example/other/project/-/merge_requests/7",
                        SHA, null, GitBranchPort.MrDisposition.CREATED, null)));

        execute();

        assertThat(committed.getLast().result()).isEqualTo(RunItemResult.UNKNOWN);
        assertThat(facts).containsEntry("git.projectId", "17").doesNotContainKey("git.mrIid");
        verify(git, never()).tryMergeRequest(any(), any(), any(), any());
    }

    private Run execute() {
        for (int i = 0; i < 5; i++) {
            if (!service.executeNextTask(run.getId().value())) return run;
        }
        throw new AssertionError("The fixed four-action fixture did not stop");
    }

    private Run commit(RunPort.TaskClaim claim, RunStep step, Map<String, String> updates) {
        assertThat(step.actionType()).isEqualTo(claim.actionType());
        committed.add(step);
        updates.forEach((key, value) -> { if (value == null) facts.remove(key); else facts.put(key, value); });
        stopped = step.result().isFailure() || step.result() == RunItemResult.UNKNOWN;
        return run;
    }

    private GitBranchPort.MrEvidence evidence(GitBranchPort.MrDisposition disposition) {
        return new GitBranchPort.MrEvidence("17", SOURCE, TARGET, "7",
                "https://gitlab.example/group/repository/-/merge_requests/7", SHA, null, disposition, null);
    }

    private GitBranchPort.MrEvidence mergedEvidence() {
        return new GitBranchPort.MrEvidence("17", SOURCE, TARGET, "7",
                "https://gitlab.example/group/repository/-/merge_requests/7", SHA, "b".repeat(40),
                GitBranchPort.MrDisposition.MR_REUSED, null);
    }

    private void checkpoint(GitBranchPort.MrEvidence evidence) {
        facts.put("git.projectId", evidence.projectId());
        facts.put("git.sourceBranch", evidence.sourceBranch());
        facts.put("git.targetBranch", evidence.targetBranch());
        if (evidence.iid() != null) facts.put("git.mrIid", evidence.iid());
        if (evidence.url() != null) facts.put("git.mrUrl", evidence.url());
        facts.put("git.sourceSha", evidence.sourceSha());
        if (evidence.mergeSha() != null) facts.put("git.mergeSha", evidence.mergeSha());
        facts.put("git.mrDisposition", evidence.disposition().name());
    }
}