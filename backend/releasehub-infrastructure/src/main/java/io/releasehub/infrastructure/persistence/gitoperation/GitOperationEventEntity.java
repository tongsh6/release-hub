package io.releasehub.infrastructure.persistence.gitoperation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** 独立操作证据的持久模型，不依赖尚未提交的业务 Run。 */
@Entity @Table(name="git_operation_event", uniqueConstraints=@UniqueConstraint(columnNames={"attempt_id", "event_sequence"}))
@Getter @Setter @NoArgsConstructor
public class GitOperationEventEntity {
    @Id @Column(length=36)
    private String id;
    @Column(name="operation_id", nullable=false, length=36)
    private String operationId;
    @Column(name="attempt_id", nullable=false, length=36)
    private String attemptId;
    @Column(name="event_sequence", nullable=false)
    private int sequence;
    @Column(name="event_type", nullable=false, length=32)
    private String type;
    @Column(name="occurred_at", nullable=false)
    private Instant at;
    @Column(name="evidence_json", nullable=false, columnDefinition="TEXT")
    private String evidenceJson;
    @Column(length=128)
    private String reason;
}
