package io.releasehub.application.dataquality;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DataQualityDispositionCasePort {
    DataQualityDispositionCaseRecord save(DataQualityDispositionCaseRecord record);

    Optional<DataQualityDispositionCaseRecord> findById(String id);

    Optional<DataQualityDispositionCaseRecord> findByCaseKey(String caseKey);

    List<DataQualityDispositionCaseRecord> findAll();

    record DataQualityDispositionCaseRecord(
            String id,
            String caseKey,
            String sourceReport,
            String dataNamespace,
            String reviewBatchId,
            String assetScope,
            String retentionPolicy,
            String resourceType,
            String resourceId,
            String riskType,
            String dispositionLevel,
            String applicationEntry,
            String allowedAction,
            String preExecutionCheck,
            String postExecutionVerification,
            String rollbackBoundary,
            String auditRecord,
            String actionSnapshot,
            String preStateSnapshot,
            String postStateSnapshot,
            String status,
            String requestedBy,
            String handledBy,
            String verifiedBy,
            String failureReason,
            String rollbackNote,
            String retryOfCaseId,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant verifiedAt,
            Instant failedAt,
            Instant cancelledAt
    ) {
    }
}
