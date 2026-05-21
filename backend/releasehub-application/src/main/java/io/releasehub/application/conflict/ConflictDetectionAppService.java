package io.releasehub.application.conflict;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionDeriverUseCase;
import io.releasehub.application.version.VersionExtractorUseCase;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.conflict.ConflictItem;
import io.releasehub.domain.conflict.ConflictReport;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionAppService {

    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationPort iterationPort;
    private final IterationRepoPort iterationRepoPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final VersionExtractorUseCase versionExtractorUseCase;
    private final VersionDeriverUseCase versionDeriverUseCase;
    private final BranchRuleUseCase branchRuleUseCase;
    private final ConflictDetectionPort conflictDetectionPort;

    private static final String RELEASE_PREFIX = "release/";
    private static final String FEATURE_PREFIX = "feature/";

    /**
     * 扫描指定发布窗口的所有冲突
     */
    public ConflictReport checkWindowConflicts(String windowId) {
        List<ConflictItem> allConflicts = new ArrayList<>();

        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElse(null);
        if (rw == null) {
            return ConflictReport.empty(windowId);
        }

        String releaseBranch = RELEASE_PREFIX + rw.getWindowKey();

        List<WindowIteration> bindings = windowIterationPort.listByWindow(ReleaseWindowId.of(windowId));
        for (WindowIteration wi : bindings) {
            Iteration iteration = iterationPort.findByKey(wi.getIterationKey()).orElse(null);
            if (iteration == null) continue;

            String iterationKey = iteration.getId().value();
            for (RepoId repoId : iteration.getRepos()) {
                Optional<CodeRepository> repoOpt = codeRepositoryPort.findById(repoId);
                if (repoOpt.isEmpty()) continue;
                CodeRepository repo = repoOpt.get();

                Optional<IterationRepoVersionInfo> versionInfoOpt =
                        iterationRepoPort.getVersionInfo(iterationKey, repoId.value());
                String featureBranch = versionInfoOpt
                        .map(IterationRepoVersionInfo::getFeatureBranch)
                        .orElse(FEATURE_PREFIX + iterationKey);
                String systemVersion = versionInfoOpt
                        .map(IterationRepoVersionInfo::getDevVersion)
                        .orElse(null);

                // 1. 版本号冲突检测
                allConflicts.addAll(detectVersionConflicts(repo, featureBranch, systemVersion, iterationKey));

                // 2. 分支冲突检测
                allConflicts.addAll(detectBranchConflicts(repo, featureBranch, releaseBranch, iterationKey));

                // 3. 合并冲突预检
                allConflicts.addAll(detectMergeConflicts(repo, featureBranch, releaseBranch, iterationKey));
            }

            // 4. 跨仓库版本一致性检测
            allConflicts.addAll(detectCrossRepoConflicts(iteration));
        }

        ConflictReport report = ConflictReport.of(windowId, allConflicts);
        conflictDetectionPort.saveReport(windowId, report);
        return report;
    }

    private List<ConflictItem> detectVersionConflicts(CodeRepository repo, String branch,
                                                       String systemVersion, String iterationKey) {
        List<ConflictItem> results = new ArrayList<>();
        if (systemVersion == null) return results;

        String repoId = repo.getId().value();
        String repoName = repo.getName();

        Optional<VersionExtractorUseCase.VersionInfo> extractedOpt;
        try {
            extractedOpt = versionExtractorUseCase.extractVersion(repo.getCloneUrl(), branch);
        } catch (RuntimeException e) {
            log.warn("Skip version conflict detection for repo {} on branch {}: {}",
                    repoId, branch, e.getMessage());
            return results;
        }

        if (extractedOpt.isEmpty()) return results;

        String repoVersion = extractedOpt.get().version();
        if (systemVersion.equals(repoVersion)) return results;

        results.add(buildVersionConflict(repoId, repoName, iterationKey, systemVersion, repoVersion));
        return results;
    }

    private ConflictItem buildVersionConflict(String repoId, String repoName, String iterationKey,
                                              String systemVersion, String repoVersion) {
        int comparison = versionDeriverUseCase.compareVersions(systemVersion, repoVersion);
        if (comparison < 0) {
            return ConflictItem.repoAhead(repoId, repoName, iterationKey, systemVersion, repoVersion);
        }
        if (comparison > 0) {
            return ConflictItem.systemAhead(repoId, repoName, iterationKey, systemVersion, repoVersion);
        }
        return ConflictItem.versionMismatch(repoId, repoName, iterationKey, systemVersion, repoVersion);
    }

    private List<ConflictItem> detectBranchConflicts(CodeRepository repo, String featureBranch,
                                                      String releaseBranch, String iterationKey) {
        List<ConflictItem> results = new ArrayList<>();
        String repoId = repo.getId().value();
        String repoName = repo.getName();

        if (!isBranchCompliantForRepo(featureBranch, repo)) {
            results.add(ConflictItem.branchNoncompliant(repoId, repoName, iterationKey, featureBranch));
        }
        if (!isBranchCompliantForRepo(releaseBranch, repo)) {
            results.add(ConflictItem.branchNoncompliant(repoId, repoName, iterationKey, releaseBranch));
        }

        return results;
    }

    private boolean isBranchCompliantForRepo(String branchName, CodeRepository repo) {
        return branchRuleUseCase.isCompliant(branchName, repo.getGroupCode(), repo.getId().value());
    }

    private List<ConflictItem> detectMergeConflicts(CodeRepository repo, String featureBranch,
                                                     String releaseBranch, String iterationKey) {
        List<ConflictItem> results = new ArrayList<>();
        GitBranchPort gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());

        BranchStatusProbe featureStatus = readBranchStatus(gitPort, repo, featureBranch, null, iterationKey);
        BranchStatusProbe releaseStatus = readBranchStatus(gitPort, repo, releaseBranch, null, iterationKey);
        featureStatus.conflict().ifPresent(results::add);
        releaseStatus.conflict().ifPresent(results::add);
        if (!results.isEmpty()) {
            return results;
        }

        String masterBranch = repo.getDefaultBranch();
        if (featureStatus.status().exists() && releaseStatus.status().exists()) {
            checkMergeability(gitPort, repo, featureBranch, releaseBranch, iterationKey)
                    .ifPresent(results::add);
        }

        if (releaseStatus.status().exists()) {
            checkMergeability(gitPort, repo, releaseBranch, masterBranch, iterationKey)
                    .ifPresent(results::add);
        }

        return results;
    }

    private BranchStatusProbe readBranchStatus(GitBranchPort gitPort, CodeRepository repo, String sourceBranch,
                                               String targetBranch, String iterationKey) {
        try {
            return new BranchStatusProbe(
                    gitPort.getBranchStatus(repo.getCloneUrl(), repo.getGitAccessToken(), sourceBranch),
                    Optional.empty());
        } catch (RuntimeException e) {
            log.warn("Git branch status check failed for repo {} branch {}: {}",
                    repo.getId().value(), sourceBranch, e.getMessage());
            return new BranchStatusProbe(GitBranchPort.BranchStatus.missing(),
                    Optional.of(buildGitAccessConflict(repo, iterationKey, sourceBranch, targetBranch, e)));
        }
    }

    private Optional<ConflictItem> checkMergeability(GitBranchPort gitPort, CodeRepository repo,
                                                     String sourceBranch, String targetBranch, String iterationKey) {
        GitBranchPort.MergeabilityResult mrCheck;
        try {
            mrCheck = gitPort.checkMergeability(
                    repo.getCloneUrl(), repo.getGitAccessToken(), sourceBranch, targetBranch);
        } catch (RuntimeException e) {
            log.warn("Git mergeability check failed for repo {} {} -> {}: {}",
                    repo.getId().value(), sourceBranch, targetBranch, e.getMessage());
            return Optional.of(buildGitAccessConflict(repo, iterationKey, sourceBranch, targetBranch, e));
        }
        if (mrCheck.canMerge()) {
            return Optional.empty();
        }
        String repoId = repo.getId().value();
        String repoName = repo.getName();
        return Optional.of(switch (mrCheck.failure()) {
            case CONFLICT -> ConflictItem.mergeConflict(repoId, repoName, iterationKey,
                    sourceBranch, targetBranch, mrCheck.detail());
            case PERMISSION_DENIED -> ConflictItem.gitPermissionDenied(repoId, repoName, iterationKey,
                    sourceBranch, targetBranch, mrCheck.detail());
            case UNAVAILABLE, UNKNOWN -> ConflictItem.gitUnavailable(repoId, repoName, iterationKey,
                    sourceBranch, targetBranch, mrCheck.detail());
            case NONE -> ConflictItem.gitUnavailable(repoId, repoName, iterationKey,
                    sourceBranch, targetBranch, "Mergeability check returned non-mergeable without a failure reason");
        });
    }

    private ConflictItem buildGitAccessConflict(CodeRepository repo, String iterationKey,
                                                 String sourceBranch, String targetBranch, RuntimeException e) {
        String repoId = repo.getId().value();
        String repoName = repo.getName();
        String detail = e.getMessage();
        if (isPermissionDenied(e)) {
            return ConflictItem.gitPermissionDenied(repoId, repoName, iterationKey,
                    sourceBranch, targetBranch, detail);
        }
        return ConflictItem.gitUnavailable(repoId, repoName, iterationKey,
                sourceBranch, targetBranch, detail);
    }

    private boolean isPermissionDenied(RuntimeException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return message.contains("401")
                || message.contains("403")
                || normalized.contains("forbidden")
                || normalized.contains("unauthorized");
    }

    private record BranchStatusProbe(
            GitBranchPort.BranchStatus status,
            Optional<ConflictItem> conflict
    ) {
    }

    private List<ConflictItem> detectCrossRepoConflicts(Iteration iteration) {
        List<ConflictItem> results = new ArrayList<>();
        List<RepoId> repos = List.copyOf(iteration.getRepos());
        if (repos.size() < 2) return results;

        String iterationKey = iteration.getId().value();

        record RepoVersionPair(String repoId, String version) {}

        List<RepoVersionPair> pairs = new ArrayList<>();
        for (RepoId repoId : repos) {
            iterationRepoPort.getVersionInfo(iterationKey, repoId.value())
                    .ifPresent(info -> {
                        if (info.getTargetVersion() != null) {
                            pairs.add(new RepoVersionPair(repoId.value(), info.getTargetVersion()));
                        }
                    });
        }

        if (pairs.size() >= 2) {
            String firstVersion = pairs.get(0).version;
            for (int i = 1; i < pairs.size(); i++) {
                if (!firstVersion.equals(pairs.get(i).version)) {
                    String repoName = codeRepositoryPort.findById(RepoId.of(pairs.get(i).repoId))
                            .map(CodeRepository::getName).orElse(pairs.get(i).repoId);
                    results.add(ConflictItem.crossRepoVersionMismatch(
                            pairs.get(i).repoId, repoName, iterationKey,
                            pairs.get(i).version, pairs.get(0).repoId, firstVersion));
                }
            }
        }

        return results;
    }

    public Optional<ConflictReport> getLatestReport(String windowId) {
        return conflictDetectionPort.getLatestReport(windowId);
    }
}
