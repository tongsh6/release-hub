package io.releasehub.application.repo;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.group.GroupPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.version.VersionExtractor;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.version.VersionSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeRepositoryAppService {
    private final CodeRepositoryPort codeRepositoryPort;
    private final VersionExtractor versionExtractor;
    private final SettingsPort settingsPort;
    private final GitLabPort gitLabPort;
    private final IterationPort iterationPort;
    private final GroupPort groupPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public CodeRepository create(String name, String cloneUrl, String defaultBranch, boolean monoRepo, String initialVersion, String groupCode) {
        String normalizedBranch = normalizeBranch(cloneUrl, defaultBranch);
        ensureLeafGroup(groupCode);
        CodeRepository repo = CodeRepository.create(name, cloneUrl, normalizedBranch, groupCode, monoRepo, Instant.now(clock));
        codeRepositoryPort.save(repo);

        if (initialVersion != null && !initialVersion.isBlank()) {
            codeRepositoryPort.updateInitialVersion(repo.getId().value(), initialVersion.trim(), VersionSource.MANUAL.name());
            log.info("Manually set initial version {} for repo {}", initialVersion.trim(), name);
        } else {
            // 尝试从仓库获取初始版本号
            try {
                versionExtractor.extractVersion(cloneUrl, normalizedBranch)
                                .ifPresent(versionInfo -> {
                                    codeRepositoryPort.updateInitialVersion(
                                            repo.getId().value(),
                                            versionInfo.version(),
                                            versionInfo.source().name()
                                    );
                                    log.info("Extracted initial version {} from {} for repo {}",
                                            versionInfo.version(), versionInfo.source(), name);
                                });
            } catch (Exception e) {
                log.warn("Failed to extract initial version for repo {}: {}", name, e.getMessage());
                codeRepositoryPort.updateInitialVersion(repo.getId().value(), null, "VERSION_UNRESOLVED");
            }
        }

        return repo;
    }

    private String normalizeBranch(String cloneUrl, String branch) {
        if (branch != null && !branch.isBlank()) {
            return branch.trim();
        }
        try {
            if (settingsPort.getGitLab().isPresent()) {
                long projectId = gitLabPort.resolveProjectId(cloneUrl);
                if (gitLabPort.branchExists(projectId, "main")) {
                    return "main";
                }
                if (gitLabPort.branchExists(projectId, "master")) {
                    return "master";
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve default branch for repo {}: {}", cloneUrl, e.getMessage());
        }
        return "main";
    }

    @Transactional
    public CodeRepository update(String repoId, String name, String cloneUrl, String defaultBranch, boolean monoRepo, String initialVersion, String groupCode) {
        CodeRepository repo = get(repoId);
        String normalizedBranch = normalizeBranch(cloneUrl, defaultBranch);
        ensureLeafGroup(groupCode);
        repo.update(name, cloneUrl, normalizedBranch, groupCode, monoRepo, Instant.now(clock));
        codeRepositoryPort.save(repo);
        if (initialVersion != null && !initialVersion.isBlank()) {
            codeRepositoryPort.updateInitialVersion(repoId, initialVersion.trim(), VersionSource.MANUAL.name());
            log.info("Manually set initial version {} for repo {}", initialVersion.trim(), repoId);
        }
        return repo;
    }

    public CodeRepository get(String repoId) {
        return codeRepositoryPort.findById(RepoId.of(repoId))
                                 .orElseThrow(() -> NotFoundException.repository(repoId));
    }

    private void ensureLeafGroup(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw ValidationException.groupCodeRequired();
        }
        groupPort.findByCode(groupCode)
                .orElseThrow(() -> NotFoundException.groupCode(groupCode));
        if (groupPort.countChildren(groupCode) > 0) {
            throw BusinessException.groupHasChildren(groupCode);
        }
    }

    @Transactional
    public void delete(String repoId) {
        CodeRepository repo = get(repoId);
        boolean attached = iterationPort.findAll().stream()
                .anyMatch(it -> it.getRepos().stream().anyMatch(r -> r.value().equals(repoId)));
        if (attached) {
            throw BusinessException.repoAttached(repoId);
        }
        codeRepositoryPort.deleteById(repo.getId());
    }

    public List<CodeRepository> list() {
        return codeRepositoryPort.findAll();
    }

    public List<CodeRepository> search(String keyword) {
        return codeRepositoryPort.search(keyword);
    }

    public PageResult<CodeRepository> searchPaged(String keyword, int page, int size) {
        return codeRepositoryPort.searchPaged(keyword, page, size);
    }

    public GateSummary getGateSummary(String repoId) {
        CodeRepository repo = get(repoId);
        if (settingsPort.getGitLab().isEmpty()) {
            return new GateSummary(false, false, false, false);
        }
        try {
            long projectId = gitLabPort.resolveProjectId(repo.getCloneUrl());
            var summary = gitLabPort.fetchGateSummary(projectId);
            return new GateSummary(summary.protectedBranch(), summary.approvalRequired(), summary.pipelineGate(), summary.permissionDenied());
        } catch (Exception e) {
            log.warn("Failed to fetch gate summary for repo {}: {}", repoId, e.getMessage());
            return new GateSummary(false, false, false, true);
        }
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

    /**
     * 手动设置仓库的初始版本号
     */
    @Transactional
    public void setInitialVersion(String repoId, String version) {
        // 验证仓库存在
        get(repoId);
        codeRepositoryPort.updateInitialVersion(repoId, version, "MANUAL");
        log.info("Manually set initial version {} for repo {}", version, repoId);
    }

    /**
     * 获取仓库的初始版本号
     */
    public String getInitialVersion(String repoId) {
        // 验证仓库存在
        get(repoId);
        return codeRepositoryPort.getInitialVersion(repoId).orElse(null);
    }

    /**
     * 从仓库同步初始版本号
     */
    @Transactional
    public String syncInitialVersionFromRepo(String repoId) {
        CodeRepository repo = get(repoId);
        return versionExtractor.extractVersion(repo.getCloneUrl(), repo.getDefaultBranch())
                               .map(versionInfo -> {
                                   codeRepositoryPort.updateInitialVersion(repoId, versionInfo.version(), versionInfo.source().name());
                                   log.info("Synced initial version {} from {} for repo {}",
                                           versionInfo.version(), versionInfo.source(), repo.getName());
                                   return versionInfo.version();
                               })
                               .orElse(null);
    }

    @Transactional
    public CodeRepository sync(String repoId) {
        CodeRepository repo = get(repoId);
        if (settingsPort.getGitLab().isEmpty()) {
            throw BusinessException.gitlabSettingsMissing();
        }
        long projectId = gitLabPort.resolveProjectId(repo.getCloneUrl());
        var branchStats = gitLabPort.fetchBranchStatistics(projectId);
        var mrStats = gitLabPort.fetchMrStatistics(projectId);
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
        return repo;
    }

    public record GateSummary(boolean protectedBranch, boolean approvalRequired, boolean pipelineGate,
                              boolean permissionDenied) {
    }

    public record BranchSummary(int totalBranches, int activeBranches, int nonCompliantBranches, int activeMrs,
                                int mergedMrs, int closedMrs) {
    }
}
