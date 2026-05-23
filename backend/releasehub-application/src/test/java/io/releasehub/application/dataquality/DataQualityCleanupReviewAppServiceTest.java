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
        return new CleanupActionInput(
                "release_window",
                "window-1",
                "DRAFT_WINDOW_REMAINS",
                "在发布窗口页按业务判断继续发布、关闭或删除；仅空 DRAFT 窗口可通过应用层删除保护删除。",
                false,
                "release_window.status:验收窗口",
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
}
