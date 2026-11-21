package io.releasehub.application.run;

import io.releasehub.application.conflict.ConflictDetectionAppService;
import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.domain.run.RunExecutionStatus;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionUpdateAppService;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.conflict.ConflictReport;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.version.BuildTool;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RunAppService 编排测试")
class RunAppServiceTest {

    @Mock private RunPort runPort;
    @Mock private ReleaseWindowPort releaseWindowPort;
    @Mock private WindowIterationPort windowIterationPort;
    @Mock private CodeRepositoryPort codeRepositoryPort;
    @Mock private GitBranchAdapterFactory gitBranchAdapterFactory;
    @Mock private GitBranchPort gitBranchPort;
    @Mock private VersionUpdateAppService versionUpdateAppService;
    @Mock private ConflictDetectionAppService conflictDetectionAppService;
    @Mock private io.releasehub.application.settings.SettingsPort settingsPort;
    @Mock private java.time.Clock clock;
    @Mock private io.releasehub.application.run.plan.RunPlanAppService runPlanAppService;
    @Mock private io.releasehub.application.branchrule.BranchRuleUseCase branchRuleUseCase;

    private RunAppService service;

    private final Instant now = Instant.parse("2026-05-11T10:00:00Z");
    private final String windowId = "window-1";
    private final String windowKey = "RW-1";
    private final String repoId = "repo-1";
    private final String releaseBranch = "release/RW-1";

    @BeforeEach
    void setUp() {
        service = new RunAppService(runPort, releaseWindowPort, windowIterationPort,
                codeRepositoryPort, gitBranchAdapterFactory, versionUpdateAppService,
                conflictDetectionAppService, settingsPort, clock, runPlanAppService, branchRuleUseCase);
        lenient().when(clock.instant()).thenReturn(now);
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.of("UTC"));
        lenient().when(settingsPort.getNaming()).thenReturn(Optional.empty());
    }

    private static final String SOURCE_SHA = "a".repeat(40);
    private static final String MERGE_SHA = "b".repeat(40);
    private static final String TAG = "v1.2.0";
    private static final String PROJECT = "17";

    private CodeRepository repository(String id) {
        return CodeRepository.rehydrate(RepoId.of(id), "Test Repo", "https://gitlab.com/test/" + id + ".git",
                "main", "G001", RepoType.SERVICE, GitProvider.GITLAB, "token", false,
                0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);
    }

    private RunItem cleanupItem(String id, int order, boolean ci, String tag) {
        RunItem item = RunItem.createForRepository(windowKey, RepoId.of(id), order, now);
        item.putMetadata("plan.windowId", windowId);
        item.putMetadata("plan.cloneUrl", repository(id).getCloneUrl());
        item.putMetadata("plan.gitProvider", "GITLAB");
        item.putMetadata("plan.releaseBranch", releaseBranch);
        item.putMetadata("plan.defaultBranch", "main");
        item.putMetadata("plan.tagName", tag);
        item.putMetadata("plan.triggerCi", Boolean.toString(ci));
        item.putMetadata("plan.ciRef", ci ? tag : null);
        item.putMetadata("plan.participatingIterationKeys", "[\"ITER-1\",\"ITER-2\"]");
        return item;
    }

    private GitBranchPort.MergeResult confirmedMerge() {
        return GitBranchPort.MergeResult.success().withEvidence(new GitBranchPort.MrEvidence(PROJECT,
                releaseBranch, "main", "7", "https://gitlab.com/test/repo/-/merge_requests/7",
                SOURCE_SHA, MERGE_SHA, GitBranchPort.MrDisposition.CREATED, null));
    }

    private Run setupCleanupRun(boolean ci) {
        Run run = Run.start(RunType.WINDOW_CLEANUP, "system", now);
        run.addItem(cleanupItem(repoId, 1, ci, TAG));
        when(runPlanAppService.admitCleanup(windowId, "system", ci))
                .thenReturn(new io.releasehub.application.run.plan.RunPlanAppService.CleanupAdmission(run, true));
        lenient().when(codeRepositoryPort.findById(RepoId.of(repoId))).thenReturn(Optional.of(repository(repoId)));
        lenient().when(gitBranchAdapterFactory.getAdapter(GitProvider.GITLAB)).thenReturn(gitBranchPort);
        lenient().when(gitBranchPort.getBranchStatus(any(), any(), eq(releaseBranch)))
                .thenReturn(GitBranchPort.BranchStatus.present(SOURCE_SHA));
        lenient().when(gitBranchPort.mergeBranch(any(), any(), eq(releaseBranch), eq("main"), any()))
                .thenReturn(confirmedMerge());
        lenient().when(gitBranchPort.readTag(any(), any(), eq(PROJECT), eq(TAG)))
                .thenReturn(new GitReadResult<>(GitReadResult.Status.NOT_FOUND, null));
        lenient().when(gitBranchPort.createTag(any(), any(), eq(PROJECT), eq(TAG), eq(MERGE_SHA), any()))
                .thenReturn(GitWriteResult.applied(new GitBranchPort.TagEvidence(PROJECT, TAG, MERGE_SHA)));
        lenient().when(gitBranchPort.triggerPipeline(any(), any(), eq(PROJECT), eq(TAG), eq(MERGE_SHA)))
                .thenReturn(GitWriteResult.applied(new GitBranchPort.PipelineEvidence(PROJECT, "101", TAG,
                        MERGE_SHA, "https://gitlab.com/test/repo/-/pipelines/101")));
        return run;
    }

    @Nested
    @DisplayName("窗口生命周期守卫")
    class WindowGuardTests {
        @Test
        @DisplayName("窗口关闭后禁止继续执行版本更新")
        void shouldRejectVersionUpdateWhenWindowClosed() {
            ReleaseWindow rw = ReleaseWindow.rehydrate(
                    ReleaseWindowId.of(windowId), windowKey, "Release 1.0", "",
                    now, "G001", ReleaseWindowStatus.CLOSED, now, now, false, now);
            when(releaseWindowPort.findById(ReleaseWindowId.of(windowId))).thenReturn(Optional.of(rw));

            assertThatThrownBy(() -> service.executeVersionUpdate(
                    windowId, repoId, "1.2.3", io.releasehub.domain.version.BuildTool.MAVEN,
                    "/tmp/repo", "pom.xml", null, "tester"))
                    .isInstanceOf(io.releasehub.common.exception.BusinessException.class)
                    .satisfies(ex -> assertThat(((io.releasehub.common.exception.BusinessException) ex).getCode()).isEqualTo("RW_009"));
        }

        @Test
        @DisplayName("窗口关闭后禁止继续执行批量版本更新")
        void shouldRejectBatchVersionUpdateWhenWindowClosed() {
            ReleaseWindow rw = ReleaseWindow.rehydrate(
                    ReleaseWindowId.of(windowId), windowKey, "Release 1.0", "",
                    now, "G001", ReleaseWindowStatus.CLOSED, now, now, false, now);
            when(releaseWindowPort.findById(ReleaseWindowId.of(windowId))).thenReturn(Optional.of(rw));

            assertThatThrownBy(() -> service.executeBatchVersionUpdate(
                    windowId,
                    List.of(new RunAppService.RepoVersionUpdateInfo(
                            repoId, io.releasehub.domain.version.BuildTool.MAVEN, "/tmp/repo", "pom.xml", null)),
                    "1.2.3",
                    "tester"))
                    .isInstanceOf(io.releasehub.common.exception.BusinessException.class)
                    .satisfies(ex -> assertThat(((io.releasehub.common.exception.BusinessException) ex).getCode()).isEqualTo("RW_009"));
        }
    }

    @Nested
    @DisplayName("retry 版本更新")
    class VersionUpdateRetry {

        @Test
        @DisplayName("只重试版本更新失败项，并保留来源追踪")
        void shouldRetryOnlyFailedVersionUpdateItemsWithTraceability() {
            Instant previousStartedAt = now.minusSeconds(60);
            Run previous = Run.start(RunType.VERSION_UPDATE, "tester", previousStartedAt);

            RunItem successItem = RunItem.createForRepository(windowKey, RepoId.of("repo-success"), 1, previousStartedAt);
            successItem.putMetadata("versionUpdate.buildTool", BuildTool.MAVEN.name());
            successItem.putMetadata("versionUpdate.branchName", releaseBranch);
            successItem.putMetadata("versionUpdate.repoPath", "/tmp/success");
            successItem.putMetadata("versionUpdate.targetVersion", "1.2.3");
            successItem.putMetadata("versionUpdate.pomPath", "pom.xml");
            successItem.finishWith(RunItemResult.VERSION_UPDATE_SUCCESS, previousStartedAt);
            previous.addItem(successItem);

            RunItem failedItem = RunItem.createForRepository(windowKey, RepoId.of(repoId), 2, previousStartedAt);
            failedItem.putMetadata("versionUpdate.buildTool", BuildTool.MAVEN.name());
            failedItem.putMetadata("versionUpdate.branchName", releaseBranch);
            failedItem.putMetadata("versionUpdate.repoPath", "/tmp/repo");
            failedItem.putMetadata("versionUpdate.targetVersion", "1.2.3");
            failedItem.putMetadata("versionUpdate.pomPath", "pom.xml");
            failedItem.finishWith(RunItemResult.VERSION_UPDATE_FAILED, previousStartedAt);
            previous.addItem(failedItem);
            previous.finish(previousStartedAt.plusSeconds(1));

            when(runPort.findById(previous.getId().value())).thenReturn(Optional.of(previous));
            when(codeRepositoryPort.findById(RepoId.of(repoId))).thenReturn(Optional.of(CodeRepository.rehydrate(
                    RepoId.of(repoId), "Test Repo", "https://gitlab.com/test/repo.git",
                    "main", "G001", RepoType.SERVICE, GitProvider.GITLAB, "token", false,
                    0, 0, 0, 0, 0, 0, 0, null, now, now, 0L)));
            when(versionUpdateAppService.updateVersion(any()))
                    .thenReturn(VersionUpdateResult.success("1.0.0", "1.2.3", "<diff />", "pom.xml"));

            Run result = service.retry(
                    previous.getId().value(),
                    List.of(failedItem.getId().value()),
                    "retryer"
            );

            assertThat(result.getRunType()).isEqualTo(RunType.VERSION_UPDATE);
            assertThat(result.getItems()).hasSize(1);
            RunItem retriedItem = result.getItems().get(0);
            assertThat(retriedItem.getRepo().value()).isEqualTo(repoId);
            assertThat(retriedItem.getFinalResult()).isEqualTo(RunItemResult.VERSION_UPDATE_SUCCESS);
            assertThat(retriedItem.getMetadata())
                    .containsEntry("retry.sourceRunId", previous.getId().value())
                    .containsEntry("retry.sourceItemId", failedItem.getId().value())
                    .containsEntry("versionUpdate.targetVersion", "1.2.3");

            ArgumentCaptor<VersionUpdateRequest> requestCaptor = ArgumentCaptor.forClass(VersionUpdateRequest.class);
            verify(versionUpdateAppService, times(1)).updateVersion(requestCaptor.capture());
            assertThat(requestCaptor.getValue().repoId().value()).isEqualTo(repoId);
            assertThat(requestCaptor.getValue().branchName()).isEqualTo(releaseBranch);
            assertThat(requestCaptor.getValue().repoPath()).isEqualTo("/tmp/repo");
            verify(runPort).save(any(Run.class));
        }
    }

    @Nested
    @DisplayName("窗口仓库收尾")
    class CleanupCi {
        @Test
        void shouldRetainBranchesAndSkipUnselectedCi() {
            setupCleanupRun(false);
            Run result = service.executeCleanup(windowId, "system", false);
            RunItem item = result.getItems().getFirst();
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
            assertThat(item.getIterationKey()).isNull();
            assertThat(item.getMetadata()).containsEntry("cleanup.referencePolicy", "RETAIN");
            assertThat(item.getSteps()).extracting(step -> step.actionType())
                    .containsExactly(ActionType.MERGE_TO_MASTER, ActionType.CREATE_TAG, ActionType.TRIGGER_CI);
            assertThat(item.getSteps().getLast().result()).isEqualTo(RunItemResult.SKIPPED);
            verify(gitBranchPort).createTag(any(), any(), eq(PROJECT), eq(TAG), eq(MERGE_SHA), any());
            verify(gitBranchPort, never()).getBranchStatus(any(), any(), eq("main"));
            verify(gitBranchPort, never()).triggerPipeline(any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).archiveBranch(any(), any(), any(), any());
            verify(gitBranchPort, never()).deleteBranch(any(), any(), any());
            verifyNoInteractions(windowIterationPort);
        }

        @Test
        void shouldStopDependentWritesAfterRejectedTag() {
            setupCleanupRun(true);
            when(gitBranchPort.createTag(any(), any(), any(), any(), any(), any()))
                    .thenReturn(GitWriteResult.rejected("PERMISSION_DENIED"));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getItems().getFirst().getFinalResult()).isEqualTo(RunItemResult.FAILED);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
            assertThat(result.getItems().getFirst().getSteps().getLast().result()).isEqualTo(RunItemResult.SKIPPED_DUE_TO_BLOCK);
            verify(gitBranchPort, never()).triggerPipeline(any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).archiveBranch(any(), any(), any(), any());
        }

        @Test
        void shouldPreserveUnknownTagAndForbidOrdinaryRetry() {
            setupCleanupRun(true);
            when(gitBranchPort.createTag(any(), any(), any(), any(), any(), any()))
                    .thenReturn(GitWriteResult.unknown("RESPONSE_UNCONFIRMED"));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
            assertThat(result.getItems().getFirst().getFinalResult()).isEqualTo(RunItemResult.UNKNOWN);
            verify(gitBranchPort, never()).triggerPipeline(any(), any(), any(), any(), any());
            verify(gitBranchPort, times(1)).readTag(any(), any(), any(), any());
            when(runPort.findById(result.getId().value())).thenReturn(Optional.of(result));
            assertThatThrownBy(() -> service.retry(result.getId().value(), List.of(result.getItems().getFirst().getId().value()), "retryer"))
                    .isInstanceOf(io.releasehub.common.exception.BusinessException.class);
            verify(runPlanAppService, never()).admitRetry(any(), any(), any());
        }

        @Test
        void shouldPreserveUnknownMergeAndSkipTagAndCi() {
            setupCleanupRun(true);
            when(gitBranchPort.mergeBranch(any(), any(), any(), any(), any()))
                    .thenReturn(GitBranchPort.MergeResult.unknown("MERGE_UNCONFIRMED").withEvidence(confirmedMerge().mrEvidence()));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
            assertThat(result.getItems().getFirst().getMetadata()).containsEntry("git.mrIid", "7");
            verify(gitBranchPort, never()).readTag(any(), any(), any(), any());
            verify(gitBranchPort, never()).createTag(any(), any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).triggerPipeline(any(), any(), any(), any(), any());
        }

        @Test
        void shouldKeepApprovalBlockEvidence() {
            setupCleanupRun(true);
            when(gitBranchPort.mergeBranch(any(), any(), any(), any(), any()))
                    .thenReturn(GitBranchPort.MergeResult.blocked(GitBranchPort.MergeBlockReason.APPROVAL_REQUIRED, "Approval required")
                            .withEvidence(confirmedMerge().mrEvidence()));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getItems().getFirst().getFinalResult()).isEqualTo(RunItemResult.MERGE_BLOCKED);
            assertThat(result.getItems().getFirst().getMetadata()).containsEntry("git.blockReason", "APPROVAL_REQUIRED");
            verify(gitBranchPort, never()).createTag(any(), any(), any(), any(), any(), any());
        }

        @Test
        void shouldRejectMissingReleaseWithoutPublishingDefaultBranch() {
            setupCleanupRun(true);
            when(gitBranchPort.getBranchStatus(any(), any(), eq(releaseBranch))).thenReturn(GitBranchPort.BranchStatus.missing());
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
            verify(gitBranchPort, never()).mergeBranch(any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).createTag(any(), any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).triggerPipeline(any(), any(), any(), any(), any());
        }

        @Test
        void shouldReuseExactTagAndRecordConfirmedPipelineEvidence() {
            setupCleanupRun(true);
            when(gitBranchPort.readTag(any(), any(), any(), any()))
                    .thenReturn(GitReadResult.found(new GitBranchPort.TagEvidence(PROJECT, TAG, MERGE_SHA)));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
            RunItem item = result.getItems().getFirst();
            assertThat(item.getSteps().get(1).result()).isEqualTo(RunItemResult.ALREADY_EXISTS);
            assertThat(item.getMetadata()).containsEntry("git.pipelineId", "101")
                    .containsEntry("git.pipelineSha", MERGE_SHA).containsEntry("git.pipelineRef", TAG);
            verify(gitBranchPort, never()).createTag(any(), any(), any(), any(), any(), any());
            verify(gitBranchPort).triggerPipeline(any(), any(), eq(PROJECT), eq(TAG), eq(MERGE_SHA));
        }

        @Test
        void shouldBlockTagPointingElsewhereWithoutOverwritingIt() {
            setupCleanupRun(true);
            when(gitBranchPort.readTag(any(), any(), any(), any()))
                    .thenReturn(GitReadResult.found(new GitBranchPort.TagEvidence(PROJECT, TAG, "c".repeat(40))));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
            verify(gitBranchPort, never()).createTag(any(), any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).triggerPipeline(any(), any(), any(), any(), any());
        }

        @Test
        void shouldReuseConcurrentExactTagAfterRejectionWithoutPostingTwice() {
            setupCleanupRun(true);
            when(gitBranchPort.readTag(any(), any(), any(), any()))
                    .thenReturn(new GitReadResult<>(GitReadResult.Status.NOT_FOUND, null))
                    .thenReturn(GitReadResult.found(new GitBranchPort.TagEvidence(PROJECT, TAG, MERGE_SHA)));
            when(gitBranchPort.createTag(any(), any(), any(), any(), any(), any()))
                    .thenReturn(GitWriteResult.rejected("HTTP_400"));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
            verify(gitBranchPort, times(1)).createTag(any(), any(), any(), any(), any(), any());
            verify(gitBranchPort, times(1)).triggerPipeline(any(), any(), any(), any(), any());
        }

        @Test
        void shouldNotInterpretUnknownPipelineAsNotConfigured() {
            setupCleanupRun(true);
            when(gitBranchPort.triggerPipeline(any(), any(), any(), any(), any()))
                    .thenReturn(GitWriteResult.unknown("RESPONSE_UNCONFIRMED"));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.NEEDS_REVIEW);
            assertThat(result.getItems().getFirst().getSteps().getLast().result()).isEqualTo(RunItemResult.UNKNOWN);
            verify(gitBranchPort, times(1)).triggerPipeline(any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).archiveBranch(any(), any(), any(), any());
        }

        @Test
        void shouldReturnExistingAdmissionWithoutAnotherExecution() {
            Run admitted = setupCleanupRun(false);
            when(runPlanAppService.admitCleanup(windowId, "system", false))
                    .thenReturn(new io.releasehub.application.run.plan.RunPlanAppService.CleanupAdmission(admitted, false));
            assertThat(service.executeCleanup(windowId, "system", false)).isSameAs(admitted);
            verifyNoInteractions(gitBranchPort, codeRepositoryPort, gitBranchAdapterFactory, runPort);
        }

        @Test
        void shouldStopAfterLocalCheckpointFailureFollowingConfirmedMerge() {
            Run admitted = setupCleanupRun(true);
            var saves = new java.util.concurrent.atomic.AtomicInteger();
            org.mockito.Mockito.doAnswer(invocation -> {
                if (saves.incrementAndGet() == 2) throw new IllegalStateException("checkpoint failed");
                return null;
            }).when(runPort).save(any());
            assertThatThrownBy(() -> service.executeCleanup(windowId, "system", true))
                    .isInstanceOf(IllegalStateException.class).hasMessage("checkpoint failed");
            assertThat(admitted.getExecutionStatus()).isEqualTo(RunExecutionStatus.RUNNING);
            verify(gitBranchPort, times(1)).mergeBranch(any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).readTag(any(), any(), any(), any());
            verify(gitBranchPort, never()).createTag(any(), any(), any(), any(), any(), any());
            verify(gitBranchPort, never()).triggerPipeline(any(), any(), any(), any(), any());
        }

        @Test
        void shouldNotGuessMergeCommitForTagFromCurrentDefaultTip() {
            setupCleanupRun(true);
            when(gitBranchPort.mergeBranch(any(), any(), any(), any(), any()))
                    .thenReturn(GitBranchPort.MergeResult.success().withEvidence(new GitBranchPort.MrEvidence(PROJECT,
                            releaseBranch, "main", null, null, SOURCE_SHA, null,
                            GitBranchPort.MrDisposition.ALREADY_MERGED, null)));
            Run result = service.executeCleanup(windowId, "system", true);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.FAILED);
            assertThat(result.getItems().getFirst().getSteps().get(1).message()).contains("合并提交 SHA");
            verify(gitBranchPort, never()).getBranchStatus(any(), any(), eq("main"));
            verify(gitBranchPort, never()).createTag(any(), any(), any(), any(), any(), any());
        }

        @Test
        void shouldContinueAnotherRepositoryAfterConfirmedTagFailure() {
            Run admitted = setupCleanupRun(false);
            admitted.addItem(cleanupItem("repo-2", 2, false, TAG));
            when(codeRepositoryPort.findById(RepoId.of("repo-2"))).thenReturn(Optional.of(repository("repo-2")));
            when(gitBranchPort.createTag(eq(repository(repoId).getCloneUrl()), any(), any(), any(), any(), any()))
                    .thenReturn(GitWriteResult.rejected("PERMISSION_DENIED"));
            Run result = service.executeCleanup(windowId, "system", false);
            assertThat(result.getExecutionStatus()).isEqualTo(RunExecutionStatus.PARTIAL_FAILED);
            assertThat(result.getItems()).extracting(RunItem::getFinalResult)
                    .containsExactly(RunItemResult.FAILED, RunItemResult.SUCCESS);
            verify(gitBranchPort).createTag(eq(repository("repo-2").getCloneUrl()), any(), any(), any(), eq(MERGE_SHA), any());
        }
    }
}
