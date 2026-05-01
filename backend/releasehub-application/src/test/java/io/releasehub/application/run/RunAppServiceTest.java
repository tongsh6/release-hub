package io.releasehub.application.run;

import io.releasehub.application.conflict.ConflictDetectionAppService;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
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
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.window.WindowIteration;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RunAppService 编排测试")
class RunAppServiceTest {

    @Mock private RunPort runPort;
    @Mock private ReleaseWindowPort releaseWindowPort;
    @Mock private WindowIterationPort windowIterationPort;
    @Mock private IterationPort iterationPort;
    @Mock private IterationRepoPort iterationRepoPort;
    @Mock private CodeRepositoryPort codeRepositoryPort;
    @Mock private GitBranchAdapterFactory gitBranchAdapterFactory;
    @Mock private GitBranchPort gitBranchPort;
    @Mock private VersionUpdateAppService versionUpdateAppService;
    @Mock private ConflictDetectionAppService conflictDetectionAppService;
    @Mock private VersionDeriverUseCase versionDeriverUseCase;

    private RunAppService service;

    private final Instant now = Instant.now();
    private final String windowId = "window-1";
    private final String windowKey = "RW-1";
    private final String repoId = "repo-1";
    private final String iterationKey = "ITER-1";
    private final String releaseBranch = "release/RW-1";
    private final String featureBranch = "feature/ITER-1";

    @BeforeEach
    void setUp() {
        service = new RunAppService(runPort, releaseWindowPort, windowIterationPort, iterationPort,
                iterationRepoPort, codeRepositoryPort, gitBranchAdapterFactory, versionUpdateAppService,
                conflictDetectionAppService, versionDeriverUseCase);
    }

    private void setupWindowAndIteration() {
        ReleaseWindow rw = ReleaseWindow.rehydrate(
                ReleaseWindowId.of(windowId), windowKey, "Release 1.0", "",
                now, "G001", ReleaseWindowStatus.PUBLISHED, now, now, false, null);
        when(releaseWindowPort.findById(ReleaseWindowId.of(windowId))).thenReturn(Optional.of(rw));
        when(conflictDetectionAppService.getLatestReport(windowId)).thenReturn(Optional.of(ConflictReport.empty(windowId)));

        Iteration it = Iteration.rehydrate(
                IterationKey.of(iterationKey), "Iteration 1", "", null, "G001",
                Set.of(RepoId.of(repoId)), IterationStatus.ACTIVE, now, now);
        when(iterationPort.findByKey(IterationKey.of(iterationKey))).thenReturn(Optional.of(it));

        WindowIteration wi = WindowIteration.rehydrate(
                io.releasehub.domain.window.WindowIterationId.generate(
                        ReleaseWindowId.of(windowId), IterationKey.of(iterationKey)),
                ReleaseWindowId.of(windowId), IterationKey.of(iterationKey),
                now, releaseBranch, true, now, now, now);
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(windowId))).thenReturn(List.of(wi));

        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of(repoId), "Test Repo", "https://gitlab.com/test/repo.git",
                "main", "G001", RepoType.SERVICE, GitProvider.GITLAB, "token", false,
                0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);
        when(codeRepositoryPort.findById(RepoId.of(repoId))).thenReturn(Optional.of(repo));
        when(gitBranchAdapterFactory.getAdapter(GitProvider.GITLAB)).thenReturn(gitBranchPort);
        when(iterationRepoPort.getVersionInfo(iterationKey, repoId)).thenReturn(Optional.of(
                IterationRepoVersionInfo.builder().repoId(repoId).featureBranch(featureBranch).build()));
    }

    @Nested
    @DisplayName("startOrchestrate 正常流程")
    class HappyPath {

        @Test
        @DisplayName("feature 分支存在时成功合并")
        void shouldMergeSuccessfullyWhenFeatureBranchExists() {
            setupWindowAndIteration();

            when(gitBranchPort.getBranchStatus(any(), any(), eq(featureBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.present("abc123"));
            when(gitBranchPort.getBranchStatus(any(), any(), eq(releaseBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.missing())
                    .thenReturn(GitBranchPort.BranchStatus.present("def456"))
                    .thenReturn(GitBranchPort.BranchStatus.present("def456"));
            when(gitBranchPort.createBranch(any(), any(), eq(releaseBranch), eq("main"))).thenReturn(true);
            when(gitBranchPort.mergeBranch(any(), any(), eq(featureBranch), eq(releaseBranch), any()))
                    .thenReturn(GitBranchPort.MergeResult.success());

            Run result = service.startOrchestrate(windowId, List.of(repoId), List.of(), true, "tester");

            assertThat(result.getItems()).hasSize(1);
            var item = result.getItems().get(0);
            assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGED);
            assertThat(item.getSteps()).hasSize(4);
            assertThat(item.getSteps().get(0).result()).isEqualTo(RunItemResult.SUCCESS);  // ENSURE_FEATURE
            assertThat(item.getSteps().get(1).result()).isEqualTo(RunItemResult.BRANCH_CREATED); // ENSURE_RELEASE
            assertThat(item.getSteps().get(2).result()).isEqualTo(RunItemResult.SUCCESS);  // ENSURE_MR
            assertThat(item.getSteps().get(3).result()).isEqualTo(RunItemResult.MERGED);   // TRY_MERGE
            verify(runPort).save(any(Run.class));
        }

        @Test
        @DisplayName("release 分支已存在时幂等跳过创建")
        void shouldSkipReleaseBranchCreationWhenAlreadyExists() {
            setupWindowAndIteration();

            when(gitBranchPort.getBranchStatus(any(), any(), eq(featureBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.present("abc123"));
            when(gitBranchPort.getBranchStatus(any(), any(), eq(releaseBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.present("def456"))
                    .thenReturn(GitBranchPort.BranchStatus.present("def456"));
            when(gitBranchPort.mergeBranch(any(), any(), eq(featureBranch), eq(releaseBranch), any()))
                    .thenReturn(GitBranchPort.MergeResult.success());

            Run result = service.startOrchestrate(windowId, List.of(repoId), List.of(), true, "tester");

            var item = result.getItems().get(0);
            assertThat(item.getSteps().get(1).result()).isEqualTo(RunItemResult.BRANCH_EXISTS);
            verify(gitBranchPort, never()).createBranch(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("startOrchestrate 异常场景")
    class EdgeCases {

        @Test
        @DisplayName("feature 分支不存在时跳过该 item")
        void shouldSkipWhenFeatureBranchNotFound() {
            setupWindowAndIteration();

            when(gitBranchPort.getBranchStatus(any(), any(), eq(featureBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.missing());

            Run result = service.startOrchestrate(windowId, List.of(repoId), List.of(), true, "tester");

            var item = result.getItems().get(0);
            assertThat(item.getFinalResult()).isEqualTo(RunItemResult.SKIPPED);
            assertThat(item.getSteps()).hasSize(1);
            verify(gitBranchPort, never()).mergeBranch(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("合并冲突时 item 标记为 MERGE_BLOCKED")
        void shouldMarkBlockedOnMergeConflict() {
            setupWindowAndIteration();

            when(gitBranchPort.getBranchStatus(any(), any(), eq(featureBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.present("abc123"));
            when(gitBranchPort.getBranchStatus(any(), any(), eq(releaseBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.present("def456"))
                    .thenReturn(GitBranchPort.BranchStatus.present("def456"));
            when(gitBranchPort.mergeBranch(any(), any(), eq(featureBranch), eq(releaseBranch), any()))
                    .thenReturn(GitBranchPort.MergeResult.conflict("merge conflict"));

            Run result = service.startOrchestrate(windowId, List.of(repoId), List.of(), true, "tester");

            var item = result.getItems().get(0);
            assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGE_BLOCKED);
        }

        @Test
        @DisplayName("failFast=true 时合并冲突后停止后续")
        void shouldStopOnConflictWhenFailFast() {
            setupWindowAndIteration();
            // Add a second iteration to verify failFast stops before processing it
            Iteration it2 = Iteration.rehydrate(
                    IterationKey.of("ITER-2"), "Iteration 2", "", null, "G001",
                    Set.of(RepoId.of(repoId)), IterationStatus.ACTIVE, now, now);
            lenient().when(iterationPort.findByKey(IterationKey.of("ITER-2"))).thenReturn(Optional.of(it2));
            lenient().when(iterationRepoPort.getVersionInfo("ITER-2", repoId)).thenReturn(Optional.of(
                    IterationRepoVersionInfo.builder().repoId(repoId).featureBranch(featureBranch).build()));

            // First iteration will hit conflict and stop, second should never be processed
            when(gitBranchPort.getBranchStatus(any(), any(), eq(featureBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.present("abc123"));
            when(gitBranchPort.getBranchStatus(any(), any(), eq(releaseBranch)))
                    .thenReturn(GitBranchPort.BranchStatus.present("def456"))
                    .thenReturn(GitBranchPort.BranchStatus.present("def456"));
            when(gitBranchPort.mergeBranch(any(), any(), eq(featureBranch), eq(releaseBranch), any()))
                    .thenReturn(GitBranchPort.MergeResult.conflict("merge conflict"));

            Run result = service.startOrchestrate(windowId, List.of(repoId), List.of(), true, "tester");

            // Only 1 item — merge conflict on first iteration + failFast stops before second
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getFinalResult()).isEqualTo(RunItemResult.MERGE_BLOCKED);
        }

        @Test
        @DisplayName("存在冲突时阻断执行")
        void shouldBlockWhenConflictsExist() {
            ReleaseWindow rw = ReleaseWindow.rehydrate(
                    ReleaseWindowId.of(windowId), windowKey, "Release 1.0", "",
                    now, "G001", ReleaseWindowStatus.PUBLISHED, now, now, false, null);
            when(releaseWindowPort.findById(ReleaseWindowId.of(windowId))).thenReturn(Optional.of(rw));

            var conflictReport = io.releasehub.domain.conflict.ConflictReport.of(windowId,
                    List.of(io.releasehub.domain.conflict.ConflictItem.branchExists(repoId, "Test", iterationKey, featureBranch)));
            when(conflictDetectionAppService.getLatestReport(windowId)).thenReturn(Optional.of(conflictReport));

            assertThatThrownBy(() -> service.startOrchestrate(windowId, List.of(repoId), List.of(), true, "tester"))
                    .isInstanceOf(io.releasehub.common.exception.BusinessException.class);
        }
    }
}
