package io.releasehub.infrastructure.persistence.gitoperation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** 独立操作证据的持久模型，不依赖尚未提交的业务 Run。 */
@Entity @Table(name="git_operation_observation", uniqueConstraints=@UniqueConstraint(columnNames={"operation_id", "operator", "request_key"}))
@Getter @Setter @NoArgsConstructor
public class GitOperationObservationEntity {
    @Id @Column(length=36)
    private String id;
    @Column(name="operation_id", nullable=false, length=36)
    private String operationId;
    @Column(nullable=false, length=128)
    private String operator;
    @Column(name="request_key", nullable=false, length=128)
    private String key;
    @Column(name="expected_version", nullable=false)
    private long expectedVersion;
    @Column(nullable=false, length=32)
    private String status;
    @Column(name="evidence_json", nullable=false, columnDefinition="TEXT")
    private String evidenceJson;
    @Column(name="observed_at", nullable=false)
    private Instant observedAt;
}
