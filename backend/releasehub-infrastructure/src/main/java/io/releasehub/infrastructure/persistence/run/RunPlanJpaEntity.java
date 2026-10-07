package io.releasehub.infrastructure.persistence.run;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 与 Flyway 结构一致，也支持既有 local profile 的 Hibernate 建表。 */
@Entity
@Table(name = "run_plan", uniqueConstraints = @UniqueConstraint(name = "uk_run_plan_request", columnNames = {"operator", "idempotency_key"}),
        indexes = @Index(name = "idx_run_plan_window_created", columnList = "window_id, created_at DESC, run_id DESC"))
@Getter
@Setter
@NoArgsConstructor
public class RunPlanJpaEntity {
    @Id @Column(name = "run_id", length = 255)
    private String runId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "run_id")
    private RunJpaEntity run;
    @Column(name = "window_id", nullable = false, length = 255)
    private String windowId;
    @Column(nullable = false, length = 128)
    private String operator;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "plan_hash", nullable = false, length = 64)
    private String planHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "item_count", nullable = false)
    private int itemCount;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshot;
}
