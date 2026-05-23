package io.releasehub.application.dataquality;

import io.releasehub.common.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DataQualityCleanupReviewAppService {
    private static final String APPROVE = "APPROVE_FOR_APPLICATION_ENTRY";
    private static final String PENDING = "PENDING";
    private static final Set<String> DIRECT_EXECUTION_DECISIONS = Set.of("EXECUTE", "EXECUTE_DIRECTLY", "AUTO_EXECUTE");

    private static final Map<String, ActionContract> CONTRACTS = Map.ofEntries(
            Map.entry(key("code_repository", "REPO_TOKEN_PLAINTEXT"),
                    new ActionContract(
                            "/repositories/{resourceId}",
                            "确认仓库仍存在，且当前 token 仍为明文或需要重新保存。",
                            "仓库保存后重新运行 SA-002 审计，仓库 token 明文数量应为 0。")),
            Map.entry(key("system_settings", "SETTINGS_TOKEN_PLAINTEXT"),
                    new ActionContract(
                            "/settings/gitlab",
                            "确认 GitLab Settings 仍存在，且 token 需要通过系统设置重新保存。",
                            "系统设置保存后重新运行 SA-002 审计，Settings token 明文数量应为 0。")),
            Map.entry(key("iteration_repo", "BRANCH_CREATION_MODE_MISSING_OR_INVALID"),
                    new ActionContract(
                            "受控迁移服务: iteration_repo.branch_creation_mode",
                            "确认 iterationKey/repoId 仍存在，且分支模式缺失或不在 AUTO、NAMED、EXISTING 范围内。",
                            "复核 version-info 返回的 branchCreationMode 已按真实业务语义补齐。")),
            Map.entry(key("iteration_repo", "FEATURE_BRANCH_MISSING"),
                    new ActionContract(
                            "/iterations/{iterationKey}",
                            "确认迭代仓库关联仍存在，且 featureBranch 仍缺失。",
                            "复核迭代详情 version-info 中 featureBranch 已恢复且符合分支规则。")),
            Map.entry(key("code_repository", "CLONE_URL_INVALID"),
                    new ActionContract(
                            "/repositories/{resourceId}",
                            "确认仓库仍存在，且 cloneUrl 仍无法通过格式和重复纳管校验。",
                            "仓库编辑保存后重新运行 SA-002 审计，cloneUrl 异常数量应减少。")),
            Map.entry(key("release_window", "DRAFT_WINDOW_REMAINS"),
                    new ActionContract(
                            "/release-windows/{resourceId}",
                            "确认发布窗口仍为 DRAFT，并由发布经理判断继续发布、关闭或删除。",
                            "复核窗口状态已符合业务决策；如删除，仅通过应用层删除保护完成。")),
            Map.entry(key("window_iteration", "ATTACH_BRANCH_NOT_CREATED"),
                    new ActionContract(
                            "/release-windows/{windowId}",
                            "确认窗口挂载关系仍存在，且 branchCreated 仍为 false。",
                            "复核窗口发布计划和 Git 分支状态一致，不伪造 branchCreated。"))
    );

    public CleanupReviewResult review(CleanupReviewCommand command) {
        if (command == null || isBlank(command.reviewer())) {
            throw ValidationException.invalidParameter("reviewer");
        }
        if (command.actions() == null || command.actions().isEmpty()) {
            throw ValidationException.invalidParameter("actions");
        }

        List<CleanupActionReview> reviews = new ArrayList<>();
        int accepted = 0;
        int pending = 0;
        int rejected = 0;
        for (CleanupActionInput action : command.actions()) {
            CleanupActionReview review = reviewAction(action);
            if (!matchesFilter(review.resourceType(), command.resourceTypeFilter())
                    || !matchesFilter(review.riskType(), command.riskTypeFilter())
                    || !matchesFilter(review.reviewStatus(), command.reviewStatusFilter())
                    || !matchesFilter(review.assetScope(), command.assetScopeFilter())) {
                continue;
            }
            reviews.add(review);
            switch (review.reviewStatus()) {
                case "ACCEPTED" -> accepted++;
                case "PENDING" -> pending++;
                default -> rejected++;
            }
        }

        return new CleanupReviewResult(
                command.reviewer().trim(),
                command.sourceReport(),
                reviews.size(),
                accepted,
                pending,
                rejected,
                List.copyOf(reviews));
    }

    private CleanupActionReview reviewAction(CleanupActionInput action) {
        if (action == null) {
            return rejected(null, null, null, null, "动作不能为空");
        }
        String resourceType = trim(action.resourceType());
        String resourceId = trim(action.resourceId());
        String riskType = trim(action.riskType());
        ActionContract contract = CONTRACTS.get(key(resourceType, riskType));
        if (isBlank(resourceType) || isBlank(resourceId) || isBlank(riskType)) {
            return rejected(action, resourceType, resourceId, riskType, contract, "资源类型、资源 ID 和风险类型不能为空");
        }
        if (contract == null) {
            return rejected(action, resourceType, resourceId, riskType, null, "未登记的资源/风险组合，不能进入受控清理闭环");
        }
        if (Boolean.TRUE.equals(action.executed())) {
            return rejected(action, resourceType, resourceId, riskType, contract, "dry-run 动作进入复核前不得标记为已执行");
        }
        String decision = normalizeDecision(action.reviewDecision());
        if (DIRECT_EXECUTION_DECISIONS.contains(decision)) {
            return rejected(action, resourceType, resourceId, riskType, contract, "SA-002 不允许直接执行或自动执行清理动作");
        }
        if (requiresField(action.preExecutionCheck(), contract.preExecutionCheck())
                || requiresField(action.postExecutionVerification(), contract.postExecutionVerification())
                || requiresField(action.applicationEntry(), contract.applicationEntry())) {
            return rejected(action, resourceType, resourceId, riskType, contract, "动作缺少应用入口、执行前检查或执行后复核口径");
        }
        if (PENDING.equals(decision) || isBlank(decision)) {
            return new CleanupActionReview(resourceType, resourceId, riskType,
                    trim(action.dataNamespace()), trim(action.reviewBatchId()), normalizeOptional(action.assetScope()),
                    trim(action.retentionPolicy()), "PENDING",
                    "等待人工复核确认进入应用层入口", contract.applicationEntry(),
                    contract.preExecutionCheck(), contract.postExecutionVerification(), false);
        }
        if (!APPROVE.equals(decision)) {
            return rejected(action, resourceType, resourceId, riskType, contract, "不支持的人工复核决策: " + decision);
        }
        return new CleanupActionReview(resourceType, resourceId, riskType,
                trim(action.dataNamespace()), trim(action.reviewBatchId()), normalizeOptional(action.assetScope()),
                trim(action.retentionPolicy()), "ACCEPTED",
                "已通过人工复核，可进入指定应用层入口继续处理；本接口不执行清理",
                contract.applicationEntry(), contract.preExecutionCheck(), contract.postExecutionVerification(), false);
    }

    private CleanupActionReview rejected(String resourceType, String resourceId, String riskType,
                                         ActionContract contract, String reason) {
        return new CleanupActionReview(resourceType, resourceId, riskType, null, null, null, null, "REJECTED", reason,
                contract == null ? null : contract.applicationEntry(),
                contract == null ? null : contract.preExecutionCheck(),
                contract == null ? null : contract.postExecutionVerification(),
                false);
    }

    private CleanupActionReview rejected(CleanupActionInput action, String resourceType, String resourceId, String riskType,
                                         ActionContract contract, String reason) {
        return new CleanupActionReview(resourceType, resourceId, riskType,
                trim(action.dataNamespace()), trim(action.reviewBatchId()), normalizeOptional(action.assetScope()),
                trim(action.retentionPolicy()), "REJECTED", reason,
                contract == null ? null : contract.applicationEntry(),
                contract == null ? null : contract.preExecutionCheck(),
                contract == null ? null : contract.postExecutionVerification(),
                false);
    }

    private static boolean matchesFilter(String actual, String expected) {
        return isBlank(expected) || trim(actual).equalsIgnoreCase(trim(expected));
    }

    private static boolean requiresField(String actual, String expected) {
        return isBlank(actual) || !trim(actual).equals(expected);
    }

    private static String normalizeDecision(String value) {
        return trim(value).toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return isBlank(value) ? "" : trim(value).toUpperCase(Locale.ROOT);
    }

    private static String key(String resourceType, String riskType) {
        return trim(resourceType).toLowerCase(Locale.ROOT) + ":" + trim(riskType).toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record ActionContract(String applicationEntry, String preExecutionCheck, String postExecutionVerification) {
    }
}
