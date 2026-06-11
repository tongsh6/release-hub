package io.releasehub.infrastructure.persistence.releasegovernance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.releasegovernance.ReleaseCandidateSignoffPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReleaseCandidateSignoffPersistenceAdapter implements ReleaseCandidateSignoffPort {
    private static final TypeReference<List<ChecklistDecision>> CHECKLIST_TYPE = new TypeReference<>() {
    };

    private final ReleaseCandidateSignoffJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public ReleaseCandidateSignoffRecord save(ReleaseCandidateSignoffRecord record) {
        ReleaseCandidateSignoffJpaEntity entity = new ReleaseCandidateSignoffJpaEntity();
        entity.setId(record.id());
        entity.setCandidateId(record.candidateId());
        entity.setCandidateLabel(record.candidateLabel());
        entity.setReviewer(record.reviewer());
        entity.setDecision(record.decision());
        entity.setNote(record.note());
        entity.setChecklistJson(writeChecklist(record.checklist()));
        entity.setCreatedAt(record.createdAt());
        return toRecord(repository.save(entity));
    }

    @Override
    public Optional<ReleaseCandidateSignoffRecord> findLatestByCandidateId(String candidateId) {
        return repository.findTopByCandidateIdOrderByCreatedAtDesc(candidateId).map(this::toRecord);
    }

    private ReleaseCandidateSignoffRecord toRecord(ReleaseCandidateSignoffJpaEntity entity) {
        return new ReleaseCandidateSignoffRecord(
                entity.getId(),
                entity.getCandidateId(),
                entity.getCandidateLabel(),
                entity.getReviewer(),
                entity.getDecision(),
                entity.getNote(),
                readChecklist(entity.getChecklistJson()),
                entity.getCreatedAt()
        );
    }

    private String writeChecklist(List<ChecklistDecision> checklist) {
        try {
            return objectMapper.writeValueAsString(checklist);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize release candidate checklist", e);
        }
    }

    private List<ChecklistDecision> readChecklist(String checklistJson) {
        try {
            return objectMapper.readValue(checklistJson, CHECKLIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize release candidate checklist", e);
        }
    }
}
