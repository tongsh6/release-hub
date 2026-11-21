package io.releasehub.infrastructure.persistence.gitoperation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** 独立操作证据的持久模型，不依赖尚未提交的业务 Run。 */
@Entity @Table(name="git_operation", uniqueConstraints=@UniqueConstraint(name="uk_git_operation_task", columnNames="task_id"))
@Getter @Setter @NoArgsConstructor
public class GitOperationEntity {
    @Id @Column(length=36)
    private String id;
    @Column(name="repo_id", nullable=false, length=255)
    private String repoId;
    @Column(name="remote_key", nullable=false, length=64)
    private String remoteKey;
    @Column(nullable=false, length=16)
    private String provider;
    @Column(name="base_url", nullable=false, length=512)
    private String baseUrl;
    @Column(name="project_id", nullable=false, length=128)
    private String projectId;
    @Column(nullable=false, length=64)
    private String action;
    @Column(name="target_json", nullable=false, columnDefinition="TEXT")
    private String targetJson;
    @Column(name="request_hash", nullable=false, length=64)
    private String requestHash;
    @Column(name="run_id", length=255)
    private String runId;
    @Column(name="task_id", length=36)
    private String taskId;
    @Column(name="claim_token", length=36)
    private String claimToken;
    @Column(nullable=false, length=128)
    private String operator;
    @Column(name="session_id", nullable=false, length=36)
    private String sessionId;
    @Column(name="attempt_id", nullable=false, length=36)
    private String attemptId;
    @Column(nullable=false)
    private long generation;
    @Column(nullable=false, length=32)
    private String state;
    @Version @Column(name="state_version", nullable=false)
    private long version;
    @Column(length=128)
    private String reason;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="updated_at", nullable=false)
    private Instant updatedAt;
}
