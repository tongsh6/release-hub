package io.releasehub.application.dataquality;

import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SA-002 存量清理人工复核服务测试")
class DataQualityCleanupReviewAppServiceTest {
    private final DataQualityCleanupReviewAppService service = new DataQualityCleanupReviewAppService();

    @Test
    void shouldAcceptReviewedActionForApplicationEntryWithoutExecuting() {
        CleanupReviewResult result = service.review(new CleanupReviewCommand(
                "release-manager",
                ".ai/reports/sa002-safe-cleanup/manual/actions.jsonl",
                List.of(validDraftWindowAction("APPROVE_FOR_APPLICATION_ENTRY"))));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isZero();
        assertThat(result.actions().get(0).reviewStatus()).isEqualTo("ACCEPTED");
        assertThat(result.actions().get(0).applicationEntry()).isEqualTo("/release-windows/{resourceId}");
        assertThat(result.actions().get(0).dispositionLevel()).isEqualTo("APPLICATION_MANUAL");
        assertThat(result.actions().get(0).allowedAction()).contains("发布经理");
        assertThat(result.actions().get(0).rollbackBoundary()).contains("保持原窗口状态");
        assertThat(result.actions().get(0).auditRecord()).contains("windowId");
        assertThat(result.actions().get(0).executionPermitted()).isFalse();
    }

    @Test
    void shouldKeepPendingActionWhenManualDecisionIsPending() {
        CleanupReviewResult result = service.review(new CleanupReviewCommand(
                "qa",
                "report",
                List.of(validDraftWindowAction("PENDING"))));

        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.actions().get(0).reviewStatus()).isEqualTo("PENDING");
        assertThat(result.actions().get(0).executionPermitted()).isFalse();
    }

    @Test
    void shouldFilterReviewQueueByResourceRiskAndStatus() {
        CleanupReviewResult result = service.review(new CleanupReviewCommand(
                "qa",
                "report",
                List.of(
                        validDraftWindowAction("APPROVE_FOR_APPLICATION_ENTRY"),
                        validWindowIterationAction("PENDING")),
                "window_iteration",
                "ATTACH_BRANCH_NOT_CREATED",
                "PENDING"));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.actions().get(0).resourceType()).isEqualTo("window_iteration");
        assertThat(result.actions().get(0).riskType()).isEqualTo("ATTACH_BRANCH_NOT_CREATED");
        assertThat(result.actions().get(0).reviewStatus()).isEqualTo("PENDING");
        assertThat(result.actions().get(0).dispositionLevel()).isEqualTo("OBSERVE_ONLY");
    }

    @Test
    void shouldReturnMigrationRequiredStrategyForBranchCreationModeRisk() {
        CleanupReviewResult result = service.review(new CleanupReviewCommand(
                "qa",
                "report",
                List.of(validBranchCreationModeAction("APPROVE_FOR_APPLICATION_ENTRY"))));

        CleanupActionReview review = result.actions().get(0);
        assertThat(review.reviewStatus()).isEqualTo("ACCEPTED");
        assertThat(review.dispositionLevel()).isEqualTo("MIGRATION_REQUIRED");
        assertThat(review.allowedAction()).contains("受控迁移服务");
        assertThat(review.rollbackBoundary()).contains("回滚");
        assertThat(review.auditRecord()).contains("迁移批次");
        assertThat(review.executionPermitted()).isFalse();
    }

    @Test
    void shouldReturnManualStrategyForMockProviderInPersistentRepository() {
        CleanupReviewResult result = service.review(new CleanupReviewCommand(
                "qa",
                "report",
                List.of(validMockProviderAction("APPROVE_FOR_APPLICATION_ENTRY"))));

        CleanupActionReview review = result.actions().get(0);
        assertThat(review.reviewStatus()).isEqualTo("ACCEPTED");
        assertThat(review.dispositionLevel()).isEqualTo("APPLICATION_MANUAL");
        assertThat(review.allowedAction()).contains("真实 Git Provider");
        assertThat(review.rollbackBoundary()).contains("保持原仓库记录不变");
        assertThat(review.auditRecord()).contains("目标 Provider");
        assertThat(review.executionPermitted()).isFalse();
    }

    @Test
    void shouldCarryNamespaceBatchScopeAndRetentionPolicy() {
        CleanupReviewResult result = service.review(new CleanupReviewCommand(
                "qa",
                "report",
                List.of(validDraftWindowAction(
                        "APPROVE_FOR_APPLICATION_ENTRY",
                        "acceptance",
                        "sa002-20260523",
                        "HISTORICAL_ACCEPTANCE",
                        "manual-review-then-archive")),
                null,
                null,
                "ACCEPTED",
                "HISTORICAL_ACCEPTANCE"));

        assertThat(result.total()).isEqualTo(1);
        CleanupActionReview review = result.actions().get(0);
        assertThat(review.dataNamespace()).isEqualTo("acceptance");
        assertThat(review.reviewBatchId()).isEqualTo("sa002-20260523");
        assertThat(review.assetScope()).isEqualTo("HISTORICAL_ACCEPTANCE");
        assertThat(review.retentionPolicy()).isEqualTo("manual-review-then-archive");
        assertThat(result.assetBoundaries())
                .extracting(CleanupReviewResult.AssetBoundarySummary::key)
                .containsExactly("API_VISIBLE_ASSETS", "DB_AUDIT_ASSETS", "REVIEW_QUEUE_ACTIONS");
        assertThat(result.assetScopeCounts()).hasSize(1);
        assertThat(result.assetScopeCounts().get(0).assetScope()).isEqualTo("HISTORICAL_ACCEPTANCE");
        assertThat(result.assetScopeCounts().get(0).count()).isEqualTo(1);
    }

    @Test
    void shouldRejectDirectExecutionDecision() {
        CleanupReviewResult result = service.review(new CleanupReviewCommand(
                "qa",
                "report",
                List.of(validDraftWindowAction("EXECUTE_DIRECTLY"))));

        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.actions().get(0).reason()).contains("不允许直接执行");
    }

    @Test
    void shouldRejectActionWithoutPreOrPostChecks() {
        CleanupActionInput action = new CleanupActionInput(
                "release_window",
                "window-1",
                "DRAFT_WINDOW_REMAINS",
                "在发布窗口页按业务判断继续发布、关闭或删除。",
                false,
                "release_window.status",
                "/release-windows/{resourceId}",
                "",
                "",
                "APPROVE_FOR_APPLICATION_ENTRY");

        CleanupReviewResult result = service.review(new CleanupReviewCommand("qa", "report", List.of(action)));

        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.actions().get(0).reason()).contains("执行前检查");
    }

    @Test
    void shouldRejectUnsupportedRiskType() {
        CleanupActionInput action = new CleanupActionInput(
                "release_window",
                "window-1",
                "DROP_DATABASE",
                "直接删库",
                false,
                "manual",
                "/release-windows/{resourceId}",
                "确认",
                "复核",
                "APPROVE_FOR_APPLICATION_ENTRY");

        CleanupReviewResult result = service.review(new CleanupReviewCommand("qa", "report", List.of(action)));

        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.actions().get(0).reason()).contains("未登记");
    }

    @Test
    void shouldRequireReviewer() {
        assertThatThrownBy(() -> service.review(new CleanupReviewCommand("", "report", List.of(validDraftWindowAction("PENDING")))))
                .isInstanceOf(ValidationException.class);
    }

    private CleanupActionInput validDraftWindowAction(String decision) {
        return validDraftWindowAction(decision, null, null, null, null);
    }

    private CleanupActionInput validDraftWindowAction(String decision, String dataNamespace, String reviewBatchId,
                                                      String assetScope, String retentionPolicy) {
        return new CleanupActionInput(
                "release_window",
                "window-1",
                "DRAFT_WINDOW_REMAINS",
                "在发布窗口页按业务判断继续发布、关闭或删除；仅空 DRAFT 窗口可通过应用层删除保护删除。",
                false,
                "release_window.status:验收窗口",
                dataNamespace,
                reviewBatchId,
                assetScope,
                retentionPolicy,
                "/release-windows/{resourceId}",
                "确认发布窗口仍为 DRAFT，并由发布经理判断继续发布、关闭或删除。",
                "复核窗口状态已符合业务决策；如删除，仅通过应用层删除保护完成。",
                decision);
    }

    private CleanupActionInput validWindowIterationAction(String decision) {
        return new CleanupActionInput(
                "window_iteration",
                "window-1::repo-1::ITER-1",
                "ATTACH_BRANCH_NOT_CREATED",
                "在发布窗口页复核挂载关系与分支状态；不得直接改写 branchCreated。",
                false,
                "window_iteration.branch_created:window-1",
                "/release-windows/{windowId}",
                "确认窗口挂载关系仍存在，且 branchCreated 仍为 false。",
                "复核窗口发布计划和 Git 分支状态一致，不伪造 branchCreated。",
                decision);
    }

    private CleanupActionInput validBranchCreationModeAction(String decision) {
        return new CleanupActionInput(
                "iteration_repo",
                "ITER-1::repo-1",
                "BRANCH_CREATION_MODE_MISSING_OR_INVALID",
                "进入受控迁移服务前先确认真实业务语义；不得直接 update iteration_repo。",
                false,
                "iteration_repo.branch_creation_mode:ITER-1",
                "受控迁移服务: iteration_repo.branch_creation_mode",
                "确认 iterationKey/repoId 仍存在，且分支模式缺失或不在 AUTO、NAMED、EXISTING 范围内。",
                "复核 version-info 返回的 branchCreationMode 已按真实业务语义补齐。",
                decision);
    }

    private CleanupActionInput validMockProviderAction(String decision) {
        return new CleanupActionInput(
                "code_repository",
                "repo-1",
                "MOCK_PROVIDER_IN_PERSISTENT_REPO",
                "通过仓库编辑入口把 Provider 改为真实 Git Provider，并填写真实 cloneUrl/token。",
                false,
                "code_repository.git_provider:MOCK:repo-1",
                "/repositories/{resourceId}",
                "确认仓库仍存在，且 gitProvider 仍为 MOCK。",
                "仓库编辑保存后重新运行 SA-002 审计，持久库中 MOCK Provider 数量应减少。",
                decision);
    }
}
