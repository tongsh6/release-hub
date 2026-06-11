package io.releasehub.infrastructure.persistence.dataquality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "data_quality_disposition_case")
@Getter
@Setter
public class DataQualityDispositionCaseJpaEntity {
    @Id
    private String id;

    @Column(name = "case_key", nullable = false, unique = true, length = 128)
    private String caseKey;

    @Column(name = "source_report", columnDefinition = "text")
    private String sourceReport;

    @Column(name = "data_namespace", length = 128)
    private String dataNamespace;

    @Column(name = "review_batch_id", length = 128)
    private String reviewBatchId;

    @Column(name = "asset_scope", length = 64)
    private String assetScope;

    @Column(name = "retention_policy", length = 128)
    private String retentionPolicy;

    @Column(name = "resource_type", nullable = false, length = 128)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 256)
    private String resourceId;

    @Column(name = "risk_type", nullable = false, length = 128)
    private String riskType;

    @Column(name = "disposition_level", nullable = false, length = 64)
    private String dispositionLevel;

    @Column(name = "application_entry", columnDefinition = "text")
    private String applicationEntry;

    @Column(name = "allowed_action", columnDefinition = "text")
    private String allowedAction;

    @Column(name = "pre_execution_check", columnDefinition = "text")
    private String preExecutionCheck;

    @Column(name = "post_execution_verification", columnDefinition = "text")
    private String postExecutionVerification;

    @Column(name = "rollback_boundary", columnDefinition = "text")
    private String rollbackBoundary;

    @Column(name = "audit_record", columnDefinition = "text")
    private String auditRecord;

    @Column(name = "action_snapshot", nullable = false, columnDefinition = "text")
    private String actionSnapshot;

    @Column(name = "pre_state_snapshot", columnDefinition = "text")
    private String preStateSnapshot;

    @Column(name = "post_state_snapshot", columnDefinition = "text")
    private String postStateSnapshot;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "handled_by", length = 128)
    private String handledBy;

    @Column(name = "verified_by", length = 128)
    private String verifiedBy;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "rollback_note", columnDefinition = "text")
    private String rollbackNote;

    @Column(name = "retry_of_case_id", length = 64)
    private String retryOfCaseId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
