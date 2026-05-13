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
    @DisplayName("addRepos 时创建 feature 分支并保存版本信息")
    void shouldCreateFeatureBranchAndSaveVersionInfoWhenAddRepos() {
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
        when(branchRuleUseCase.isCompliant("feature/ITER-1")).thenReturn(true);
        when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        when(gitBranchPort.createBranch(repo.getCloneUrl(), repo.getGitAccessToken(), "feature/ITER-1", "master")).thenReturn(true);

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.AUTO, null);

        verify(gitBranchPort).createBranch(repo.getCloneUrl(), repo.getGitAccessToken(), "feature/ITER-1", "master");
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
                "master", "G001", RepoType.SERVICE, false, 0, 0, 0, 0, 0, 0, 0, null, now, now, 0L);

        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        IterationRepoVersionInfo versionInfo = IterationRepoVersionInfo.builder()
                                                                       .repoId("repo-1")
                                                                       .featureBranch("feature/ITER-1")
                                                                       .build();
        when(iterationRepoPort.getVersionInfo("ITER-1", "repo-1"))
                .thenReturn(Optional.of(versionInfo));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);

        iterationAppService.removeRepos("ITER-1", Set.of("repo-1"));

        verify(gitBranchPort).archiveBranch(repo.getCloneUrl(), repo.getGitAccessToken(), "feature/ITER-1", "unpublished");
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

    // ==== 分支创建模式测试 ====

    @Test
    @DisplayName("NAMED 模式 — 分支名不在 feature/ 路径下时仓库仍被添加但版本信息不保存")
    void shouldStillAddRepoButSkipVersionWhenNamedBranchInvalid() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));

        // addRepos swallows setup exceptions — repo is still added
        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.NAMED, "hotfix/critical");

        verify(iterationPort).save(any(Iteration.class));
        verify(iterationRepoPort, never()).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("NAMED 模式 — 分支名符合规则时创建成功")
    void shouldCreateNamedBranchWhenValid() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(branchRuleUseCase.isCompliant("feature/upgrade-guava")).thenReturn(true);
        when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        when(gitBranchPort.createBranch(repo.getCloneUrl(), repo.getGitAccessToken(), "feature/upgrade-guava", "master")).thenReturn(true);

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.NAMED, "feature/upgrade-guava");

        verify(gitBranchPort).createBranch(repo.getCloneUrl(), repo.getGitAccessToken(), "feature/upgrade-guava", "master");
        verify(iterationRepoPort).saveWithVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"),
                eq("feature/upgrade-guava"), eq("SYSTEM"), any(Instant.class));
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
                .thenReturn(GitBranchPort.BranchStatus.present("abc123"));

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.EXISTING, "feature/JIRA-4521");

        verify(iterationRepoPort).saveWithVersion(
                eq("ITER-1"), eq("repo-1"), eq("1.0.0"), eq("1.0.1-SNAPSHOT"), eq("1.0.1"),
                eq("feature/JIRA-4521"), eq("SYSTEM"), any(Instant.class));
    }

    @Test
    @DisplayName("EXISTING 模式 — 分支不存在时仓库仍添加但跳过版本信息")
    void shouldStillAddRepoButSkipVersionWhenExistingBranchNotFound() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        when(gitBranchPort.getBranchStatus(repo.getCloneUrl(), null, "feature/nonexistent"))
                .thenReturn(GitBranchPort.BranchStatus.missing());

        // addRepos swallows setup exceptions — repo is still added
        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.EXISTING, "feature/nonexistent");

        verify(iterationPort).save(any(Iteration.class));
        verify(iterationRepoPort, never()).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("create 带 repoConfigs 时为每个仓库调用 setupRepoForIteration")
    void shouldCallSetupForEachRepoWhenCreateWithRepoConfigs() {
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
        when(branchRuleUseCase.isCompliant(anyString())).thenReturn(true);
        when(gitBranchAdapterFactory.getAdapter(any())).thenReturn(gitBranchPort);
        when(gitBranchPort.createBranch(anyString(), any(), anyString(), anyString())).thenReturn(true);

        List<IterationAppService.RepoBranchConfig> configs = List.of(
                new IterationAppService.RepoBranchConfig("repo-1", BranchCreationMode.AUTO, null),
                new IterationAppService.RepoBranchConfig("repo-2", BranchCreationMode.NAMED, "feature/custom-fix")
        );
        iterationAppService.create("Iter", "Desc", LocalDate.now(), "G001", Set.of("repo-1", "repo-2"), configs);

        verify(iterationRepoPort, times(2)).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("create 无 repoConfigs 时默认 AUTO 模式")
    void shouldDefaultToAutoWhenNoRepoConfigs() {
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        Group group = Group.rehydrate(GroupId.of("G001"), "Group", "G001", null, Instant.now(), Instant.now(), 0L);
        when(groupPort.findByCode("G001")).thenReturn(Optional.of(group));
        when(groupPort.countChildren("G001")).thenReturn(0L);
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(codeRepositoryPort.getInitialVersion("repo-1")).thenReturn(Optional.of("1.0.0"));
        when(versionDeriverUseCase.deriveDevVersion("1.0.0")).thenReturn("1.0.1-SNAPSHOT");
        when(versionDeriverUseCase.deriveTargetVersion("1.0.1-SNAPSHOT")).thenReturn("1.0.1");
        when(branchRuleUseCase.isCompliant(anyString())).thenReturn(true);
        when(gitBranchAdapterFactory.getAdapter(repo.getGitProvider())).thenReturn(gitBranchPort);
        when(gitBranchPort.createBranch(eq(repo.getCloneUrl()), any(), anyString(), eq("master"))).thenReturn(true);

        iterationAppService.create("Iter", "Desc", LocalDate.now(), "G001", Set.of("repo-1"), null);

        ArgumentCaptor<String> branchCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitBranchPort).createBranch(eq(repo.getCloneUrl()), any(), branchCaptor.capture(), eq("master"));
        assertThat(branchCaptor.getValue()).startsWith("feature/ITER-");
    }

    @Test
    @DisplayName("NAMED 模式 — 自定义名不符合 BranchRule 时仓库仍添加但版本信息不保存")
    void shouldStillAddRepoButSkipVersionWhenNamedBranchFailsBranchRule() {
        Instant now = Instant.now();
        Iteration existing = Iteration.rehydrate(
                IterationKey.of("ITER-1"), "Iter", "Desc", null, "G001", Set.of(), IterationStatus.ACTIVE, now, now);
        CodeRepository repo = createRepo("repo-1", "git@gitlab.com:test/repo.git");
        when(iterationPort.findByKey(IterationKey.of("ITER-1"))).thenReturn(Optional.of(existing));
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
        when(branchRuleUseCase.isCompliant("feature/bad/name")).thenReturn(false);

        iterationAppService.addRepos("ITER-1", Set.of("repo-1"), BranchCreationMode.NAMED, "feature/bad/name");

        verify(iterationPort).save(any(Iteration.class));
        verify(iterationRepoPort, never()).saveWithVersion(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    // ==== 辅助方法 ====

    private CodeRepository createRepo(String id, String cloneUrl) {
        return CodeRepository.rehydrate(
                RepoId.of(id), "Repo-" + id, cloneUrl, "master", "G001", RepoType.SERVICE,
                false, 0, 0, 0, 0, 0, 0, 0, null, Instant.now(), Instant.now(), 0L);
    }
}
