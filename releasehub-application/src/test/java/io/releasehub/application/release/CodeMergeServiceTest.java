package io.releasehub.application.release;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.port.out.GitLabBranchPort.MergeResult;
import io.releasehub.application.release.CodeMergeService.CodeMergeResult;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.run.MergeStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeMergeService 测试")
class CodeMergeServiceTest {

    @Mock
    private GitLabBranchPort gitLabBranchPort;
    @Mock
    private WindowIterationPort windowIterationPort;
    @Mock
    private IterationAppService iterationAppService;
    @Mock
    private IterationRepoPort iterationRepoPort;
    @Mock
    private CodeRepositoryPort codeRepositoryPort;

    private CodeMergeService codeMergeService;

    @BeforeEach
    void setUp() {
        codeMergeService = new CodeMergeService(
                gitLabBranchPort, windowIterationPort, iterationAppService,
                iterationRepoPort, codeRepositoryPort
        );
    }

    private WindowIteration createWindowIteration() {
        Instant now = Instant.now();
        return WindowIteration.attach(
                ReleaseWindowId.of("window-001"),
                IterationKey.of("iter-001"),
                now, now
        );
    }

    private Iteration createIteration(Set<RepoId> repos) {
        return Iteration.rehydrate(
                IterationKey.of("iter-001"),
                "测试迭代",
                null, null, "G001",
                repos, IterationStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
    }

    private CodeRepository createRepository(String id, String cloneUrl) {
        Instant now = Instant.now();
        return CodeRepository.rehydrate(
                RepoId.of(id),
                "测试仓库",
                cloneUrl,
                "master",
                "G001",
                RepoType.SERVICE,
                false,
                0, 0, 0, 0, 0, 0, 0,  // branch/mr counts
                now, now, now, 0L
        );
    }

    private IterationRepoVersionInfo createVersionInfo(String featureBranch) {
        return IterationRepoVersionInfo.builder()
                                       .repoId("repo-001")
                                       .featureBranch(featureBranch)
                                       .baseVersion("1.0.0")
                                       .devVersion("1.1.0-SNAPSHOT")
                                       .targetVersion("1.1.0")
                                       .build();
    }

    @Nested
    @DisplayName("mergeFeatureToRelease - 合并单个迭代")
    class MergeFeatureToReleaseTest {

        private static final String WINDOW_ID = "window-001";
        private static final String ITERATION_KEY = "iter-001";
        private static final String REPO_ID = "repo-001";
        private static final String REPO_URL = "git@gitlab.com:test/repo.git";
        private static final String RELEASE_BRANCH = "release/v1.0.0";
        private static final String FEATURE_BRANCH = "feature/iter-001";

        @Test
        @DisplayName("成功合并 feature 到 release")
        void shouldMergeFeatureToReleaseSuccessfully() {
            setupMocks();

            when(gitLabBranchPort.branchExists(REPO_URL, FEATURE_BRANCH)).thenReturn(true);
            when(gitLabBranchPort.branchExists(REPO_URL, RELEASE_BRANCH)).thenReturn(true);
            when(gitLabBranchPort.mergeBranch(eq(REPO_URL), eq(FEATURE_BRANCH), eq(RELEASE_BRANCH), anyString()))
                    .thenReturn(MergeResult.success());

            List<CodeMergeResult> results = codeMergeService.mergeFeatureToRelease(WINDOW_ID, ITERATION_KEY);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(MergeStatus.SUCCESS);
            assertThat(results.get(0).repoId()).isEqualTo(REPO_ID);
            assertThat(results.get(0).sourceBranch()).isEqualTo(FEATURE_BRANCH);
            assertThat(results.get(0).targetBranch()).isEqualTo(RELEASE_BRANCH);
            assertThat(results.get(0).mergedAt()).isNotNull();

            verify(windowIterationPort).updateLastMergeAt(eq(WINDOW_ID), eq(ITERATION_KEY), any(Instant.class));
        }

        private void setupMocks() {
            WindowIteration wi = createWindowIteration();
            when(windowIterationPort.findByWindowIdAndIterationKey(any(), any()))
                    .thenReturn(Optional.of(wi));
            when(windowIterationPort.getReleaseBranch(WINDOW_ID, ITERATION_KEY)).thenReturn(RELEASE_BRANCH);

            Iteration iteration = createIteration(Set.of(RepoId.of(REPO_ID)));
            when(iterationAppService.get(ITERATION_KEY)).thenReturn(iteration);

            CodeRepository repo = createRepository(REPO_ID, REPO_URL);
            when(codeRepositoryPort.findById(RepoId.of(REPO_ID))).thenReturn(Optional.of(repo));

            IterationRepoVersionInfo versionInfo = createVersionInfo(FEATURE_BRANCH);
            when(iterationRepoPort.getVersionInfo(ITERATION_KEY, REPO_ID)).thenReturn(Optional.of(versionInfo));
        }

        @Test
        @DisplayName("合并冲突时返回冲突状态")
        void shouldReturnConflictWhenMergeConflicts() {
            setupMocks();

            when(gitLabBranchPort.branchExists(REPO_URL, FEATURE_BRANCH)).thenReturn(true);
            when(gitLabBranchPort.branchExists(REPO_URL, RELEASE_BRANCH)).thenReturn(true);
            when(gitLabBranchPort.mergeBranch(eq(REPO_URL), eq(FEATURE_BRANCH), eq(RELEASE_BRANCH), anyString()))
                    .thenReturn(MergeResult.conflict("Conflict in pom.xml"));

            List<CodeMergeResult> results = codeMergeService.mergeFeatureToRelease(WINDOW_ID, ITERATION_KEY);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(MergeStatus.CONFLICT);
            assertThat(results.get(0).message()).contains("pom.xml");
        }

        @Test
        @DisplayName("feature 分支不存在时跳过")
        void shouldSkipWhenFeatureBranchNotExists() {
            setupMocks();

            when(gitLabBranchPort.branchExists(REPO_URL, FEATURE_BRANCH)).thenReturn(false);

            List<CodeMergeResult> results = codeMergeService.mergeFeatureToRelease(WINDOW_ID, ITERATION_KEY);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(MergeStatus.SUCCESS); // 跳过视为成功
            assertThat(results.get(0).message()).contains("does not exist");

            verify(gitLabBranchPort, never()).mergeBranch(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("release 分支不存在时返回失败")
        void shouldFailWhenReleaseBranchNotExists() {
            setupMocks();

            when(gitLabBranchPort.branchExists(REPO_URL, FEATURE_BRANCH)).thenReturn(true);
            when(gitLabBranchPort.branchExists(REPO_URL, RELEASE_BRANCH)).thenReturn(false);

            List<CodeMergeResult> results = codeMergeService.mergeFeatureToRelease(WINDOW_ID, ITERATION_KEY);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(MergeStatus.FAILED);
        }

        @Test
        @DisplayName("窗口-迭代关联不存在时返回空列表")
        void shouldReturnEmptyWhenRelationNotExists() {
            when(windowIterationPort.findByWindowIdAndIterationKey(any(), any()))
                    .thenReturn(Optional.empty());

            List<CodeMergeResult> results = codeMergeService.mergeFeatureToRelease(WINDOW_ID, ITERATION_KEY);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("release 分支未设置时返回空列表")
        void shouldReturnEmptyWhenReleaseBranchNotSet() {
            WindowIteration wi = createWindowIteration();
            when(windowIterationPort.findByWindowIdAndIterationKey(any(), any()))
                    .thenReturn(Optional.of(wi));
            when(windowIterationPort.getReleaseBranch(WINDOW_ID, ITERATION_KEY)).thenReturn(null);

            List<CodeMergeResult> results = codeMergeService.mergeFeatureToRelease(WINDOW_ID, ITERATION_KEY);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("mergeAllFeaturesToRelease - 批量合并")
    class MergeAllFeaturesToReleaseTest {

        @Test
        @DisplayName("合并所有迭代的代码")
        void shouldMergeAllIterations() {
            String windowId = "window-001";
            String iterKey1 = "iter-001";
            String iterKey2 = "iter-002";

            Instant now = Instant.now();
            WindowIteration wi1 = WindowIteration.attach(
                    ReleaseWindowId.of(windowId), IterationKey.of(iterKey1), now, now);
            WindowIteration wi2 = WindowIteration.attach(
                    ReleaseWindowId.of(windowId), IterationKey.of(iterKey2), now, now);

            when(windowIterationPort.listByWindow(any(ReleaseWindowId.class)))
                    .thenReturn(List.of(wi1, wi2));

            // 模拟第一个迭代合并（无关联，返回空）
            when(windowIterationPort.findByWindowIdAndIterationKey(any(), eq(IterationKey.of(iterKey1))))
                    .thenReturn(Optional.empty());
            when(windowIterationPort.findByWindowIdAndIterationKey(any(), eq(IterationKey.of(iterKey2))))
                    .thenReturn(Optional.empty());

            List<CodeMergeResult> results = codeMergeService.mergeAllFeaturesToRelease(windowId);

            // 两个迭代都无法合并（因为关联不存在）
            assertThat(results).isEmpty();

            verify(windowIterationPort).listByWindow(any(ReleaseWindowId.class));
        }

        @Test
        @DisplayName("无迭代时返回空列表")
        void shouldReturnEmptyWhenNoIterations() {
            when(windowIterationPort.listByWindow(any(ReleaseWindowId.class)))
                    .thenReturn(List.of());

            List<CodeMergeResult> results = codeMergeService.mergeAllFeaturesToRelease("window-001");

            assertThat(results).isEmpty();
        }
    }
}
