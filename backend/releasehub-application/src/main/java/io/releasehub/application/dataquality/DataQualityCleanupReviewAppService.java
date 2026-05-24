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
                            "仓库保存后重新运行 SA-002 审计，仓库 token 明文数量应为 0。",
                            "APPLICATION_MANUAL",
                            "通过仓库编辑页重新保存 Git token，复用仓库保存流程的加密与校验。",
                            "如保存失败，不修改原仓库记录；保留 dry-run 动作，等待重新输入有效 token。",
                            "记录 reviewer、sourceReport、resourceId、riskType、reviewDecision 和重新审计结果。")),
            Map.entry(key("system_settings", "SETTINGS_TOKEN_PLAINTEXT"),
                    new ActionContract(
                            "/settings/gitlab",
                            "确认 GitLab Settings 仍存在，且 token 需要通过系统设置重新保存。",
                            "系统设置保存后重新运行 SA-002 审计，Settings token 明文数量应为 0。",
                            "APPLICATION_MANUAL",
                            "通过系统设置页重新保存 GitLab token，复用 Settings 加密与连接测试流程。",
                            "如连接测试或保存失败，不覆盖原配置；保留 dry-run 动作并重新复核。",
                            "记录 reviewer、sourceReport、resourceId、riskType、reviewDecision 和 token 明文复核结果。")),
            Map.entry(key("iteration_repo", "BRANCH_CREATION_MODE_MISSING_OR_INVALID"),
                    new ActionContract(
                            "受控迁移服务: iteration_repo.branch_creation_mode",
                            "确认 iterationKey/repoId 仍存在，且分支模式缺失或不在 AUTO、NAMED、EXISTING 范围内。",
                            "复核 version-info 返回的 branchCreationMode 已按真实业务语义补齐。",
                            "MIGRATION_REQUIRED",
                            "不得在复核队列直接修复；必须先设计受控迁移服务并限定可迁移数据范围。",
                            "迁移失败时必须回滚本批次已写入的 branchCreationMode，并保留原始审计快照。",
                            "记录迁移批次、操作者、迁移前后值、失败回滚结果和复核查询。")),
            Map.entry(key("iteration_repo", "FEATURE_BRANCH_MISSING"),
                    new ActionContract(
                            "/iterations/{iterationKey}",
                            "确认迭代仓库关联仍存在，且 featureBranch 仍缺失。",
                            "复核迭代详情 version-info 中 featureBranch 已恢复且符合分支规则。",
                            "APPLICATION_MANUAL",
                            "通过迭代详情和既有分支规则流程补齐或重新关联 feature 分支，不直接改写字段。",
                            "如分支恢复失败，保持原迭代仓库关联不变，并继续作为风险项复核。",
                            "记录 reviewer、iterationKey、repoId、目标 feature 分支和 version-info 复核结果。")),
            Map.entry(key("code_repository", "CLONE_URL_INVALID"),
                    new ActionContract(
                            "/repositories/{resourceId}",
                            "确认仓库仍存在，且 cloneUrl 仍无法通过格式和重复纳管校验。",
                            "仓库编辑保存后重新运行 SA-002 审计，cloneUrl 异常数量应减少。",
                            "APPLICATION_MANUAL",
                            "通过仓库编辑页修正 Clone URL，复用格式校验和规范化重复纳管保护。",
                            "如保存失败，不修改原仓库地址；保留 dry-run 动作等待人工确认。",
                            "记录 reviewer、sourceReport、resourceId、修正前后 Clone URL 和重新审计结果。")),
            Map.entry(key("code_repository", "MOCK_PROVIDER_IN_PERSISTENT_REPO"),
                    new ActionContract(
                            "/repositories/{resourceId}",
                            "确认仓库仍存在，且 gitProvider 仍为 MOCK。",
                            "仓库编辑保存后重新运行 SA-002 审计，持久库中 MOCK Provider 数量应减少。",
                            "APPLICATION_MANUAL",
                            "通过仓库编辑页改为真实 Git Provider 并填写真实 Clone URL/Token；不得通过数据库脚本绕过仓库校验。",
                            "如保存失败，保持原仓库记录不变；保留 dry-run 动作等待真实 Provider 信息。",
                            "记录 reviewer、sourceReport、resourceId、原 Provider、目标 Provider 和重新审计结果。")),
            Map.entry(key("release_window", "DRAFT_WINDOW_REMAINS"),
                    new ActionContract(
                            "/release-windows/{resourceId}",
                            "确认发布窗口仍为 DRAFT，并由发布经理判断继续发布、关闭或删除。",
                            "复核窗口状态已符合业务决策；如删除，仅通过应用层删除保护完成。",
                            "APPLICATION_MANUAL",
                            "发布经理在发布窗口页按业务判断继续发布、关闭或删除；仅空 DRAFT 窗口可走应用层删除保护。",
                            "如关闭、删除或继续发布失败，保持原窗口状态，并保留复核动作重新判断。",
                            "记录 reviewer、sourceReport、windowId、业务决策、执行前状态和执行后窗口状态。")),
            Map.entry(key("window_iteration", "ATTACH_BRANCH_NOT_CREATED"),
                    new ActionContract(
                            "/release-windows/{windowId}",
                            "确认窗口挂载关系仍存在，且 branchCreated 仍为 false。",
                            "复核窗口发布计划和 Git 分支状态一致，不伪造 branchCreated。",
                            "OBSERVE_ONLY",
                            "只允许复核发布计划与 Git 分支状态；需要修复时回到挂载/解除挂载业务流程，不直接伪造 branchCreated。",
                            "观察型风险不执行写入；若后续业务流程失败，保留原挂载记录和 dry-run 动作。",
                            "记录 reviewer、sourceReport、windowId、iterationKey、branchStatus 复核结果。"))
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
                    contract.preExecutionCheck(), contract.postExecutionVerification(),
                    contract.dispositionLevel(), contract.allowedAction(), contract.rollbackBoundary(), contract.auditRecord(), false);
        }
        if (!APPROVE.equals(decision)) {
            return rejected(action, resourceType, resourceId, riskType, contract, "不支持的人工复核决策: " + decision);
        }
        return new CleanupActionReview(resourceType, resourceId, riskType,
                trim(action.dataNamespace()), trim(action.reviewBatchId()), normalizeOptional(action.assetScope()),
                trim(action.retentionPolicy()), "ACCEPTED",
                "已通过人工复核，可进入指定应用层入口继续处理；本接口不执行清理",
                contract.applicationEntry(), contract.preExecutionCheck(), contract.postExecutionVerification(),
                contract.dispositionLevel(), contract.allowedAction(), contract.rollbackBoundary(), contract.auditRecord(), false);
    }

    private CleanupActionReview rejected(String resourceType, String resourceId, String riskType,
                                         ActionContract contract, String reason) {
        return new CleanupActionReview(resourceType, resourceId, riskType, null, null, null, null, "REJECTED", reason,
                contract == null ? null : contract.applicationEntry(),
                contract == null ? null : contract.preExecutionCheck(),
                contract == null ? null : contract.postExecutionVerification(),
                contract == null ? null : contract.dispositionLevel(),
                contract == null ? null : contract.allowedAction(),
                contract == null ? null : contract.rollbackBoundary(),
                contract == null ? null : contract.auditRecord(),
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
                contract == null ? null : contract.dispositionLevel(),
                contract == null ? null : contract.allowedAction(),
                contract == null ? null : contract.rollbackBoundary(),
                contract == null ? null : contract.auditRecord(),
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

    private record ActionContract(String applicationEntry,
                                  String preExecutionCheck,
                                  String postExecutionVerification,
                                  String dispositionLevel,
                                  String allowedAction,
                                  String rollbackBoundary,
                                  String auditRecord) {
    }
}
