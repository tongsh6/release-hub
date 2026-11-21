package io.releasehub.infrastructure.persistence.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 固定编排动作复用既有 target_type/target_id；不由 Run 级联写入。 */
@Entity
@Table(name = "run_task")
@Getter
@Setter
@NoArgsConstructor
public class RunTaskJpaEntity {
    @Id
    private String id;
    @Column(name = "run_id", nullable = false)
    private String runId;
    @Column(name = "task_type", nullable = false)
    private String taskType;
    @Column(name = "task_order", nullable = false)
    private int taskOrder;
    @Column(name = "target_type")
    private String targetType;
    @Column(name = "target_id")
    private String targetId;
    @Column(nullable = false)
    private String status;
    @Column(name = "claim_token")
    private String claimToken;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
