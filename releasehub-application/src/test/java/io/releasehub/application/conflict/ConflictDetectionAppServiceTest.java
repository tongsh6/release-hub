package io.releasehub.application.conflict;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionExtractorUseCase;
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
import io.releasehub.domain.version.ConflictType;
import io.releasehub.domain.version.VersionSource;
import io.releasehub.domain.window.WindowIteration;
import io.releasehub.domain.window.WindowIterationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConflictDetectionAppServiceTest {

    @Mock private ReleaseWindowPort releaseWindowPort;
    @Mock private WindowIterationPort windowIterationPort;
    @Mock private IterationPort iterationPort;
    @Mock private IterationRepoPort iterationRepoPort;
    @Mock private CodeRepositoryPort codeRepositoryPort;
    @Mock private GitBranchAdapterFactory gitBranchAdapterFactory;
    @Mock private VersionExtractorUseCase versionExtractorUseCase;
    @Mock private BranchRuleUseCase branchRuleUseCase;
    @Mock private ConflictDetectionPort conflictDetectionPort;
    @Mock private GitBranchPort gitBranchPort;

    private ConflictDetectionAppService service;

    private static final String WINDOW_ID = "W001";
    private static final String ITERATION_KEY = "ITER-001";

    @BeforeEach
    void setUp() {
        service = new ConflictDetectionAppService(
                releaseWindowPort, windowIterationPort, iterationPort,
                iterationRepoPort, codeRepositoryPort, gitBranchAdapterFactory,
                versionExtractorUseCase, branchRuleUseCase, conflictDetectionPort);
        when(gitBranchAdapterFactory.getAdapter(any())).thenReturn(gitBranchPort);
        when(branchRuleUseCase.isCompliant(anyString())).thenReturn(true);
    }

    @Test
    void shouldDetectVersionMismatch() {
        // Given
        setupWindowWithIteration();
        setupRepo("R001", "test-repo", "master");
        setupVersionInfo("1.0.0");
        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.1.0", null)));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.missing());

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.getConflicts().stream().anyMatch(
                c -> c.getConflictType() == ConflictType.MISMATCH)).isTrue();
    }

    @Test
    void shouldDetectBranchAlreadyExists() {
        // Given
        setupWindowWithIteration();
        setupRepo("R001", "test-repo", "master");
        setupVersionInfo("1.0.0");
        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.0.0", null)));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.present("abc123"));
        when(gitBranchPort.checkMergeability(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.MergeabilityResult.mergeable());

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.getConflicts().stream().anyMatch(
                c -> c.getConflictType() == ConflictType.BRANCH_EXISTS)).isTrue();
    }

    @Test
    void shouldDetectMergeConflict() {
        // Given
        setupWindowWithIteration();
        setupRepo("R001", "test-repo", "master");
        setupVersionInfo("1.0.0");
        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.0.0", null)));

        // feature 和 release 分支都存在
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.present("abc"));

        // checkMergeability 返回冲突
        when(gitBranchPort.checkMergeability(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.MergeabilityResult.conflict("conflict in pom.xml"));

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.getConflicts().stream().anyMatch(
                c -> c.getConflictType() == ConflictType.MERGE_CONFLICT)).isTrue();
    }

    @Test
    void shouldReturnNoConflictsWhenEverythingClean() {
        // Given
        setupWindowWithIteration();
        setupRepo("R001", "test-repo", "master");
        setupVersionInfo("1.0.0");
        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.0.0", null)));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.missing());

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.hasConflicts()).isFalse();
    }

    @Test
    void shouldDetectCrossRepoVersionMismatch() {
        // Given
        setupWindowWithTwoReposIteration();
        setupRepo("R001", "repo-a", "master");
        setupRepo("R002", "repo-b", "master");
        setupVersionInfoForRepo("R001", "1.0.0", "2.0.0", "feature/ITER-001", "2.0.0");
        setupVersionInfoForRepo("R002", "1.0.0", "1.5.0", "feature/ITER-001", "1.5.0");

        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.0.0", null)));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.missing());

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.getConflicts().stream().anyMatch(
                c -> c.getConflictType() == ConflictType.CROSS_REPO_VERSION_MISMATCH)).isTrue();
    }

    // --- helpers ---

    private void setupWindowWithIteration() {
        ReleaseWindow rw = ReleaseWindow.rehydrate(
                ReleaseWindowId.of(WINDOW_ID), "rel-1.0", "Release 1.0", "",
                null, "default", ReleaseWindowStatus.DRAFT,
                Instant.now(), Instant.now(), false, null);

        Iteration it = Iteration.rehydrate(
                IterationKey.of(ITERATION_KEY), "Iteration 1", "", null, "",
                Set.of(RepoId.of("R001")), IterationStatus.ACTIVE,
                Instant.now(), Instant.now());

        when(releaseWindowPort.findById(ReleaseWindowId.of(WINDOW_ID))).thenReturn(Optional.of(rw));
        WindowIteration wi = WindowIteration.rehydrate(
                WindowIterationId.generate(ReleaseWindowId.of(WINDOW_ID), IterationKey.of(ITERATION_KEY)),
                ReleaseWindowId.of(WINDOW_ID), IterationKey.of(ITERATION_KEY),
                null, Instant.now(), Instant.now());
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(WINDOW_ID))).thenReturn(List.of(wi));
        when(iterationPort.findByKey(IterationKey.of(ITERATION_KEY))).thenReturn(Optional.of(it));
    }

    private void setupWindowWithTwoReposIteration() {
        ReleaseWindow rw = ReleaseWindow.rehydrate(
                ReleaseWindowId.of(WINDOW_ID), "rel-1.0", "Release 1.0", "",
                null, "default", ReleaseWindowStatus.DRAFT,
                Instant.now(), Instant.now(), false, null);

        Iteration it = Iteration.rehydrate(
                IterationKey.of(ITERATION_KEY), "Iteration 1", "", null, "",
                Set.of(RepoId.of("R001"), RepoId.of("R002")), IterationStatus.ACTIVE,
                Instant.now(), Instant.now());

        when(releaseWindowPort.findById(ReleaseWindowId.of(WINDOW_ID))).thenReturn(Optional.of(rw));
        WindowIteration wi = WindowIteration.rehydrate(
                WindowIterationId.generate(ReleaseWindowId.of(WINDOW_ID), IterationKey.of(ITERATION_KEY)),
                ReleaseWindowId.of(WINDOW_ID), IterationKey.of(ITERATION_KEY),
                null, Instant.now(), Instant.now());
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(WINDOW_ID))).thenReturn(List.of(wi));
        when(iterationPort.findByKey(IterationKey.of(ITERATION_KEY))).thenReturn(Optional.of(it));
    }

    private void setupRepo(String repoId, String repoName, String defaultBranch) {
        CodeRepository repo = CodeRepository.rehydrate(
                RepoId.of(repoId), repoName, "https://gitlab.com/group/" + repoName,
                defaultBranch != null ? defaultBranch : "master",
                "default",
                RepoType.SERVICE, GitProvider.GITLAB, "token", false,
                0, 0, 0, 0, 0, 0, 0, null,
                Instant.now(), Instant.now(), 0L);
        when(codeRepositoryPort.findById(RepoId.of(repoId))).thenReturn(Optional.of(repo));
    }

    private void setupVersionInfo(String devVersion) {
        setupVersionInfoForRepo("R001", "1.0.0", devVersion, "feature/" + ITERATION_KEY, "2.0.0");
    }

    private void setupVersionInfoForRepo(String repoId, String baseVersion, String devVersion,
                                         String featureBranch, String targetVersion) {
        IterationRepoVersionInfo info = IterationRepoVersionInfo.builder()
                .repoId(repoId)
                .repoName("test-repo")
                .baseVersion(baseVersion)
                .devVersion(devVersion)
                .targetVersion(targetVersion)
                .featureBranch(featureBranch)
                .versionSource(VersionSource.SYSTEM)
                .build();
        when(iterationRepoPort.getVersionInfo(ITERATION_KEY, repoId)).thenReturn(Optional.of(info));
    }
}
