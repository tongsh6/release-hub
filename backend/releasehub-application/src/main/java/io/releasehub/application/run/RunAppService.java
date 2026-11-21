package io.releasehub.application.run;

import io.releasehub.application.conflict.ConflictDetectionAppService;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionUpdateAppService;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.conflict.ConflictReport;
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
    private final CodeRepositoryPort codeRepositoryPort;
    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final VersionUpdateAppService versionUpdateAppService;
    private final ConflictDetectionAppService conflictDetectionAppService;
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
        GitBranchPort.MergeResult result = executeMerge(repo, git, source, target);
        GitBranchPort.MrEvidence evidence = result.mrEvidence();
        recordMergeEvidence(item, result);
        RunItemResult outcome = mergeOutcome(result);
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

    private GitBranchPort.MergeResult executeMerge(CodeRepository repo, GitBranchPort git, String source, String target) {
        GitBranchPort.MergeResult result;
        try {
            result = git.mergeBranch(repo.getCloneUrl(), repo.getGitAccessToken(), source, target,
                    "Merge " + source + " into " + target);
        } catch (RuntimeException error) {
            // 复合 MR 工作流可能已完成远端写，再遭遇本地证据保存失败。
            result = GitBranchPort.MergeResult.unknown(safeFailureMessage(error));
        }
        if (result == null) result = GitBranchPort.MergeResult.unknown("缺少合并结果");
        if (result.status() == MergeStatus.SUCCESS && !hasConfirmedMergeEvidence(result.mrEvidence(), source, target)) {
            result = GitBranchPort.MergeResult.unknown("合并成功响应缺少项目、MR 身份或提交证据").withEvidence(result.mrEvidence());
        }
        return result;
    }

    private RunItemResult mergeOutcome(GitBranchPort.MergeResult result) {
        return switch (result.status()) {
            case SUCCESS -> result.mrEvidence().disposition() == GitBranchPort.MrDisposition.ALREADY_MERGED
                    ? RunItemResult.ALREADY_MERGED : RunItemResult.MERGED;
            case CONFLICT, BLOCKED -> RunItemResult.MERGE_BLOCKED;
            case UNKNOWN -> RunItemResult.UNKNOWN;
            case FAILED -> RunItemResult.FAILED;
        };
    }

    private boolean isCommitSha(String value) {
        return value != null && value.matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}");
    }

    private boolean hasConfirmedMergeEvidence(GitBranchPort.MrEvidence evidence, String source, String target) {
        if (evidence == null || isBlank(evidence.projectId()) || !isCommitSha(evidence.sourceSha())
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

    /** 关闭受理由独立短事务封存；只有首次受理的 Run 可以派发外部动作。 */
    public Run executeCleanup(String windowId, String operator, boolean triggerCi) {
        var admission = runPlanAppService.admitCleanup(windowId, operator, triggerCi);
        Run run = admission.run();
        if (!admission.created()) return run;
        if (run.getRunType() != RunType.WINDOW_CLEANUP || run.getItems().isEmpty()
                || run.getItems().stream().map(RunItem::getRepo).distinct().count() != run.getItems().size()) {
            throw invalidRunScope("收尾计划必须按窗口仓库唯一受理");
        }
        int order = 0;
        try (var operationScope = io.releasehub.application.gitoperation.GitOperationContext.withRun(run.getId().value())) {
            for (RunItem item : run.getItems().stream().sorted(Comparator.comparingInt(RunItem::getPlannedOrder)).toList()) {
                item.setExecutedOrder(++order);
                item.putMetadata("cleanup.referencePolicy", "RETAIN");
                runPort.save(run);
                executeCleanupItem(run, item);
            }
            run.finish(clock.instant());
            runPort.save(run);
            return run;
        }
    }

    private void executeCleanupItem(Run run, RunItem item) {
        CodeRepository repo;
        GitBranchPort git;
        String source = item.getMetadata().get("plan.releaseBranch");
        String target = item.getMetadata().get("plan.defaultBranch");
        String tagName = item.getMetadata().get("plan.tagName");
        String ciRef = item.getMetadata().get("plan.ciRef");
        boolean triggerCi = Boolean.parseBoolean(item.getMetadata().get("plan.triggerCi"));
        try {
            repo = codeRepositoryPort.findById(item.getRepo()).orElseThrow(() -> invalidRunScope("计划仓库已不存在"));
            if (item.getIterationKey() != null || repo.getGitProvider() != io.releasehub.domain.repo.GitProvider.GITLAB
                    || !java.util.Objects.equals(repo.getCloneUrl(), item.getMetadata().get("plan.cloneUrl"))
                    || !java.util.Objects.equals(repo.getGitProvider().name(), item.getMetadata().get("plan.gitProvider"))
                    || isBlank(source) || isBlank(target)
                    || !java.util.Set.of("true", "false").contains(item.getMetadata().getOrDefault("plan.triggerCi", ""))
                    || triggerCi && (isBlank(tagName) || !tagName.equals(ciRef))) {
                throw invalidRunScope("仓库映射或收尾计划前置条件已改变");
            }
            git = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
            var release = git.getBranchStatus(repo.getCloneUrl(), repo.getGitAccessToken(), source);
            if (release == null || !release.exists() || !isCommitSha(release.latestCommit())) {
                throw invalidRunScope("计划 release 分支不存在或提交证据不完整");
            }
        } catch (RuntimeException error) {
            saveFailedStep(run, item, ActionType.MERGE_TO_MASTER, error);
            skipCleanupAfterMerge(run, item);
            return;
        }

        Instant started = clock.instant();
        var merged = executeMerge(repo, git, source, target);
        recordMergeEvidence(item, merged);
        RunItemResult mergeOutcome = mergeOutcome(merged);
        if (!saveCleanupStep(run, item, new RunStep(ActionType.MERGE_TO_MASTER, mergeOutcome,
                started, clock.instant(), merged.detail() == null ? source + " → " + target : merged.detail()))) {
            skipCleanupAfterMerge(run, item);
            return;
        }

        String projectId = merged.mrEvidence().projectId();
        String targetSha = merged.mrEvidence().mergeSha();
        RunStep tagStep = isBlank(tagName)
                ? new RunStep(ActionType.CREATE_TAG, RunItemResult.SKIPPED, clock.instant(), clock.instant(),
                        "没有明确的发布版本，本次不创建 Tag")
                : ensureCleanupTag(repo, git, item, projectId, tagName, targetSha);
        if (!saveCleanupStep(run, item, tagStep)) {
            skipCleanupCi(run, item);
            return;
        }
        RunStep ciStep = triggerCi
                ? triggerCleanupCi(repo, git, item, projectId, ciRef, targetSha)
                : new RunStep(ActionType.TRIGGER_CI, RunItemResult.SKIPPED, clock.instant(), clock.instant(),
                        "未选择触发 CI");
        if (!saveCleanupStep(run, item, ciStep)) return;
        item.finishWith(RunItemResult.SUCCESS, clock.instant());
        runPort.save(run);
    }

    private RunStep ensureCleanupTag(CodeRepository repo, GitBranchPort git, RunItem item,
                                     String projectId, String tagName, String targetSha) {
        Instant started = clock.instant();
        item.putMetadata("git.tagName", tagName);
        item.putMetadata("git.tagTargetSha", targetSha);
        if (!isCommitSha(targetSha)) return new RunStep(ActionType.CREATE_TAG, RunItemResult.FAILED,
                started, clock.instant(), "缺少已确认的合并提交 SHA，不从默认分支当前 tip 猜测 Tag 目标");
        boolean writeAttempted = false;
        try {
            var observed = git.readTag(repo.getCloneUrl(), repo.getGitAccessToken(), projectId, tagName);
            if (observed != null && observed.status() == io.releasehub.application.gitoperation.GitReadResult.Status.FOUND) {
                boolean matches = tagMatches(observed.evidence(), projectId, tagName, targetSha);
                item.putMetadata("git.tagObservedSha", observed.evidence() == null ? null : observed.evidence().commitSha());
                return new RunStep(ActionType.CREATE_TAG, matches ? RunItemResult.ALREADY_EXISTS : RunItemResult.FAILED,
                        started, clock.instant(), matches ? "复用同名同 SHA Tag: " + tagName : "同名 Tag 的提交与计划不一致");
            }
            if (observed == null || observed.status() != io.releasehub.application.gitoperation.GitReadResult.Status.NOT_FOUND) {
                return new RunStep(ActionType.CREATE_TAG, RunItemResult.FAILED, started, clock.instant(),
                        "无法确认 Tag 不存在，停止创建");
            }
            writeAttempted = true;
            var written = git.createTag(repo.getCloneUrl(), repo.getGitAccessToken(), projectId,
                    tagName, targetSha, "Release " + tagName);
            writeAttempted = false;
            if (written == null || written.outcome() == io.releasehub.application.gitoperation.GitWriteResult.Outcome.UNKNOWN) {
                return new RunStep(ActionType.CREATE_TAG, RunItemResult.UNKNOWN, started, clock.instant(), "Tag 写入结果未知，等待核对");
            }
            if (written.outcome() == io.releasehub.application.gitoperation.GitWriteResult.Outcome.APPLIED) {
                boolean confirmed = tagMatches(written.evidence(), projectId, tagName, targetSha);
                return new RunStep(ActionType.CREATE_TAG, confirmed ? RunItemResult.TAG_CREATED : RunItemResult.UNKNOWN,
                        started, clock.instant(), confirmed ? "Tag 已指向确认提交: " + tagName : "Tag 写响应证据不完整");
            }
            item.putMetadata("git.tagRejectionReason", written.reason());
            // 请求已确认拒绝，仅允许只读核对竞争者创建的精确同名同 SHA Tag，不重发 POST。
            var raced = git.readTag(repo.getCloneUrl(), repo.getGitAccessToken(), projectId, tagName);
            boolean confirmed = raced != null
                    && raced.status() == io.releasehub.application.gitoperation.GitReadResult.Status.FOUND
                    && tagMatches(raced.evidence(), projectId, tagName, targetSha);
            return new RunStep(ActionType.CREATE_TAG, confirmed ? RunItemResult.ALREADY_EXISTS : RunItemResult.FAILED,
                    started, clock.instant(), confirmed ? "复用并发创建的同名同 SHA Tag: " + tagName : "Tag 创建被拒绝");
        } catch (RuntimeException error) {
            return new RunStep(ActionType.CREATE_TAG, writeAttempted ? RunItemResult.UNKNOWN : failedResult(error),
                    started, clock.instant(), safeFailureMessage(error));
        }
    }

    private boolean tagMatches(GitBranchPort.TagEvidence evidence, String projectId, String tagName, String targetSha) {
        return evidence != null && projectId.equals(evidence.projectId())
                && tagName.equals(evidence.tagName()) && targetSha.equals(evidence.commitSha());
    }

    private RunStep triggerCleanupCi(CodeRepository repo, GitBranchPort git, RunItem item,
                                     String projectId, String ref, String expectedSha) {
        Instant started = clock.instant();
        try {
            var result = git.triggerPipeline(repo.getCloneUrl(), repo.getGitAccessToken(), projectId, ref, expectedSha);
            if (result == null || result.outcome() == io.releasehub.application.gitoperation.GitWriteResult.Outcome.UNKNOWN)
                return new RunStep(ActionType.TRIGGER_CI, RunItemResult.UNKNOWN, started, clock.instant(), "CI 触发结果未知，禁止重发");
            if (result.outcome() == io.releasehub.application.gitoperation.GitWriteResult.Outcome.REJECTED) {
                item.putMetadata("git.pipelineRejectionReason", result.reason());
                return new RunStep(ActionType.TRIGGER_CI, RunItemResult.FAILED, started, clock.instant(), "CI 触发被拒绝");
            }
            var evidence = result.evidence();
            if (evidence == null || !projectId.equals(evidence.projectId()) || isBlank(evidence.id())
                    || !ref.equals(evidence.ref()) || !expectedSha.equals(evidence.sha()) || isBlank(evidence.url()))
                return new RunStep(ActionType.TRIGGER_CI, RunItemResult.UNKNOWN, started, clock.instant(), "CI 响应缺少匹配的身份或提交证据");
            item.putMetadata("git.pipelineId", evidence.id());
            item.putMetadata("git.pipelineUrl", evidence.url());
            item.putMetadata("git.pipelineRef", evidence.ref());
            item.putMetadata("git.pipelineSha", evidence.sha());
            return new RunStep(ActionType.TRIGGER_CI, RunItemResult.CI_TRIGGERED, started, clock.instant(),
                    "已确认触发 pipeline " + evidence.id() + "，不代表流水线已执行成功");
        } catch (RuntimeException error) {
            return new RunStep(ActionType.TRIGGER_CI, RunItemResult.UNKNOWN, started, clock.instant(), safeFailureMessage(error));
        }
    }

    private boolean saveCleanupStep(Run run, RunItem item, RunStep step) {
        item.addStep(step);
        boolean stopped = step.result() == RunItemResult.UNKNOWN || step.result().isFailure();
        if (stopped) {
            item.finishWith(step.result(), clock.instant());
            if (step.result() == RunItemResult.UNKNOWN) item.putMetadata("git.blockReason", "UNKNOWN");
        }
        // 检查点失败必须向上传播，不能被当作 Git 拒绝后继续派发。
        runPort.save(run);
        return !stopped;
    }

    private void skipCleanupAfterMerge(Run run, RunItem item) {
        boolean planned = !isBlank(item.getMetadata().get("plan.tagName"));
        item.addStep(new RunStep(ActionType.CREATE_TAG, planned ? RunItemResult.SKIPPED_DUE_TO_BLOCK : RunItemResult.SKIPPED,
                clock.instant(), clock.instant(), planned ? "前置合并未完成，未创建 Tag" : "没有明确的发布版本，本次不创建 Tag"));
        skipCleanupCi(run, item);
    }

    private void skipCleanupCi(Run run, RunItem item) {
        boolean planned = Boolean.parseBoolean(item.getMetadata().get("plan.triggerCi"));
        item.addStep(new RunStep(ActionType.TRIGGER_CI, planned ? RunItemResult.SKIPPED_DUE_TO_BLOCK : RunItemResult.SKIPPED,
                clock.instant(), clock.instant(), planned ? "前置步骤未完成，未触发 CI" : "未选择触发 CI"));
        runPort.save(run);
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

        RunItem item = RunItem.createForRepository(rw.getWindowKey(), RepoId.of(repoId), 1, now);
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

            RunItem item = RunItem.createForRepository(rw.getWindowKey(), RepoId.of(repoInfo.repoId()), order, now);
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

            RunItem item = RunItem.createForRepository(prevItem.getWindowKey(), prevItem.getRepo(), prevItem.getPlannedOrder(), now);
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
