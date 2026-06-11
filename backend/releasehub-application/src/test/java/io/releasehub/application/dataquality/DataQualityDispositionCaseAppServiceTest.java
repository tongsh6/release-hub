package io.releasehub.application.dataquality;

import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataQualityDispositionCaseAppServiceTest {
    private final InMemoryPort port = new InMemoryPort();
    private final DataQualityDispositionCaseAppService service = new DataQualityDispositionCaseAppService(port);

    @Test
    void shouldCreateDispositionCaseWithoutExecutingBusinessResource() {
        DataQualityDispositionCaseView created = service.create(new CreateDispositionCaseCommand(
                "release-manager",
                "report/actions.jsonl",
                acceptedDraftWindowAction()));

        assertThat(created.status()).isEqualTo("PLANNED");
        assertThat(created.dispositionLevel()).isEqualTo("APPLICATION_MANUAL");
        assertThat(created.preStateSnapshot()).isEmpty();
        assertThat(created.postStateSnapshot()).isEmpty();
        assertThat(created.actionSnapshot()).contains("resourceType=release_window");
        assertThat(port.records).hasSize(1);
    }

    @Test
    void shouldReturnExistingCaseForDuplicateAction() {
        DataQualityDispositionCaseView first = service.create(new CreateDispositionCaseCommand(
                "release-manager",
                "report/actions.jsonl",
                acceptedDraftWindowAction()));
        DataQualityDispositionCaseView second = service.create(new CreateDispositionCaseCommand(
                "release-manager",
                "report/actions.jsonl",
                acceptedDraftWindowAction()));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(port.records).hasSize(1);
    }

    @Test
    void shouldStartAndVerifyApplicationManualCaseWithSnapshots() {
        DataQualityDispositionCaseView created = service.create(new CreateDispositionCaseCommand(
                "release-manager",
                "report/actions.jsonl",
                acceptedDraftWindowAction()));

        DataQualityDispositionCaseView started = service.start(created.id(),
                DispositionCaseTransitionCommand.start("release-manager", "{\"status\":\"DRAFT\"}"));
        assertThat(started.status()).isEqualTo("IN_PROGRESS");
        assertThat(started.preStateSnapshot()).contains("DRAFT");

        DataQualityDispositionCaseView verified = service.verify(created.id(),
                DispositionCaseTransitionCommand.verify("qa", "{\"status\":\"CLOSED\"}"));
        assertThat(verified.status()).isEqualTo("VERIFIED");
        assertThat(verified.verifiedBy()).isEqualTo("qa");
        assertThat(verified.postStateSnapshot()).contains("CLOSED");
    }

    @Test
    void shouldBlockStartForObserveOnlyCase() {
        DataQualityDispositionCaseView created = service.create(new CreateDispositionCaseCommand(
                "release-manager",
                "report/actions.jsonl",
                acceptedObserveOnlyAction()));

        assertThatThrownBy(() -> service.start(created.id(),
                DispositionCaseTransitionCommand.start("release-manager", "{\"branchCreated\":false}")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldRecordFailureWithoutChangingOriginalSnapshot() {
        DataQualityDispositionCaseView created = service.create(new CreateDispositionCaseCommand(
                "release-manager",
                "report/actions.jsonl",
                acceptedDraftWindowAction()));
        service.start(created.id(), DispositionCaseTransitionCommand.start("release-manager", "{\"status\":\"DRAFT\"}"));

        DataQualityDispositionCaseView failed = service.fail(created.id(),
                DispositionCaseTransitionCommand.fail("release-manager", "应用层保存失败", "保持原窗口状态"));

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.failureReason()).isEqualTo("应用层保存失败");
        assertThat(failed.rollbackNote()).isEqualTo("保持原窗口状态");
        assertThat(failed.preStateSnapshot()).contains("DRAFT");
    }

    @Test
    void shouldRejectNonAcceptedReviewAction() {
        CleanupActionReview pendingAction = new CleanupActionReview(
                "release_window",
                "window-1",
                "DRAFT_WINDOW_REMAINS",
                "acceptance",
                "batch-1",
                "HISTORICAL_ACCEPTANCE",
                "manual-review",
                "PENDING",
                "等待复核",
                "/release-windows/{resourceId}",
                "check",
                "verify",
                "APPLICATION_MANUAL",
                "handle",
                "rollback",
                "audit",
                false);

        assertThatThrownBy(() -> service.create(new CreateDispositionCaseCommand(
                "release-manager",
                "report/actions.jsonl",
                pendingAction)))
                .isInstanceOf(ValidationException.class);
    }

    private CleanupActionReview acceptedDraftWindowAction() {
        return new CleanupActionReview(
                "release_window",
                "window-1",
                "DRAFT_WINDOW_REMAINS",
                "acceptance",
                "batch-1",
                "HISTORICAL_ACCEPTANCE",
                "manual-review",
                "ACCEPTED",
                "已通过人工复核",
                "/release-windows/{resourceId}",
                "确认发布窗口仍为 DRAFT。",
                "复核窗口状态已符合业务决策。",
                "APPLICATION_MANUAL",
                "发布经理在发布窗口页按业务判断继续发布、关闭或删除。",
                "如失败，保持原窗口状态。",
                "记录 reviewer、windowId 和业务决策。",
                false);
    }

    private CleanupActionReview acceptedObserveOnlyAction() {
        return new CleanupActionReview(
                "window_iteration",
                "window-1::iteration-1",
                "ATTACH_BRANCH_NOT_CREATED",
                "acceptance",
                "batch-1",
                "HISTORICAL_ACCEPTANCE",
                "manual-review",
                "ACCEPTED",
                "已通过人工复核",
                "/release-windows/{windowId}",
                "确认挂载关系仍存在。",
                "复核窗口发布计划和 Git 分支状态一致。",
                "OBSERVE_ONLY",
                "只允许复核发布计划与 Git 分支状态。",
                "观察型风险不执行写入。",
                "记录 reviewer、windowId、iterationKey。",
                false);
    }

    private static class InMemoryPort implements DataQualityDispositionCasePort {
        private final List<DataQualityDispositionCaseRecord> records = new ArrayList<>();

        @Override
        public DataQualityDispositionCaseRecord save(DataQualityDispositionCaseRecord record) {
            records.removeIf(existing -> existing.id().equals(record.id()));
            records.add(record);
            return record;
        }

        @Override
        public Optional<DataQualityDispositionCaseRecord> findById(String id) {
            return records.stream().filter(record -> record.id().equals(id)).findFirst();
        }

        @Override
        public Optional<DataQualityDispositionCaseRecord> findByCaseKey(String caseKey) {
            return records.stream().filter(record -> record.caseKey().equals(caseKey)).findFirst();
        }

        @Override
        public List<DataQualityDispositionCaseRecord> findAll() {
            return records.stream()
                    .sorted(Comparator.comparing(DataQualityDispositionCaseRecord::createdAt).reversed())
                    .toList();
        }
    }
}
