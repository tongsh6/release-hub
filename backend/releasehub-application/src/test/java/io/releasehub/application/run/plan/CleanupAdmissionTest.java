package io.releasehub.application.run.plan;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.version.VersionDeriver;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
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
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunExecutionStatus;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.window.WindowIteration;
import io.releasehub.domain.window.WindowIterationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CleanupAdmissionTest {
    private final RunPlanPort plans = mock(RunPlanPort.class);
    private final RunPort runs = mock(RunPort.class);
    private final ReleaseWindowPort windows = mock(ReleaseWindowPort.class);
    private final WindowIterationPort bindings = mock(WindowIterationPort.class);
    private final IterationPort iterations = mock(IterationPort.class);
    private final IterationRepoPort versions = mock(IterationRepoPort.class);
    private final CodeRepositoryPort repositories = mock(CodeRepositoryPort.class);
    private final SettingsPort settings = mock(SettingsPort.class);
    private final Instant now = Instant.parse("2026-11-21T10:00:00.123456789Z");
    private final ReleaseWindowId windowId = ReleaseWindowId.of("window-cleanup");
    private RunPlanAppService service;
    private ReleaseWindow window;

    @BeforeEach
    void setUp() {
        service = new RunPlanAppService(plans, runs, windows, bindings, iterations, versions,
                repositories, settings, Clock.fixed(now, ZoneOffset.UTC), new VersionDeriver());
        setWindow(ReleaseWindowStatus.PUBLISHED, false);
        when(bindings.listByWindow(windowId)).thenReturn(List.of(binding("ITER-LATE", now), binding("ITER-EARLY", now.minusSeconds(10))));
        for (String key : List.of("ITER-EARLY", "ITER-LATE")) {
            registerIteration(key);
            when(versions.listVersionInfo(key)).thenReturn(List.of(version("repo-a", "1.1.0"), version("repo-b", "1.1.0")));
        }
        for (String id : List.of("repo-a", "repo-b")) {
            when(repositories.findById(RepoId.of(id))).thenReturn(Optional.of(repo(id,
                    "https://gitlab.invalid/acme/" + id + ".git", "main", GitProvider.GITLAB)));
        }
    }

    @Test
    void cleanupAdmitsOneRepositoryItemAndFreezesAllParticipantsBeforeClosing() {
        var admission = service.admitCleanup(windowId.value(), "alice", false);
        SavedRunPlan plan = savedPlan();

        assertThat(admission.created()).isTrue();
        assertThat(admission.run().getRunType()).isEqualTo(RunType.WINDOW_CLEANUP);
        assertThat(admission.run().getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
        assertThat(admission.run().getItems()).hasSize(2).allSatisfy(item -> {
            assertThat(item.getIterationKey()).isNull();
            assertThat(item.getSteps()).isEmpty();
            assertThat(item.getExecutedOrder()).isZero();
            assertThat(item.getMetadata()).containsEntry("plan.windowId", windowId.value())
                    .containsEntry("plan.participatingIterationKeys", "[\"ITER-EARLY\",\"ITER-LATE\"]")
                    .containsEntry("plan.releaseBranch", "release/RW-CLEANUP")
                    .containsEntry("plan.defaultBranch", "main")
                    .containsEntry("plan.tagName", "v1.1.0")
                    .containsEntry("plan.triggerCi", "false")
                    .doesNotContainKeys("plan.featureBranch", "plan.ciRef")
                    .doesNotContainValue("fixture-token");
        });
        assertThat(plan.snapshot().items()).extracting(item -> item.repoId()).containsExactly("repo-a", "repo-b");
        assertThat(plan.snapshot().items()).allSatisfy(item -> {
            assertThat(item.iterationKey()).isNull();
            assertThat(item.featureBranch()).isNull();
            assertThat(item.featureBranchMode()).isNull();
            assertThat(item.participatingIterationKeys()).containsExactly("ITER-EARLY", "ITER-LATE");
            assertThat(item.tagName()).isEqualTo("v1.1.0");
            assertThat(item.triggerCi()).isFalse();
            assertThat(item.ciRef()).isNull();
        });
        assertThat(plan.snapshot().items()).extracting(item -> item.itemId())
                .containsExactlyElementsOf(admission.run().getItems().stream().map(item -> item.getId().value()).toList());
        assertThat(window.getStatus()).isEqualTo(ReleaseWindowStatus.CLOSED);
        var order = inOrder(windows, plans, runs);
        order.verify(windows).findByIdForUpdate(windowId);
        order.verify(plans).findCleanupByWindowId(windowId.value());
        order.verify(runs).save(admission.run());
        order.verify(plans).save(plan);
        order.verify(windows).save(window);
    }

    @Test
    void explicitCiUsesTheImmutableTagAndChangesThePlanFingerprint() {
        service.admitCleanup(windowId.value(), "alice", false);
        String firstHash = savedPlan().planHash();
        clearInvocations(plans, runs);
        setWindow(ReleaseWindowStatus.PUBLISHED, false);

        service.admitCleanup(windowId.value(), "alice", true);
        SavedRunPlan selected = savedPlan();

        assertThat(selected.planHash()).isNotEqualTo(firstHash);
        assertThat(selected.snapshot().items()).allSatisfy(item -> {
            assertThat(item.triggerCi()).isTrue();
            assertThat(item.ciRef()).isEqualTo(item.tagName()).isEqualTo("v1.1.0");
        });
    }

    @Test
    void absentVersionProducesAnExplicitlyUnselectedTagWithoutGuessingFromAnotherIteration() {
        when(versions.listVersionInfo("ITER-LATE")).thenReturn(List.of());
        var admitted = service.admitCleanup(windowId.value(), "alice", false);

        assertThat(savedPlan().snapshot().items()).allSatisfy(item -> {
            assertThat(item.tagName()).isNull();
            assertThat(item.targetVersion()).isNull();
            assertThat(item.ciRef()).isNull();
        });
        assertThat(admitted.run().getItems()).allSatisfy(item ->
                assertThat(item.getMetadata()).doesNotContainKeys("plan.tagName", "plan.ciRef"));
    }

    @Test
    void ciWithoutAnUnambiguousVersionRejectsTheWholeWindowBeforeAnySave() {
        when(versions.listVersionInfo("ITER-LATE")).thenReturn(List.of(version("repo-a", "1.1.0")));
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", true));
    }

    @Test
    void conflictingIterationVersionsRejectAllRepositoriesInsteadOfTakingFirstOrMaximum() {
        when(versions.listVersionInfo("ITER-LATE")).thenReturn(List.of(version("repo-a", "1.1.0"), version("repo-b", "2.0.0")));
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void recordedTargetCannotDisagreeWithTheExistingDevelopmentVersionDeriver() {
        var conflicting = version("repo-b", "2.0.0");
        conflicting.setDevVersion("1.1.0-SNAPSHOT");
        when(versions.listVersionInfo("ITER-LATE")).thenReturn(List.of(version("repo-a", "1.1.0"), conflicting));
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void missingTargetUsesTheExistingVersionDeriver() {
        for (String key : List.of("ITER-EARLY", "ITER-LATE")) {
            var a = version("repo-a", "1.1.0");
            a.setTargetVersion(null);
            var b = version("repo-b", "1.1.0");
            b.setTargetVersion(null);
            when(versions.listVersionInfo(key)).thenReturn(List.of(a, b));
        }
        service.admitCleanup(windowId.value(), "alice", false);
        assertThat(savedPlan().snapshot().items()).allSatisfy(item -> assertThat(item.tagName()).isEqualTo("v1.1.0"));
    }

    @Test
    void anotherOperatorGetsTheSameClosedRunBeforeAnyCurrentConfigurationIsRead() {
        var original = service.admitCleanup(windowId.value(), "alice", false);
        SavedRunPlan saved = savedPlan();
        when(plans.findCleanupByWindowId(windowId.value())).thenReturn(Optional.of(saved));
        when(runs.findById(saved.runId())).thenReturn(Optional.of(original.run()));
        clearInvocations(plans, runs, windows, bindings, iterations, versions, repositories, settings);

        var repeated = service.admitCleanup(windowId.value(), "bob", false);

        assertThat(repeated.created()).isFalse();
        assertThat(repeated.run()).isSameAs(original.run());
        assertThat(repeated.run().getOperator()).isEqualTo("alice");
        verifyNoInteractions(bindings, iterations, versions, repositories, settings);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
        verify(windows, never()).save(any());
    }

    @Test
    void databaseItemReadOrderCannotInvalidateTheSameSavedCleanupPlan() {
        Run original = service.admitCleanup(windowId.value(), "alice", false).run();
        SavedRunPlan saved = savedPlan();
        var reversed = new java.util.ArrayList<>(original.getItems());
        java.util.Collections.reverse(reversed);
        Run reloaded = Run.rehydrate(original.getId(), original.getRunType(), original.getOperator(),
                original.getStartedAt(), original.getFinishedAt(), reversed, original.getCreatedAt(),
                original.getUpdatedAt(), original.getVersion(), original.getExecutionVersion(), original.getExecutionStatus());
        when(plans.findCleanupByWindowId(windowId.value())).thenReturn(Optional.of(saved));
        when(runs.findById(saved.runId())).thenReturn(Optional.of(reloaded));
        clearInvocations(plans, runs, windows);

        var repeated = service.admitCleanup(windowId.value(), "bob", false);

        assertThat(repeated.created()).isFalse();
        assertThat(repeated.run()).isSameAs(reloaded);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
        verify(windows, never()).save(any());
    }

    @Test
    void repeatedCloseCannotReplaceTheOriginalCiSelection() {
        var original = service.admitCleanup(windowId.value(), "alice", false);
        SavedRunPlan saved = savedPlan();
        when(plans.findCleanupByWindowId(windowId.value())).thenReturn(Optional.of(saved));
        when(runs.findById(saved.runId())).thenReturn(Optional.of(original.run()));
        clearInvocations(plans, runs, windows);

        assertThatThrownBy(() -> service.admitCleanup(windowId.value(), "bob", true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo("RUN_005"));
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
        verify(windows, never()).save(any());
    }

    @Test
    void closedWindowWithoutACompletePlanNeverStartsNewWrites() {
        setWindow(ReleaseWindowStatus.CLOSED, false);
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void aPlanWhoseRunIsMissingCannotBeReplayedAsANewAdmission() {
        service.admitCleanup(windowId.value(), "alice", false);
        SavedRunPlan saved = savedPlan();
        when(plans.findCleanupByWindowId(windowId.value())).thenReturn(Optional.of(saved));
        when(runs.findById(saved.runId())).thenReturn(Optional.empty());
        clearInvocations(plans, runs, windows);

        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "bob", false));
    }

    @Test
    void draftAndFrozenWindowsRejectFirstCleanupAdmission() {
        setWindow(ReleaseWindowStatus.DRAFT, false);
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
        setWindow(ReleaseWindowStatus.PUBLISHED, true);
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void aNonGitLabRepositoryRejectsTheWholeWindowBeforeSave() {
        when(repositories.findById(RepoId.of("repo-b"))).thenReturn(Optional.of(repo("repo-b",
                "https://github.invalid/acme/repo-b.git", "main", GitProvider.GITHUB)));
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void duplicateRepositoryMappingsCannotPublishTheSameRemoteTwice() {
        when(repositories.findById(RepoId.of("repo-b"))).thenReturn(Optional.of(repo("repo-b",
                "git@gitlab.invalid:acme/repo-a.git", "main", GitProvider.GITLAB)));
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void releaseMustBeUniqueAndDifferentFromTheDefaultBranch() {
        when(bindings.listByWindow(windowId)).thenReturn(List.of(
                recordedBinding("ITER-EARLY", "release/first"), recordedBinding("ITER-LATE", "release/second")));
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
        when(bindings.listByWindow(windowId)).thenReturn(List.of(
                recordedBinding("ITER-EARLY", "main"), recordedBinding("ITER-LATE", "main")));
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void invalidBranchNamesOrTagNamesFailBeforeSavingIntent() {
        when(repositories.findById(RepoId.of("repo-b"))).thenReturn(Optional.of(repo("repo-b",
                "https://gitlab.invalid/acme/repo-b.git", "main bad", GitProvider.GITLAB)));
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
        when(repositories.findById(RepoId.of("repo-b"))).thenReturn(Optional.of(repo("repo-b",
                "https://gitlab.invalid/acme/repo-b.git", "main", GitProvider.GITLAB)));
        for (String key : List.of("ITER-EARLY", "ITER-LATE")) {
            when(versions.listVersionInfo(key)).thenReturn(List.of(version("repo-a", "1..2"), version("repo-b", "1.1.0")));
        }
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void missingBindingTargetsAndEmptyScopeAreNotSilentlyDropped() {
        when(iterations.findByKey(IterationKey.of("ITER-LATE"))).thenReturn(Optional.empty());
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
        when(bindings.listByWindow(windowId)).thenReturn(List.of());
        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "alice", false));
    }

    @Test
    void aPausedAdmittedOrchestrationPreventsCleanupBeforeAnyNewIntentIsSaved() {
        Run source = service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-EARLY"), true, "alice");
        assertThat(source.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
        when(plans.hasUnresolvedExecutionByWindowId(windowId.value())).thenReturn(true);
        clearInvocations(plans, runs, windows);

        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "bob", false));

        assertThat(window.getStatus()).isEqualTo(ReleaseWindowStatus.PUBLISHED);
    }

    @Test
    void aNeedsReviewOrchestrationAlsoPreventsClosingAndPublishingItsRelease() {
        Run source = service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-EARLY"), true, "alice");
        source.getItems().getFirst().finishWith(io.releasehub.domain.run.RunItemResult.UNKNOWN, now);
        source.finish(now);
        assertThat(source.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
        when(plans.hasUnresolvedExecutionByWindowId(windowId.value())).thenReturn(true);
        clearInvocations(plans, runs, windows);

        assertRejectedBeforeSave(() -> service.admitCleanup(windowId.value(), "bob", false));

        assertThat(window.getStatus()).isEqualTo(ReleaseWindowStatus.PUBLISHED);
    }

    @Test
    void aPlannedPreviewDoesNotBlockAnExplicitCleanupAdmission() {
        service.create(windowId.value(), List.of("repo-a"), List.of("ITER-EARLY"), true, "alice", "preview-only-001");
        var preview = ArgumentCaptor.forClass(Run.class);
        verify(runs).save(preview.capture());
        assertThat(preview.getValue().getExecutionStatus()).isEqualTo(RunExecutionStatus.PLANNED);
        when(plans.hasUnresolvedExecutionByWindowId(windowId.value())).thenReturn(false);
        clearInvocations(plans, runs, windows);

        assertThat(service.admitCleanup(windowId.value(), "bob", false).created()).isTrue();
        assertThat(savedPlan().snapshot().items()).hasSize(2);
    }

    @Test
    void orchestrationAdmissionUsesTheSameWindowLockBeforeSavingIntent() {
        Run run = service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-EARLY"), true, "alice");

        var order = inOrder(windows, runs);
        order.verify(windows).findByIdForUpdate(windowId);
        order.verify(runs).save(run);
    }

    @Test
    void retryAdmissionUsesTheSameWindowLockBeforeSavingIntent() {
        Run source = service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-EARLY"), true, "alice");
        SavedRunPlan saved = savedPlan();
        source.getItems().getFirst().finishWith(io.releasehub.domain.run.RunItemResult.FAILED, now);
        source.finish(now);
        when(plans.findByRunId(source.getId().value())).thenReturn(Optional.of(saved));
        clearInvocations(plans, runs, windows);

        Run retry = service.admitRetry(source, List.of(source.getItems().getFirst().getId().value()), "bob");

        var order = inOrder(windows, runs);
        order.verify(windows).findByIdForUpdate(windowId);
        order.verify(runs).save(retry);
    }

    private SavedRunPlan savedPlan() {
        var captor = ArgumentCaptor.forClass(SavedRunPlan.class);
        verify(plans).save(captor.capture());
        return captor.getValue();
    }

    private void assertRejectedBeforeSave(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
        verify(windows, never()).save(any());
    }

    private void setWindow(ReleaseWindowStatus status, boolean frozen) {
        window = ReleaseWindow.rehydrate(windowId, "RW-CLEANUP", "收尾窗口", null, now, "G001", status, now, now, frozen, null);
        when(windows.findById(windowId)).thenReturn(Optional.of(window));
        when(windows.findByIdForUpdate(windowId)).thenReturn(Optional.of(window));
    }

    private void registerIteration(String key) {
        when(iterations.findByKey(IterationKey.of(key))).thenReturn(Optional.of(Iteration.rehydrate(
                IterationKey.of(key), key, null, null, "G001", Set.of(RepoId.of("repo-a"), RepoId.of("repo-b")),
                IterationStatus.ACTIVE, now, now)));
    }

    private WindowIteration binding(String key, Instant attachedAt) {
        return WindowIteration.attach(windowId, IterationKey.of(key), attachedAt, attachedAt);
    }

    private WindowIteration recordedBinding(String key, String target) {
        return WindowIteration.rehydrate(WindowIterationId.generate(windowId, IterationKey.of(key)),
                windowId, IterationKey.of(key), now, target, true, null, now, now);
    }

    private IterationRepoVersionInfo version(String repoId, String target) {
        return IterationRepoVersionInfo.builder().repoId(repoId).featureBranch("feature/" + repoId)
                .baseVersion("1.0.0").devVersion(target + "-SNAPSHOT").targetVersion(target).build();
    }

    private CodeRepository repo(String id, String cloneUrl, String branch, GitProvider provider) {
        return CodeRepository.rehydrate(RepoId.of(id), id, cloneUrl, branch, "G001", RepoType.SERVICE,
                provider, "fixture-token", false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0);
    }
}
