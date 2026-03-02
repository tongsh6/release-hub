package io.releasehub.infrastructure.git;

import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.release.executors.ArchiveFeatureBranchExecutor;
import io.releasehub.application.release.executors.CreateReleaseBranchExecutor;
import io.releasehub.application.release.executors.CreateTagExecutor;
import io.releasehub.application.release.executors.MergeFeatureToReleaseExecutor;
import io.releasehub.application.release.executors.MergeReleaseToMasterExecutor;
import io.releasehub.application.run.RunTaskContext;
import io.releasehub.application.run.RunTaskContextPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.run.TargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitBranchExecutorsIntegrationTest {

    @Mock
    private CodeRepositoryPort codeRepositoryPort;

    @Mock
    private RunTaskContextPort runTaskContextPort;

    private MockGitBranchAdapter mockAdapter;
    private GitBranchAdapterFactory gitBranchAdapterFactory;
    private CodeRepository repo;

    @BeforeEach
    void setUp() {
        mockAdapter = new MockGitBranchAdapter();
        gitBranchAdapterFactory = new GitBranchAdapterFactoryImpl(java.util.List.of(mockAdapter));
        repo = createRepo("repo-1", "https://github.com/acme/releasehub.git");
        when(codeRepositoryPort.findById(RepoId.of("repo-1"))).thenReturn(Optional.of(repo));
    }

    @Test
    void shouldCreateReleaseBranchWithCreateReleaseBranchExecutor() throws Exception {
        CreateReleaseBranchExecutor executor = new CreateReleaseBranchExecutor(
                gitBranchAdapterFactory,
                codeRepositoryPort,
                runTaskContextPort
        );

        RunTask task = createTask(RunTaskType.CREATE_RELEASE_BRANCH);
        when(runTaskContextPort.getContext(task)).thenReturn(Optional.of(RunTaskContext.builder()
                .windowKey("RW-20260301")
                .releaseBranch("release/RW-20260301")
                .build()));

        executor.execute(task);

        assertTrue(mockAdapter.getBranchStatus(repo.getCloneUrl(), repo.getGitToken(), "release/RW-20260301").exists());
    }

    @Test
    void shouldMergeFeatureToReleaseAndArchiveFeatureBranch() throws Exception {
        CreateReleaseBranchExecutor createReleaseBranchExecutor = new CreateReleaseBranchExecutor(
                gitBranchAdapterFactory,
                codeRepositoryPort,
                runTaskContextPort
        );
        MergeFeatureToReleaseExecutor mergeExecutor = new MergeFeatureToReleaseExecutor(
                gitBranchAdapterFactory,
                codeRepositoryPort,
                runTaskContextPort
        );
        ArchiveFeatureBranchExecutor archiveExecutor = new ArchiveFeatureBranchExecutor(
                gitBranchAdapterFactory,
                codeRepositoryPort,
                runTaskContextPort
        );

        RunTask createReleaseTask = createTask(RunTaskType.CREATE_RELEASE_BRANCH);
        when(runTaskContextPort.getContext(createReleaseTask)).thenReturn(Optional.of(RunTaskContext.builder()
                .windowKey("RW-20260301")
                .releaseBranch("release/RW-20260301")
                .build()));
        createReleaseBranchExecutor.execute(createReleaseTask);

        assertTrue(mockAdapter.createBranch(repo.getCloneUrl(), repo.getGitToken(), "feature/ITER-1", "main"));

        RunTask mergeTask = createTask(RunTaskType.MERGE_FEATURE_TO_RELEASE);
        when(runTaskContextPort.getContext(mergeTask)).thenReturn(Optional.of(RunTaskContext.builder()
                .featureBranch("feature/ITER-1")
                .releaseBranch("release/RW-20260301")
                .build()));
        mergeExecutor.execute(mergeTask);

        RunTask archiveTask = createTask(RunTaskType.ARCHIVE_FEATURE_BRANCH);
        when(runTaskContextPort.getContext(archiveTask)).thenReturn(Optional.of(RunTaskContext.builder()
                .featureBranch("feature/ITER-1")
                .build()));
        archiveExecutor.execute(archiveTask);

        assertFalse(mockAdapter.getBranchStatus(repo.getCloneUrl(), repo.getGitToken(), "feature/ITER-1").exists());
    }

    @Test
    void shouldMergeReleaseToDefaultBranch() throws Exception {
        MergeReleaseToMasterExecutor executor = new MergeReleaseToMasterExecutor(
                gitBranchAdapterFactory,
                codeRepositoryPort,
                runTaskContextPort
        );

        assertTrue(mockAdapter.createBranch(repo.getCloneUrl(), repo.getGitToken(), "release/RW-20260301", "main"));

        RunTask task = createTask(RunTaskType.MERGE_RELEASE_TO_MASTER);
        when(runTaskContextPort.getContext(task)).thenReturn(Optional.of(RunTaskContext.builder()
                .releaseBranch("release/RW-20260301")
                .build()));

        executor.execute(task);

        assertTrue(mockAdapter.getBranchStatus(repo.getCloneUrl(), repo.getGitToken(), "main").exists());
    }

    @Test
    void shouldCreateTagWithCreateTagExecutor() throws Exception {
        CreateTagExecutor executor = new CreateTagExecutor(
                gitBranchAdapterFactory,
                codeRepositoryPort,
                runTaskContextPort
        );

        RunTask task = createTask(RunTaskType.CREATE_TAG);
        when(runTaskContextPort.getContext(task)).thenReturn(Optional.of(RunTaskContext.builder()
                .targetVersion("1.2.3")
                .build()));

        executor.execute(task);

        @SuppressWarnings("unchecked")
        Map<String, Set<String>> tags = (Map<String, Set<String>>) org.springframework.test.util.ReflectionTestUtils.getField(mockAdapter, "tags");
        assertNotNull(tags);
        assertTrue(tags.containsKey(repo.getCloneUrl()));
        assertTrue(tags.get(repo.getCloneUrl()).contains("v1.2.3"));
    }

    private RunTask createTask(RunTaskType taskType) {
        Instant now = Instant.now();
        Run run = Run.start(RunType.VERSION_UPDATE, "tester", now);
        return RunTask.create(run.getId(), taskType, 1, TargetType.REPOSITORY, "repo-1", 1, now);
    }

    private CodeRepository createRepo(String repoId, String cloneUrl) {
        Instant now = Instant.now();
        return CodeRepository.rehydrate(
                RepoId.of(repoId),
                "repo",
                cloneUrl,
                "main",
                "G001",
                RepoType.SERVICE,
                GitProvider.MOCK,
                "mock-token",
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                now,
                now,
                0L
        );
    }
}
