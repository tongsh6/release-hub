package io.releasehub.application.iteration;

import io.releasehub.application.branchrule.BranchRuleAppService;
import io.releasehub.application.group.GroupPort;
import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionDeriver;
import io.releasehub.application.version.VersionExtractor;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IterationAppService 测试")
class IterationAppServiceTest {

    @Mock
    private IterationPort iterationPort;
    @Mock
    private ReleaseWindowPort releaseWindowPort;
    @Mock
    private WindowIterationPort windowIterationPort;
    @Mock
    private CodeRepositoryPort codeRepositoryPort;
    @Mock
    private IterationRepoPort iterationRepoPort;
    @Mock
    private GitLabBranchPort gitLabBranchPort;
    @Mock
    private VersionDeriver versionDeriver;
    @Mock
    private VersionExtractor versionExtractor;
    @Mock
    private BranchRuleAppService branchRuleAppService;
    @Mock
    private GroupPort groupPort;

    private IterationAppService iterationAppService;

    @BeforeEach
    void setUp() {
        iterationAppService = new IterationAppService(
                iterationPort, releaseWindowPort, windowIterationPort,
                codeRepositoryPort, iterationRepoPort, gitLabBranchPort,
                branchRuleAppService, versionDeriver, versionExtractor, groupPort
        );
    }

    @Test
    @DisplayName("create 时生成迭代标识且允许空仓库")
    void shouldGenerateIterationKeyAndAllowEmptyRepos() {
        ArgumentCaptor<Iteration> captor = ArgumentCaptor.forClass(Iteration.class);
        Group group = Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L);
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(group));
        when(groupPort.countChildren("G001")).thenReturn(0L);

        Iteration created = iterationAppService.create("Iter", "Desc", LocalDate.now(), "G001", null);

        verify(iterationPort).save(captor.capture());
        Iteration saved = captor.getValue();
        assertThat(saved.getId().value()).matches("ITER-\\d{8}-[0-9A-F]{4}");
        assertThat(saved.getRepos()).isEmpty();
        assertThat(created.getId().value()).startsWith("ITER-");
    }

    @Test
    @DisplayName("group 非末端节点时创建失败")
    void shouldFailCreateWhenGroupHasChildren() {
        Instant now = Instant.now();
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, now, now, 0L)));
        when(groupPort.countChildren("G001")).thenReturn(1L);

        assertThatThrownBy(() -> iterationAppService.create("Iter", "Desc", LocalDate.now(), "G001", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("GROUP_008"));
    }

    @Test
    @DisplayName("group 不存在时创建失败")
    void shouldFailCreateWhenGroupNotFound() {
        when(groupPort.findByCode("G404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> iterationAppService.create("Iter", "Desc", LocalDate.now(), "G404", null))
                .isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo("GROUP_002"));
    }

    @Test
    @DisplayName("addRepos 时创建 feature 分支并保存版本信息")
    void shouldCreateFeatureBranchAndSaveVersionInfoWhenAddRepos() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.<RepoId>of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "git@gitlab.com:test/repo.git",
                "master", "G001", false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriver.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriver.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(branchRuleAppService.isCompliant("feature/ITER-1")).thenReturn(true);
        when(gitLabBranchPort.createBranch(repo.getCloneUrl(), "feature/ITER-1", "master")).thenReturn(true);

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"));

        verify(gitLabBranchPort).createBranch(repo.getCloneUrl(), "feature/ITER-1", "master");
        verify(iterationRepoPort).saveWithVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"),
                eq("feature/ITER-1"), eq("SYSTEM"), any(Instant.class));
        verify(iterationPort).save(any(Iteration.class));
    }

    @Test
    @DisplayName("removeRepos 时归档 feature 分支，原因 unpublished")
    void shouldArchiveFeatureBranchWithReasonUnpublishedWhenRemoveRepos() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "git@gitlab.com:test/repo.git",
                "master", "G001", false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        IterationRepoVersionInfo versionInfo = IterationRepoVersionInfo.builder()
                                                                       .repoId("repo-1")
                                                                       .featureBranch("feature/ITER-1")
                                                                       .build();
        when(iterationRepoPort.getVersionInfo("ITER-1", "repo-1"))
                .thenReturn(Optional.of(versionInfo));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));

        iterationAppService.removeRepos("ITER-1", Set.of("repo-1"));

        verify(gitLabBranchPort).archiveBranch(repo.getCloneUrl(), "feature/ITER-1", "unpublished");
        verify(iterationPort).save(any(Iteration.class));
    }

    @Test
    @DisplayName("delete 时存在仓库则失败")
    void shouldFailDeleteWhenHasRepos() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> iterationAppService.delete("ITER-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ITER_002"));
        verify(iterationPort, never()).deleteByKey(any());
    }

    @Test
    @DisplayName("delete 时已关联窗口则失败")
    void shouldFailDeleteWhenAttachedToWindow() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.<RepoId>of(), IterationStatus.ACTIVE, now, now);
        ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
        WindowIteration wi = WindowIteration.attach(ReleaseWindowId.of("window-1"), IterationKey.of("ITER-1"), now, now);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(releaseWindowPort.findAll()).thenReturn(List.of(window));
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(window.getId().value()))).thenReturn(List.of(wi));

        assertThatThrownBy(() -> iterationAppService.delete("ITER-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ITER_002"));
        verify(iterationPort, never()).deleteByKey(any());
    }

    @Test
    @DisplayName("delete 时无关联且无仓库则成功")
    void shouldDeleteWhenNoReposAndNotAttached() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.<RepoId>of(), IterationStatus.ACTIVE, now, now);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(releaseWindowPort.findAll()).thenReturn(List.of());

        iterationAppService.delete("ITER-1");

        verify(iterationPort).deleteByKey(IterationKey.of("ITER-1"));
    }
}
