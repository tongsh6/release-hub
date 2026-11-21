package io.releasehub.application.run;

import io.releasehub.application.conflict.ConflictDetectionAppService;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.version.VersionDeriverUseCase;
import io.releasehub.application.version.VersionUpdateAppService;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.conflict.ConflictReport;
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
import io.releasehub.domain.run.*;
import io.releasehub.domain.window.WindowIteration;
import io.releasehub.domain.window.WindowIterationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RunOrchestrationTest {
    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");
    private static final String WINDOW = "window-1";
    private static final String RELEASE = "release/W1";
    private final RunPort runs = mock(RunPort.class);
    private final ReleaseWindowPort windows = mock(ReleaseWindowPort.class);
    private final WindowIterationPort bindings = mock(WindowIterationPort.class);
    private final IterationPort iterations = mock(IterationPort.class);
    private final IterationRepoPort versions = mock(IterationRepoPort.class);
    private final CodeRepositoryPort repos = mock(CodeRepositoryPort.class);
    private final GitBranchAdapterFactory adapters = mock(GitBranchAdapterFactory.class);
    private final GitBranchPort git = mock(GitBranchPort.class);
    private final ConflictDetectionAppService conflicts = mock(ConflictDetectionAppService.class);
    private final SettingsPort settings = mock(SettingsPort.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final io.releasehub.application.branchrule.BranchRuleUseCase rules = mock(io.releasehub.application.branchrule.BranchRuleUseCase.class);
    private RunAppService service;

    @BeforeEach
    void setUp() {
        var planner = new io.releasehub.application.run.plan.RunPlanAppService(
                mock(io.releasehub.application.run.plan.RunPlanPort.class), runs, windows, bindings,
                iterations, versions, repos, settings, clock);
        service = new RunAppService(runs, windows, bindings, iterations, versions, repos, adapters,
                mock(VersionUpdateAppService.class), conflicts, mock(VersionDeriverUseCase.class), settings, clock, planner, rules);
        when(rules.isCompliant(anyString(), anyString(), anyString())).thenReturn(true);
        when(settings.getNaming()).thenReturn(Optional.empty());
        when(windows.findById(ReleaseWindowId.of(WINDOW))).thenReturn(Optional.of(window(false)));
        when(conflicts.getLatestReport(WINDOW)).thenReturn(Optional.of(ConflictReport.empty(WINDOW)));
        when(bindings.listByWindow(ReleaseWindowId.of(WINDOW))).thenReturn(List.of(binding("I2", 2), binding("I1", 1)));
        when(iterations.findByKey(IterationKey.of("I1"))).thenReturn(Optional.of(iteration("I1", Set.of("r1", "r2"))));
        when(iterations.findByKey(IterationKey.of("I2"))).thenReturn(Optional.of(iteration("I2", Set.of("r1"))));
        for (String id : List.of("r1", "r2")) {
            when(repos.findById(RepoId.of(id))).thenReturn(Optional.of(repo(id)));
        }
        when(adapters.getAdapter(GitProvider.GITLAB)).thenReturn(git);
        when(git.getBranchStatus(any(), any(), any())).thenReturn(GitBranchPort.BranchStatus.present("known-sha"));
        when(git.mergeBranch(any(), any(), any(), any(), any())).thenAnswer(inv -> merged(inv.getArgument(2), inv.getArgument(3)));
    }

    @Test
    void failFastStopsOnlyTheBlockedRepositoryAndRecordsEveryPlannedItem() {
        when(git.mergeBranch(eq(url("r1")), any(), eq("feature/I1"), eq(RELEASE), any()))
                .thenReturn(GitBranchPort.MergeResult.conflict("conflict"));
        Run run = service.startOrchestrate(WINDOW, List.of("r2", "r1", "r1"), List.of(), true, "tester");
        assertThat(run.getItems()).hasSize(3);
        assertThat(item(run, "r1", "I1").getFinalResult()).isEqualTo(RunItemResult.MERGE_BLOCKED);
        assertThat(item(run, "r1", "I2").getFinalResult()).isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
        assertThat(item(run, "r1", "I2").getExecutedOrder()).isZero();
        assertThat(item(run, "r2", "I1").getFinalResult()).isEqualTo(RunItemResult.MERGED);
        assertThat(item(run, "r2", "I1").getPlannedOrder()).isEqualTo(3);
        assertThat(item(run, "r2", "I1").getExecutedOrder()).isEqualTo(2);
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.PARTIAL_FAILED);
        verify(git, never()).mergeBranch(eq(url("r1")), any(), eq("feature/I2"), any(), any());
        verify(git, times(1)).mergeBranch(eq(url("r2")), any(), eq("feature/I1"), any(), any());
    }

    @Test
    void explicitContinueRunsLaterItemsOfTheSameRepository() {
        when(git.mergeBranch(eq(url("r1")), any(), eq("feature/I1"), eq(RELEASE), any()))
                .thenReturn(GitBranchPort.MergeResult.conflict("conflict"));
        Run run = service.startOrchestrate(WINDOW, List.of("r1", "r2"), List.of(), false, "tester");
        assertThat(run.getItems()).hasSize(3);
        assertThat(item(run, "r1", "I2").getFinalResult()).isEqualTo(RunItemResult.MERGED);
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.PARTIAL_FAILED);
    }

    @Test
    void unknownResultAlwaysStopsTheSameRepositoryEvenWithContinueSelected() {
        when(git.mergeBranch(eq(url("r1")), any(), eq("feature/I1"), eq(RELEASE), any()))
                .thenReturn(GitBranchPort.MergeResult.unknown("response lost"));
        Run run = service.startOrchestrate(WINDOW, List.of("r1", "r2"), List.of(), false, "tester");
        assertThat(item(run, "r1", "I1").getFinalResult()).isEqualTo(RunItemResult.UNKNOWN);
        assertThat(item(run, "r1", "I2").getFinalResult()).isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
        assertThat(item(run, "r2", "I1").getFinalResult()).isEqualTo(RunItemResult.MERGED);
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        when(runs.findById(run.getId().value())).thenReturn(Optional.of(run));
        clearInvocations(git);
        assertThatThrownBy(() -> service.retry(run.getId().value(), List.of(item(run, "r1", "I1").getId().value()), "tester"))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(git);
    }

    @Test
    void validatesAllRepositorySelectionsBeforeAnyGitOperation() {
        assertThatThrownBy(() -> service.startOrchestrate(WINDOW, List.of("r1", "missing"), List.of(), true, "tester"))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(git);
    }

    @Test
    void rejectsAnUnboundIterationWithoutSilentlyShrinkingScope() {
        assertThatThrownBy(() -> service.startOrchestrate(WINDOW, List.of("r1"), List.of("I1", "missing"), true, "tester"))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(git);
    }

    @Test
    void rejectsAnEmptySelectionWithoutGit() {
        assertThatThrownBy(() -> service.startOrchestrate(WINDOW, List.of(), List.of(), true, "tester"))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(git);
    }

    @Test
    void createsMissingFeatureFromThePlannedBaseBeforeMerging() {
        when(git.getBranchStatus(any(), any(), eq("feature/I1"))).thenReturn(GitBranchPort.BranchStatus.missing());
        when(git.createBranch(any(), any(), eq("feature/I1"), eq("main"))).thenReturn(true);
        Run run = service.startOrchestrate(WINDOW, List.of("r1"), List.of("I1"), true, "tester");
        assertThat(run.getItems()).singleElement().extracting(RunItem::getFinalResult).isEqualTo(RunItemResult.MERGED);
        verify(git).createBranch(url("r1"), "token", "feature/I1", "main");
    }

    @Test
    void persistsIntentBeforeTheFirstRemoteWrite() {
        when(git.mergeBranch(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            verify(runs, atLeastOnce()).save(any(Run.class));
            return merged(inv.getArgument(2), inv.getArgument(3));
        });
        Run run = service.startOrchestrate(WINDOW, List.of("r1"), List.of("I1"), true, "tester");
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
    }

    @Test
    void frozenWindowCannotWriteGit() {
        when(windows.findById(ReleaseWindowId.of(WINDOW))).thenReturn(Optional.of(window(true)));
        assertThatThrownBy(() -> service.startOrchestrate(WINDOW, List.of("r1"), List.of(), true, "tester"))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(git);
    }

    @Test
    void repositoryExceptionIsRecordedAndDoesNotAbortOtherRepositories() {
        when(git.getBranchStatus(eq(url("r1")), any(), any())).thenThrow(new IllegalStateException("provider unavailable"));
        Run run = service.startOrchestrate(WINDOW, List.of("r1", "r2"), List.of(), true, "tester");
        assertThat(run.getItems()).hasSize(3);
        assertThat(item(run, "r1", "I1").getFinalResult()).isEqualTo(RunItemResult.FAILED);
        assertThat(item(run, "r1", "I2").getFinalResult()).isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
        assertThat(item(run, "r2", "I1").getFinalResult()).isEqualTo(RunItemResult.MERGED);
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.PARTIAL_FAILED);
    }

    @Test
    void exceptionAfterBranchWriteIsUnknownAndStopsTheRepositoryEvenWhenContinueWasSelected() {
        when(git.getBranchStatus(eq(url("r1")), any(), eq("feature/I1")))
                .thenReturn(GitBranchPort.BranchStatus.missing());
        var remoteWriteAccepted = new java.util.concurrent.atomic.AtomicBoolean();
        when(git.createBranch(eq(url("r1")), any(), eq("feature/I1"), eq("main"))).thenAnswer(inv -> {
            remoteWriteAccepted.set(true);
            throw new IllegalStateException("journal commit failed after remote acceptance");
        });

        Run run = service.startOrchestrate(WINDOW, List.of("r1", "r2"), List.of(), false, "tester");

        assertThat(remoteWriteAccepted).isTrue();
        assertThat(item(run, "r1", "I1").getFinalResult()).isEqualTo(RunItemResult.UNKNOWN);
        assertThat(item(run, "r1", "I2").getFinalResult()).isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
        assertThat(item(run, "r2", "I1").getFinalResult()).isEqualTo(RunItemResult.MERGED);
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        verify(git, never()).mergeBranch(eq(url("r1")), any(), any(), any(), any());
    }

    @Test
    void unexpectedExceptionDuringMrWorkflowCannotBeRetriedAsConfirmedFailure() {
        when(git.mergeBranch(eq(url("r1")), any(), eq("feature/I1"), eq(RELEASE), any()))
                .thenThrow(new IllegalStateException("local persistence failed after remote merge"));
        Run run = service.startOrchestrate(WINDOW, List.of("r1", "r2"), List.of(), false, "tester");
        assertThat(item(run, "r1", "I1").getFinalResult()).isEqualTo(RunItemResult.UNKNOWN);
        assertThat(item(run, "r1", "I2").getFinalResult()).isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
        assertThat(item(run, "r2", "I1").getFinalResult()).isEqualTo(RunItemResult.MERGED);
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
    }

    @Test
    void missingExistingFeatureCannotBeRecreatedOrMerged() {
        when(versions.listVersionInfo("I1")).thenReturn(List.of(IterationRepoVersionInfo.builder()
                .repoId("r1").featureBranch("feature/external")
                .branchCreationMode(io.releasehub.domain.iteration.BranchCreationMode.EXISTING).build()));
        when(git.getBranchStatus(eq(url("r1")), any(), eq("feature/external")))
                .thenReturn(GitBranchPort.BranchStatus.missing());
        when(git.createBranch(any(), any(), any(), any())).thenReturn(true);

        Run run = service.startOrchestrate(WINDOW, List.of("r1"), List.of("I1"), true, "tester");

        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
        assertThat(run.getItems()).singleElement().extracting(RunItem::getFinalResult).isEqualTo(RunItemResult.FAILED);
        verify(git, never()).createBranch(any(), any(), any(), any());
        verify(git, never()).mergeBranch(any(), any(), any(), any(), any());
    }

    @Test
    void successWithoutMergeRequestIdentityIsUnknown() {
        when(git.mergeBranch(any(), any(), any(), any(), any())).thenReturn(GitBranchPort.MergeResult.success()
                .withEvidence(new GitBranchPort.MrEvidence("17", "feature/I1", RELEASE, null, null,
                        "a".repeat(40), "b".repeat(40), GitBranchPort.MrDisposition.MR_REUSED, null)));
        Run run = service.startOrchestrate(WINDOW, List.of("r1"), List.of("I1"), true, "tester");
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        assertThat(run.getItems()).singleElement().extracting(RunItem::getFinalResult).isEqualTo(RunItemResult.UNKNOWN);
    }

    @Test
    void provenTargetContainmentMayCompleteWithoutCreatingAnEmptyMergeRequest() {
        when(git.mergeBranch(any(), any(), any(), any(), any())).thenReturn(GitBranchPort.MergeResult.success()
                .withEvidence(new GitBranchPort.MrEvidence("17", "feature/I1", RELEASE, null, null,
                        "a".repeat(40), null, GitBranchPort.MrDisposition.ALREADY_MERGED, null)));
        Run run = service.startOrchestrate(WINDOW, List.of("r1"), List.of("I1"), true, "tester");
        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(run.getItems()).singleElement().extracting(RunItem::getFinalResult).isEqualTo(RunItemResult.ALREADY_MERGED);
        assertThat(run.getItems().getFirst().getSteps()).extracting(RunStep::actionType)
                .contains(ActionType.ENSURE_MR, ActionType.TRY_MERGE);
    }

    private GitBranchPort.MergeResult merged(String source, String target) {
        return GitBranchPort.MergeResult.success().withEvidence(new GitBranchPort.MrEvidence(
                "17", source, target, "7", "https://gitlab.example/group/project/-/merge_requests/7",
                "a".repeat(40), "b".repeat(40), GitBranchPort.MrDisposition.MR_REUSED, null));
    }

    private ReleaseWindow window(boolean frozen) {
        return ReleaseWindow.rehydrate(ReleaseWindowId.of(WINDOW), "W1", "Window", "", NOW, "G1",
                ReleaseWindowStatus.DRAFT, NOW, NOW, frozen, null);
    }

    private WindowIteration binding(String key, int seconds) {
        return WindowIteration.rehydrate(WindowIterationId.generate(ReleaseWindowId.of(WINDOW), IterationKey.of(key)),
                ReleaseWindowId.of(WINDOW), IterationKey.of(key), NOW.plusSeconds(seconds), null, false, null, NOW, NOW);
    }

    private Iteration iteration(String key, Set<String> repoIds) {
        return Iteration.rehydrate(IterationKey.of(key), key, "", null, "G1",
                repoIds.stream().map(RepoId::of).collect(java.util.stream.Collectors.toSet()), IterationStatus.ACTIVE, NOW, NOW);
    }

    private CodeRepository repo(String id) {
        return CodeRepository.rehydrate(RepoId.of(id), id, url(id), "main", "G1", RepoType.SERVICE,
                GitProvider.GITLAB, "token", false, 0, 0, 0, 0, 0, 0, 0, null, NOW, NOW, 0L);
    }

    private static String url(String repo) { return "https://gitlab.example/group/" + repo + ".git"; }

    private RunItem item(Run run, String repo, String iteration) {
        return run.getItems().stream().filter(i -> i.getRepo().value().equals(repo)
                && i.getIterationKey().value().equals(iteration)).findFirst().orElseThrow();
    }
}
