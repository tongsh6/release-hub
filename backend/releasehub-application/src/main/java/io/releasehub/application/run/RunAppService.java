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
    private final io.releasehub.application.run.plan.RunPlanAppService runPlanAppService;
    private final io.releasehub.application.branchrule.BranchRuleUseCase branchRuleUseCase;

    private static final String META_VERSION_BUILD_TOOL = "versionUpdate.buildTool";
    private static final String META_VERSION_BRANCH_NAME = "versionUpdate.branchName";
    private static final String META_VERSION_REPO_PATH = "versionUpdate.repoPath";
    private static final String META_VERSION_TARGET_VERSION = "versionUpdate.targetVersion";
    private static final String META_VERSION_POM_PATH = "versionUpdate.pomPath";
    private static final String META_VERSION_GRADLE_PROPERTIES_PATH = "versionUpdate.gradlePropertiesPath";
    private static final String META_RETRY_SOURCE_RUN_ID = "retry.sourceRunId";
    private static final String META_RETRY_SOURCE_ITEM_ID = "retry.sourceItemId";

    private String deriveReleaseBranch(String windowKey) {
        return io.releasehub.application.window.ReleaseBranchNaming.resolve(windowKey, settingsPort.getNaming().orElse(null));
    }

    public Run startOrchestrate(String windowId, List<String> repoIds, List<String> iterationKeys, boolean failFast, String operator) {
        Run run = runPlanAppService.admit(windowId, repoIds, iterationKeys, failFast, operator);
        return executeOrchestration(run, failFast);
    }

    private Run executeOrchestration(Run run, boolean failFast) {
        Map<String, String> blockedRepositories = new java.util.HashMap<>();
        int executedOrder = 0;
        try (var operationScope = io.releasehub.application.gitoperation.GitOperationContext.withRun(run.getId().value())) {
            for (RunItem item : run.getItems().stream().sorted(Comparator.comparingInt(RunItem::getPlannedOrder)).toList()) {
                String blockedBy = blockedRepositories.get(item.getRepo().value());
                if (blockedBy != null) {
                    item.putMetadata("blockedBy.itemId", blockedBy);
                    item.addStep(new RunStep(ActionType.TRY_MERGE, RunItemResult.SKIPPED_DUE_TO_BLOCK,
                            clock.instant(), clock.instant(), "同仓库前序执行项阻塞: " + blockedBy));
                    item.finishWith(RunItemResult.SKIPPED_DUE_TO_BLOCK, clock.instant());
                    runPort.save(run);
                    continue;
                }
                item.setExecutedOrder(++executedOrder);
                runPort.save(run);
                executeOrchestrationItem(run, item);
                if (item.getFinalResult() == RunItemResult.UNKNOWN
                        || (failFast && item.getFinalResult().isFailure())) {
                    blockedRepositories.put(item.getRepo().value(), item.getId().value());
                }
            }
            run.finish(clock.instant());
            runPort.save(run);
            return run;
        }
    }

    private void executeOrchestrationItem(Run run, RunItem item) {
        CodeRepository repo;
        GitBranchPort git;
        String source = item.getMetadata().get("plan.featureBranch");
        String target = item.getMetadata().get("plan.releaseBranch");
        String base = item.getMetadata().get("plan.defaultBranch");
        String featureMode = item.getMetadata().get("plan.featureBranchMode");
        try {
            repo = codeRepositoryPort.findById(item.getRepo()).orElseThrow(() -> invalidRunScope("计划仓库已不存在"));
            if (repo.getGitProvider() != io.releasehub.domain.repo.GitProvider.GITLAB
                    || !java.util.Objects.equals(item.getMetadata().get("plan.cloneUrl"), repo.getCloneUrl())
                    || !java.util.Objects.equals(item.getMetadata().get("plan.gitProvider"), repo.getGitProvider().name())
                    || isBlank(source) || isBlank(target) || isBlank(base) || isBlank(featureMode)
                    || !java.util.Set.of("AUTO", "NAMED", "EXISTING").contains(featureMode)) {
                throw invalidRunScope("仓库映射或计划前置条件已改变");
            }
            git = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
        } catch (RuntimeException error) {
            saveFailedStep(run, item, ActionType.ENSURE_FEATURE, error);
            return;
        }
        if (!saveBranchStep(run, item, ensureBranch(repo, git, source, base, ActionType.ENSURE_FEATURE,
                !"EXISTING".equals(featureMode)))) return;
        if (!saveBranchStep(run, item, ensureBranch(repo, git, target, base, ActionType.ENSURE_RELEASE, true))) return;
        String windowId = item.getMetadata().get("plan.windowId");
        windowIterationPort.updateReleaseBranch(windowId, item.getIterationKey().value(), target, clock.instant());

        Instant started = clock.instant();
        GitBranchPort.MergeResult result;
        try {
            result = git.mergeBranch(repo.getCloneUrl(), repo.getGitAccessToken(), source, target,
                    "Merge " + source + " into " + target);
        } catch (RuntimeException error) {
            // The workflow may have written remotely before a local journal/persistence failure.
            result = GitBranchPort.MergeResult.unknown(safeFailureMessage(error));
        }
        if (result == null) result = GitBranchPort.MergeResult.unknown("缺少合并结果");
        GitBranchPort.MrEvidence evidence = result.mrEvidence();
        if (result.status() == MergeStatus.SUCCESS && !hasConfirmedMergeEvidence(evidence, source, target)) {
            result = GitBranchPort.MergeResult.unknown("合并成功响应缺少项目、MR 身份或提交证据").withEvidence(evidence);
        }
        recordMergeEvidence(item, result);
        RunItemResult outcome = switch (result.status()) {
            case SUCCESS -> evidence.disposition() == GitBranchPort.MrDisposition.ALREADY_MERGED
                    ? RunItemResult.ALREADY_MERGED : RunItemResult.MERGED;
            case CONFLICT, BLOCKED -> RunItemResult.MERGE_BLOCKED;
            case UNKNOWN -> RunItemResult.UNKNOWN;
            case FAILED -> RunItemResult.FAILED;
        };
        boolean hasMergeRequest = evidence != null && !isBlank(evidence.iid());
        RunItemResult ensuredMr = hasMergeRequest
                ? (evidence.disposition() == GitBranchPort.MrDisposition.CREATED ? RunItemResult.CREATED : RunItemResult.MR_REUSED)
                : outcome == RunItemResult.ALREADY_MERGED ? RunItemResult.ALREADY_MERGED : outcome;
        String message = result.detail() == null ? source + " → " + target : result.detail();
        item.addStep(new RunStep(ActionType.ENSURE_MR, ensuredMr, started, clock.instant(), message));
        if (hasMergeRequest || outcome == RunItemResult.ALREADY_MERGED) {
            item.addStep(new RunStep(ActionType.TRY_MERGE, outcome, started, clock.instant(), message));
        }
        item.finishWith(outcome, clock.instant());
        runPort.save(run);
        if (outcome == RunItemResult.MERGED || outcome == RunItemResult.ALREADY_MERGED) {
            windowIterationPort.updateLastMergeAt(windowId, item.getIterationKey().value(), clock.instant());
        }
    }

    private boolean hasConfirmedMergeEvidence(GitBranchPort.MrEvidence evidence, String source, String target) {
        if (evidence == null || isBlank(evidence.projectId()) || isBlank(evidence.sourceSha())
                || !evidence.sourceSha().matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")
                || !source.equals(evidence.sourceBranch()) || !target.equals(evidence.targetBranch())
                || evidence.disposition() == null) return false;
        return evidence.disposition() == GitBranchPort.MrDisposition.ALREADY_MERGED
                || (!isBlank(evidence.iid()) && !isBlank(evidence.url()));
    }

    private RunStep ensureBranch(CodeRepository repo, GitBranchPort git, String branch, String base,
                                 ActionType action, boolean allowCreate) {
        Instant started = clock.instant();
        boolean writeAttempted = false;
        try {
            if (!branchRuleUseCase.isCompliant(branch, repo.getGroupCode(), repo.getId().value())) {
                throw invalidRunScope("分支名称不符合当前仓库规则: " + branch);
            }
            RunItemResult result;
            if (git.getBranchStatus(repo.getCloneUrl(), repo.getGitAccessToken(), branch).exists()) {
                result = RunItemResult.ALREADY_EXISTS;
            } else if (!allowCreate) {
                return new RunStep(action, RunItemResult.FAILED, started, clock.instant(),
                        "EXISTING 分支已不存在: " + branch);
            } else {
                writeAttempted = true;
                boolean created = git.createBranch(repo.getCloneUrl(), repo.getGitAccessToken(), branch, base);
                // A returned false is a confirmed rejection; a thrown exception carries no such proof.
                writeAttempted = false;
                result = created ? RunItemResult.CREATED
                        : git.getBranchStatus(repo.getCloneUrl(), repo.getGitAccessToken(), branch).exists()
                        ? RunItemResult.ALREADY_EXISTS : RunItemResult.FAILED;
            }
            return new RunStep(action, result, started, clock.instant(), branch);
        } catch (RuntimeException error) {
            return new RunStep(action, writeAttempted ? RunItemResult.UNKNOWN : failedResult(error),
                    started, clock.instant(), safeFailureMessage(error));
        }
    }

    private boolean saveBranchStep(Run run, RunItem item, RunStep step) {
        item.addStep(step);
        boolean failed = step.result().isFailure() || step.result() == RunItemResult.UNKNOWN;
        if (failed) {
            item.putMetadata("git.blockReason", "UNKNOWN");
            item.finishWith(step.result(), clock.instant());
        }
        runPort.save(run);
        return !failed;
    }

    private void saveFailedStep(Run run, RunItem item, ActionType action, RuntimeException error) {
        RunItemResult result = failedResult(error);
        item.putMetadata("git.blockReason", "UNKNOWN");
        item.addStep(new RunStep(action, result, clock.instant(), clock.instant(), safeFailureMessage(error)));
        item.finishWith(result, clock.instant());
        runPort.save(run);
    }

    private RunItemResult failedResult(RuntimeException error) {
        if (error instanceof BusinessException business
                && (io.releasehub.common.exception.ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN.getCode().equals(business.getCode())
                    || io.releasehub.common.exception.ErrorCode.GIT_WRITE_BLOCKED.getCode().equals(business.getCode()))) {
            return RunItemResult.UNKNOWN;
        }
        return RunItemResult.FAILED;
    }

    private String safeFailureMessage(RuntimeException error) {
        return error instanceof BusinessException business ? business.getCode() : error.getClass().getSimpleName();
    }

    private void recordMergeEvidence(RunItem item, GitBranchPort.MergeResult result) {
        item.putMetadata("git.requestId", result.remoteRequestId());
        var evidence = result.mrEvidence();
        if (evidence == null) {
            item.putMetadata("git.blockReason", "UNKNOWN");
            return;
        }
        item.putMetadata("git.projectId", evidence.projectId());
        item.putMetadata("git.sourceBranch", evidence.sourceBranch());
        item.putMetadata("git.targetBranch", evidence.targetBranch());
        item.putMetadata("git.mrIid", evidence.iid());
        item.putMetadata("git.mrUrl", evidence.url());
        item.putMetadata("git.sourceSha", evidence.sourceSha());
        item.putMetadata("git.mergeSha", evidence.mergeSha());
        if (evidence.disposition() != null) item.putMetadata("git.mrDisposition", evidence.disposition().name());
        if (evidence.blockReason() != null) item.putMetadata("git.blockReason", evidence.blockReason().name());
    }

    public Run retry(String runId, List<String> items, String operator) {
        Run previous = runPort.findById(runId).orElseThrow(() ->
                BusinessException.of(io.releasehub.common.exception.ErrorCode.RUN_NOT_FOUND, runId));
        if (items == null || items.isEmpty() || items.stream().anyMatch(this::isBlank)) {
            throw invalidRunScope("请选择明确的失败执行项 ID");
        }
        var selectedIds = java.util.Set.copyOf(items);
        var selected = previous.getItems().stream().filter(item -> selectedIds.contains(item.getId().value())).toList();
        if (selected.size() != selectedIds.size()) throw invalidRunScope("执行项不属于指定 Run");
        if (previous.getExecutionStatus() == io.releasehub.domain.run.RunExecutionStatus.PLANNED
                || previous.getExecutionStatus() == io.releasehub.domain.run.RunExecutionStatus.RUNNING
                || previous.getExecutionStatus() == io.releasehub.domain.run.RunExecutionStatus.NEEDS_REVIEW
                || selected.stream().anyMatch(item -> item.getFinalResult() == null || !item.getFinalResult().isFailure())) {
            throw invalidRunScope("只能重试已确认失败或因阻塞跳过的执行项，未决结果须先核对");
        }
        if (previous.getRunType() == RunType.VERSION_UPDATE) {
            return retryVersionUpdate(previous, items, operator, clock.instant());
        }
        if (previous.getRunType() != RunType.WINDOW_ORCHESTRATION) {
            throw invalidRunScope("该动作尚未具备逐步安全重试条件");
        }
        Run run = runPlanAppService.admitRetry(previous, items, operator);
        boolean failFast = Boolean.parseBoolean(run.getItems().getFirst().getMetadata().get("plan.failFast"));
        return executeOrchestration(run, failFast);
    }

    private BusinessException invalidRunScope(String reason) {
        return BusinessException.of(io.releasehub.common.exception.ErrorCode.RUN_PLAN_INVALID_SCOPE, reason);
    }

    @Transactional
    public Run executeCleanup(String windowId, String operator) {
        Instant now = Instant.now(clock);
        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        log.info("Starting cleanup run for closed window {}", windowId);

        Run run = Run.start(RunType.WINDOW_CLEANUP, operator, now);
        try (var operationScope = io.releasehub.application.gitoperation.GitOperationContext.withRun(run.getId().value())) {
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
                String releaseBranch = wi.getReleaseBranch() == null || wi.getReleaseBranch().isBlank()
                        ? deriveReleaseBranch(rw.getWindowKey())
                        : wi.getReleaseBranch();

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

                // Step 2: MERGE_TO_MASTER
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

                // Step 3: CREATE_TAG
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

                // Step 4: TRIGGER_CI
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

                boolean blockedBeforeArchive = itemFailed;

                // Step 5: ARCHIVE_BRANCH — archive feature branch after merge/tag/CI evidence is written.
                Instant saf = Instant.now(clock);
                if (blockedBeforeArchive) {
                    item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SKIPPED_DUE_TO_BLOCK, saf, saf,
                            "Skipped feature branch archive due to earlier failure"));
                } else if (featureBranch == null) {
                    item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SKIPPED, saf, saf,
                            "featureBranch 未配置，跳过归档"));
                } else if (gitPort.getBranchStatus(cloneUrl, token, featureBranch).exists()) {
                    boolean archived = gitPort.archiveBranch(cloneUrl, token, featureBranch, "released");
                    if (archived) {
                        item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SUCCESS, saf, saf,
                                "Archived feature branch: " + featureBranch));
                    } else {
                        item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.FAILED, saf, saf,
                                "Failed to archive feature branch: " + featureBranch));
                        itemFailed = true;
                    }
                } else {
                    item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SKIPPED, saf, saf,
                            "Feature branch not found, skip archive"));
                }

                // Step 6: ARCHIVE_BRANCH — archive release branch after CI has had a stable ref.
                Instant sar = Instant.now(clock);
                if (blockedBeforeArchive) {
                    item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SKIPPED_DUE_TO_BLOCK, sar, sar,
                            "Skipped release branch archive due to earlier failure"));
                } else if (gitPort.getBranchStatus(cloneUrl, token, releaseBranch).exists()) {
                    boolean archived = gitPort.archiveBranch(cloneUrl, token, releaseBranch, "released");
                    if (archived) {
                        item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SUCCESS, sar, sar,
                                "Archived release branch: " + releaseBranch));
                    } else {
                        item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.FAILED, sar, sar,
                                "Failed to archive release branch: " + releaseBranch));
                        itemFailed = true;
                    }
                } else {
                    item.addStep(new RunStep(ActionType.ARCHIVE_BRANCH, RunItemResult.SKIPPED, sar, sar,
                            "Release branch not found, skip archive"));
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
        try (var operationScope = io.releasehub.application.gitoperation.GitOperationContext.withRun(run.getId().value())) {

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
        try (var operationScope = io.releasehub.application.gitoperation.GitOperationContext.withRun(run.getId().value())) {

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
    }

    private Run retryVersionUpdate(Run previous, List<String> items, String operator, Instant now) {
        Run run = Run.start(RunType.VERSION_UPDATE, operator, now);
        try (var operationScope = io.releasehub.application.gitoperation.GitOperationContext.withRun(run.getId().value())) {

        for (RunItem prevItem : previous.getItems()) {
            String key = prevItem.getId().value();
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
