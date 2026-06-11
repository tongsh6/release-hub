package io.releasehub.application.releasegovernance;

import io.releasehub.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReleaseGovernanceAppService {
    private static final String CANDIDATE_ID = "release-candidate-2026-05-23";
    private static final String CANDIDATE_LABEL = "ReleaseHub 发布候选 2026-05-23";
    private static final Set<String> DECISIONS = Set.of("APPROVE_FOR_CONTROLLED_REVIEW", "HOLD_FOR_FOLLOW_UP");
    private static final Set<String> CHECKLIST_STATUSES = Set.of("CONFIRMED", "NEEDS_FOLLOW_UP", "NOT_APPLICABLE");
    private static final Set<String> REQUIRED_CHECKLIST_KEYS = Set.of(
            "acceptance-baseline",
            "static-scan",
            "data-quality-boundary",
            "deferred-scope"
    );

    private final ReleaseCandidateSignoffPort signoffPort;

    @Transactional(readOnly = true)
    public ReleaseCandidateReviewSummary getCandidateReview() {
        ReleaseCandidateSignoffView latest = signoffPort.findLatestByCandidateId(CANDIDATE_ID)
                .map(ReleaseCandidateSignoffView::from)
                .orElse(null);
        return new ReleaseCandidateReviewSummary(
                CANDIDATE_ID,
                CANDIDATE_LABEL,
                "可进入受控发布候选评审 / dogfood / staging；不是无条件 GA。",
                "发布前必须确认 SA-002 数据质量复核队列处理策略，并显式接受残留非目标。",
                evidenceItems(),
                riskItems(),
                checklistItems(),
                latest
        );
    }

    @Transactional
    public ReleaseCandidateSignoffView signoff(ReleaseCandidateSignoffCommand command) {
        if (command == null) {
            throw ValidationException.invalidParameter("request");
        }
        if (!CANDIDATE_ID.equals(trim(command.candidateId()))) {
            throw ValidationException.invalidParameter("candidateId");
        }
        if (isBlank(command.reviewer())) {
            throw ValidationException.invalidParameter("reviewer");
        }
        String decision = normalize(command.decision());
        if (!DECISIONS.contains(decision)) {
            throw ValidationException.invalidParameter("decision");
        }
        List<ReleaseCandidateSignoffPort.ChecklistDecision> checklist = command.checklist().stream()
                .map(this::normalizeChecklist)
                .toList();
        if (checklist.isEmpty()) {
            throw ValidationException.invalidParameter("checklist");
        }
        Set<String> checklistKeys = new HashSet<>();
        for (ReleaseCandidateSignoffPort.ChecklistDecision item : checklist) {
            if (!REQUIRED_CHECKLIST_KEYS.contains(item.key()) || !checklistKeys.add(item.key())) {
                throw ValidationException.invalidParameter("checklist.key");
            }
        }
        if (!checklistKeys.containsAll(REQUIRED_CHECKLIST_KEYS)) {
            throw ValidationException.invalidParameter("checklist");
        }

        ReleaseCandidateSignoffPort.ReleaseCandidateSignoffRecord record = signoffPort.save(
                new ReleaseCandidateSignoffPort.ReleaseCandidateSignoffRecord(
                        UUID.randomUUID().toString(),
                        CANDIDATE_ID,
                        CANDIDATE_LABEL,
                        trim(command.reviewer()),
                        decision,
                        trim(command.note()),
                        checklist,
                        Instant.now()
                ));
        return ReleaseCandidateSignoffView.from(record);
    }

    private ReleaseCandidateSignoffPort.ChecklistDecision normalizeChecklist(
            ReleaseCandidateSignoffPort.ChecklistDecision item) {
        if (item == null || isBlank(item.key())) {
            throw ValidationException.invalidParameter("checklist.key");
        }
        String status = normalize(item.status());
        if (!CHECKLIST_STATUSES.contains(status)) {
            throw ValidationException.invalidParameter("checklist.status");
        }
        return new ReleaseCandidateSignoffPort.ChecklistDecision(trim(item.key()), status, trim(item.note()));
    }

    private List<ReleaseCandidateReviewSummary.EvidenceItem> evidenceItems() {
        return List.of(
                new ReleaseCandidateReviewSummary.EvidenceItem(
                        "全量场景验收",
                        "PASS",
                        "tasks/records/2026-05-23-sa-001-full-baseline-rerun.md",
                        "170 PASS / 0 FAIL / 0 SKIP"),
                new ReleaseCandidateReviewSummary.EvidenceItem(
                        "静态扫描",
                        "PASS",
                        ".ai/reports/static-scan/20260523-193829/summary.md",
                        "SpotBugs 0、frontend lint PASS、typecheck PASS"),
                new ReleaseCandidateReviewSummary.EvidenceItem(
                        "数据质量复核队列",
                        "REVIEW_REQUIRED",
                        "tasks/records/2026-05-23-sa-002-data-quality-review-queue.md",
                        "188 条待复核动作已有应用内复核队列"),
                new ReleaseCandidateReviewSummary.EvidenceItem(
                        "发布候选收口报告",
                        "READY",
                        "docs/reports/release-candidate-2026-05-23.md",
                        "进入受控发布候选评审")
        );
    }

    private List<ReleaseCandidateReviewSummary.RiskItem> riskItems() {
        return List.of(
                new ReleaseCandidateReviewSummary.RiskItem(
                        "NON_BLOCKING",
                        "SA-002 历史 DRAFT / attach 残留",
                        "需人工复核，不阻断新建场景闭环",
                        ".ai/reports/sa002-safe-cleanup/20260523-aligned-baseline/summary.md"),
                new ReleaseCandidateReviewSummary.RiskItem(
                        "DEFERRED",
                        "RBAC / 通知 / 批量迁移 / PDF",
                        "明确暂缓，不进入当前阶段",
                        "docs/reports/release-candidate-2026-05-23.md"),
                new ReleaseCandidateReviewSummary.RiskItem(
                        "BOUNDARY",
                        "签核不执行发布或清理动作",
                        "只记录评审结论",
                        "docs/openspec/specs/release-governance/spec.md")
        );
    }

    private List<ReleaseCandidateReviewSummary.ChecklistItem> checklistItems() {
        return List.of(
                new ReleaseCandidateReviewSummary.ChecklistItem(
                        "acceptance-baseline",
                        "确认全量场景验收 170/0/0 可作为本轮候选基线",
                        "CONFIRMED",
                        "tasks/records/2026-05-23-sa-001-full-baseline-rerun.md",
                        true),
                new ReleaseCandidateReviewSummary.ChecklistItem(
                        "static-scan",
                        "确认静态扫描和类型检查无新增阻断",
                        "CONFIRMED",
                        ".ai/reports/static-scan/20260523-193829/summary.md",
                        true),
                new ReleaseCandidateReviewSummary.ChecklistItem(
                        "data-quality-boundary",
                        "确认 SA-002 188 条待复核动作进入人工队列且不自动执行",
                        "CONFIRMED",
                        "tasks/records/2026-05-23-sa-002-data-quality-review-queue.md",
                        true),
                new ReleaseCandidateReviewSummary.ChecklistItem(
                        "deferred-scope",
                        "确认 RBAC、通知、批量迁移、PDF 和生产分支清理工具不进入当前阶段",
                        "CONFIRMED",
                        "docs/reports/release-candidate-2026-05-23.md",
                        true)
        );
    }

    private static String normalize(String value) {
        return trim(value).toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
