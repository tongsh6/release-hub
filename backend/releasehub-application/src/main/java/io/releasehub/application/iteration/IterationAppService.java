package io.releasehub.application.iteration;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.group.GroupPort;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
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
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.BranchCreationMode;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.version.BuildTool;
import io.releasehub.domain.version.ConflictResolution;
import io.releasehub.domain.version.VersionSource;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IterationAppService {
    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final IterationPort iterationPort;
    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final IterationRepoPort iterationRepoPort;
    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final BranchRuleUseCase branchRuleUseCase;
    private final VersionDeriverUseCase versionDeriverUseCase;
    private final VersionExtractorUseCase versionExtractorUseCase;
    private final VersionUpdateAppService versionUpdateAppService;
    private final GroupPort groupPort;
    private final Clock clock;

    private static final Set<String> BLOCKED_SEGMENTS = Set.of("release/", "hotfix/");

    /**
     * 仓库分支配置，用于 create/update 时传入每个仓库的分支创建模式。
     */
    public record RepoBranchConfig(String repoId, BranchCreationMode branchCreationMode, String customBranchName) {
        public RepoBranchConfig {
            if (branchCreationMode == null) {
                branchCreationMode = BranchCreationMode.AUTO;
            }
        }
    }

    @Transactional
    public Iteration create(String name, String description, LocalDate expectedReleaseAt, String groupCode, Set<String> repoIds, List<RepoBranchConfig> repoConfigs) {
        String iterationKey = generateIterationKey();
        Set<String> safeRepoIds = repoIds == null ? java.util.Set.of() : repoIds;
        Set<RepoId> repos = safeRepoIds.stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        ensureLeafGroup(groupCode);
        Iteration it = Iteration.create(IterationKey.of(iterationKey), name, description, expectedReleaseAt, groupCode, repos, Instant.now(clock));
        iterationPort.save(it);

        // 统一执行分支创建/映射
        var configs = resolveRepoConfigs(repos, repoConfigs);
        Instant now = Instant.now(clock);
        for (var config : configs) {
            try {
                setupRepoForIteration(it.getId(), RepoId.of(config.repoId), config.branchCreationMode, config.customBranchName, now);
            } catch (Exception e) {
                log.error("Failed to setup repo {} for iteration {}: {}", config.repoId, iterationKey, e.getMessage());
            }
        }
        return it;
    }

    /**
     * 自动生成迭代标识
     * 格式: ITER-yyyyMMdd-xxxx (xxxx 为随机4位)
     */
    private String generateIterationKey() {
        String datePart = KEY_DATE_FORMAT.format(Instant.now(clock).atZone(ZoneId.systemDefault()));
        String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "ITER-" + datePart + "-" + randomPart;
    }

    public List<Iteration> list() {
        return iterationPort.findAll();
    }

    public PageResult<Iteration> listPaged(String keyword, int page, int size) {
        return iterationPort.findPaged(keyword, page, size);
    }

    @Transactional
    public Iteration update(String key, String name, String description, LocalDate expectedReleaseAt, String groupCode, Set<String> repoIds, List<RepoBranchConfig> repoConfigs) {
        Iteration existing = get(key);
        Set<String> safeRepoIds = repoIds == null ? java.util.Set.of() : repoIds;
        Set<RepoId> newRepos = safeRepoIds.stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        Instant now = Instant.now(clock);
        ensureLeafGroup(groupCode);

        // 检测新增的仓库：执行分支创建/映射
        var configs = resolveRepoConfigs(newRepos, repoConfigs);
        for (var config : configs) {
            RepoId repoId = RepoId.of(config.repoId);
            if (!existing.getRepos().contains(repoId)) {
                try {
                    setupRepoForIteration(existing.getId(), repoId, config.branchCreationMode, config.customBranchName, now);
                } catch (Exception e) {
                    log.error("Failed to setup repo {} for iteration {}: {}", config.repoId, key, e.getMessage());
                }
            }
        }

        // 检测移除的仓库：归档 feature 分支
        for (RepoId oldRepoId : existing.getRepos()) {
            if (!newRepos.contains(oldRepoId)) {
                try {
                    archiveFeatureBranchForRepo(existing.getId(), oldRepoId);
                } catch (Exception e) {
                    log.error("Failed to archive branch for removed repo {}: {}", oldRepoId.value(), e.getMessage());
                }
            }
        }

        Iteration updated = Iteration.rehydrate(existing.getId(), name, description, expectedReleaseAt, groupCode, newRepos, existing.getStatus(), existing.getCreatedAt(), now);
        iterationPort.save(updated);
        return updated;
    }

    public Iteration get(String key) {
        return iterationPort.findByKey(IterationKey.of(key))
                            .orElseThrow(() -> NotFoundException.iteration(key));
    }

    @Transactional
    public Iteration addRepos(String key, Set<String> repoIds, BranchCreationMode branchCreationMode, String customBranchName) {
        Iteration existing = get(key);
        Set<RepoId> toAdd = (repoIds == null ? java.util.Set.<String>of() : repoIds).stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        java.util.Set<RepoId> merged = new java.util.LinkedHashSet<>(existing.getRepos());
        Instant now = Instant.now(clock);
        BranchCreationMode mode = branchCreationMode != null ? branchCreationMode : BranchCreationMode.AUTO;

        for (RepoId repoId : toAdd) {
            if (!merged.contains(repoId)) {
                try {
                    setupRepoForIteration(existing.getId(), repoId, mode, customBranchName, now);
                } catch (Exception e) {
                    log.error("Failed to setup repo {} for iteration {}: {}", repoId.value(), key, e.getMessage());
                }
            }
        }

        merged.addAll(toAdd);
        Iteration updated = Iteration.rehydrate(existing.getId(), existing.getName(), existing.getDescription(), existing.getExpectedReleaseAt(), existing.getGroupCode(), merged, existing.getStatus(), existing.getCreatedAt(), now);
        iterationPort.save(updated);
        return updated;
    }

    /**
     * 为迭代设置仓库：根据分支创建模式确定/创建 feature 分支，推导版本号，保存关联记录。
     */
    private void setupRepoForIteration(IterationKey iterationKey, RepoId repoId,
                                        BranchCreationMode mode, String customBranchName, Instant now) {
        CodeRepository repo = codeRepositoryPort.findById(repoId)
                .orElseThrow(() -> NotFoundException.repository(repoId.value()));

        // 1. 确定 feature 分支名
        String featureBranch = switch (mode) {
            case AUTO -> {
                String autoBranch = "feature/" + iterationKey.value();
                if (!branchRuleUseCase.isCompliant(autoBranch)) {
                    throw ValidationException.invalidParameter("自动生成的分支名不符合 BranchRule 规则");
                }
                yield autoBranch;
            }
            case NAMED -> {
                validateFeaturePrefix(customBranchName);
                if (!branchRuleUseCase.isCompliant(customBranchName)) {
                    throw ValidationException.invalidParameter("分支名不符合 BranchRule 规则");
                }
                yield customBranchName;
            }
            case EXISTING -> {
                validateFeaturePrefix(customBranchName);
                validateBranchExists(repo, customBranchName);
                yield customBranchName;
            }
        };

        // 2. 非 EXISTING 模式：创建分支
        if (mode != BranchCreationMode.EXISTING) {
            var gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
            boolean branchCreated = gitPort.createBranch(repo.getCloneUrl(), repo.getGitAccessToken(), featureBranch, repo.getDefaultBranch());
            if (!branchCreated) {
                log.warn("Feature branch {} already exists or failed to create for repo {}", featureBranch, repoId.value());
            }
        }

        // 3. 推导版本
        String baseVersion = codeRepositoryPort.getInitialVersion(repoId.value())
                .orElse("1.0.0-SNAPSHOT");
        String devVersion = versionDeriverUseCase.deriveDevVersion(baseVersion);
        String targetVersion = versionDeriverUseCase.deriveTargetVersion(devVersion);

        // 4. 写入 versionInfo（所有模式统一执行）
        iterationRepoPort.saveWithVersion(
                iterationKey.value(),
                repoId.value(),
                baseVersion,
                devVersion,
                targetVersion,
                featureBranch,
                VersionSource.SYSTEM.name(),
                now
        );

        log.info("Setup repo {} for iteration {}: mode={} featureBranch={} baseVersion={} devVersion={} targetVersion={}",
                repoId.value(), iterationKey.value(), mode, featureBranch, baseVersion, devVersion, targetVersion);
    }

    private void validateFeaturePrefix(String branchName) {
        if (branchName == null || !branchName.startsWith("feature/")) {
            throw ValidationException.invalidParameter("分支必须在 feature/ 路径下");
        }
        for (String blocked : BLOCKED_SEGMENTS) {
            if (branchName.startsWith(blocked)) {
                throw ValidationException.invalidParameter("不可操作保护分支: " + branchName);
            }
        }
        if (branchName.contains("../") || branchName.contains("..\\")) {
            throw ValidationException.invalidParameter("非法分支名");
        }
    }

    private void validateBranchExists(CodeRepository repo, String branchName) {
        var gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
        if (!gitPort.getBranchStatus(repo.getCloneUrl(), repo.getGitAccessToken(), branchName).exists()) {
            throw ValidationException.invalidParameter("分支不存在: " + branchName);
        }
    }

    /**
     * 将 repoIds 和可选的 repoConfigs 合并为统一的配置列表。
     * repoConfigs 中有配置的仓库使用指定模式，未配置的仓库默认 AUTO。
     * repoConfigs 为空时，全部仓库使用 AUTO 模式。
     */
    private List<RepoBranchConfig> resolveRepoConfigs(Set<RepoId> repos, List<RepoBranchConfig> repoConfigs) {
        if (repoConfigs == null || repoConfigs.isEmpty()) {
            List<RepoBranchConfig> result = new ArrayList<>();
            for (RepoId repoId : repos) {
                result.add(new RepoBranchConfig(repoId.value(), BranchCreationMode.AUTO, null));
            }
            return result;
        }
        Set<String> configuredIds = repoConfigs.stream().map(RepoBranchConfig::repoId).collect(java.util.stream.Collectors.toSet());
        List<RepoBranchConfig> merged = new ArrayList<>(repoConfigs);
        for (RepoId repoId : repos) {
            if (!configuredIds.contains(repoId.value())) {
                merged.add(new RepoBranchConfig(repoId.value(), BranchCreationMode.AUTO, null));
            }
        }
        return merged;
    }

    private void archiveFeatureBranchForRepo(IterationKey iterationKey, RepoId repoId) {
        String featureBranch = iterationRepoPort.getVersionInfo(iterationKey.value(), repoId.value())
                .map(IterationRepoVersionInfo::getFeatureBranch)
                .orElse("feature/" + iterationKey.value());
        codeRepositoryPort.findById(repoId).ifPresent(repo -> {
            var gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
            boolean archived = gitPort.archiveBranch(repo.getCloneUrl(), repo.getGitAccessToken(), featureBranch, "unpublished");
            if (!archived) {
                log.warn("Failed to archive branch {} for repo {}", featureBranch, repoId.value());
            }
        });
    }

    @Transactional
    public Iteration removeRepos(String key, Set<String> repoIds) {
        Iteration existing = get(key);
        java.util.Set<String> toRemove = repoIds == null ? java.util.Set.of() : repoIds;
        java.util.Set<RepoId> filtered = existing.getRepos().stream()
                                                 .filter(r -> !toRemove.contains(r.value()))
                                                 .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (RepoId repoId : existing.getRepos()) {
            if (toRemove.contains(repoId.value())) {
                try {
                    String featureBranch = iterationRepoPort.getVersionInfo(existing.getId().value(), repoId.value())
                            .map(IterationRepoVersionInfo::getFeatureBranch)
                            .orElse("feature/" + existing.getId().value());
                    codeRepositoryPort.findById(repoId).ifPresent(repo -> {
                        var gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
                        boolean archived = gitPort.archiveBranch(repo.getCloneUrl(), repo.getGitAccessToken(), featureBranch, "unpublished");
                        if (!archived) {
                            log.warn("Failed to archive branch {} for repo {}", featureBranch, repoId.value());
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to archive branch for repo {}: {}", repoId.value(), e.getMessage());
                }
            }
        }
        Instant now = Instant.now(clock);
        Iteration updated = Iteration.rehydrate(existing.getId(), existing.getName(), existing.getDescription(), existing.getExpectedReleaseAt(), existing.getGroupCode(), filtered, existing.getStatus(), existing.getCreatedAt(), now);
        iterationPort.save(updated);
        return updated;
    }

    public java.util.Set<String> listRepos(String key) {
        Iteration existing = get(key);
        return existing.getRepos().stream().map(RepoId::value).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Transactional
    public void delete(String key) {
        Iteration existing = get(key);
        if (!existing.getRepos().isEmpty()) {
            throw BusinessException.iterationAttached(key);
        }
        List<ReleaseWindow> windows = releaseWindowPort.findAll();
        boolean attached = windows.stream()
                                  .map(w -> windowIterationPort.listByWindow(ReleaseWindowId.of(w.getId().value())))
                                  .flatMap(List::stream)
                                  .map(WindowIteration::getIterationKey)
                                  .anyMatch(k -> k.equals(existing.getId()));
        if (attached) {
            throw BusinessException.iterationAttached(key);
        }
        iterationPort.deleteByKey(existing.getId());
    }

    // ==== 版本管理方法 ====

    /**
     * 检测版本冲突
     */
    public VersionConflict checkVersionConflict(String iterationKeyStr, String repoIdStr) {
        IterationKey iterationKey = IterationKey.of(iterationKeyStr);
        RepoId repoId = RepoId.of(repoIdStr);

        // 获取系统记录的版本
        IterationRepoVersionInfo versionInfo = getIterationRepoVersionInfo(iterationKey, repoId);
        String systemVersion = versionInfo.getDevVersion();

        // 获取仓库实际版本
        CodeRepository repo = codeRepositoryPort.findById(repoId)
                                                .orElseThrow(() -> NotFoundException.repository(repoIdStr));

        String repoVersion = versionExtractorUseCase.extractVersion(repo.getCloneUrl(), versionInfo.getFeatureBranch())
                                             .map(VersionExtractorUseCase.VersionInfo::version)
                                             .orElse(null);

        if (repoVersion == null) {
            return VersionConflict.noConflict(repoIdStr, iterationKeyStr, systemVersion);
        }

        if (systemVersion.equals(repoVersion)) {
            return VersionConflict.noConflict(repoIdStr, iterationKeyStr, systemVersion);
        }

        // 简单版本比较（可扩展为语义化版本比较）
        return VersionConflict.mismatch(repoIdStr, iterationKeyStr, systemVersion, repoVersion);
    }

    /**
     * 获取迭代仓库版本信息
     */
    public IterationRepoVersionInfo getIterationRepoVersionInfo(IterationKey iterationKey, RepoId repoId) {
        return iterationRepoPort.getVersionInfo(iterationKey.value(), repoId.value())
                                .orElseThrow(() -> NotFoundException.iterationRepo(iterationKey.value(), repoId.value()));
    }

    /**
     * 解决版本冲突
     */
    @Transactional
    public IterationRepoVersionInfo resolveVersionConflict(IterationKey iterationKey, RepoId repoId, ConflictResolution resolution) {
        IterationRepoVersionInfo versionInfo = getIterationRepoVersionInfo(iterationKey, repoId);

        Instant now = Instant.now(clock);

        switch (resolution) {
            case USE_REPO:
                // 使用仓库版本，从仓库同步
                return syncVersionFromRepo(iterationKey, repoId);

            case USE_SYSTEM:
                updateRepoToSystemVersion(repoId, versionInfo);
                iterationRepoPort.updateVersion(
                        iterationKey.value(),
                        repoId.value(),
                        versionInfo.getDevVersion(),
                        VersionSource.SYSTEM.name(),
                        now
                );
                log.info("Resolved conflict using system version for repo {} iteration {}",
                        repoId.value(), iterationKey.value());
                return getIterationRepoVersionInfo(iterationKey, repoId);

            case CANCEL:
            default:
                // 不做任何操作
                log.info("Conflict resolution cancelled for repo {} iteration {}",
                        repoId.value(), iterationKey.value());
                return versionInfo;
        }
    }

    private void updateRepoToSystemVersion(RepoId repoId, IterationRepoVersionInfo versionInfo) {
        if (versionInfo.getFeatureBranch() == null || versionInfo.getFeatureBranch().isBlank()) {
            throw ValidationException.invalidParameter("featureBranch");
        }
        if (versionInfo.getDevVersion() == null || versionInfo.getDevVersion().isBlank()) {
            throw ValidationException.invalidParameter("devVersion");
        }

        CodeRepository repo = codeRepositoryPort.findById(repoId)
                .orElseThrow(() -> NotFoundException.repository(repoId.value()));
        VersionExtractorUseCase.VersionInfo repoVersion = versionExtractorUseCase
                .extractVersion(repo.getCloneUrl(), versionInfo.getFeatureBranch())
                .orElseThrow(BusinessException::versionNotFoundInFile);

        BuildTool buildTool = switch (repoVersion.source()) {
            case POM -> BuildTool.MAVEN;
            case GRADLE -> BuildTool.GRADLE;
            default -> throw ValidationException.invalidParameter("Unsupported version source: " + repoVersion.source());
        };

        VersionUpdateRequest request = buildTool == BuildTool.MAVEN
                ? VersionUpdateRequest.forMaven(repoId, versionInfo.getFeatureBranch(), ".",
                        versionInfo.getDevVersion(), "pom.xml")
                : VersionUpdateRequest.forGradle(repoId, versionInfo.getFeatureBranch(), ".",
                        versionInfo.getDevVersion(), "gradle.properties");

        VersionUpdateResult result = versionUpdateAppService.updateVersion(request);
        if (!result.success()) {
            throw ValidationException.invalidParameter(result.errorMessage());
        }
    }

    /**
     * 从仓库同步版本号
     */
    @Transactional
    public IterationRepoVersionInfo syncVersionFromRepo(IterationKey iterationKey, RepoId repoId) {
        IterationRepoVersionInfo versionInfo = getIterationRepoVersionInfo(iterationKey, repoId);

        CodeRepository repo = codeRepositoryPort.findById(repoId)
                                                .orElseThrow(() -> NotFoundException.repository(repoId.value()));

        String repoVersion = versionExtractorUseCase.extractVersion(repo.getCloneUrl(), versionInfo.getFeatureBranch())
                                             .map(VersionExtractorUseCase.VersionInfo::version)
                                             .orElse(versionInfo.getDevVersion());

        Instant now = Instant.now(clock);

        iterationRepoPort.updateVersion(
                iterationKey.value(),
                repoId.value(),
                repoVersion,
                VersionSource.POM.name(),  // 假设从 POM 同步
                now
        );

        log.info("Synced version from repo {} for iteration {}: {} -> {}",
                repoId.value(), iterationKey.value(), versionInfo.getDevVersion(), repoVersion);

        return getIterationRepoVersionInfo(iterationKey, repoId);
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
}
