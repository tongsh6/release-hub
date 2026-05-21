package io.releasehub.application.run;

import io.releasehub.application.conflict.ConflictDetectionAppService;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionDeriverUseCase;
import io.releasehub.application.version.VersionUpdateAppService;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.conflict.ConflictReport;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.version.BuildTool;
import io.releasehub.domain.run.MergeStatus;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunStep;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.window.WindowIteration;
import io.releasehub.application.settings.SettingsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunAppService {
    private final RunPort runPort;
    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationPort iterationPort;
    private final IterationRepoPort iterationRepoPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final VersionUpdateAppService versionUpdateAppService;
    private final ConflictDetectionAppService conflictDetectionAppService;
    private final VersionDeriverUseCase versionDeriverUseCase;
    private final SettingsPort settingsPort;
    private final Clock clock;

    private static final String META_VERSION_BUILD_TOOL = "versionUpdate.buildTool";
    private static final String META_VERSION_BRANCH_NAME = "versionUpdate.branchName";
    private static final String META_VERSION_REPO_PATH = "versionUpdate.repoPath";
    private static final String META_VERSION_TARGET_VERSION = "versionUpdate.targetVersion";
    private static final String META_VERSION_POM_PATH = "versionUpdate.pomPath";
    private static final String META_VERSION_GRADLE_PROPERTIES_PATH = "versionUpdate.gradlePropertiesPath";
    private static final String META_RETRY_SOURCE_RUN_ID = "retry.sourceRunId";
    private static final String META_RETRY_SOURCE_ITEM_ID = "retry.sourceItemId";

    private String deriveReleaseBranch(String windowKey) {
        return settingsPort.getNaming()
                .map(n -> {
                    String template = n.releaseTemplate();
                    if (template == null || template.isBlank()) {
                        return "release/" + windowKey;
                    }
                    return template.replace("{key}", windowKey);
                })
                .orElse("release/" + windowKey);
    }

    @Transactional
    public Run startOrchestrate(String windowId, List<String> repoIds, List<String> iterationKeys, boolean failFast, String operator) {
        Instant now = Instant.now(clock);
        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        ensureWindowNotClosed(rw);
        Run run = Run.start(RunType.WINDOW_ORCHESTRATION, operator, now);

        // 冲突预检
        ConflictReport conflictReport = conflictDetectionAppService.getLatestReport(windowId)
                .orElseGet(() -> conflictDetectionAppService.checkWindowConflicts(windowId));
        if (conflictReport.hasConflicts()) {
            throw BusinessException.conflictDetected(
                    "发布窗口存在 " + conflictReport.totalCount() + " 个冲突，请先解决所有冲突");
        }

        String releaseBranch = deriveReleaseBranch(rw.getWindowKey());
        List<WindowIteration> bindings = new java.util.ArrayList<>(
                windowIterationPort.listByWindow(ReleaseWindowId.of(windowId)));
        log.info("[Orchestrate] windowId={} windowIterationBindings={}", windowId, bindings.size());
        bindings.sort(Comparator.comparing(WindowIteration::getAttachAt));
        List<IterationKey> orderedIterations = bindings.stream().map(WindowIteration::getIterationKey).distinct().toList();
        log.info("[Orchestrate] orderedIterations={} repoIds={} iterationKeys={}", orderedIterations, repoIds, iterationKeys);
        if (orderedIterations.isEmpty()) {
            log.warn("[Orchestrate] No iterations bound to window {}, orchestration will produce 0 items", windowId);
        }

        int order = 0;
        boolean blocked = false;

        for (String repoIdStr : repoIds) {
            RepoId repoId = RepoId.of(repoIdStr);
            CodeRepository repo = codeRepositoryPort.findById(repoId).orElse(null);
            if (repo == null) {
                log.warn("[Orchestrate] FILTER_B: repo not found: {}", repoIdStr);
                continue;
            }
            GitBranchPort gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
            String token = repo.getGitAccessToken();
            String cloneUrl = repo.getCloneUrl();

            for (IterationKey ik : orderedIterations) {
                if (!iterationKeys.isEmpty() && iterationKeys.stream().noneMatch(k -> k.equals(ik.value()))) {
                    log.debug("[Orchestrate] FILTER_C: iterationKey mismatch, ik={} not in {}", ik.value(), iterationKeys);
                    continue;
                }
                Iteration it = iterationPort.findByKey(ik).orElse(null);
                if (it == null) {
                    log.warn("[Orchestrate] FILTER_D: iteration not found for key={}", ik.value());
                    continue;
                }
                if (it.getRepos().stream().noneMatch(r -> r.equals(repoId))) {
                    log.warn("[Orchestrate] FILTER_D: repo {} not in iteration {} repos {}", repoIdStr, ik.value(), it.getRepos());
                    continue;
                }

                log.info("[Orchestrate] Creating RunItem: repo={} iteration={} order={}", repoIdStr, ik.value(), order + 1);
                RunItem item = RunItem.create(rw.getWindowKey(), repoId, ik, ++order, now);
                String iterationKey = ik.value();

                Optional<IterationRepoVersionInfo> versionInfoOpt = iterationRepoPort.getVersionInfo(iterationKey, repoIdStr);
                String featureBranch = versionInfoOpt.map(IterationRepoVersionInfo::getFeatureBranch).orElse(null);

                // Step 1: ENSURE_FEATURE — check feature branch exists
                Instant s1 = Instant.now(clock);
                if (featureBranch == null) {
                    item.addStep(new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.SKIPPED, s1, s1, "featureBranch 未配置"));
                    item.setExecutedOrder(order);
                    item.finishWith(RunItemResult.SKIPPED, Instant.now(clock));
                    run.addItem(item);
                    continue;
                }
                boolean featureExists = gitPort.getBranchStatus(cloneUrl, token, featureBranch).exists();
                if (!featureExists) {
                    item.addStep(new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.SKIPPED, s1, s1, "Feature branch not found: " + featureBranch));
                    item.setExecutedOrder(order);
                    item.finishWith(RunItemResult.SKIPPED, Instant.now(clock));
                    run.addItem(item);
                    continue;
                }
                item.addStep(new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.SUCCESS, s1, s1, "Feature branch exists: " + featureBranch));

                // Step 2: ENSURE_RELEASE — create release branch if not exists
                Instant s2 = Instant.now(clock);
                boolean releaseExists = gitPort.getBranchStatus(cloneUrl, token, releaseBranch).exists();
                if (!releaseExists) {
                    boolean created = gitPort.createBranch(cloneUrl, token, releaseBranch, repo.getDefaultBranch());
                    if (!created) {
                        item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.FAILED, s2, s2, "Failed to create release branch: " + releaseBranch));
                        item.setExecutedOrder(order);
                        item.finishWith(RunItemResult.FAILED, Instant.now(clock));
                        run.addItem(item);
                        if (failFast) { blocked = true; break; }
                        continue;
                    }
                    item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.BRANCH_CREATED, s2, s2, "Created release branch: " + releaseBranch));
                } else {
                    item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.BRANCH_EXISTS, s2, s2, "Release branch exists: " + releaseBranch));
                }

                // Step 3: ENSURE_MR — verify both branches ready for merge
                Instant s3 = Instant.now(clock);
                if (!gitPort.getBranchStatus(cloneUrl, token, featureBranch).exists() || !gitPort.getBranchStatus(cloneUrl, token, releaseBranch).exists()) {
                    item.addStep(new RunStep(ActionType.ENSURE_MR, RunItemResult.SKIPPED, s3, s3, "Branches not ready for merge"));
                    item.setExecutedOrder(order);
                    item.finishWith(RunItemResult.SKIPPED, Instant.now(clock));
                    run.addItem(item);
                    continue;
                }
                item.addStep(new RunStep(ActionType.ENSURE_MR, RunItemResult.SUCCESS, s3, s3, "Ready to merge " + featureBranch + " → " + releaseBranch));

                // Step 4: TRY_MERGE — attempt merge
                Instant s4 = Instant.now(clock);
                GitBranchPort.MergeResult mergeResult = gitPort.mergeBranch(cloneUrl, token, featureBranch, releaseBranch,
                        "Merge " + featureBranch + " into " + releaseBranch);
                Instant s4End = Instant.now(clock);

                RunItemResult mergeOutcome;
                String mergeMessage;
                switch (mergeResult.status()) {
                    case SUCCESS -> {
                        mergeOutcome = RunItemResult.MERGED;
                        mergeMessage = "Merged " + featureBranch + " → " + releaseBranch;
                        windowIterationPort.updateLastMergeAt(windowId, iterationKey, s4End);
                    }
                    case CONFLICT -> {
                        mergeOutcome = RunItemResult.MERGE_BLOCKED;
                        mergeMessage = "Merge conflict: " + mergeResult.detail();
                        if (failFast) { blocked = true; }
                    }
                    default -> {
                        mergeOutcome = RunItemResult.FAILED;
                        mergeMessage = "Merge failed: " + mergeResult.detail();
                        if (failFast) { blocked = true; }
                    }
                }
                item.addStep(new RunStep(ActionType.TRY_MERGE, mergeOutcome, s4, s4End, mergeMessage));
                item.setExecutedOrder(order);
                item.finishWith(mergeOutcome, s4End);
                run.addItem(item);

                if (blocked) break;
            }
            if (blocked) break;
        }

        log.info("[Orchestrate] Complete: totalItems={} windowId={}", run.getItems().size(), windowId);
        if (run.getItems().isEmpty()) {
            if (orderedIterations.isEmpty()) {
                log.info("[Orchestrate] Window {} has no bound iterations — nothing to orchestrate", windowId);
            } else {
                throw BusinessException.runNoItemsCreated(orderedIterations.size(), windowId);
            }
        }
        run.finish(Instant.now(clock));
        runPort.save(run);
        return run;
    }

    @Transactional
    public Run retry(String runId, List<String> items, String operator) {
        Instant now = Instant.now(clock);
        Run previous = runPort.findById(runId).orElseThrow();
        if (previous.getRunType() == RunType.VERSION_UPDATE) {
            return retryVersionUpdate(previous, items, operator, now);
        }
        Run run = Run.start(previous.getRunType(), operator, now);

        for (RunItem prevItem : previous.getItems()) {
            String key = prevItem.getWindowKey() + "::" + prevItem.getRepo().value() + "::" + prevItem.getIterationKey().value();
            if (items.stream().noneMatch(sel -> sel.equals(key))) {
                continue;
            }
            if (prevItem.getFinalResult() != RunItemResult.FAILED && prevItem.getFinalResult() != RunItemResult.MERGE_BLOCKED) {
                continue;
            }

            RepoId repoId = prevItem.getRepo();
            IterationKey iterationKey = prevItem.getIterationKey();
            CodeRepository repo = codeRepositoryPort.findById(repoId).orElse(null);
            if (repo == null) continue;

            GitBranchPort gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
            String token = repo.getGitAccessToken();
            String cloneUrl = repo.getCloneUrl();

            RunItem item = RunItem.createRetry(prevItem.getWindowKey(), repoId, iterationKey, prevItem.getPlannedOrder(), run.getId().value(), now);
            addRetryTrace(item, previous, prevItem);

            Optional<IterationRepoVersionInfo> versionInfoOpt = iterationRepoPort.getVersionInfo(iterationKey.value(), repoId.value());
            String featureBranch = versionInfoOpt.map(IterationRepoVersionInfo::getFeatureBranch).orElse(null);
            String releaseBranch = windowIterationPort.getReleaseBranch(prevItem.getWindowKey(), iterationKey.value());
            if (releaseBranch == null) {
                releaseBranch = "release/" + prevItem.getWindowKey();
            }

            // Step 1: ENSURE_FEATURE
            Instant s1 = Instant.now(clock);
            if (featureBranch == null) {
                item.addStep(new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.SKIPPED, s1, s1, "featureBranch 未配置"));
                item.setExecutedOrder(prevItem.getPlannedOrder());
                item.finishWith(RunItemResult.SKIPPED, Instant.now(clock));
                run.addItem(item);
                continue;
            }
            if (!gitPort.getBranchStatus(cloneUrl, token, featureBranch).exists()) {
                item.addStep(new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.SKIPPED, s1, s1, "Feature branch not found: " + featureBranch));
                item.setExecutedOrder(prevItem.getPlannedOrder());
                item.finishWith(RunItemResult.SKIPPED, Instant.now(clock));
                run.addItem(item);
                continue;
            }
            item.addStep(new RunStep(ActionType.ENSURE_FEATURE, RunItemResult.SUCCESS, s1, s1, "Feature branch exists: " + featureBranch));

            // Step 2: ENSURE_RELEASE
            Instant s2 = Instant.now(clock);
            if (!gitPort.getBranchStatus(cloneUrl, token, releaseBranch).exists()) {
                boolean created = gitPort.createBranch(cloneUrl, token, releaseBranch, repo.getDefaultBranch());
                if (!created) {
                    item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.FAILED, s2, s2, "Failed to create release branch: " + releaseBranch));
                    item.setExecutedOrder(prevItem.getPlannedOrder());
                    item.finishWith(RunItemResult.FAILED, Instant.now(clock));
                    run.addItem(item);
                    continue;
                }
                item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.BRANCH_CREATED, s2, s2, "Created release branch: " + releaseBranch));
            } else {
                item.addStep(new RunStep(ActionType.ENSURE_RELEASE, RunItemResult.BRANCH_EXISTS, s2, s2, "Release branch exists: " + releaseBranch));
            }

            // Step 3: ENSURE_MR
            Instant s3 = Instant.now(clock);
            item.addStep(new RunStep(ActionType.ENSURE_MR, RunItemResult.SUCCESS, s3, s3, "Ready to merge " + featureBranch + " → " + releaseBranch));

            // Step 4: TRY_MERGE
            Instant s4 = Instant.now(clock);
            GitBranchPort.MergeResult mergeResult = gitPort.mergeBranch(cloneUrl, token, featureBranch, releaseBranch,
                    "Merge " + featureBranch + " into " + releaseBranch);
            Instant s4End = Instant.now(clock);

            RunItemResult mergeOutcome;
            String mergeMessage;
            switch (mergeResult.status()) {
                case SUCCESS -> {
                    mergeOutcome = RunItemResult.MERGED;
                    mergeMessage = "Merged " + featureBranch + " → " + releaseBranch;
                }
                case CONFLICT -> {
                    mergeOutcome = RunItemResult.MERGE_BLOCKED;
                    mergeMessage = "Merge conflict: " + mergeResult.detail();
                }
                default -> {
                    mergeOutcome = RunItemResult.FAILED;
                    mergeMessage = "Merge failed: " + mergeResult.detail();
                }
            }
            item.addStep(new RunStep(ActionType.TRY_MERGE, mergeOutcome, s4, s4End, mergeMessage));
            item.setExecutedOrder(prevItem.getPlannedOrder());
            item.finishWith(mergeOutcome, s4End);
            run.addItem(item);
        }

        run.finish(Instant.now(clock));
        runPort.save(run);
        return run;
    }

    @Transactional
    public Run executeCleanup(String windowId, String operator) {
        Instant now = Instant.now(clock);
        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        log.info("Starting cleanup run for closed window {}", windowId);

        Run run = Run.start(RunType.WINDOW_ORCHESTRATION, operator, now);
        String releaseBranch = deriveReleaseBranch(rw.getWindowKey());
        List<WindowIteration> bindings = new java.util.ArrayList<>(
                windowIterationPort.listByWindow(ReleaseWindowId.of(windowId)));
        bindings.sort(Comparator.comparing(WindowIteration::getAttachAt));

        int order = 0;

        // Phase 1: Close iterations (domain-only, no RunItems)
        for (WindowIteration wi : bindings) {
            Iteration iteration = iterationPort.findByKey(wi.getIterationKey()).orElse(null);
            if (iteration == null || iteration.isClosed()) continue;
            iteration.close(now);
            iterationPort.save(iteration);
            log.info("Iteration closed: {}", wi.getIterationKey().value());
        }

        // Phase 2: Repo-level cleanup (archive → merge to master → tag → CI)
        for (WindowIteration wi : bindings) {
            Iteration iteration = iterationPort.findByKey(wi.getIterationKey()).orElse(null);
            if (iteration == null) continue;
            String iterationKey = wi.getIterationKey().value();
            for (RepoId repoId : iteration.getRepos()) {
                CodeRepository repo = codeRepositoryPort.findById(repoId).orElse(null);
                if (repo == null) continue;
                GitBranchPort gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
                String token = repo.getGitAccessToken();
                String cloneUrl = repo.getCloneUrl();

                // Get per-repo version info
                Optional<IterationRepoVersionInfo> repoVersionInfo = iterationRepoPort.getVersionInfo(iterationKey, repoId.value());
                String featureBranch = repoVersionInfo.map(IterationRepoVersionInfo::getFeatureBranch).orElse(null);
                String devVersion = repoVersionInfo.map(IterationRepoVersionInfo::getDevVersion).orElse(null);
                String releaseVersion = devVersion != null ? versionDeriverUseCase.deriveTargetVersion(devVersion) : null;

                RunItem item = RunItem.create(rw.getWindowKey(), repoId, wi.getIterationKey(), ++order, now);
                boolean itemFailed = false;

                // Step 1: UPDATE_VERSION — derive and record release version
                Instant sv = Instant.now(clock);
                if (releaseVersion != null) {
                    item.addStep(new RunStep(ActionType.UPDATE_VERSION, RunItemResult.VERSION_UPDATE_SUCCESS, sv, sv,
                            "Release version: " + releaseVersion + " (from " + devVersion + ")"));
                } else {
                    item.addStep(new RunStep(ActionType.UPDATE_VERSION, RunItemResult.SKIPPED, sv, sv,
                            "No dev version found, skip version derivation"));
                }

                // Step 2: ARCHIVE_BRANCH
                Instant sa = Instant.now(clock);
                if (featureBranch == null) {
                    item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SKIPPED, sa, sa,
                            "featureBranch 未配置，跳过归档"));
                } else if (gitPort.getBranchStatus(cloneUrl, token, featureBranch).exists()) {
                    boolean archived = gitPort.archiveBranch(cloneUrl, token, featureBranch, "released");
                    if (archived) {
                        item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SUCCESS, sa, sa,
                                "Archived feature branch: " + featureBranch));
                    } else {
                        item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.FAILED, sa, sa,
                                "Failed to archive: " + featureBranch));
                    }
                } else {
                    item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SKIPPED, sa, sa,
                            "Feature branch not found, skip archive"));
                }

                // Step 3: MERGE_TO_MASTER
                Instant sm = Instant.now(clock);
                if (gitPort.getBranchStatus(cloneUrl, token, releaseBranch).exists()) {
                    String masterBranch = repo.getDefaultBranch();
                    GitBranchPort.MergeResult mr = gitPort.mergeBranch(cloneUrl, token, releaseBranch, masterBranch,
                            "Merge " + releaseBranch + " into " + masterBranch);
                    Instant smEnd = Instant.now(clock);
                    switch (mr.status()) {
                        case SUCCESS -> item.addStep(new RunStep(ActionType.MERGE_TO_MASTER, RunItemResult.MERGED, sm, smEnd,
                                "Merged " + releaseBranch + " → " + masterBranch));
                        case CONFLICT -> {
                            item.addStep(new RunStep(ActionType.MERGE_TO_MASTER, RunItemResult.MERGE_BLOCKED, sm, smEnd,
                                    "Merge conflict: " + mr.detail()));
                            itemFailed = true;
                        }
                        default -> {
                            item.addStep(new RunStep(ActionType.MERGE_TO_MASTER, RunItemResult.FAILED, sm, smEnd,
                                    "Merge failed: " + mr.detail()));
                            itemFailed = true;
                        }
                    }
                } else {
                    item.addStep(new RunStep(ActionType.MERGE_TO_MASTER, RunItemResult.SKIPPED, sm, sm,
                            "Release branch not found, skip merge to master"));
                }

                // Step 4: CREATE_TAG
                Instant st = Instant.now(clock);
                if (!itemFailed && releaseVersion != null) {
                    String tagName = "v" + releaseVersion;
                    boolean tagged = gitPort.createTag(cloneUrl, token, tagName, repo.getDefaultBranch(),
                            "Release " + tagName);
                    if (tagged) {
                        item.addStep(new RunStep(ActionType.CREATE_TAG, RunItemResult.TAG_CREATED, st, st,
                                "Tag created: " + tagName));
                    } else {
                        item.addStep(new RunStep(ActionType.CREATE_TAG, RunItemResult.FAILED, st, st,
                                "Failed to create tag: " + tagName));
                    }
                } else if (releaseVersion == null) {
                    item.addStep(new RunStep(ActionType.CREATE_TAG, RunItemResult.SKIPPED, st, st,
                            "No release version, skip tag creation"));
                } else {
                    item.addStep(new RunStep(ActionType.CREATE_TAG, RunItemResult.SKIPPED_DUE_TO_BLOCK, st, st,
                            "Skipped due to earlier failure"));
                }

                // Step 5: TRIGGER_CI
                Instant sc = Instant.now(clock);
                RunItemResult ciResult = null;
                if (!itemFailed) {
                    String ref = releaseBranch;
                    if (!gitPort.getBranchStatus(cloneUrl, token, ref).exists()) {
                        ref = repo.getDefaultBranch();
                    }
                    String pipelineId = gitPort.triggerPipeline(cloneUrl, token, ref);
                    if (pipelineId != null) {
                        ciResult = RunItemResult.CI_TRIGGERED;
                        item.addStep(new RunStep(ActionType.TRIGGER_CI, ciResult, sc, sc,
                                "Pipeline triggered: " + pipelineId + " on " + ref));
                    } else {
                        ciResult = RunItemResult.CI_NOT_CONFIGURED;
                        item.addStep(new RunStep(ActionType.TRIGGER_CI, ciResult, sc, sc,
                                "CI not configured for provider: " + repo.getGitProvider()));
                    }
                } else {
                    item.addStep(new RunStep(ActionType.TRIGGER_CI, RunItemResult.SKIPPED_DUE_TO_BLOCK, sc, sc,
                            "Skipped due to earlier failure"));
                }

                item.setExecutedOrder(order);
                item.finishWith(resolveCleanupFinalResult(itemFailed, ciResult), Instant.now(clock));
                run.addItem(item);
            }
        }

        run.finish(Instant.now(clock));
        runPort.save(run);
        log.info("Cleanup run {} completed for window {}", run.getId().value(), windowId);
        return run;
    }

    private RunItemResult resolveCleanupFinalResult(boolean itemFailed, RunItemResult ciResult) {
        if (itemFailed) {
            return RunItemResult.FAILED;
        }
        if (ciResult == RunItemResult.CI_NOT_CONFIGURED) {
            return RunItemResult.CI_NOT_CONFIGURED;
        }
        return RunItemResult.SUCCESS;
    }

    @Transactional
    public Run executeVersionUpdate(
            String windowId,
            String repoId,
            String targetVersion,
            BuildTool buildTool,
            String repoPath,
            String pomPath,
            String gradlePropertiesPath,
            String operator
    ) {
        Instant now = Instant.now(clock);
        Run run = Run.start(RunType.VERSION_UPDATE, operator, now);

        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));
        ensureWindowNotClosed(rw);

        codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));

        ConflictReport conflictReport = conflictDetectionAppService.getLatestReport(windowId)
                .orElseGet(() -> conflictDetectionAppService.checkWindowConflicts(windowId));
        if (conflictReport.hasConflicts()) {
            throw BusinessException.conflictDetected(
                    "发布窗口存在 " + conflictReport.totalCount() + " 个冲突，请先解决所有冲突");
        }

        String branchName = deriveReleaseBranch(rw.getWindowKey());
        VersionUpdateRequest request = buildTool == BuildTool.MAVEN
                ? VersionUpdateRequest.forMaven(RepoId.of(repoId), branchName, repoPath, targetVersion, pomPath)
                : VersionUpdateRequest.forGradle(RepoId.of(repoId), branchName, repoPath, targetVersion, gradlePropertiesPath);

        Instant stepStart = Instant.now(clock);
        VersionUpdateResult result = versionUpdateAppService.updateVersion(request);
        Instant stepEnd = Instant.now(clock);

        IterationKey dummyIterationKey = IterationKey.of("VERSION_UPDATE");
        RunItem item = RunItem.create(rw.getWindowKey(), RepoId.of(repoId), dummyIterationKey, 1, now);
        addVersionUpdateMetadata(item, request);

        RunItemResult stepResult = result.success()
                ? RunItemResult.VERSION_UPDATE_SUCCESS
                : RunItemResult.VERSION_UPDATE_FAILED;

        String stepMessage;
        if (result.success()) {
            String baseMessage = String.format("Version updated from %s to %s. File: %s",
                    result.oldVersion(), result.newVersion(), result.filePath());
            if (result.diff() != null && !result.diff().isBlank()) {
                stepMessage = baseMessage + "\n--- Diff ---\n" + result.diff();
            } else {
                stepMessage = baseMessage;
            }
        } else {
            stepMessage = result.errorMessage();
        }

        RunStep step = new RunStep(ActionType.UPDATE_VERSION, stepResult, stepStart, stepEnd, stepMessage);
        item.addStep(step);
        item.setExecutedOrder(1);
        item.finishWith(stepResult, stepEnd);

        run.addItem(item);
        run.finish(stepEnd);
        runPort.save(run);

        return run;
    }

    @Transactional
    public Run executeBatchVersionUpdate(
            String windowId,
            List<RepoVersionUpdateInfo> repositories,
            String targetVersion,
            String operator
    ) {
        Instant now = Instant.now(clock);
        Run run = Run.start(RunType.VERSION_UPDATE, operator, now);

        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));
        ensureWindowNotClosed(rw);

        ConflictReport conflictReport = conflictDetectionAppService.getLatestReport(windowId)
                .orElseGet(() -> conflictDetectionAppService.checkWindowConflicts(windowId));
        if (conflictReport.hasConflicts()) {
            throw BusinessException.conflictDetected(
                    "发布窗口存在 " + conflictReport.totalCount() + " 个冲突，请先解决所有冲突");
        }

        String branchName = deriveReleaseBranch(rw.getWindowKey());
        int order = 1;
        for (RepoVersionUpdateInfo repoInfo : repositories) {
            codeRepositoryPort.findById(RepoId.of(repoInfo.repoId()))
                    .orElseThrow(() -> NotFoundException.repository(repoInfo.repoId()));

            VersionUpdateRequest request = repoInfo.buildTool() == BuildTool.MAVEN
                    ? VersionUpdateRequest.forMaven(RepoId.of(repoInfo.repoId()), branchName, repoInfo.repoPath(), targetVersion, repoInfo.pomPath())
                    : VersionUpdateRequest.forGradle(RepoId.of(repoInfo.repoId()), branchName, repoInfo.repoPath(), targetVersion, repoInfo.gradlePropertiesPath());

            Instant stepStart = Instant.now(clock);
            VersionUpdateResult result = versionUpdateAppService.updateVersion(request);
            Instant stepEnd = Instant.now(clock);

            IterationKey dummyIterationKey = IterationKey.of("VERSION_UPDATE");
            RunItem item = RunItem.create(rw.getWindowKey(), RepoId.of(repoInfo.repoId()), dummyIterationKey, order, now);
            addVersionUpdateMetadata(item, request);

            RunItemResult stepResult = result.success()
                    ? RunItemResult.VERSION_UPDATE_SUCCESS
                    : RunItemResult.VERSION_UPDATE_FAILED;

            String stepMessage;
            if (result.success()) {
                String baseMessage = String.format("Version updated from %s to %s. File: %s",
                        result.oldVersion(), result.newVersion(), result.filePath());
                if (result.diff() != null && !result.diff().isBlank()) {
                    stepMessage = baseMessage + "\n--- Diff ---\n" + result.diff();
                } else {
                    stepMessage = baseMessage;
                }
            } else {
                stepMessage = result.errorMessage();
            }

            RunStep step = new RunStep(ActionType.UPDATE_VERSION, stepResult, stepStart, stepEnd, stepMessage);
            item.addStep(step);
            item.setExecutedOrder(order);
            item.finishWith(stepResult, stepEnd);

            run.addItem(item);
            order++;
        }

        run.finish(Instant.now(clock));
        runPort.save(run);

        return run;
    }

    private Run retryVersionUpdate(Run previous, List<String> items, String operator, Instant now) {
        Run run = Run.start(RunType.VERSION_UPDATE, operator, now);

        for (RunItem prevItem : previous.getItems()) {
            String key = prevItem.getWindowKey() + "::" + prevItem.getRepo().value() + "::" + prevItem.getIterationKey().value();
            if (items.stream().noneMatch(sel -> sel.equals(key))) {
                continue;
            }
            if (prevItem.getFinalResult() != RunItemResult.VERSION_UPDATE_FAILED) {
                continue;
            }

            RunItem item = RunItem.createRetry(
                    prevItem.getWindowKey(),
                    prevItem.getRepo(),
                    prevItem.getIterationKey(),
                    prevItem.getPlannedOrder(),
                    run.getId().value(),
                    now
            );
            addRetryTrace(item, previous, prevItem);

            Optional<VersionUpdateRequest> requestOpt = buildVersionUpdateRequest(prevItem);
            if (requestOpt.isEmpty()) {
                Instant failedAt = Instant.now(clock);
                item.addStep(new RunStep(
                        ActionType.UPDATE_VERSION,
                        RunItemResult.VERSION_UPDATE_FAILED,
                        failedAt,
                        failedAt,
                        "Missing version update retry metadata"
                ));
                item.setExecutedOrder(prevItem.getPlannedOrder());
                item.finishWith(RunItemResult.VERSION_UPDATE_FAILED, failedAt);
                run.addItem(item);
                continue;
            }

            VersionUpdateRequest request = requestOpt.get();
            addVersionUpdateMetadata(item, request);

            if (codeRepositoryPort.findById(request.repoId()).isEmpty()) {
                Instant failedAt = Instant.now(clock);
                item.addStep(new RunStep(
                        ActionType.UPDATE_VERSION,
                        RunItemResult.VERSION_UPDATE_FAILED,
                        failedAt,
                        failedAt,
                        "Repository not found: " + request.repoId().value()
                ));
                item.setExecutedOrder(prevItem.getPlannedOrder());
                item.finishWith(RunItemResult.VERSION_UPDATE_FAILED, failedAt);
                run.addItem(item);
                continue;
            }

            Instant stepStart = Instant.now(clock);
            VersionUpdateResult result = versionUpdateAppService.updateVersion(request);
            Instant stepEnd = Instant.now(clock);

            RunItemResult stepResult = result.success()
                    ? RunItemResult.VERSION_UPDATE_SUCCESS
                    : RunItemResult.VERSION_UPDATE_FAILED;

            item.addStep(new RunStep(ActionType.UPDATE_VERSION, stepResult, stepStart, stepEnd, buildVersionUpdateStepMessage(result)));
            item.setExecutedOrder(prevItem.getPlannedOrder());
            item.finishWith(stepResult, stepEnd);
            run.addItem(item);
        }

        run.finish(Instant.now(clock));
        runPort.save(run);
        return run;
    }

    private Optional<VersionUpdateRequest> buildVersionUpdateRequest(RunItem item) {
        Map<String, String> metadata = item.getMetadata();
        try {
            String buildToolValue = metadata.get(META_VERSION_BUILD_TOOL);
            String branchName = metadata.get(META_VERSION_BRANCH_NAME);
            String repoPath = metadata.get(META_VERSION_REPO_PATH);
            String targetVersion = metadata.get(META_VERSION_TARGET_VERSION);
            if (isBlank(buildToolValue) || isBlank(branchName) || isBlank(repoPath) || isBlank(targetVersion)) {
                return Optional.empty();
            }

            BuildTool buildTool = BuildTool.valueOf(buildToolValue);
            if (buildTool == BuildTool.MAVEN) {
                return Optional.of(VersionUpdateRequest.forMaven(item.getRepo(), branchName, repoPath, targetVersion, metadata.get(META_VERSION_POM_PATH)));
            }
            if (buildTool == BuildTool.GRADLE) {
                return Optional.of(VersionUpdateRequest.forGradle(item.getRepo(), branchName, repoPath, targetVersion, metadata.get(META_VERSION_GRADLE_PROPERTIES_PATH)));
            }
            return Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void addVersionUpdateMetadata(RunItem item, VersionUpdateRequest request) {
        item.putMetadata(META_VERSION_BUILD_TOOL, request.buildTool().name());
        item.putMetadata(META_VERSION_BRANCH_NAME, request.branchName());
        item.putMetadata(META_VERSION_REPO_PATH, request.repoPath());
        item.putMetadata(META_VERSION_TARGET_VERSION, request.targetVersion());
        item.putMetadata(META_VERSION_POM_PATH, request.pomPath());
        item.putMetadata(META_VERSION_GRADLE_PROPERTIES_PATH, request.gradlePropertiesPath());
    }

    private void addRetryTrace(RunItem item, Run previous, RunItem prevItem) {
        item.putMetadata(META_RETRY_SOURCE_RUN_ID, previous.getId().value());
        item.putMetadata(META_RETRY_SOURCE_ITEM_ID, prevItem.getId().value());
    }

    private String buildVersionUpdateStepMessage(VersionUpdateResult result) {
        if (!result.success()) {
            return result.errorMessage();
        }
        String baseMessage = String.format("Version updated from %s to %s. File: %s",
                result.oldVersion(), result.newVersion(), result.filePath());
        if (result.diff() != null && !result.diff().isBlank()) {
            return baseMessage + "\n--- Diff ---\n" + result.diff();
        }
        return baseMessage;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record RepoVersionUpdateInfo(
            String repoId,
            BuildTool buildTool,
            String repoPath,
            String pomPath,
            String gradlePropertiesPath
    ) {}

    private void ensureWindowNotClosed(ReleaseWindow releaseWindow) {
        if (releaseWindow.getStatus() == ReleaseWindowStatus.CLOSED) {
            throw BusinessException.rwInvalidState(releaseWindow.getStatus());
        }
    }
}
