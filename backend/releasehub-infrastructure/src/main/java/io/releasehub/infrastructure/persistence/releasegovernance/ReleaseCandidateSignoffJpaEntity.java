package io.releasehub.infrastructure.persistence.releasegovernance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "release_candidate_signoff")
@Getter
@Setter
public class ReleaseCandidateSignoffJpaEntity {
    @Id
    private String id;

    @Column(name = "candidate_id", nullable = false, length = 128)
    private String candidateId;

    @Column(name = "candidate_label", nullable = false, length = 200)
    private String candidateLabel;

    @Column(nullable = false, length = 128)
    private String reviewer;

    @Column(nullable = false, length = 64)
    private String decision;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "checklist_json", nullable = false, columnDefinition = "text")
    private String checklistJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
