package io.releasehub.application.release;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.port.out.GitLabBranchPort.MergeResult;
import io.releasehub.application.release.ReleaseBranchService.BranchOperationResult;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.MergeStatus;
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
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseBranchService 测试")
class ReleaseBranchServiceTest {

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

    private ReleaseBranchService releaseBranchService;

    @BeforeEach
    void setUp() {
        releaseBranchService = new ReleaseBranchService(
                gitLabBranchPort, windowIterationPort, iterationAppService,
                iterationRepoPort, codeRepositoryPort
        );
    }

    @Nested
    @DisplayName("createReleaseBranchAndMerge - 创建 release 分支并合并")
    class CreateReleaseBranchAndMergeTest {

        private static final String WINDOW_ID = "window-001";
        private static final String WINDOW_KEY = "v1.0.0";
        private static final String ITERATION_KEY = "iter-001";
        private static final String REPO_ID = "repo-001";
        private static final String REPO_URL = "git@gitlab.com:test/repo.git";

        @Test
        @DisplayName("成功创建 release 分支并合并 feature")
        void shouldCreateReleaseBranchAndMergeFeature() {
            // 准备迭代数据
            Iteration iteration = createIteration(Set.of(RepoId.of(REPO_ID)));
            when(iterationAppService.get(ITERATION_KEY)).thenReturn(iteration);

            // 准备仓库数据
            CodeRepository repo = createRepository(REPO_ID, REPO_URL);
            when(codeRepositoryPort.findById(RepoId.of(REPO_ID))).thenReturn(Optional.of(repo));

            // 准备版本信息
            IterationRepoVersionInfo versionInfo = createVersionInfo("feature/iter-001");
            when(iterationRepoPort.getVersionInfo(ITERATION_KEY, REPO_ID)).thenReturn(Optional.of(versionInfo));

            // 配置分支操作
            when(gitLabBranchPort.branchExists(REPO_URL, "release/v1.0.0")).thenReturn(false);
            when(gitLabBranchPort.createBranch(REPO_URL, "release/v1.0.0", "master")).thenReturn(true);
            when(gitLabBranchPort.branchExists(REPO_URL, "feature/iter-001")).thenReturn(true);
            when(gitLabBranchPort.mergeBranch(eq(REPO_URL), eq("feature/iter-001"), eq("release/v1.0.0"), anyString()))
                    .thenReturn(MergeResult.success());

            // 执行
            List<BranchOperationResult> results = releaseBranchService.createReleaseBranchAndMerge(
                    WINDOW_ID, WINDOW_KEY, ITERATION_KEY
            );

            // 验证
            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(MergeStatus.SUCCESS);
            assertThat(results.get(0).repoId()).isEqualTo(REPO_ID);

            verify(gitLabBranchPort).createBranch(REPO_URL, "release/v1.0.0", "master");
            verify(gitLabBranchPort).mergeBranch(eq(REPO_URL), eq("feature/iter-001"), eq("release/v1.0.0"), anyString());
            verify(windowIterationPort).updateReleaseBranch(eq(WINDOW_ID), eq(ITERATION_KEY), eq("release/v1.0.0"), any(Instant.class));
        }

        private Iteration createIteration(Set<RepoId> repos) {
            return Iteration.rehydrate(
                    IterationKey.of(ITERATION_KEY),
                    "测试迭代",
                    null, null,
                    "G001",
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
                    false,
                    0, 0, 0, 0, 0, 0, 0,  // branch/mr counts
                    now, now, now, 0L
            );
        }

        private IterationRepoVersionInfo createVersionInfo(String featureBranch) {
            return IterationRepoVersionInfo.builder()
                                           .repoId(REPO_ID)
                                           .featureBranch(featureBranch)
                                           .baseVersion("1.0.0")
                                           .devVersion("1.1.0-SNAPSHOT")
                                           .targetVersion("1.1.0")
                                           .build();
        }

        @Test
        @DisplayName("release 分支已存在时跳过创建")
        void shouldSkipCreationWhenReleaseBranchExists() {
            Iteration iteration = createIteration(Set.of(RepoId.of(REPO_ID)));
            when(iterationAppService.get(ITERATION_KEY)).thenReturn(iteration);

            CodeRepository repo = createRepository(REPO_ID, REPO_URL);
            when(codeRepositoryPort.findById(RepoId.of(REPO_ID))).thenReturn(Optional.of(repo));

            IterationRepoVersionInfo versionInfo = createVersionInfo("feature/iter-001");
            when(iterationRepoPort.getVersionInfo(ITERATION_KEY, REPO_ID)).thenReturn(Optional.of(versionInfo));

            // release 分支已存在
            when(gitLabBranchPort.branchExists(REPO_URL, "release/v1.0.0")).thenReturn(true);
            when(gitLabBranchPort.branchExists(REPO_URL, "feature/iter-001")).thenReturn(true);
            when(gitLabBranchPort.mergeBranch(eq(REPO_URL), eq("feature/iter-001"), eq("release/v1.0.0"), anyString()))
                    .thenReturn(MergeResult.success());

            List<BranchOperationResult> results = releaseBranchService.createReleaseBranchAndMerge(
                    WINDOW_ID, WINDOW_KEY, ITERATION_KEY
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(MergeStatus.SUCCESS);

            // 不应创建分支
            verify(gitLabBranchPort, never()).createBranch(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("合并冲突时返回冲突状态")
        void shouldReturnConflictStatusWhenMergeConflict() {
            Iteration iteration = createIteration(Set.of(RepoId.of(REPO_ID)));
            when(iterationAppService.get(ITERATION_KEY)).thenReturn(iteration);

            CodeRepository repo = createRepository(REPO_ID, REPO_URL);
            when(codeRepositoryPort.findById(RepoId.of(REPO_ID))).thenReturn(Optional.of(repo));

            IterationRepoVersionInfo versionInfo = createVersionInfo("feature/iter-001");
            when(iterationRepoPort.getVersionInfo(ITERATION_KEY, REPO_ID)).thenReturn(Optional.of(versionInfo));

            when(gitLabBranchPort.branchExists(REPO_URL, "release/v1.0.0")).thenReturn(true);
            when(gitLabBranchPort.branchExists(REPO_URL, "feature/iter-001")).thenReturn(true);
            when(gitLabBranchPort.mergeBranch(eq(REPO_URL), eq("feature/iter-001"), eq("release/v1.0.0"), anyString()))
                    .thenReturn(MergeResult.conflict("pom.xml has conflicts"));

            List<BranchOperationResult> results = releaseBranchService.createReleaseBranchAndMerge(
                    WINDOW_ID, WINDOW_KEY, ITERATION_KEY
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(MergeStatus.CONFLICT);
            assertThat(results.get(0).message()).contains("pom.xml");
        }

        @Test
        @DisplayName("feature 分支不存在时跳过合并")
        void shouldSkipMergeWhenFeatureBranchNotExists() {
            Iteration iteration = createIteration(Set.of(RepoId.of(REPO_ID)));
            when(iterationAppService.get(ITERATION_KEY)).thenReturn(iteration);

            CodeRepository repo = createRepository(REPO_ID, REPO_URL);
            when(codeRepositoryPort.findById(RepoId.of(REPO_ID))).thenReturn(Optional.of(repo));

            when(iterationRepoPort.getVersionInfo(ITERATION_KEY, REPO_ID)).thenReturn(Optional.empty());

            when(gitLabBranchPort.branchExists(REPO_URL, "release/v1.0.0")).thenReturn(true);
            when(gitLabBranchPort.branchExists(REPO_URL, "feature/iter-001")).thenReturn(false);

            List<BranchOperationResult> results = releaseBranchService.createReleaseBranchAndMerge(
                    WINDOW_ID, WINDOW_KEY, ITERATION_KEY
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(MergeStatus.SUCCESS);

            verify(gitLabBranchPort, never()).mergeBranch(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("处理多个仓库")
        void shouldProcessMultipleRepositories() {
            String repoId2 = "repo-002";
            String repoUrl2 = "git@gitlab.com:test/repo2.git";

            Iteration iteration = createIteration(Set.of(RepoId.of(REPO_ID), RepoId.of(repoId2)));
            when(iterationAppService.get(ITERATION_KEY)).thenReturn(iteration);

            CodeRepository repo1 = createRepository(REPO_ID, REPO_URL);
            CodeRepository repo2 = createRepository(repoId2, repoUrl2);
            when(codeRepositoryPort.findById(RepoId.of(REPO_ID))).thenReturn(Optional.of(repo1));
            when(codeRepositoryPort.findById(RepoId.of(repoId2))).thenReturn(Optional.of(repo2));

            when(iterationRepoPort.getVersionInfo(eq(ITERATION_KEY), anyString())).thenReturn(Optional.empty());

            when(gitLabBranchPort.branchExists(anyString(), eq("release/v1.0.0"))).thenReturn(true);
            when(gitLabBranchPort.branchExists(anyString(), startsWith("feature/"))).thenReturn(false);

            List<BranchOperationResult> results = releaseBranchService.createReleaseBranchAndMerge(
                    WINDOW_ID, WINDOW_KEY, ITERATION_KEY
            );

            assertThat(results).hasSize(2);
            assertThat(results).extracting(BranchOperationResult::status)
                               .containsOnly(MergeStatus.SUCCESS);
        }
    }
}
