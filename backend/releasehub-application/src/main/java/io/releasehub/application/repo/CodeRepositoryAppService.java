package io.releasehub.application.repo;

import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.group.GroupPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.version.VersionExtractorUseCase;
import io.releasehub.application.version.VersionExtractorUseCase.VersionInspection;
import io.releasehub.application.version.VersionExtractorUseCase.VersionInspectionError;
import io.releasehub.application.version.VersionExtractorUseCase.VersionInspectionStatus;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.CloneUrl;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.version.VersionSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeRepositoryAppService {
    private final CodeRepositoryPort codeRepositoryPort;
    private final VersionExtractorUseCase versionExtractorUseCase;
    private final SettingsPort settingsPort;
    private final GitLabPort gitLabPort;
    private final IterationPort iterationPort;
    private final GroupPort groupPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public CodeRepository create(String name, String cloneUrl, String defaultBranch, RepoType repoType, boolean monoRepo, String initialVersion, String groupCode) {
        return create(name, cloneUrl, defaultBranch, repoType, monoRepo, initialVersion, groupCode, GitProvider.GITLAB, null);
    }

    @Transactional
    public CodeRepository create(String name, String cloneUrl, String defaultBranch, RepoType repoType, boolean monoRepo, String initialVersion, String groupCode, GitProvider gitProvider, String gitAccessToken) {
        CloneUrl parsedCloneUrl = CloneUrl.parse(cloneUrl);
        String normalizedBranch = normalizeBranch(parsedCloneUrl.value(), defaultBranch);
        GitProvider effectiveProvider = gitProvider != null ? gitProvider : GitProvider.GITLAB;
        ensureProductGitProvider(effectiveProvider);
        ensureLeafGroup(groupCode);
        ensureCloneUrlUnique(parsedCloneUrl, null);
        CodeRepository repo = CodeRepository.create(name, parsedCloneUrl.value(), normalizedBranch, groupCode, repoType, effectiveProvider, gitAccessToken, monoRepo, Instant.now(clock));
        codeRepositoryPort.save(repo);

        if (initialVersion != null && !initialVersion.isBlank()) {
            codeRepositoryPort.updateInitialVersion(repo.getId().value(), initialVersion.trim(), VersionSource.MANUAL.name());
            log.info("Manually set initial version {} for repo {}", initialVersion.trim(), name);
        } else {
            inspectAndStoreInitialVersion(repo, normalizedBranch);
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
    public CodeRepository update(String repoId, String name, String cloneUrl, String defaultBranch, RepoType repoType, boolean monoRepo, String initialVersion, String groupCode) {
        return update(repoId, name, cloneUrl, defaultBranch, repoType, monoRepo, initialVersion, groupCode, null, null);
    }

    @Transactional
    public CodeRepository update(String repoId, String name, String cloneUrl, String defaultBranch, RepoType repoType, boolean monoRepo, String initialVersion, String groupCode, GitProvider gitProvider, String gitAccessToken) {
        CodeRepository repo = get(repoId);
        CloneUrl parsedCloneUrl = CloneUrl.parse(cloneUrl);
        String normalizedBranch = normalizeBranch(parsedCloneUrl.value(), defaultBranch);
        GitProvider effectiveProvider = gitProvider != null ? gitProvider : repo.getGitProvider();
        ensureProductGitProvider(effectiveProvider);
        ensureLeafGroup(groupCode);
        ensureCloneUrlUnique(parsedCloneUrl, repo.getId());
        String effectiveToken = (gitAccessToken != null && !gitAccessToken.isBlank()) ? gitAccessToken.trim() : repo.getGitAccessToken();
        repo.update(name, parsedCloneUrl.value(), normalizedBranch, groupCode, repoType, effectiveProvider, effectiveToken, monoRepo, Instant.now(clock));
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
            throw BusinessException.groupNotLeaf(groupCode);
        }
    }

    private void ensureProductGitProvider(GitProvider gitProvider) {
        if (gitProvider == GitProvider.MOCK) {
            throw ValidationException.repoMockProviderForbidden();
        }
    }

    private void ensureCloneUrlUnique(CloneUrl candidate, RepoId excludedRepoId) {
        codeRepositoryPort.findAll().stream()
                .filter(existing -> excludedRepoId == null || !existing.getId().equals(excludedRepoId))
                .filter(existing -> sameCloneUrl(existing, candidate))
                .findFirst()
                .ifPresent(existing -> {
                    throw BusinessException.repoCloneUrlExists(candidate.value(), existing.getId().value());
                });
    }

    private boolean sameCloneUrl(CodeRepository existing, CloneUrl candidate) {
        try {
            return CloneUrl.parse(existing.getCloneUrl()).canonicalKey().equals(candidate.canonicalKey());
        } catch (ValidationException e) {
            log.warn("Skip invalid existing cloneUrl while checking repository uniqueness, repoId={}", existing.getId().value());
            return false;
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

    public PageResult<CodeRepository> searchPaged(String keyword, String groupCode, int page, int size) {
        if (groupCode == null || groupCode.isBlank()) {
            return searchPaged(keyword, page, size);
        }
        groupPort.findByCode(groupCode)
                .orElseThrow(() -> NotFoundException.groupCode(groupCode));
        Set<String> groupCodes = collectGroupScopeCodes(groupCode);
        return codeRepositoryPort.searchPaged(keyword, groupCodes, page, size);
    }

    private Set<String> collectGroupScopeCodes(String rootGroupCode) {
        Set<String> codes = new LinkedHashSet<>();
        collectGroupScopeCodes(rootGroupCode, codes);
        return codes;
    }

    private void collectGroupScopeCodes(String groupCode, Set<String> codes) {
        if (!codes.add(groupCode)) {
            return;
        }
        groupPort.findByParentCode(groupCode).forEach(child -> collectGroupScopeCodes(child.getCode(), codes));
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
     * 获取仓库初始版本和来源，用于前端呈现版本解析状态。
     */
    public InitialVersionInfo getInitialVersionInfo(String repoId) {
        CodeRepository repo = get(repoId);
        String versionSource = codeRepositoryPort.getInitialVersionSource(repoId).orElse(null);
        return new InitialVersionInfo(
                codeRepositoryPort.getInitialVersion(repoId).orElse(null),
                versionSource,
                repo.getDefaultBranch(),
                checkedPathsForVersionSource(versionSource),
                errorTypeForVersionSource(versionSource),
                messageForVersionSource(versionSource)
        );
    }

    /**
     * 从仓库同步初始版本号
     */
    @Transactional
    public String syncInitialVersionFromRepo(String repoId) {
        CodeRepository repo = get(repoId);
        VersionInspection inspection = inspectVersion(repo.getCloneUrl(), repo.getDefaultBranch());
        storeInitialVersionInspection(repo, inspection);
        return inspection.version();
    }

    private void inspectAndStoreInitialVersion(CodeRepository repo, String branch) {
        VersionInspection inspection = inspectVersion(repo.getCloneUrl(), branch);
        storeInitialVersionInspection(repo, inspection);
    }

    private VersionInspection inspectVersion(String cloneUrl, String branch) {
        try {
            VersionInspection inspection = versionExtractorUseCase.inspectVersion(cloneUrl, branch);
            if (inspection != null) {
                return inspection;
            }
            return versionExtractorUseCase.extractVersion(cloneUrl, branch)
                    .map(info -> VersionInspection.resolved(info.version(), info.source(), branch, List.of("pom.xml", "gradle.properties")))
                    .orElseGet(() -> VersionInspection.unresolved(
                            VersionInspectionError.VERSION_UNRESOLVED,
                            branch,
                            List.of("pom.xml", "gradle.properties"),
                            "未能从仓库解析版本号"
                    ));
        } catch (Exception e) {
            log.warn("Failed to inspect initial version for branch {}: {}", branch, e.getMessage());
            return VersionInspection.unresolved(
                    VersionInspectionError.VERSION_READ_ERROR,
                    branch,
                    List.of("pom.xml", "gradle.properties"),
                    "读取版本文件失败: " + e.getMessage()
            );
        }
    }

    private void storeInitialVersionInspection(CodeRepository repo, VersionInspection inspection) {
        if (inspection.status() == VersionInspectionStatus.RESOLVED) {
            codeRepositoryPort.updateInitialVersion(
                    repo.getId().value(),
                    inspection.version(),
                    inspection.source().name()
            );
            log.info("Extracted initial version {} from {} for repo {}",
                    inspection.version(), inspection.source(), repo.getName());
            return;
        }

        String errorType = inspection.errorType() != null
                ? inspection.errorType().name()
                : VersionInspectionError.VERSION_UNRESOLVED.name();
        codeRepositoryPort.updateInitialVersion(repo.getId().value(), null, errorType);
        log.info("Initial version unresolved for repo {}, errorType={}, branch={}, checkedPaths={}",
                repo.getName(), errorType, inspection.branch(), inspection.checkedPaths());
    }

    private List<String> checkedPathsForVersionSource(String versionSource) {
        if (versionSource == null || !versionSource.startsWith("VERSION_")) {
            return List.of();
        }
        return List.of("pom.xml", "gradle.properties");
    }

    private String errorTypeForVersionSource(String versionSource) {
        if (versionSource == null || !versionSource.startsWith("VERSION_")) {
            return null;
        }
        return versionSource;
    }

    private String messageForVersionSource(String versionSource) {
        if (versionSource == null) {
            return null;
        }
        return switch (versionSource) {
            case "VERSION_FILE_MISSING" -> "未找到 pom.xml 或 gradle.properties";
            case "VERSION_DECL_MISSING" -> "版本文件存在，但未找到项目版本号声明";
            case "VERSION_INVALID" -> "版本号格式异常，请检查版本文件中的 version 值";
            case "VERSION_READ_ERROR" -> "读取版本文件失败，请检查 Git 访问权限、仓库地址和默认分支";
            case "VERSION_UNRESOLVED" -> "未能从仓库解析版本号";
            default -> null;
        };
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

    public record InitialVersionInfo(
            String version,
            String versionSource,
            String branch,
            List<String> checkedPaths,
            String errorType,
            String message
    ) {
        public InitialVersionInfo {
            checkedPaths = checkedPaths == null ? List.of() : List.copyOf(checkedPaths);
        }

        @Override
        public List<String> checkedPaths() {
            return List.copyOf(checkedPaths);
        }
    }
}
