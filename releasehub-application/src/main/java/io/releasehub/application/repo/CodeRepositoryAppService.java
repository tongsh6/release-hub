package io.releasehub.application.repo;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.project.ProjectId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeRepositoryAppService {
    private final CodeRepositoryPort codeRepositoryPort;
    private final GitLabPort gitLabPort;
    private final SettingsPort settingsPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public CodeRepository create(String projectId, Long gitlabProjectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo) {
        CodeRepository repo = CodeRepository.create(new ProjectId(projectId), gitlabProjectId, name, cloneUrl, defaultBranch, monoRepo, Instant.now(clock));
        codeRepositoryPort.save(repo);
        return repo;
    }

    @Transactional
    public CodeRepository update(String repoId, Long gitlabProjectId, String name, String cloneUrl, String defaultBranch, boolean monoRepo) {
        CodeRepository repo = get(repoId);
        repo.update(gitlabProjectId, name, cloneUrl, defaultBranch, monoRepo, Instant.now(clock));
        codeRepositoryPort.save(repo);
        return repo;
    }

    @Transactional
    public void delete(String repoId) {
        CodeRepository repo = get(repoId);
        codeRepositoryPort.deleteById(repo.getId());
    }

    public CodeRepository get(String repoId) {
        return codeRepositoryPort.findById(new RepoId(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));
    }

    public List<CodeRepository> list() {
        return codeRepositoryPort.findAll();
    }

    public List<CodeRepository> listByProject(String projectId) {
        return codeRepositoryPort.findByProjectId(new ProjectId(projectId));
    }
    
    public List<CodeRepository> search(String keyword, String projectId, Long gitlabProjectId) {
        ProjectId pid = projectId != null && !projectId.isBlank() ? new ProjectId(projectId) : null;
        return codeRepositoryPort.search(keyword, pid, gitlabProjectId);
    }

    @Transactional
    public void syncRepository(String repoId) {
        CodeRepository repo = get(repoId);
        if (settingsPort.getGitLab().isEmpty()) {
            throw BusinessException.gitlabSettingsMissing();
        }
        var branchStats = gitLabPort.fetchBranchStatistics(repo.getGitlabProjectId());
        var mrStats = gitLabPort.fetchMrStatistics(repo.getGitlabProjectId());
        
        repo.updateStatistics(
                branchStats.total(),
                branchStats.active(),
                branchStats.nonCompliant(),
                mrStats.total(),
                mrStats.open(),
                mrStats.merged(),
                mrStats.closed(),
                Instant.now(clock)
        );
        codeRepositoryPort.save(repo);
    }

    public GateSummary getGateSummary(String repoId) {
        // Mock implementation for now
        return new GateSummary(true, true, true, false);
    }

    public BranchSummary getBranchSummary(String repoId) {
        CodeRepository repo = get(repoId);
        return new BranchSummary(
                repo.getBranchCount(),
                repo.getActiveBranchCount(),
                repo.getNonCompliantBranchCount(),
                repo.getOpenMrCount(),
                repo.getMergedMrCount(),
                repo.getClosedMrCount()
        );
    }

    public record GateSummary(boolean protectedBranch, boolean approvalRequired, boolean pipelineGate, boolean permissionDenied) {}
    public record BranchSummary(int totalBranches, int activeBranches, int nonCompliantBranches, int activeMrs, int mergedMrs, int closedMrs) {}
}
