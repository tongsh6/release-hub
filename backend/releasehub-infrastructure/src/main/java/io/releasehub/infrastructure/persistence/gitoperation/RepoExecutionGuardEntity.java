package io.releasehub.infrastructure.persistence.gitoperation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** 独立操作证据的持久模型，不依赖尚未提交的业务 Run。 */
@Entity @Table(name="repo_execution_guard")
@Getter @Setter @NoArgsConstructor
public class RepoExecutionGuardEntity {
    @Id @Column(name="remote_key", length=64)
    private String remoteKey;
    @Column(name="session_id", length=36)
    private String sessionId;
    @Column(nullable=false)
    private long generation;
    @Column(name="operation_id", length=36)
    private String operationId;
    @Column(length=128)
    private String reason;
    @Column(name="updated_at", nullable=false)
    private Instant updatedAt;
}
