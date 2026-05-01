package io.releasehub.infrastructure.persistence.run;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "run_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RunItemJpaEntity {
    @Id
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private RunJpaEntity run;

    @Column(name = "window_key", nullable = false)
    private String windowKey;
    @Column(name = "repo_id", nullable = false)
    private String repoId;
    @Column(name = "iteration_key", nullable = false)
    private String iterationKey;
    @Column(name = "planned_order", nullable = false)
    private int plannedOrder;
    @Column(name = "executed_order", nullable = false)
    private int executedOrder;
    @Column(name = "final_result")
    private String finalResult;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "run_step", joinColumns = @JoinColumn(name = "run_item_id"))
    private List<RunStepJpaEmbeddable> steps = new ArrayList<>();
}
