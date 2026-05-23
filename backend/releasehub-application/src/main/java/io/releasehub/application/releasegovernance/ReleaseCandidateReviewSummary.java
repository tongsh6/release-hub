package io.releasehub.application.releasegovernance;

import java.util.List;

public record ReleaseCandidateReviewSummary(
        String candidateId,
        String candidateLabel,
        String conclusion,
        String recommendation,
        List<EvidenceItem> evidence,
        List<RiskItem> risks,
        List<ChecklistItem> checklist,
        ReleaseCandidateSignoffView latestSignoff
) {
    public ReleaseCandidateReviewSummary {
        evidence = List.copyOf(evidence);
        risks = List.copyOf(risks);
        checklist = List.copyOf(checklist);
    }

    public record EvidenceItem(String label, String status, String sourcePath, String result) {
    }

    public record RiskItem(String level, String title, String status, String sourcePath) {
    }

    public record ChecklistItem(String key, String title, String expectedStatus, String sourcePath, boolean required) {
    }
}
