package io.releasehub.application.releasegovernance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReleaseCandidateSignoffPort {
    ReleaseCandidateSignoffRecord save(ReleaseCandidateSignoffRecord record);

    Optional<ReleaseCandidateSignoffRecord> findLatestByCandidateId(String candidateId);

    record ReleaseCandidateSignoffRecord(
            String id,
            String candidateId,
            String candidateLabel,
            String reviewer,
            String decision,
            String note,
            List<ChecklistDecision> checklist,
            Instant createdAt
    ) {
        public ReleaseCandidateSignoffRecord {
            checklist = checklist == null ? List.of() : List.copyOf(checklist);
        }
    }

    record ChecklistDecision(
            String key,
            String status,
            String note
    ) {
    }
}
