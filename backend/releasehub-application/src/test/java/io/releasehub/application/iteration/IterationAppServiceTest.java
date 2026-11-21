package io.releasehub.application.iteration;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.group.GroupPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionDeriverUseCase;
import io.releasehub.application.version.VersionExtractorUseCase;
import io.releasehub.application.version.VersionUpdateAppService;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.group.Group;
import io.releasehub.domain.group.GroupId;
import io.releasehub.domain.iteration.BranchCreationMode;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.version.BuildTool;
import io.releasehub.domain.version.ConflictResolution;
import io.releasehub.domain.version.VersionSource;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    private VersionExtractorUseCase versionExtractorUseCase;
    @Mock
    private GroupPort groupPort;
    @Mock
    private GitBranchAdapterFactory gitBranchAdapterFactory;
    @Mock
    private GitBranchPort gitBranchPort;
    @Mock
    private BranchRuleUseCase branchRuleUseCase;
    @Mock
    private VersionDeriverUseCase versionDeriverUseCase;
    @Mock
    private VersionUpdateAppService versionUpdateAppService;
    @Mock
    private java.time.Clock clock;

    private IterationAppService iterationAppService;

    private final Instant now = Instant.parse("2026-05-11T10:00:00Z");

    @BeforeEach
    void setUp() {
        iterationAppService = new IterationAppService(
                iterationPort, releaseWindowPort, windowIterationPort,
                codeRepositoryPort, iterationRepoPort,
                gitBranchAdapterFactory, branchRuleUseCase, versionDeriverUseCase,
                versionExtractorUseCase, versionUpdateAppService, groupPort, clock
        );
        lenient().when(clock.instant()).thenReturn(now);
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.of("UTC"));
        lenient().when(branchRuleUseCase.isCompliant(anyString(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("create 时生成迭代标识且允许空仓库")
    void shouldGenerateIterationKeyAndAllowEmptyRepos() {
        ArgumentCaptor<Iteration> captor = ArgumentCaptor.forClass(Iteration.class);
        Group group = Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L);
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(group));
        when(groupPort.countChildren("G001")).thenReturn(0L);

        Iteration created = iterationAppService.create("Iter", "Desc", LocalDate.now(), "G001", null, null);

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

        assertThatThrownBy(() -> iterationAppService.create("Iter", "Desc", LocalDate.now(), "G001", null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("GROUP_014"));
    }

    @Test
    @DisplayName("group 不存在时创建失败")
    void shouldFailCreateWhenGroupNotFound() {
        when(groupPort.findByCode("G404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> iterationAppService.create("Iter", "Desc", LocalDate.now(), "G404", null, null))
                .isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo("GROUP_002"));
    }

    @Test
    @DisplayName("addRepos 只初始化 feature 映射和版本，不访问 Git")
    void shouldInitializeFeatureMappingWithoutGitWhenAddRepos() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.<RepoId>of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "git@gitlab.com:test/repo.git",
                "master", "G001", RepoType.SERVICE, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(branchRuleUseCase.isCompliant("feature/ITER-1", "G001", "repo-1")).thenReturn(true);
        lenient().when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.AUTO, null);

        verify(branchRuleUseCase).isCompliant("feature/ITER-1", "G001", "repo-1");
        verify(iterationRepoPort).saveWithVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"),
                eq("feature/ITER-1"), eq("SYSTEM"), any(Instant.class), eq(BranchCreationMode.AUTO));
        verify(iterationPort).save(any(Iteration.class));
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort);
    }

    @Test
    @DisplayName("Git 不可用时 addRepos 仍只保存配置，不尝试远端写入")
    void shouldInitializeMappingWithoutContactingAnUnavailableGitProvider() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.<RepoId>of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "git@gitlab.com:test/repo.git",
                "master", "G001", RepoType.SERVICE, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(branchRuleUseCase.isCompliant("feature/ITER-1", "G001", "repo-1")).thenReturn(true);
        lenient().when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        lenient().when(gitBranchPort.createBranch(repo.getCloneUrl(), repo.getGitAccessToken(), "feature/ITER-1", "master"))
                .thenThrow(new RuntimeException("I/O error"));

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.AUTO, null);

        verify(iterationRepoPort).saveWithVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"),
                eq("feature/ITER-1"), eq("SYSTEM"), any(Instant.class), eq(BranchCreationMode.AUTO));
        verify(iterationPort).save(any(Iteration.class));
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort);
    }

    @Test
    @DisplayName("addRepos 时拒绝跨分组仓库且不创建分支或版本记录")
    void shouldRejectAddReposWhenRepositoryBelongsToDifferentGroup() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.<RepoId>of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-2"), "Repo", "git@gitlab.com:test/repo.git",
                "master", "G002", RepoType.SERVICE, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-2"))).thenReturn(Optional.of(repo));

        assertThatThrownBy(() -> iterationAppService.addRepos("ITER-1", Set.of("repo-2"), BranchCreationMode.AUTO, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ITER_005"));
        verify(gitBranchAdapterFactory, never()).getAdapter(any());
        verify(iterationRepoPort, never()).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(iterationPort, never()).save(any());
    }

    @Test
    @DisplayName("addRepos 时已挂载发布窗口则拒绝修改仓库集合")
    void shouldRejectAddReposWhenIterationAttachedToWindow() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.<RepoId>of(), IterationStatus.ACTIVE, now, now);
        ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
        WindowIteration wi = WindowIteration.attach(ReleaseWindowId.of(window.getId().value()), IterationKey.of("ITER-1"), now, now);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(releaseWindowPort.findAll()).thenReturn(List.of(window));
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(window.getId().value()))).thenReturn(List.of(wi));

        assertThatThrownBy(() -> iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.AUTO, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ITER_002"));
        verify(codeRepositoryPort, never()).findById(any());
        verify(gitBranchAdapterFactory, never()).getAdapter(any());
        verify(iterationRepoPort, never()).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(iterationPort, never()).save(any());
    }

    @Test
    @DisplayName("removeRepos 仅解除仓库关联，Git 不可用也不归档 feature")
    void shouldRemoveRepoMappingWithoutArchivingFeatureWhenGitIsUnavailable() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "git@gitlab.com:test/repo.git",
                "master", "G001", RepoType.SERVICE, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        IterationRepoVersionInfo versionInfo = IterationRepoVersionInfo.builder()
                                                                       .repoId("repo-1")
                                                                       .featureBranch("feature/ITER-1")
                                                                       .build();
        lenient().when(iterationRepoPort.getVersionInfo("ITER-1", "repo-1"))
                .thenReturn(Optional.of(versionInfo));
        lenient().when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        lenient().when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        lenient().when(gitBranchPort.archiveBranch(anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Git unavailable"));

        Iteration updated = iterationAppService.removeRepos("ITER-1", Set.of("repo-1"));

        assertThat(updated.getRepos()).isEmpty();
        verify(iterationPort).save(updated);
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort);
    }

    @Test
    @DisplayName("removeRepos 时已挂载发布窗口则拒绝修改仓库集合")
    void shouldRejectRemoveReposWhenIterationAttachedToWindow() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);
        ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
        WindowIteration wi = WindowIteration.attach(ReleaseWindowId.of(window.getId().value()), IterationKey.of("ITER-1"), now, now);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(releaseWindowPort.findAll()).thenReturn(List.of(window));
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(window.getId().value()))).thenReturn(List.of(wi));

        assertThatThrownBy(() -> iterationAppService.removeRepos("ITER-1", Set.of("repo-1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ITER_002"));
        verify(gitBranchAdapterFactory, never()).getAdapter(any());
        verify(iterationPort, never()).save(any());
    }

    @Test
    @DisplayName("update 时已挂载发布窗口则拒绝修改仓库集合")
    void shouldRejectUpdateRepoScopeWhenIterationAttachedToWindow() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);
        ReleaseWindow window = ReleaseWindow.createDraft("RW-1", "Window", null, now, "G001", now);
        WindowIteration wi = WindowIteration.attach(ReleaseWindowId.of(window.getId().value()), IterationKey.of("ITER-1"), now, now);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, now, now, 0L)));
        when(groupPort.countChildren("G001")).thenReturn(0L);
        when(releaseWindowPort.findAll()).thenReturn(List.of(window));
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(window.getId().value()))).thenReturn(List.of(wi));

        assertThatThrownBy(() -> iterationAppService.update("ITER-1", "Iter", "Desc", null, "G001", Set.of("repo-1", "repo-2"), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ITER_002"));
        verify(codeRepositoryPort, never()).findById(any());
        verify(gitBranchAdapterFactory, never()).getAdapter(any());
        verify(iterationPort, never()).save(any());
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
    @DisplayName("resolveVersionConflict USE_SYSTEM 时把系统版本写回 feature 分支")
    void shouldUpdateRepoVersionWhenResolvingConflictWithSystemVersion() {
        IterationRepoVersionInfo versionInfo = IterationRepoVersionInfo.builder()
                .repoId("repo-1")
                .baseVersion("1.2.0")
                .devVersion("1.3.0-SNAPSHOT")
                .targetVersion("1.3.0")
                .featureBranch("feature/ITER-1")
                .versionSource(VersionSource.SYSTEM)
                .build();
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "http://localhost/group/repo.git",
                "master", "G001", RepoType.SERVICE, GitProvider.GITLAB, "token",
                false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);
        ArgumentCaptor<VersionUpdateRequest> requestCaptor = ArgumentCaptor.forClass(VersionUpdateRequest.class);

        when(iterationRepoPort.getVersionInfo("ITER-1", "repo-1")).thenReturn(Optional.of(versionInfo));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(versionExtractorUseCase.extractVersion(repo.getCloneUrl(), "feature/ITER-1"))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.2.0", VersionSource.POM)));
        when(versionUpdateAppService.updateVersion(any(VersionUpdateRequest.class)))
                .thenReturn(VersionUpdateResult.success("1.2.0", "1.3.0-SNAPSHOT", "diff", "pom.xml"));

        iterationAppService.resolveVersionConflict(
                IterationKey.of("ITER-1"), RepoId.of("repo-1"), ConflictResolution.USE_SYSTEM);

        verify(versionUpdateAppService).updateVersion(requestCaptor.capture());
        VersionUpdateRequest request = requestCaptor.getValue();
        assertThat(request.repoId()).isEqualTo(RepoId.of("repo-1"));
        assertThat(request.branchName()).isEqualTo("feature/ITER-1");
        assertThat(request.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(request.targetVersion()).isEqualTo("1.3.0-SNAPSHOT");
        assertThat(request.repoPath()).isEqualTo(".");
        assertThat(request.pomPath()).isEqualTo("pom.xml");
        verify(iterationRepoPort).updateVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.3.0-SNAPSHOT"), eq("SYSTEM"), any(Instant.class));
    }

    @Test
    @DisplayName("resolveVersionConflict USE_SYSTEM 缺少 featureBranch 时拒绝写回仓库")
    void shouldRejectSystemResolutionWhenFeatureBranchMissing() {
        IterationRepoVersionInfo versionInfo = IterationRepoVersionInfo.builder()
                .repoId("repo-1")
                .baseVersion("1.2.0")
                .devVersion("1.3.0-SNAPSHOT")
                .targetVersion("1.3.0")
                .featureBranch(null)
                .versionSource(VersionSource.SYSTEM)
                .build();

        when(iterationRepoPort.getVersionInfo("ITER-1", "repo-1")).thenReturn(Optional.of(versionInfo));

        assertThatThrownBy(() -> iterationAppService.resolveVersionConflict(
                IterationKey.of("ITER-1"), RepoId.of("repo-1"), ConflictResolution.USE_SYSTEM))
                .isInstanceOf(ValidationException.class);
        verify(versionUpdateAppService, never()).updateVersion(any(VersionUpdateRequest.class));
        verify(iterationRepoPort, never()).updateVersion(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("resolveVersionConflict USE_REPO 时接受仓库版本并更新系统记录")
    void shouldAcceptRepoVersionWhenResolvingRepoAheadConflict() {
        IterationRepoVersionInfo versionInfo = IterationRepoVersionInfo.builder()
                .repoId("repo-1")
                .baseVersion("1.2.0")
                .devVersion("1.3.0-SNAPSHOT")
                .targetVersion("1.3.0")
                .featureBranch("feature/ITER-1")
                .versionSource(VersionSource.SYSTEM)
                .build();
        IterationRepoVersionInfo syncedInfo = IterationRepoVersionInfo.builder()
                .repoId("repo-1")
                .baseVersion("1.2.0")
                .devVersion("1.4.0-SNAPSHOT")
                .targetVersion("1.3.0")
                .featureBranch("feature/ITER-1")
                .versionSource(VersionSource.REPO)
                .build();
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of("repo-1"), "Repo", "http://localhost/group/repo.git",
                "master", "G001", RepoType.SERVICE, GitProvider.GITLAB, "token",
                false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(iterationRepoPort.getVersionInfo("ITER-1", "repo-1"))
                .thenReturn(Optional.of(versionInfo))
                .thenReturn(Optional.of(syncedInfo));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(versionExtractorUseCase.extractVersion(repo.getCloneUrl(), "feature/ITER-1"))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.4.0-SNAPSHOT", VersionSource.POM)));

        IterationRepoVersionInfo result = iterationAppService.resolveVersionConflict(
                IterationKey.of("ITER-1"), RepoId.of("repo-1"), ConflictResolution.USE_REPO);

        assertThat(result.getDevVersion()).isEqualTo("1.4.0-SNAPSHOT");
        assertThat(result.getVersionSource()).isEqualTo(VersionSource.REPO);
        verify(iterationRepoPort).updateVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.4.0-SNAPSHOT"), eq("REPO"), any(Instant.class));
        verify(versionUpdateAppService, never()).updateVersion(any(VersionUpdateRequest.class));
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

    @Test
    @DisplayName("update 新增仓库只初始化映射，保留自定义分支名和版本")
    void shouldInitializeNewRepoMappingWithoutGitWhenUpdatingIteration() {
        Iteration existing = Iteration.rehydrate(IterationKey.of("ITER-1"), "Iter", "Desc", null,
                "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(existing.getId())).thenReturn(Optional.of(existing));
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(
                Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, now, now, 0L)));
        when(groupPort.countChildren("G001")).thenReturn(0L);
        when(codeRepositoryPort.findById(repo.getId())).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        lenient().when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);

        Iteration updated = iterationAppService.update("ITER-1", "Updated", "Desc", null, "G001",
                Set.of("repo-1"), List.of(new IterationAppService.RepoBranchConfig(
                        "repo-1", BranchCreationMode.NAMED, "feature/planned-change")));

        assertThat(updated.getRepos()).containsExactly(RepoId.of("repo-1"));
        verify(iterationRepoPort).saveWithVersion(eq("ITER-1"), eq("repo-1"), eq("1.0.0"),
                eq("1.0.1-SNAPSHOT"), eq("1.0.1"), eq("feature/planned-change"),
                eq("SYSTEM"), eq(now), eq(BranchCreationMode.NAMED));
        verify(iterationPort).save(updated);
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort);
    }

    @Test
    @DisplayName("update 移除仓库只保存保留的关联，不归档已有 feature")
    void shouldRemoveOnlyRepoMappingWithoutGitWhenUpdatingIteration() {
        Iteration existing = Iteration.rehydrate(IterationKey.of("ITER-1"), "Iter", "Desc", null,
                "G001", Set.of(RepoId.of("repo-1"), RepoId.of("repo-2")), IterationStatus.ACTIVE, now, now);
        CodeRepository removed = createRepo("repo-1", "git@gitlab.com:test/removed.git");
        CodeRepository retained = createRepo("repo-2", "git@gitlab.com:test/retained.git");
        when(iterationPort.findByKey(existing.getId())).thenReturn(Optional.of(existing));
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(
                Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, now, now, 0L)));
        when(groupPort.countChildren("G001")).thenReturn(0L);
        when(codeRepositoryPort.findById(retained.getId())).thenReturn(Optional.of(retained));
        lenient().when(codeRepositoryPort.findById(removed.getId())).thenReturn(Optional.of(removed));
        lenient().when(gitBranchAdapterFactory.getAdapter(removed.getGitProvider())).thenReturn(gitBranchPort);

        Iteration updated = iterationAppService.update("ITER-1", "Updated", "Desc", null, "G001",
                Set.of("repo-2"), null);

        assertThat(updated.getRepos()).containsExactly(RepoId.of("repo-2"));
        verify(iterationPort).save(updated);
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort, iterationRepoPort);
    }

    @Test
    @DisplayName("EXISTING 读取失败不转成新建分支或保存映射")
    void shouldPropagateExistingBranchReadFailureWithoutSavingOrWritingGit() {
        Iteration existing = Iteration.rehydrate(IterationKey.of("ITER-1"), "Iter", "Desc", null,
                "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(existing.getId())).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(repo.getId())).thenReturn(Optional.of(repo));
        when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        var failure = new IllegalStateException("Git read unavailable");
        when(gitBranchPort.getBranchStatus(repo.getCloneUrl(), null, "feature/existing")).thenThrow(failure);

        assertThatThrownBy(() -> iterationAppService.addRepos("ITER-1", Set.of("repo-1"),
                BranchCreationMode.EXISTING, "feature/existing")).isSameAs(failure);

        verify(iterationPort, never()).save(any());
        verifyNoInteractions(iterationRepoPort);
        verify(gitBranchPort).getBranchStatus(repo.getCloneUrl(), null, "feature/existing");
        verifyNoMoreInteractions(gitBranchPort);
    }

    // ==== 分支创建模式测试 ====

    @Test
    @DisplayName("NAMED 模式 — 分支名不在 feature/ 路径下时写入前拒绝")
    void shouldRejectBeforeSavingRepoWhenNamedBranchInvalid() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));

        assertThatThrownBy(() -> iterationAppService.addRepos(
                "ITER-1", Set.of("repo-1"), BranchCreationMode.NAMED, "hotfix/critical"))
                .isInstanceOf(ValidationException.class);

        verify(iterationPort, never()).save(any(Iteration.class));
        verify(gitBranchAdapterFactory, never()).getAdapter(any());
        verify(iterationRepoPort, never()).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("NAMED 模式 — 保存自定义名称和版本，等待显式编排创建")
    void shouldPreserveNamedBranchConfigurationWithoutGit() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(branchRuleUseCase.isCompliant("feature/upgrade-guava", "G001", "repo-1")).thenReturn(true);
        lenient().when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.NAMED, "feature/upgrade-guava");

        verify(iterationRepoPort).saveWithVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"),
                eq("feature/upgrade-guava"), eq("SYSTEM"), any(Instant.class), eq(BranchCreationMode.NAMED));
        verify(branchRuleUseCase).isCompliant("feature/upgrade-guava", "G001", "repo-1");
        verify(iterationPort).save(any(Iteration.class));
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort);
    }

    @Test
    @DisplayName("EXISTING 模式 — 不创建分支，只建立映射")
    void shouldNotCreateBranchWhenExistingMode() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        when(gitBranchPort.getBranchStatus(repo.getCloneUrl(), null, "feature/JIRA-4521"))
                .thenReturn(GitBranchPort.BranchStatus.present("17", "abc123"));

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.EXISTING, "feature/JIRA-4521");

        verify(iterationRepoPort).saveWithVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"),
                eq("feature/JIRA-4521"), eq("SYSTEM"), any(Instant.class), eq(BranchCreationMode.EXISTING));
        verify(gitBranchPort).getBranchStatus(repo.getCloneUrl(), null, "feature/JIRA-4521");
        verifyNoMoreInteractions(gitBranchPort);
    }

    @Test
    @DisplayName("EXISTING 模式 — 分支不存在时写入前拒绝")
    void shouldRejectBeforeSavingRepoWhenExistingBranchNotFound() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        when(gitBranchPort.getBranchStatus(repo.getCloneUrl(), null, "feature/nonexistent"))
                .thenReturn(GitBranchPort.BranchStatus.missing("17"));

        assertThatThrownBy(() -> iterationAppService.addRepos(
                "ITER-1", Set.of("repo-1"), BranchCreationMode.EXISTING, "feature/nonexistent"))
                .isInstanceOf(ValidationException.class);

        verify(iterationPort, never()).save(any(Iteration.class));
        verify(iterationRepoPort, never()).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("create 部分配置保留 NAMED，未配置仓库初始化为 AUTO，零 Git 调用")
    void shouldInitializeEachRepoConfigurationWithoutGitWhenCreatingIteration() {
        CodeRepository repo1 = createRepo("repo-1", "git@gitlab.com:test/repo1.git");
        CodeRepository repo2 = createRepo("repo-2", "git@gitlab.com:test/repo2.git");
        Group group = Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L);
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(group));
        when(groupPort.countChildren("G001")).thenReturn(0L);
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo1));
        when(codeRepositoryPort.findById(RepoId.of("repo-2"))).thenReturn(Optional.of(repo2));
        when(codeRepositoryPort.getInitialVersion(anyString())).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(branchRuleUseCase.isCompliant(anyString(), any(), any())).thenReturn(true);
        lenient().when(gitBranchAdapterFactory.getAdapter(any())).thenReturn(gitBranchPort);

        List<IterationAppService.RepoBranchConfig> configs = List.of(
                new IterationAppService.RepoBranchConfig("repo-2", BranchCreationMode.NAMED, "feature/custom-fix")
        );
        Iteration created = iterationAppService.create("Iter", "Desc", LocalDate.now(), "G001", Set.of("repo-1", "repo-2"), configs);

        assertThat(created.getRepos()).containsExactlyInAnyOrder(RepoId.of("repo-1"), RepoId.of("repo-2"));
        verify(iterationRepoPort).saveWithVersion(eq(created.getId().value()), eq("repo-1"),
                eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"), eq("feature/" + created.getId().value()),
                eq("SYSTEM"), eq(now), eq(BranchCreationMode.AUTO));
        verify(iterationRepoPort).saveWithVersion(eq(created.getId().value()), eq("repo-2"),
                eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"), eq("feature/custom-fix"),
                eq("SYSTEM"), eq(now), eq(BranchCreationMode.NAMED));
        verify(iterationPort).save(created);
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort);
    }

    @Test
    @DisplayName("create 无 repoConfigs 时只保存 AUTO 分支规划")
    void shouldDefaultToAutoConfigurationWithoutGitWhenCreatingIteration() {
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        Group group = Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L);
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(group));
        when(groupPort.countChildren("G001")).thenReturn(0L);
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(branchRuleUseCase.isCompliant(anyString(), any(), any())).thenReturn(true);
        lenient().when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);

        Iteration created = iterationAppService.create("Iter", "Desc", LocalDate.now(), "G001", Set.of("repo-1"), null);

        ArgumentCaptor<String> branchCaptor = ArgumentCaptor.forClass(String.class);
        verify(iterationRepoPort).saveWithVersion(eq(created.getId().value()), eq("repo-1"),
                eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"), branchCaptor.capture(),
                eq("SYSTEM"), eq(now), eq(BranchCreationMode.AUTO));
        assertThat(branchCaptor.getValue()).isEqualTo("feature/" + created.getId().value());
        verify(iterationPort).save(created);
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort);
    }

    @Test
    @DisplayName("NAMED 模式 — 自定义名不符合 BranchRule 时写入和 Git 创建前拒绝")
    void shouldRejectBeforeSavingOrCreatingBranchWhenNamedBranchFailsBranchRule() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(branchRuleUseCase.isCompliant("feature/bad/name", "G001", "repo-1")).thenReturn(false);

        assertThatThrownBy(() -> iterationAppService.addRepos(
                "ITER-1", Set.of("repo-1"), BranchCreationMode.NAMED, "feature/bad/name"))
                .isInstanceOf(ValidationException.class);

        verify(iterationPort, never()).save(any(Iteration.class));
        verify(gitBranchAdapterFactory, never()).getAdapter(any());
        verify(iterationRepoPort, never()).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("分页读取迭代仓库详情时只加载当前页仓库和版本信息")
    void shouldPageRepoDetailsWithVersionMetadata() {
        Instant now = Instant.now();
        Set<RepoId> repoIds = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(index -> RepoId.of(String.format("repo-%02d", index)))
                .collect(java.util.stream.Collectors.toSet());
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", repoIds, IterationStatus.ACTIVE, now, now);
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        for (int index = 11; index <= 20; index++) {
            String repoId = String.format("repo-%02d", index);
            when(codeRepositoryPort.findById(RepoId.of(repoId))).thenReturn(Optional.of(createRepo(repoId, "git@gitlab.com:test/" + repoId + ".git")));
            when(iterationRepoPort.getVersionInfo("ITER-1", repoId)).thenReturn(Optional.of(
                    IterationRepoVersionInfo.builder()
                            .repoId(repoId)
                            .baseVersion("1.0.0")
                            .devVersion("1.1.0-SNAPSHOT")
                            .targetVersion("1.1.0")
                            .featureBranch("feature/ITER-1")
                            .branchCreationMode(BranchCreationMode.AUTO)
                            .versionSource(VersionSource.SYSTEM)
                            .versionSyncedAt(now)
                            .build()
            ));
        }

        PageResult<IterationRepoDetailView> result = iterationAppService.listRepoDetailsPaged("ITER-1", 2, 10);

        assertThat(result.total()).isEqualTo(25);
        assertThat(result.items()).hasSize(10);
        assertThat(result.items().get(0))
                .extracting(
                        IterationRepoDetailView::repoId,
                        IterationRepoDetailView::repoName,
                        IterationRepoDetailView::branchCreationMode,
                        IterationRepoDetailView::featureBranch,
                        IterationRepoDetailView::baseVersion,
                        IterationRepoDetailView::devVersion,
                        IterationRepoDetailView::targetVersion,
                        IterationRepoDetailView::versionSource
                )
                .containsExactly(
                        "repo-11",
                        "Repo-repo-11",
                        "AUTO",
                        "feature/ITER-1",
                        "1.0.0",
                        "1.1.0-SNAPSHOT",
                        "1.1.0",
                        "SYSTEM"
                );
        verify(codeRepositoryPort, never()).findById(RepoId.of("repo-01"));
        verify(iterationRepoPort, never()).getVersionInfo("ITER-1", "repo-01");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"G001", "OTHER"})
    @DisplayName("create 拒绝 repoIds 之外的同组或跨组分支配置")
    void shouldRejectUndeclaredRepoConfigurationBeforeCreatingIteration(String extraGroup) {
        stubConfigurationGroup();
        CodeRepository selected = createRepo("repo-1", "git@gitlab.com:test/selected.git");
        CodeRepository extra = CodeRepository.rehydrate(RepoId.of("repo-extra"), "Extra",
                "git@gitlab.com:test/extra.git", "master", extraGroup, RepoType.SERVICE,
                false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);
        when(codeRepositoryPort.findById(selected.getId())).thenReturn(Optional.of(selected));
        lenient().when(codeRepositoryPort.findById(extra.getId())).thenReturn(Optional.of(extra));

        assertThatThrownBy(() -> iterationAppService.create("Iter", "Desc", null, "G001",
                Set.of("repo-1"), List.of(new IterationAppService.RepoBranchConfig(
                        "repo-extra", BranchCreationMode.AUTO, null))))
                .isInstanceOf(ValidationException.class);

        verify(iterationPort, never()).save(any());
        verify(codeRepositoryPort, never()).findById(extra.getId());
        verifyNoInteractions(iterationRepoPort, gitBranchAdapterFactory, gitBranchPort);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("create 拒绝重复仓库配置，不按相同或不同模式静默覆盖")
    void shouldRejectDuplicateRepoConfigurationsBeforeCreatingIteration(boolean conflictingMode) {
        stubConfigurationGroup();
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(codeRepositoryPort.findById(repo.getId())).thenReturn(Optional.of(repo));
        var first = new IterationAppService.RepoBranchConfig("repo-1", BranchCreationMode.AUTO, null);
        var second = conflictingMode
                ? new IterationAppService.RepoBranchConfig("repo-1", BranchCreationMode.NAMED, "feature/custom")
                : first;

        assertThatThrownBy(() -> iterationAppService.create("Iter", "Desc", null, "G001",
                Set.of("repo-1"), List.of(first, second))).isInstanceOf(ValidationException.class);

        verify(iterationPort, never()).save(any());
        verifyNoInteractions(iterationRepoPort, gitBranchAdapterFactory, gitBranchPort);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"G001", "OTHER"})
    @DisplayName("update 拒绝完整目标仓库集合之外的分支配置")
    void shouldRejectUndeclaredRepoConfigurationBeforeUpdatingIteration(String extraGroup) {
        stubConfigurationGroup();
        Iteration existing = Iteration.rehydrate(IterationKey.of("ITER-1"), "Iter", "Desc", null,
                "G001", Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);
        CodeRepository selected = createRepo("repo-1", "git@gitlab.com:test/selected.git");
        CodeRepository extra = CodeRepository.rehydrate(RepoId.of("repo-extra"), "Extra",
                "git@gitlab.com:test/extra.git", "master", extraGroup, RepoType.SERVICE,
                false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);
        when(iterationPort.findByKey(existing.getId())).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(selected.getId())).thenReturn(Optional.of(selected));
        lenient().when(codeRepositoryPort.findById(extra.getId())).thenReturn(Optional.of(extra));

        assertThatThrownBy(() -> iterationAppService.update("ITER-1", "Updated", "Desc", null, "G001",
                Set.of("repo-1"), List.of(new IterationAppService.RepoBranchConfig(
                        "repo-extra", BranchCreationMode.NAMED, "feature/extra"))))
                .isInstanceOf(ValidationException.class);

        verify(iterationPort, never()).save(any());
        verify(codeRepositoryPort, never()).findById(extra.getId());
        verifyNoInteractions(iterationRepoPort, gitBranchAdapterFactory, gitBranchPort);
    }

    @Test
    @DisplayName("update 对已有仓库的重复配置也在筛选新增仓库前拒绝")
    void shouldRejectDuplicateConfigurationForAnAlreadyBoundRepository() {
        stubConfigurationGroup();
        Iteration existing = Iteration.rehydrate(IterationKey.of("ITER-1"), "Iter", "Desc", null,
                "G001", Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(existing.getId())).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(repo.getId())).thenReturn(Optional.of(repo));
        var repeated = new IterationAppService.RepoBranchConfig("repo-1", BranchCreationMode.NAMED, "feature/recorded");

        assertThatThrownBy(() -> iterationAppService.update("ITER-1", "Updated", "Desc", null, "G001",
                Set.of("repo-1"), List.of(repeated, repeated))).isInstanceOf(ValidationException.class);

        verify(iterationPort, never()).save(any());
        verifyNoInteractions(iterationRepoPort, gitBranchAdapterFactory, gitBranchPort);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ALL", "EXISTING_ONLY", "OMITTED"})
    @DisplayName("update 接收全量或部分配置，仅初始化新增仓库，缺省配置为 AUTO")
    void shouldPreserveExistingMappingAndDefaultOnlyNewUnconfiguredRepositories(String configuration) {
        stubConfigurationGroup();
        Iteration existing = Iteration.rehydrate(IterationKey.of("ITER-1"), "Iter", "Desc", null,
                "G001", Set.of(RepoId.of("repo-1")), IterationStatus.ACTIVE, now, now);
        CodeRepository retained = createRepo("repo-1", "git@gitlab.com:test/retained.git");
        CodeRepository added = createRepo("repo-2", "git@gitlab.com:test/added.git");
        when(iterationPort.findByKey(existing.getId())).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(retained.getId())).thenReturn(Optional.of(retained));
        when(codeRepositoryPort.findById(added.getId())).thenReturn(Optional.of(added));
        when(codeRepositoryPort.getInitialVersion("repo-2")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        var retainedConfig = new IterationAppService.RepoBranchConfig(
                "repo-1", BranchCreationMode.EXISTING, "feature/recorded");
        List<IterationAppService.RepoBranchConfig> configs = switch (configuration) {
            case "ALL" -> List.of(retainedConfig, new IterationAppService.RepoBranchConfig(
                    "repo-2", BranchCreationMode.NAMED, "feature/new-config"));
            case "EXISTING_ONLY" -> List.of(retainedConfig);
            default -> null;
        };

        Iteration updated = iterationAppService.update("ITER-1", "Updated", "Desc", null, "G001",
                Set.of("repo-1", "repo-2"), configs);

        boolean named = configuration.equals("ALL");
        assertThat(updated.getRepos()).containsExactlyInAnyOrder(retained.getId(), added.getId());
        verify(iterationRepoPort).saveWithVersion(eq("ITER-1"), eq("repo-2"), eq("1.0.0"),
                eq("1.0.1-SNAPSHOT"), eq("1.0.1"), eq(named ? "feature/new-config" : "feature/ITER-1"),
                eq("SYSTEM"), eq(now), eq(named ? BranchCreationMode.NAMED : BranchCreationMode.AUTO));
        verifyNoMoreInteractions(iterationRepoPort);
        verify(iterationPort).save(updated);
        verifyNoInteractions(gitBranchAdapterFactory, gitBranchPort);
    }

    private void stubConfigurationGroup() {
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(
                Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, now, now, 0L)));
        when(groupPort.countChildren("G001")).thenReturn(0L);
    }

    // ==== 辅助方法 ====

    private CodeRepository createRepo(String id, String cloneUrl) {
        return CodeRepository.rehydrate(
                RepoId.of(id), "Repo-" + id, cloneUrl, "master", "G001", RepoType.SERVICE,
                false, 0, 0, 0, 0, 0, 0, 0, null, Instant.now(), Instant.now(), 0L);
    }
}
