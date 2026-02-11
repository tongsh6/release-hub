package io.releasehub.application.window;

import io.releasehub.application.branchrule.BranchRuleAppService;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.window.WindowIteration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttachAppService 测试")
class AttachAppServiceTest {

    @Mock
    private ReleaseWindowPort releaseWindowPort;
    @Mock
    private IterationPort iterationPort;
    @Mock
    private WindowIterationPort windowIterationPort;
    @Mock
    private IterationRepoPort iterationRepoPort;
    @Mock
    private GitLabBranchPort gitLabBranchPort;
    @Mock
    private CodeRepositoryPort codeRepositoryPort;
    @Mock
    private BranchRuleAppService branchRuleAppService;

    private AttachAppService attachAppService;

    @BeforeEach
    void setUp() {
        attachAppService = new AttachAppService(
                releaseWindowPort, iterationPort, windowIterationPort,
                iterationRepoPort, gitLabBranchPort, codeRepositoryPort, branchRuleAppService
        );
    }

    @Test
    @DisplayName("attach 时创建 release 分支并合并 feature")
    void shouldCreateReleaseBranchAndMergeFeatureWhenAttach() {
        Instant now = Instant.now();
        ReleaseWindow window = ReleaseWindow.rehydrate(
                ReleaseWindowId.of("window-1"), "RW-1", "Window", null,
                now, "G001", ReleaseWindowStatus.DRAFT, now, now, false, null);
        Iteration iteration = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", null, null, "G001",
                Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "git@gitlab.com:test/repo.git",
                "master", "G001", RepoType.SERVICE, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(releaseWindowPort.findById(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(iteration));
        when(windowIterationPort.attach(any(), any(), any())).thenReturn(
                WindowIteration.attach(ReleaseWindowId.of("window-1"), IterationKey.of("ITER-1"), now, now));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(iterationRepoPort.getVersionInfo("ITER-1", "repo-1")).thenReturn(Optional.of(
                IterationRepoVersionInfo.builder().repoId("repo-1").featureBranch("feature/ITER-1").build()
        ));
        when(branchRuleAppService.isCompliant("release/RW-1")).thenReturn(true);
        when(gitLabBranchPort.createBranch(repo.getCloneUrl(), "release/RW-1", "master")).thenReturn(true);
        when(gitLabBranchPort.mergeBranch(eq(repo.getCloneUrl()), eq("feature/ITER-1"), eq("release/RW-1"), any()))
                .thenReturn(GitLabBranchPort.MergeResult.success());

        attachAppService.attach("window-1", List.of("ITER-1"));

        verify(gitLabBranchPort).createBranch(repo.getCloneUrl(), "release/RW-1", "master");
        verify(gitLabBranchPort).mergeBranch(eq(repo.getCloneUrl()), eq("feature/ITER-1"), eq("release/RW-1"), any());
        verify(windowIterationPort).updateReleaseBranch(eq("window-1"), eq("ITER-1"), eq("release/RW-1"), any());
        verify(windowIterationPort).updateLastMergeAt(eq("window-1"), eq("ITER-1"), any());
    }

    @Test
    @DisplayName("detach 时归档 release 分支，原因 unpublished")
    void shouldArchiveReleaseBranchWithReasonUnpublishedWhenDetach() {
        Instant now = Instant.now();
        ReleaseWindow window = ReleaseWindow.rehydrate(
                ReleaseWindowId.of("window-1"), "RW-1", "Window", null,
                now, "G001", ReleaseWindowStatus.DRAFT, now, now, false, null);
        Iteration iteration = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", null, null, "G001",
                Set.of(RepoId.of("repo-1"), RepoId.of("repo-2")), IterationStatus.ACTIVE, now, now);
        CodeRepository repo1 = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo1", "git@gitlab.com:test/repo1.git",
                "master", "G001", RepoType.SERVICE, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);
        CodeRepository repo2 = CodeRepository.rehydrate(
                RepoId.of("repo-2"), "Repo2", "git@gitlab.com:test/repo2.git",
                "master", "G001", RepoType.SERVICE, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(releaseWindowPort.findById(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(iteration));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo1));
        when(codeRepositoryPort.findById(RepoId.of("repo-2"))).thenReturn(Optional.of(repo2));

        attachAppService.detach("window-1", "ITER-1");

        verify(gitLabBranchPort).archiveBranch(repo1.getCloneUrl(), "release/RW-1", "unpublished");
        verify(gitLabBranchPort).archiveBranch(repo2.getCloneUrl(), "release/RW-1", "unpublished");
        verify(windowIterationPort).detach(ReleaseWindowId.of("window-1"), IterationKey.of("ITER-1"));
    }

    @Test
    @DisplayName("冻结窗口时禁止 attach")
    void shouldRejectAttachWhenWindowFrozen() {
        Instant now = Instant.now();
        ReleaseWindow window = ReleaseWindow.rehydrate(
                ReleaseWindowId.of("window-1"), "RW-1", "Window", null,
                now, "G001", ReleaseWindowStatus.DRAFT, now, now, false, null);
        window.freeze(now);

        when(releaseWindowPort.findById(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));

        assertThatThrownBy(() -> attachAppService.attach("window-1", List.of("ITER-1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_006"));
    }

    @Test
    @DisplayName("冻结窗口时禁止 detach")
    void shouldRejectDetachWhenWindowFrozen() {
        Instant now = Instant.now();
        ReleaseWindow window = ReleaseWindow.rehydrate(
                ReleaseWindowId.of("window-1"), "RW-1", "Window", null,
                now, "G001", ReleaseWindowStatus.DRAFT, now, now, false, null);
        window.freeze(now);

        when(releaseWindowPort.findById(ReleaseWindowId.of("window-1"))).thenReturn(Optional.of(window));

        assertThatThrownBy(() -> attachAppService.detach("window-1", "ITER-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RW_006"));
    }
}
