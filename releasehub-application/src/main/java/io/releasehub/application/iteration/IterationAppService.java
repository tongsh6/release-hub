package io.releasehub.application.iteration;

import io.releasehub.application.branchrule.BranchRuleAppService;
import io.releasehub.application.group.GroupPort;
import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionDeriver;
import io.releasehub.application.version.VersionExtractor;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
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
    private final GitLabBranchPort gitLabBranchPort;
    private final BranchRuleAppService branchRuleAppService;
    private final VersionDeriver versionDeriver;
    private final VersionExtractor versionExtractor;
    private final GroupPort groupPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public Iteration create(String name, String description, LocalDate expectedReleaseAt, String groupCode, Set<String> repoIds) {
        String iterationKey = generateIterationKey();
        Set<String> safeRepoIds = repoIds == null ? java.util.Set.of() : repoIds;
        Set<RepoId> repos = safeRepoIds.stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        ensureLeafGroup(groupCode);
        Iteration it = Iteration.create(IterationKey.of(iterationKey), name, description, expectedReleaseAt, groupCode, repos, Instant.now(clock));
        iterationPort.save(it);
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
    public Iteration update(String key, String name, String description, LocalDate expectedReleaseAt, String groupCode, Set<String> repoIds) {
        Iteration existing = get(key);
        Set<String> safeRepoIds = repoIds == null ? java.util.Set.of() : repoIds;
        Set<RepoId> repos = safeRepoIds.stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        Instant now = Instant.now(clock);
        ensureLeafGroup(groupCode);
        Iteration updated = Iteration.rehydrate(existing.getId(), name, description, expectedReleaseAt, groupCode, repos, existing.getStatus(), existing.getCreatedAt(), now);
        iterationPort.save(updated);
        return updated;
    }

    public Iteration get(String key) {
        return iterationPort.findByKey(IterationKey.of(key))
                            .orElseThrow(() -> NotFoundException.iteration(key));
    }

    @Transactional
    public Iteration addRepos(String key, Set<String> repoIds) {
        Iteration existing = get(key);
        Set<RepoId> toAdd = (repoIds == null ? java.util.Set.<String>of() : repoIds).stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        java.util.Set<RepoId> merged = new java.util.LinkedHashSet<>(existing.getRepos());
        Instant now = Instant.now(clock);

        // 为每个新增的仓库创建 feature 分支并设置版本信息
        for (RepoId repoId : toAdd) {
            if (!merged.contains(repoId)) {
                try {
                    setupRepoForIteration(existing.getId(), repoId, now);
                } catch (Exception e) {
                    log.error("Failed to setup repo {} for iteration {}: {}", repoId.value(), key, e.getMessage());
                    // 继续处理其他仓库，不影响整体流程
                }
            }
        }

        merged.addAll(toAdd);
        Iteration updated = Iteration.rehydrate(existing.getId(), existing.getName(), existing.getDescription(), existing.getExpectedReleaseAt(), existing.getGroupCode(), merged, existing.getStatus(), existing.getCreatedAt(), now);
        iterationPort.save(updated);
        return updated;
    }

    /**
     * 为迭代设置仓库：创建 feature 分支，推导版本号，保存关联记录
     */
    private void setupRepoForIteration(IterationKey iterationKey, RepoId repoId, Instant now) {
        // 1. 获取仓库信息
        CodeRepository repo = codeRepositoryPort.findById(repoId)
                                                .orElseThrow(() -> NotFoundException.repository(repoId.value()));

        String masterBranch = repo.getDefaultBranch();

        // 2. 获取 master 分支版本号（从初始版本号获取）
        String baseVersion = codeRepositoryPort.getInitialVersion(repoId.value())
                                               .orElse("1.0.0-SNAPSHOT");

        // 3. 创建 feature 分支
        String featureBranch = "feature/" + iterationKey.value();
        if (!branchRuleAppService.isCompliant(featureBranch)) {
            throw ValidationException.invalidParameter("branchName");
        }
        boolean branchCreated = gitLabBranchPort.createBranch(repo.getCloneUrl(), featureBranch, masterBranch);
        if (!branchCreated) {
            log.warn("Failed to create feature branch {} for repo {}", featureBranch, repoId.value());
        }

        // 4. 推导开发版本和目标版本
        String devVersion = versionDeriver.deriveDevVersion(baseVersion);
        String targetVersion = versionDeriver.deriveTargetVersion(devVersion);

        // 5. 保存版本信息到关联记录
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

        log.info("Setup repo {} for iteration {}: feature branch={}, baseVersion={}, devVersion={}, targetVersion={}",
                repoId.value(), iterationKey.value(), featureBranch, baseVersion, devVersion, targetVersion);
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
                        boolean archived = gitLabBranchPort.archiveBranch(repo.getCloneUrl(), featureBranch, "unpublished");
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

        String repoVersion = versionExtractor.extractVersion(repo.getCloneUrl(), versionInfo.getFeatureBranch())
                                             .map(VersionExtractor.VersionInfo::version)
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
                // 使用系统版本，更新同步时间
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

    /**
     * 从仓库同步版本号
     */
    @Transactional
    public IterationRepoVersionInfo syncVersionFromRepo(IterationKey iterationKey, RepoId repoId) {
        IterationRepoVersionInfo versionInfo = getIterationRepoVersionInfo(iterationKey, repoId);

        CodeRepository repo = codeRepositoryPort.findById(repoId)
                                                .orElseThrow(() -> NotFoundException.repository(repoId.value()));

        String repoVersion = versionExtractor.extractVersion(repo.getCloneUrl(), versionInfo.getFeatureBranch())
                                             .map(VersionExtractor.VersionInfo::version)
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
