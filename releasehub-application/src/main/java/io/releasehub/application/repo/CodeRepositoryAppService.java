package io.releasehub.application.repo;

import io.releasehub.application.version.VersionExtractor;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
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
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public CodeRepository create(String name, String cloneUrl, String defaultBranch, boolean monoRepo) {
        CodeRepository repo = CodeRepository.create(name, cloneUrl, defaultBranch, monoRepo, Instant.now(clock));
        codeRepositoryPort.save(repo);
        
        // 尝试从仓库获取初始版本号
        try {
            versionExtractor.extractVersion(cloneUrl, defaultBranch)
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
        }
        
        return repo;
    }

    @Transactional
    public CodeRepository update(String repoId, String name, String cloneUrl, String defaultBranch, boolean monoRepo) {
        CodeRepository repo = get(repoId);
        repo.update(name, cloneUrl, defaultBranch, monoRepo, Instant.now(clock));
        codeRepositoryPort.save(repo);
        return repo;
    }

    @Transactional
    public void delete(String repoId) {
        CodeRepository repo = get(repoId);
        codeRepositoryPort.deleteById(repo.getId());
    }

    public CodeRepository get(String repoId) {
        return codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));
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

    public record GateSummary(boolean protectedBranch, boolean approvalRequired, boolean pipelineGate, boolean permissionDenied) {}
    public record BranchSummary(int totalBranches, int activeBranches, int nonCompliantBranches, int activeMrs, int mergedMrs, int closedMrs) {}
}
