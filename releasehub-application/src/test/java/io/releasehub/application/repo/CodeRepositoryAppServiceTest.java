package io.releasehub.application.repo;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.group.GroupPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.version.VersionExtractor;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.version.VersionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeRepositoryAppService 测试")
class CodeRepositoryAppServiceTest {

    @Mock
    private CodeRepositoryPort codeRepositoryPort;
    @Mock
    private VersionExtractor versionExtractor;
    @Mock
    private SettingsPort settingsPort;
    @Mock
    private GitLabPort gitLabPort;
    @Mock
    private IterationPort iterationPort;
    @Mock
    private GroupPort groupPort;

    private CodeRepositoryAppService appService;

    @BeforeEach
    void setUp() {
        appService = new CodeRepositoryAppService(codeRepositoryPort, versionExtractor, settingsPort, gitLabPort, iterationPort, groupPort);
    }

    @Test
    @DisplayName("默认分支为空时优先解析 main")
    void shouldResolveMainWhenDefaultBranchBlank() {
        ArgumentCaptor<CodeRepository> captor = ArgumentCaptor.forClass(CodeRepository.class);
        when(settingsPort.getGitLab()).thenReturn(Optional.of(new SettingsPort.SettingsGitLab("http://git", "token")));
        when(gitLabPort.resolveProjectId("git@gitlab.com:test/repo.git")).thenReturn(1L);
        when(gitLabPort.branchExists(1L, "main")).thenReturn(true);
        when(versionExtractor.extractVersion(anyString(), anyString())).thenReturn(Optional.empty());
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L)));
        when(groupPort.countChildren("G001")).thenReturn(0L);

        appService.create("Repo", "git@gitlab.com:test/repo.git", " ", false, null, "G001");

        verify(codeRepositoryPort).save(captor.capture());
        assertThat(captor.getValue().getDefaultBranch()).isEqualTo("main");
    }

    @Test
    @DisplayName("main 不存在时回退到 master")
    void shouldResolveMasterWhenMainNotExists() {
        ArgumentCaptor<CodeRepository> captor = ArgumentCaptor.forClass(CodeRepository.class);
        when(settingsPort.getGitLab()).thenReturn(Optional.of(new SettingsPort.SettingsGitLab("http://git", "token")));
        when(gitLabPort.resolveProjectId("git@gitlab.com:test/repo.git")).thenReturn(1L);
        when(gitLabPort.branchExists(1L, "main")).thenReturn(false);
        when(gitLabPort.branchExists(1L, "master")).thenReturn(true);
        when(versionExtractor.extractVersion(anyString(), anyString())).thenReturn(Optional.empty());
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L)));
        when(groupPort.countChildren("G001")).thenReturn(0L);

        appService.create("Repo", "git@gitlab.com:test/repo.git", null, false, null, "G001");

        verify(codeRepositoryPort).save(captor.capture());
        assertThat(captor.getValue().getDefaultBranch()).isEqualTo("master");
    }

    @Test
    @DisplayName("版本解析失败时标记 VERSION_UNRESOLVED")
    void shouldMarkVersionUnresolvedWhenExtractorThrows() {
        ArgumentCaptor<CodeRepository> captor = ArgumentCaptor.forClass(CodeRepository.class);
        when(versionExtractor.extractVersion(anyString(), anyString())).thenThrow(new RuntimeException("boom"));
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L)));
        when(groupPort.countChildren("G001")).thenReturn(0L);

        appService.create("Repo", "git@gitlab.com:test/repo.git", "main", false, null, "G001");

        verify(codeRepositoryPort).save(captor.capture());
        String repoId = captor.getValue().getId().value();
        verify(codeRepositoryPort).updateInitialVersion(repoId, null, "VERSION_UNRESOLVED");
    }

    @Test
    @DisplayName("update 时传入初始版本则手动更新")
    void shouldUpdateInitialVersionWhenProvided() {
        Instant now = Instant.now();
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "git@gitlab.com:test/repo.git", "main", "G001",
                false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(groupPort.findByCode("G002")).thenReturn(Optional.of(Group.rehydrate(GroupId.of("G002"), "Group", "G002", null, now, now, 0L)));
        when(groupPort.countChildren("G002")).thenReturn(0L);

        appService.update("repo-1", "Repo", "git@gitlab.com:test/repo.git", "main", false, "2.0.0", "G002");

        verify(codeRepositoryPort).updateInitialVersion("repo-1", "2.0.0", VersionSource.MANUAL.name());
    }

    @Test
    @DisplayName("删除时仓库已关联迭代则失败")
    void shouldRejectDeleteWhenRepoAttachedToIteration() {
        Instant now = Instant.now();
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "git@gitlab.com:test/repo.git", "main", "G001",
                false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);
        Iteration iteration = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", null, null, "G001",
                Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);

        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(iterationPort.findAll()).thenReturn(List.of(iteration));

        assertThatThrownBy(() -> appService.delete("repo-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REPO_011"));
        verify(codeRepositoryPort, never()).deleteById(any());
    }

    @Test
    @DisplayName("group 非末端节点时创建失败")
    void shouldFailCreateWhenGroupHasChildren() {
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L)));
        when(groupPort.countChildren("G001")).thenReturn(2L);

        assertThatThrownBy(() -> appService.create("Repo", "git@gitlab.com:test/repo.git", "main", false, null, "G001"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("GROUP_008"));
    }

    @Test
    @DisplayName("group 不存在时创建失败")
    void shouldFailCreateWhenGroupNotFound() {
        when(groupPort.findByCode("G404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appService.create("Repo", "git@gitlab.com:test/repo.git", "main", false, null, "G404"))
                .isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo("GROUP_002"));
    }
}
