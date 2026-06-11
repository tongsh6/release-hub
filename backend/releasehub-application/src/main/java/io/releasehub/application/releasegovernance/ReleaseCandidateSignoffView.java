package io.releasehub.application.releasegovernance;

import java.time.Instant;
import java.util.List;

public record ReleaseCandidateSignoffView(
        String id,
        String candidateId,
        String candidateLabel,
        String reviewer,
        String decision,
        String note,
        List<ReleaseCandidateSignoffPort.ChecklistDecision> checklist,
        Instant createdAt
) {
    public ReleaseCandidateSignoffView {
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
    }

    public static ReleaseCandidateSignoffView from(ReleaseCandidateSignoffPort.ReleaseCandidateSignoffRecord record) {
        return new ReleaseCandidateSignoffView(
                record.id(),
                record.candidateId(),
                record.candidateLabel(),
                record.reviewer(),
                record.decision(),
                record.note(),
                record.checklist(),
                record.createdAt()
        );
    }
}
