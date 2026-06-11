package io.releasehub.application.releasegovernance;

import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SA-001 发布候选评审服务测试")
class ReleaseGovernanceAppServiceTest {
    private final InMemorySignoffPort signoffPort = new InMemorySignoffPort();
    private final ReleaseGovernanceAppService service = new ReleaseGovernanceAppService(signoffPort);

    @Test
    void shouldExposeReleaseCandidateReviewSummaryWithEvidenceAndChecklist() {
        ReleaseCandidateReviewSummary summary = service.getCandidateReview();

        assertThat(summary.candidateId()).isEqualTo("release-candidate-2026-05-23");
        assertThat(summary.conclusion()).contains("受控发布候选评审");
        assertThat(summary.evidence()).hasSize(4);
        assertThat(summary.risks()).extracting(ReleaseCandidateReviewSummary.RiskItem::level)
                .contains("NON_BLOCKING", "DEFERRED", "BOUNDARY");
        assertThat(summary.checklist()).hasSize(4);
        assertThat(summary.latestSignoff()).isNull();
    }

    @Test
    void shouldPersistSignoffWithoutChangingReleaseState() {
        ReleaseCandidateSignoffView signoff = service.signoff(new ReleaseCandidateSignoffCommand(
                "release-candidate-2026-05-23",
                "release-manager",
                "APPROVE_FOR_CONTROLLED_REVIEW",
                "进入 staging",
                confirmedChecklist()));

        assertThat(signoff.id()).isNotBlank();
        assertThat(signoff.decision()).isEqualTo("APPROVE_FOR_CONTROLLED_REVIEW");
        assertThat(signoff.checklist()).hasSize(4);
        assertThat(signoffPort.saved).hasSize(1);

        ReleaseCandidateReviewSummary summary = service.getCandidateReview();
        assertThat(summary.latestSignoff()).isNotNull();
        assertThat(summary.latestSignoff().reviewer()).isEqualTo("release-manager");
    }

    @Test
    void shouldRejectUnsupportedDecision() {
        assertThatThrownBy(() -> service.signoff(new ReleaseCandidateSignoffCommand(
                "release-candidate-2026-05-23",
                "release-manager",
                "DEPLOY_NOW",
                "",
                confirmedChecklist())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldRejectUnknownOrMissingChecklistItem() {
        assertThatThrownBy(() -> service.signoff(new ReleaseCandidateSignoffCommand(
                "release-candidate-2026-05-23",
                "release-manager",
                "APPROVE_FOR_CONTROLLED_REVIEW",
                "",
                List.of(new ReleaseCandidateSignoffPort.ChecklistDecision(
                        "unknown-check", "CONFIRMED", "")))))
                .isInstanceOf(ValidationException.class);
    }

    private static List<ReleaseCandidateSignoffPort.ChecklistDecision> confirmedChecklist() {
        return List.of(
                new ReleaseCandidateSignoffPort.ChecklistDecision(
                        "acceptance-baseline", "CONFIRMED", "170/0/0 已确认"),
                new ReleaseCandidateSignoffPort.ChecklistDecision(
                        "static-scan", "CONFIRMED", "静态扫描已确认"),
                new ReleaseCandidateSignoffPort.ChecklistDecision(
                        "data-quality-boundary", "CONFIRMED", "数据质量队列已确认"),
                new ReleaseCandidateSignoffPort.ChecklistDecision(
                        "deferred-scope", "CONFIRMED", "暂缓项已确认")
        );
    }

    private static class InMemorySignoffPort implements ReleaseCandidateSignoffPort {
        private final List<ReleaseCandidateSignoffRecord> saved = new ArrayList<>();

        @Override
        public ReleaseCandidateSignoffRecord save(ReleaseCandidateSignoffRecord record) {
            saved.add(record);
            return record;
        }

        @Override
        public Optional<ReleaseCandidateSignoffRecord> findLatestByCandidateId(String candidateId) {
            return saved.stream()
                    .filter(item -> item.candidateId().equals(candidateId))
                    .reduce((first, second) -> second);
        }
    }
}
