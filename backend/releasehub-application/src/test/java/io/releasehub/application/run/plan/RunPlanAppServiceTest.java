package io.releasehub.application.run.plan;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.releasewindow.ReleaseWindowAppService;
import io.releasehub.application.releasewindow.BranchStatusView;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.window.PlanAppService;
import io.releasehub.application.window.WindowIterationPort;
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
import io.releasehub.domain.run.RunPlanSnapshot;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunExecutionStatus;
import io.releasehub.domain.iteration.BranchCreationMode;
import io.releasehub.domain.window.WindowIterationId;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.domain.window.WindowIteration;
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

class RunPlanAppServiceTest {
    private final RunPlanPort plans = mock(RunPlanPort.class);
    private final RunPort runs = mock(RunPort.class);
    private final ReleaseWindowPort windows = mock(ReleaseWindowPort.class);
    private final WindowIterationPort bindings = mock(WindowIterationPort.class);
    private final IterationPort iterations = mock(IterationPort.class);
    private final IterationRepoPort versions = mock(IterationRepoPort.class);
    private final CodeRepositoryPort repositories = mock(CodeRepositoryPort.class);
    private final SettingsPort settings = mock(SettingsPort.class);
    private final GitBranchAdapterFactory adapters = mock(GitBranchAdapterFactory.class);
    private final GitBranchPort git = mock(GitBranchPort.class);
    private final Instant now = Instant.parse("2026-11-21T04:00:00.123456789Z");
    private final ReleaseWindowId windowId = ReleaseWindowId.of("window-1");
    private RunPlanAppService service;

    @BeforeEach
    void setUp() {
        service = new RunPlanAppService(plans, runs, windows, bindings, iterations, versions,
                repositories, settings, Clock.fixed(now, ZoneOffset.UTC), new io.releasehub.application.version.VersionDeriver());
        when(windows.findById(windowId)).thenReturn(Optional.of(window(ReleaseWindowStatus.DRAFT, false)));
        when(windows.findByIdForUpdate(any())).thenAnswer(call -> windows.findById(call.getArgument(0)));
        when(bindings.listByWindow(windowId)).thenReturn(List.of(binding("ITER-B"), binding("ITER-A")));
        for (String key : List.of("ITER-A", "ITER-B")) {
            when(iterations.findByKey(IterationKey.of(key))).thenReturn(Optional.of(Iteration.rehydrate(
                    IterationKey.of(key), key, null, null, "G001",
                    Set.of(RepoId.of("repo-b"), RepoId.of("repo-a")), IterationStatus.ACTIVE, now, now)));
            when(versions.listVersionInfo(key)).thenReturn(List.of(version("repo-a", "feature/" + key), version("repo-b", "feature/" + key)));
        }
        for (String id : List.of("repo-a", "repo-b")) {
            when(repositories.findById(RepoId.of(id))).thenReturn(Optional.of(CodeRepository.rehydrate(
                    RepoId.of(id), id, "https://gitlab.invalid/acme/" + id + ".git", "main", "G001",
                    RepoType.SERVICE, GitProvider.GITLAB, "secret-token", false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0)));
        }
    }

    @Test
    void missingFeatureConfigurationUsesTheExistingIterationDefault() {
        when(versions.listVersionInfo("ITER-A")).thenReturn(List.of());

        var plan = service.create(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", "preview-001");

        assertThat(plan.snapshot().items()).singleElement()
                .satisfies(item -> assertThat(item.featureBranch()).isEqualTo("feature/ITER-A"));
    }

    @Test
    void previewItemsCarryTheExactSnapshotInputsForExecution() {
        var plan = service.create(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", "preview-001");
        ArgumentCaptor<Run> saved = ArgumentCaptor.forClass(Run.class);
        verify(runs).save(saved.capture());
        assertThat(saved.getValue().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMetadata()).containsEntry("plan.windowId", windowId.value())
                    .containsEntry("plan.featureBranch", "feature/ITER-A")
                    .containsEntry("plan.releaseBranch", "release/RW-1")
                    .containsEntry("plan.defaultBranch", "main")
                    .containsEntry("plan.failFast", "true")
                    .containsEntry("plan.cloneUrl", "https://gitlab.invalid/acme/repo-a.git")
                    .containsEntry("plan.gitProvider", "GITLAB");
            assertThat(item.getId().value()).isEqualTo(plan.snapshot().items().get(0).itemId());
        });
    }

    @Test
    void windowPlanUsesTheSameStableRepositoryAndAttachmentOrder() {
        var saved = service.create(windowId.value(), List.of("repo-b", "repo-a"), List.of(), true, "operator", "preview-001");
        var legacyView = new PlanAppService(service).getPlanByWindow(windowId.value());

        assertThat(legacyView.stream().map(i -> i.getRepoId() + "/" + i.getIterationKey() + "/" + i.getPlannedOrder()).toList())
                .containsExactlyElementsOf(saved.snapshot().items().stream()
                        .map(i -> i.repoId() + "/" + i.iterationKey() + "/" + i.plannedOrder()).toList());
    }


    @Test
    void admissionSavesRunningRunAndTheSameItemIdentitiesBeforeExecution() {
        Run run = admittedRun(service.admit(windowId.value(), List.of("repo-b", "repo-a", "repo-a"),
                List.of("ITER-B", "ITER-A", "ITER-B"), false, "operator", java.util.UUID.randomUUID().toString()));
        ArgumentCaptor<SavedRunPlan> saved = ArgumentCaptor.forClass(SavedRunPlan.class);
        verify(plans).save(saved.capture());
        verify(runs).save(run);

        assertThat(run.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
        assertThat(run.getFinishedAt()).isNull();
        assertThat(run.getItems()).hasSize(4).allSatisfy(item -> {
            assertThat(item.getSteps()).isEmpty();
            assertThat(item.getFinalResult()).isNull();
            assertThat(item.getExecutedOrder()).isZero();
            assertThat(item.getMetadata()).containsEntry("plan.failFast", "false")
                    .doesNotContainValue("secret-token");
        });
        assertThat(saved.getValue().runId()).isEqualTo(run.getId().value());
        assertThat(saved.getValue().createdAt()).isEqualTo(Instant.parse("2026-11-21T04:00:00.123456Z"));
        assertThat(saved.getValue().snapshot().items().stream().map(item -> item.itemId()).toList())
                .containsExactlyElementsOf(run.getItems().stream().map(item -> item.getId().value()).toList());
        assertThat(saved.getValue().snapshot().items().stream().map(i -> i.repoId() + "/" + i.iterationKey()).toList())
                .containsExactly("repo-a/ITER-A", "repo-a/ITER-B", "repo-b/ITER-A", "repo-b/ITER-B");
        var ordered = inOrder(runs, plans);
        ordered.verify(runs).save(run);
        ordered.verify(plans).save(saved.getValue());
    }

    @Test
    void draftAndPublishedWindowsCanBeExplicitlyAdmitted() {
        for (var status : List.of(ReleaseWindowStatus.DRAFT, ReleaseWindowStatus.PUBLISHED)) {
            when(windows.findById(windowId)).thenReturn(Optional.of(window(status, false)));
            assertThat(admittedRun(service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString()))
                    .getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
        }
    }

    @Test
    void closedOrFrozenAdmissionDoesNotPersistAFalseRun() {
        for (var window : List.of(window(ReleaseWindowStatus.CLOSED, false), window(ReleaseWindowStatus.DRAFT, true),
                window(ReleaseWindowStatus.PUBLISHED, true))) {
            when(windows.findById(windowId)).thenReturn(Optional.of(window));
            assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString()))
                    .isInstanceOf(BusinessException.class);
        }
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void invalidSelectionRejectsTheWholeAdmissionBeforeSavingAnyItem() {
        for (var repos : List.of(List.<String>of(), List.of("repo-a", "missing-repo"))) {
            assertThatThrownBy(() -> service.admit(windowId.value(), repos, List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString()))
                    .isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A", "NOT-ATTACHED"), true, "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        when(iterations.findByKey(IterationKey.of("ITER-B"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a"), List.of(), true, "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).satisfies(error -> assertThat(((BusinessException) error).getArgs()[0].toString()).contains("绑定迭代不存在"));
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void crossGroupRepositoryCannotBeSilentlyOmitted() {
        CodeRepository crossGroup = CodeRepository.rehydrate(RepoId.of("repo-b"), "repo-b",
                "https://gitlab.invalid/acme/repo-b.git", "main", "OTHER", RepoType.SERVICE, GitProvider.GITLAB,
                null, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0);
        when(repositories.findById(RepoId.of("repo-b"))).thenReturn(Optional.of(crossGroup));
        assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a", "repo-b"), List.of(), true, "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).satisfies(error -> assertThat(((BusinessException) error).getArgs()[0].toString()).contains("仓库与窗口不在同一分组"));
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void explicitlySelectedDisjointIterationIsAnInvalidScope() {
        when(iterations.findByKey(IterationKey.of("ITER-B"))).thenReturn(Optional.of(Iteration.rehydrate(
                IterationKey.of("ITER-B"), "B", null, null, "G001", Set.of(RepoId.of("repo-b")),
                IterationStatus.ACTIVE, now, now)));
        assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A", "ITER-B"), true, "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).satisfies(error -> assertThat(((BusinessException) error).getArgs()[0].toString()).contains("所选迭代不包含任何所选仓库"));
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void allBindingsContributeTheOneReleaseTargetEvenForAnIterationSubset() {
        when(bindings.listByWindow(windowId)).thenReturn(List.of(recordedBinding("ITER-B", "release/recorded"), binding("ITER-A")));
        var run = admittedRun(service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString()));
        assertThat(run.getItems()).singleElement().satisfies(item ->
                assertThat(item.getMetadata()).containsEntry("plan.releaseBranch", "release/recorded"));
    }

    @Test
    void conflictingTargetsOnASharedRepositoryAreRejectedBeforeSaving() {
        when(bindings.listByWindow(windowId)).thenReturn(List.of(
                recordedBinding("ITER-A", "release/one"), recordedBinding("ITER-B", "release/two")));
        assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).satisfies(error -> assertThat(((BusinessException) error).getArgs()[0].toString()).contains("多个 release 目标"));
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void namedFeatureIsPreservedAndIncompleteCustomConfigurationIsRejected() {
        var named = version("repo-a", "feature/custom");
        named.setBranchCreationMode(BranchCreationMode.NAMED);
        when(versions.listVersionInfo("ITER-A")).thenReturn(List.of(named));
        assertThat(admittedRun(service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString())).getItems())
                .singleElement().satisfies(item -> assertThat(item.getMetadata()).containsEntry("plan.featureBranch", "feature/custom"));
        clearInvocations(runs, plans);
        named.setFeatureBranch(null);
        assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).satisfies(error -> assertThat(((BusinessException) error).getArgs()[0].toString()).contains("自定义迭代分支缺少名称"));
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void previewRemainsPlannedAndIdempotentAfterSourceChanges() {
        var first = service.create(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", "preview-001");
        ArgumentCaptor<Run> saved = ArgumentCaptor.forClass(Run.class);
        verify(runs).save(saved.capture());
        assertThat(saved.getValue().getExecutionStatus()).isEqualTo(RunExecutionStatus.PLANNED);
        when(plans.findByRequest("operator", "preview-001")).thenReturn(Optional.of(first));
        clearInvocations(windows, bindings, iterations, repositories, versions, runs);
        assertThat(service.create(windowId.value(), List.of("repo-a", " repo-a "), List.of("ITER-A", "ITER-A"), true, "operator", "preview-001"))
                .isSameAs(first);
        verifyNoInteractions(windows, bindings, iterations, repositories, versions, runs);
        assertThatThrownBy(() -> service.create(windowId.value(), List.of("repo-a"), List.of("ITER-A"), false, "operator", "preview-001"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void anEmptyWindowCanBeDisplayedButCannotCreateAnExecution() {
        when(bindings.listByWindow(windowId)).thenReturn(List.of());
        assertThat(service.previewWindow(windowId.value())).isEmpty();
        assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a"), List.of(), true, "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void windowDisplayDoesNotSilentlyDropAMissingIteration() {
        when(iterations.findByKey(IterationKey.of("ITER-B"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.previewWindow(windowId.value()))
                .isInstanceOf(BusinessException.class).satisfies(error -> assertThat(((BusinessException) error).getArgs()[0].toString()).contains("绑定迭代不存在"));
        verifyNoInteractions(runs, plans);
    }


    @Test
    void retryCopiesOnlySelectedSnapshotTuplesWithNewIdsAndOriginalOrder() {
        Run previous = failedSource();
        List<String> selected = List.of(previous.getItems().get(3).getId().value(), previous.getItems().get(0).getId().value());
        List<String> originalIds = previous.getItems().stream().map(item -> item.getId().value()).toList();

        Run retry = admittedRun(service.admitRetry(previous, selected, "operator", java.util.UUID.randomUUID().toString()));
        ArgumentCaptor<SavedRunPlan> saved = ArgumentCaptor.forClass(SavedRunPlan.class);
        verify(plans).save(saved.capture());
        assertThat(retry.getId()).isNotEqualTo(previous.getId());
        assertThat(retry.getItems().stream().map(item -> item.getRepo().value() + "/" + item.getIterationKey().value()).toList())
                .containsExactly("repo-a/ITER-A", "repo-b/ITER-B");
        assertThat(retry.getItems().stream().map(item -> item.getPlannedOrder()).toList()).containsExactly(1, 4);
        assertThat(retry.getItems()).allSatisfy(item -> {
            assertThat(item.getId().value()).isNotIn(originalIds);
            assertThat(item.getMetadata()).containsEntry("retry.sourceRunId", previous.getId().value())
                    .containsEntry("plan.failFast", "false");
            assertThat(item.getMetadata().get("retry.sourceItemId")).isIn(selected);
            assertThat(item.getSteps()).isEmpty();
            assertThat(item.getFinalResult()).isNull();
        });
        assertThat(saved.getValue().snapshot().items().stream().map(item -> item.itemId()).toList())
                .containsExactlyElementsOf(retry.getItems().stream().map(item -> item.getId().value()).toList());
        assertThat(saved.getValue().snapshot().items().stream().map(item -> item.plannedOrder()).toList()).containsExactly(1, 4);
        assertThat(previous.getItems().stream().map(item -> item.getId().value()).toList()).containsExactlyElementsOf(originalIds);
        assertThat(previous.getItems()).allSatisfy(item -> assertThat(item.getFinalResult()).isEqualTo(RunItemResult.FAILED));
    }

    @Test
    void retryUsesOriginalFeatureAndBaseRefEvenWhenLiveConfigurationChanges() {
        Run previous = failedSource();
        CodeRepository repo = repositories.findById(RepoId.of("repo-a")).orElseThrow();
        repo.changeDefaultBranch("develop", now);
        when(versions.listVersionInfo("ITER-A")).thenReturn(List.of(version("repo-a", "feature/new")));
        clearInvocations(versions);

        Run retry = admittedRun(service.admitRetry(previous, List.of(previous.getItems().get(0).getId().value()), "operator", java.util.UUID.randomUUID().toString()));

        assertThat(retry.getItems()).singleElement().satisfies(item -> assertThat(item.getMetadata())
                .containsEntry("plan.featureBranch", "feature/ITER-A")
                .containsEntry("plan.defaultBranch", "main")
                .containsEntry("plan.featureBranchMode", "AUTO"));
        verifyNoInteractions(versions);
    }

    @Test
    void retryAcceptsBlockedAndSkippedItemsButNeverSuccessfulItems() {
        Run previous = failedSource();
        previous.getItems().get(0).finishWith(RunItemResult.MERGE_BLOCKED, now);
        previous.getItems().get(1).finishWith(RunItemResult.SKIPPED_DUE_TO_BLOCK, now);
        previous.getItems().get(2).finishWith(RunItemResult.MERGED, now);
        previous.finish(now);
        Run retry = admittedRun(service.admitRetry(previous, List.of(
                previous.getItems().get(0).getId().value(), previous.getItems().get(1).getId().value()), "operator", java.util.UUID.randomUUID().toString()));
        assertThat(retry.getItems()).hasSize(2);
        clearInvocations(runs, plans);

        assertThatThrownBy(() -> service.admitRetry(previous, List.of(previous.getItems().get(2).getId().value()), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void retryRejectsMissingPlanUnknownIdsAndOldTupleKeysBeforeSaving() {
        Run previous = failedSource();
        for (var ids : List.of(List.<String>of(), List.of("unknown-item"), List.of("RW-1::repo-a::ITER-A"))) {
            assertThatThrownBy(() -> service.admitRetry(previous, ids, "operator", java.util.UUID.randomUUID().toString())).isInstanceOf(BusinessException.class);
        }
        when(plans.findByRunId(previous.getId().value())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.admitRetry(previous, List.of(previous.getItems().get(0).getId().value()), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void retryRejectsDetachedTuplesAndRepositoryRedirection() {
        Run previous = failedSource();
        String selected = previous.getItems().get(0).getId().value();
        when(bindings.listByWindow(windowId)).thenReturn(List.of(binding("ITER-B")));
        assertThatThrownBy(() -> service.admitRetry(previous, List.of(selected), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        when(bindings.listByWindow(windowId)).thenReturn(List.of(binding("ITER-A"), binding("ITER-B")));
        CodeRepository redirected = CodeRepository.rehydrate(RepoId.of("repo-a"), "repo-a",
                "https://gitlab.invalid/different/project.git", "main", "G001", RepoType.SERVICE, GitProvider.GITLAB,
                null, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0);
        when(repositories.findById(RepoId.of("repo-a"))).thenReturn(Optional.of(redirected));
        assertThatThrownBy(() -> service.admitRetry(previous, List.of(selected), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void retryRejectsFrozenWindowAndSourceWithAnUnknownOutcome() {
        Run previous = failedSource();
        String selected = previous.getItems().get(0).getId().value();
        when(windows.findById(windowId)).thenReturn(Optional.of(window(ReleaseWindowStatus.DRAFT, true)));
        assertThatThrownBy(() -> service.admitRetry(previous, List.of(selected), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        when(windows.findById(windowId)).thenReturn(Optional.of(window(ReleaseWindowStatus.DRAFT, false)));
        previous.getItems().get(0).finishWith(RunItemResult.UNKNOWN, now);
        previous.finish(now);
        assertThatThrownBy(() -> service.admitRetry(previous, List.of(selected), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }


    @Test
    void failFastSkippedRetryRequiresExplicitSelectionOfItsEarlierBlocker() {
        Run previous = failedSource(true);
        previous.getItems().get(0).finishWith(RunItemResult.MERGE_BLOCKED, now);
        previous.getItems().get(1).finishWith(RunItemResult.SKIPPED_DUE_TO_BLOCK, now);
        previous.finish(now);
        String blockedId = previous.getItems().get(0).getId().value();
        String skippedId = previous.getItems().get(1).getId().value();

        assertThatThrownBy(() -> service.admitRetry(previous, List.of(skippedId), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());

        assertThat(admittedRun(service.admitRetry(previous, List.of(skippedId, blockedId), "operator", java.util.UUID.randomUUID().toString())).getItems())
                .hasSize(2).allSatisfy(item -> assertThat(item.getRepo().value()).isEqualTo("repo-a"));
    }

    @Test
    void featureCreationModeIsRecordedAndChangesThePlanFingerprint() {
        service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString());
        var existing = version("repo-a", "feature/ITER-A");
        existing.setBranchCreationMode(BranchCreationMode.EXISTING);
        when(versions.listVersionInfo("ITER-A")).thenReturn(List.of(existing));

        Run run = admittedRun(service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString()));
        assertThat(run.getItems()).singleElement().satisfies(item ->
                assertThat(item.getMetadata()).containsEntry("plan.featureBranchMode", "EXISTING"));
        ArgumentCaptor<SavedRunPlan> saved = ArgumentCaptor.forClass(SavedRunPlan.class);
        verify(plans, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).planHash()).isNotEqualTo(saved.getAllValues().get(1).planHash());
    }

    @Test
    void retryDoesNotGuessMissingFeatureCreationModeFromLiveConfiguration() {
        Run previous = failedSource();
        previous.getItems().get(0).putMetadata("plan.featureBranchMode", "");
        assertThatThrownBy(() -> service.admitRetry(previous, List.of(previous.getItems().get(0).getId().value()), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void credentialBearingCloneUrlsAreRejectedWithoutPersistingOrEchoingThem() {
        for (String url : List.of("https://fixture-secret@gitlab.invalid/acme/repo-a.git",
                "https://user:fixture-secret@gitlab.invalid/acme/repo-a.git",
                "https://gitlab.invalid/acme/repo-a.git?token=fixture-secret",
                "https://gitlab.invalid/acme/repo-a.git#fixture-secret")) {
            when(repositories.findById(RepoId.of("repo-a"))).thenReturn(Optional.of(repository(url)));
            assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString()))
                    .isInstanceOf(BusinessException.class).satisfies(error -> {
                        assertThat(error.getMessage()).doesNotContain("fixture-secret");
                        assertThat(java.util.Arrays.toString(((BusinessException) error).getArgs())).doesNotContain("fixture-secret");
                    });
        }
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void standardSshCloneUrlsRemainAvailableWithoutEmbeddingCredentials() {
        for (String url : List.of("git@gitlab.invalid:acme/repo-a.git", "ssh://git@gitlab.invalid/acme/repo-a.git")) {
            when(repositories.findById(RepoId.of("repo-a"))).thenReturn(Optional.of(repository(url)));
            assertThat(admittedRun(service.admit(windowId.value(), List.of("repo-a"), List.of("ITER-A"), true, "operator", java.util.UUID.randomUUID().toString())).getItems())
                    .singleElement().satisfies(item -> assertThat(item.getMetadata()).containsEntry("plan.cloneUrl", url));
        }
    }


    @Test
    void admissionRejectsEveryNonGitLabProviderBeforeSavingRunOrPlan() {
        for (GitProvider provider : List.of(GitProvider.GITHUB, GitProvider.MOCK)) {
            when(repositories.findById(RepoId.of("repo-b"))).thenReturn(Optional.of(CodeRepository.rehydrate(
                    RepoId.of("repo-b"), "repo-b", "https://git.example/acme/repo-b.git", "main", "G001",
                    RepoType.SERVICE, provider, null, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0)));
            assertThatThrownBy(() -> service.admit(windowId.value(), List.of("repo-a", "repo-b"), List.of(), true, "operator", java.util.UUID.randomUUID().toString()))
                    .isInstanceOf(BusinessException.class);
        }
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    @Test
    void retryRejectsGitHubEvenWhenTheOriginalSnapshotAndCurrentRepositoryAgree() {
        Run previous = failedSource();
        String cloneUrl = "https://github.example/acme/repo-a.git";
        previous.getItems().stream().filter(item -> item.getRepo().value().equals("repo-a")).forEach(item -> {
            item.putMetadata("plan.gitProvider", "GITHUB");
            item.putMetadata("plan.cloneUrl", cloneUrl);
        });
        SavedRunPlan original = plans.findByRunId(previous.getId().value()).orElseThrow();
        var originalSnapshot = original.snapshot();
        var githubItems = originalSnapshot.items().stream().map(item -> item.repoId().equals("repo-a")
                ? new RunPlanSnapshot.Item(item.itemId(), item.plannedOrder(), item.repoId(), item.repoName(), item.iterationKey(),
                        "GITHUB", item.defaultBranch(), item.featureBranch(), item.featureBranchMode(), item.releaseBranch(),
                        item.baseVersion(), item.devVersion(), item.targetVersion(), item.steps(),
                        item.participatingIterationKeys(), item.tagName(), item.triggerCi(), item.ciRef()) : item).toList();
        var githubSnapshot = new RunPlanSnapshot(originalSnapshot.windowKey(), originalSnapshot.groupCode(),
                originalSnapshot.windowStatus(), originalSnapshot.frozen(), originalSnapshot.failFast(), githubItems);
        when(plans.findByRunId(previous.getId().value())).thenReturn(Optional.of(new SavedRunPlan(
                original.runId(), original.windowId(), original.operator(), original.idempotencyKey(),
                original.requestHash(), original.planHash(), original.createdAt(), githubSnapshot)));
        when(repositories.findById(RepoId.of("repo-a"))).thenReturn(Optional.of(CodeRepository.rehydrate(
                RepoId.of("repo-a"), "repo-a", cloneUrl, "main", "G001", RepoType.SERVICE, GitProvider.GITHUB,
                null, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0)));

        assertThatThrownBy(() -> service.admitRetry(previous, List.of(previous.getItems().get(0).getId().value()), "operator", java.util.UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }


    @Test
    void branchStatusUsesConfiguredRefsFromTheSameReadOnlySnapshot() {
        var named = version("repo-a", "team/custom-a");
        named.setBranchCreationMode(BranchCreationMode.NAMED);
        var existing = version("repo-b", "team/existing-b");
        existing.setBranchCreationMode(BranchCreationMode.EXISTING);
        when(versions.listVersionInfo("ITER-A")).thenReturn(List.of(named, existing));
        when(versions.listVersionInfo("ITER-B")).thenReturn(List.of());
        when(settings.getNaming()).thenReturn(Optional.of(new SettingsPort.SettingsNaming(null, "candidate/{key}")));
        when(adapters.getAdapter(GitProvider.GITLAB)).thenReturn(git);
        when(git.getBranchStatus(any(), any(), any())).thenReturn(GitBranchPort.BranchStatus.present("17", "actual-sha"));

        BranchStatusView status = branchStatusService().getBranchStatus(windowId.value());

        assertThat(status.repos()).extracting(row -> row.repoId() + "/" + row.iterationKey() + "/" + row.featureBranch().branchName())
                .containsExactly("repo-a/ITER-A/team/custom-a", "repo-a/ITER-B/feature/ITER-B",
                        "repo-b/ITER-A/team/existing-b", "repo-b/ITER-B/feature/ITER-B");
        assertThat(status.repos()).allSatisfy(row -> {
            assertThat(row.releaseBranch().branchName()).isEqualTo("candidate/RW-1");
            assertThat(row.releaseBranch().latestCommit()).isEqualTo("actual-sha");
        });
        verify(git, times(1)).getBranchStatus("https://gitlab.invalid/acme/repo-a.git", "secret-token", "candidate/RW-1");
        verify(git, times(1)).getBranchStatus("https://gitlab.invalid/acme/repo-b.git", "secret-token", "candidate/RW-1");
        verifyNoInteractions(runs, plans);
    }

    @Test
    void branchStatusReusesTheRecordedSharedReleaseTarget() {
        when(bindings.listByWindow(windowId)).thenReturn(List.of(recordedBinding("ITER-B", "release/shared"), binding("ITER-A")));
        when(adapters.getAdapter(GitProvider.GITLAB)).thenReturn(git);
        when(git.getBranchStatus(any(), any(), any())).thenReturn(GitBranchPort.BranchStatus.missing("17"));

        var status = branchStatusService().getBranchStatus(windowId.value());

        assertThat(status.repos()).hasSize(4).allSatisfy(row -> {
            assertThat(row.releaseBranch().branchName()).isEqualTo("release/shared");
            assertThat(row.releaseBranch().exists()).isFalse();
        });
        verifyNoInteractions(runs, plans);
    }

    @Test
    void branchStatusRejectsConflictingTargetsBeforeRemoteReads() {
        when(bindings.listByWindow(windowId)).thenReturn(List.of(
                recordedBinding("ITER-A", "release/one"), recordedBinding("ITER-B", "release/two")));

        assertThatThrownBy(() -> branchStatusService().getBranchStatus(windowId.value()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getArgs()[0].toString()).contains("多个 release 目标"));
        verifyNoInteractions(adapters, git, runs, plans);
    }

    @Test
    void branchStatusRejectsMissingCustomBranchNamesBeforeRemoteReads() {
        for (var mode : List.of(BranchCreationMode.NAMED, BranchCreationMode.EXISTING)) {
            var invalid = version("repo-a", " ");
            invalid.setBranchCreationMode(mode);
            when(versions.listVersionInfo("ITER-A")).thenReturn(List.of(invalid));
            assertThatThrownBy(() -> branchStatusService().getBranchStatus(windowId.value()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error).getArgs()[0].toString()).contains("自定义迭代分支缺少名称"));
        }
        verifyNoInteractions(adapters, git, runs, plans);
    }

    @Test
    void branchStatusDoesNotSilentlyOmitBrokenIterationOrRepositoryReferences() {
        when(iterations.findByKey(IterationKey.of("ITER-B"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> branchStatusService().getBranchStatus(windowId.value()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(adapters, git);

        when(bindings.listByWindow(windowId)).thenReturn(List.of(binding("ITER-A")));
        when(repositories.findById(RepoId.of("repo-b"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> branchStatusService().getBranchStatus(windowId.value()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(adapters, git, runs, plans);
    }

    @Test
    void branchStatusPreservesReadFailuresInsteadOfInventingMissingRefs() {
        when(adapters.getAdapter(GitProvider.GITLAB)).thenReturn(git);
        for (String failingBranch : List.of("feature/ITER-A", "release/RW-1")) {
            RuntimeException failure = new IllegalStateException("remote read failed");
            when(git.getBranchStatus(any(), any(), any())).thenAnswer(call -> {
                if (failingBranch.equals(call.getArgument(2))) throw failure;
                return GitBranchPort.BranchStatus.present("17", "sha");
            });

            assertThatThrownBy(() -> branchStatusService().getBranchStatus(windowId.value())).isSameAs(failure);
        }
        verifyNoInteractions(runs, plans);
    }

    @Test
    void readOnlySnapshotAllowsClosedFrozenAndEmptyWindowsWithoutSavingARun() {
        for (var current : List.of(window(ReleaseWindowStatus.CLOSED, false), window(ReleaseWindowStatus.PUBLISHED, true))) {
            when(windows.findById(windowId)).thenReturn(Optional.of(current));
            var snapshot = new PlanAppService(service).getSnapshotByWindow(windowId.value());
            assertThat(snapshot.items()).hasSize(4);
            assertThat(snapshot.windowStatus()).isEqualTo(current.getStatus().name());
            assertThat(snapshot.frozen()).isEqualTo(current.isFrozen());
        }
        when(bindings.listByWindow(windowId)).thenReturn(List.of());
        assertThat(new PlanAppService(service).getSnapshotByWindow(windowId.value()).items()).isEmpty();
        assertThat(branchStatusService().getBranchStatus(windowId.value()).repos()).isEmpty();
        verifyNoInteractions(runs, plans, adapters, git);
    }

    private ReleaseWindowAppService branchStatusService() {
        return new ReleaseWindowAppService(windows, bindings, null, null, repositories, adapters, new PlanAppService(service));
    }

    private CodeRepository repository(String cloneUrl) {
        return CodeRepository.rehydrate(RepoId.of("repo-a"), "repo-a", cloneUrl, "main", "G001", RepoType.SERVICE,
                GitProvider.GITLAB, null, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0);
    }

    @Test
    void retryKeepsTheProjectIdentityWhenOnlyTheFeaturePrefixSucceeded() {
        Run previous = failedSource();
        var selected = previous.getItems().getFirst();
        selected.putMetadata("git.projectId", "17");
        var sourceTasks = runs.findTasks(previous.getId().value()).stream().map(task -> {
            if (!task.itemId().equals(selected.getId().value())) return task;
            String status = task.actionType() == io.releasehub.domain.run.ActionType.ENSURE_FEATURE ? "COMPLETED"
                    : task.actionType() == io.releasehub.domain.run.ActionType.ENSURE_RELEASE ? "FAILED" : "SKIPPED";
            return new RunPort.TaskView(task.taskId(), task.runId(), task.itemId(), task.actionType(),
                    task.taskOrder(), status, task.startedAt(), task.finishedAt(), task.errorMessage());
        }).toList();
        when(runs.findTasks(previous.getId().value())).thenReturn(sourceTasks);

        Run retry = admittedRun(service.admitRetry(previous, List.of(selected.getId().value()), "operator", "retry-project-prefix"));

        assertThat(retry.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getMetadata()).containsEntry("git.projectId", "17"));
    }

    @Test
    void retryInheritsTheIdentityConfirmedByAnotherItemOfTheSameRepository() {
        Run previous = failedSource();
        var selected = previous.getItems().getFirst();
        previous.getItems().stream().filter(item -> item.getRepo().equals(selected.getRepo()) && item != selected)
                .findFirst().orElseThrow().putMetadata("git.projectId", "17");

        Run retry = admittedRun(service.admitRetry(previous, List.of(selected.getId().value()), "operator", "retry-project-sibling"));

        assertThat(retry.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getMetadata()).containsEntry("git.projectId", "17"));
    }

    @Test
    void retryRejectsContradictoryProjectIdentitiesBeforeSavingAnyIntent() {
        Run previous = failedSource();
        var selected = previous.getItems().getFirst();
        selected.putMetadata("git.projectId", "17");
        previous.getItems().stream().filter(item -> item.getRepo().equals(selected.getRepo()) && item != selected)
                .findFirst().orElseThrow().putMetadata("git.projectId", "18");

        assertThatThrownBy(() -> service.admitRetry(previous, List.of(selected.getId().value()),
                "operator", "retry-project-conflict")).isInstanceOf(BusinessException.class);
        verify(runs, never()).save(any());
        verify(plans, never()).save(any());
    }

    private Run admittedRun(RunPlanAppService.OrchestrationAdmission admission) {
        var saved = ArgumentCaptor.forClass(Run.class);
        verify(runs, atLeastOnce()).save(saved.capture());
        return saved.getAllValues().stream()
                .filter(run -> run.getId().value().equals(admission.runId()))
                .findFirst().orElseThrow();
    }

    private Run failedSource() {
        return failedSource(false);
    }

    private Run failedSource(boolean failFast) {
        Run previous = admittedRun(service.admit(windowId.value(), List.of("repo-a", "repo-b"), List.of(), failFast, "operator", java.util.UUID.randomUUID().toString()));
        ArgumentCaptor<SavedRunPlan> saved = ArgumentCaptor.forClass(SavedRunPlan.class);
        verify(plans).save(saved.capture());
        when(plans.findByRunId(previous.getId().value())).thenReturn(Optional.of(saved.getValue()));
        when(runs.findTasks(previous.getId().value())).thenReturn(previous.getItems().stream()
                .flatMap(item -> java.util.stream.IntStream.range(0, 4).mapToObj(index -> new RunPort.TaskView(
                        "task-" + item.getId().value() + "-" + index, previous.getId().value(), item.getId().value(),
                        io.releasehub.domain.run.ActionType.orchestrationSequence().get(index),
                        item.getPlannedOrder() * 4 + index, index == 0 ? "FAILED" : "SKIPPED", now, now, null))).toList());
        previous.getItems().forEach(item -> item.finishWith(RunItemResult.FAILED, now));
        previous.finish(now);
        clearInvocations(runs, plans);
        return previous;
    }

    private WindowIteration recordedBinding(String key, String target) {
        return WindowIteration.rehydrate(WindowIterationId.generate(windowId, IterationKey.of(key)),
                windowId, IterationKey.of(key), now, target, true, null, now, now);
    }

    private WindowIteration binding(String key) {
        return WindowIteration.attach(windowId, IterationKey.of(key), now, now);
    }

    private ReleaseWindow window(ReleaseWindowStatus status, boolean frozen) {
        return ReleaseWindow.rehydrate(windowId, "RW-1", "窗口", null, now, "G001",
                status, now, now, frozen, null);
    }

    private IterationRepoVersionInfo version(String repoId, String feature) {
        return IterationRepoVersionInfo.builder().repoId(repoId).featureBranch(feature)
                .baseVersion("1.0.0").devVersion("1.1.0-SNAPSHOT").targetVersion("1.1.0").build();
    }
}