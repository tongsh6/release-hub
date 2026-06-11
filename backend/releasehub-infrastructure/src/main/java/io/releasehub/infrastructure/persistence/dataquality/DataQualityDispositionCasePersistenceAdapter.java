package io.releasehub.infrastructure.persistence.dataquality;

import io.releasehub.application.dataquality.DataQualityDispositionCasePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DataQualityDispositionCasePersistenceAdapter implements DataQualityDispositionCasePort {
    private final DataQualityDispositionCaseJpaRepository repository;

    @Override
    public DataQualityDispositionCaseRecord save(DataQualityDispositionCaseRecord record) {
        return toRecord(repository.save(toEntity(record)));
    }

    @Override
    public Optional<DataQualityDispositionCaseRecord> findById(String id) {
        return repository.findById(id).map(this::toRecord);
    }

    @Override
    public Optional<DataQualityDispositionCaseRecord> findByCaseKey(String caseKey) {
        return repository.findByCaseKey(caseKey).map(this::toRecord);
    }

    @Override
    public List<DataQualityDispositionCaseRecord> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toRecord).toList();
    }

    private DataQualityDispositionCaseJpaEntity toEntity(DataQualityDispositionCaseRecord record) {
        DataQualityDispositionCaseJpaEntity entity = new DataQualityDispositionCaseJpaEntity();
        entity.setId(record.id());
        entity.setCaseKey(record.caseKey());
        entity.setSourceReport(record.sourceReport());
        entity.setDataNamespace(record.dataNamespace());
        entity.setReviewBatchId(record.reviewBatchId());
        entity.setAssetScope(record.assetScope());
        entity.setRetentionPolicy(record.retentionPolicy());
        entity.setResourceType(record.resourceType());
        entity.setResourceId(record.resourceId());
        entity.setRiskType(record.riskType());
        entity.setDispositionLevel(record.dispositionLevel());
        entity.setApplicationEntry(record.applicationEntry());
        entity.setAllowedAction(record.allowedAction());
        entity.setPreExecutionCheck(record.preExecutionCheck());
        entity.setPostExecutionVerification(record.postExecutionVerification());
        entity.setRollbackBoundary(record.rollbackBoundary());
        entity.setAuditRecord(record.auditRecord());
        entity.setActionSnapshot(record.actionSnapshot());
        entity.setPreStateSnapshot(record.preStateSnapshot());
        entity.setPostStateSnapshot(record.postStateSnapshot());
        entity.setStatus(record.status());
        entity.setRequestedBy(record.requestedBy());
        entity.setHandledBy(record.handledBy());
        entity.setVerifiedBy(record.verifiedBy());
        entity.setFailureReason(record.failureReason());
        entity.setRollbackNote(record.rollbackNote());
        entity.setRetryOfCaseId(record.retryOfCaseId());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        entity.setStartedAt(record.startedAt());
        entity.setVerifiedAt(record.verifiedAt());
        entity.setFailedAt(record.failedAt());
        entity.setCancelledAt(record.cancelledAt());
        return entity;
    }

    private DataQualityDispositionCaseRecord toRecord(DataQualityDispositionCaseJpaEntity entity) {
        return new DataQualityDispositionCaseRecord(
                entity.getId(),
                entity.getCaseKey(),
                entity.getSourceReport(),
                entity.getDataNamespace(),
                entity.getReviewBatchId(),
                entity.getAssetScope(),
                entity.getRetentionPolicy(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getRiskType(),
                entity.getDispositionLevel(),
                entity.getApplicationEntry(),
                entity.getAllowedAction(),
                entity.getPreExecutionCheck(),
                entity.getPostExecutionVerification(),
                entity.getRollbackBoundary(),
                entity.getAuditRecord(),
                entity.getActionSnapshot(),
                entity.getPreStateSnapshot(),
                entity.getPostStateSnapshot(),
                entity.getStatus(),
                entity.getRequestedBy(),
                entity.getHandledBy(),
                entity.getVerifiedBy(),
                entity.getFailureReason(),
                entity.getRollbackNote(),
                entity.getRetryOfCaseId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getStartedAt(),
                entity.getVerifiedAt(),
                entity.getFailedAt(),
                entity.getCancelledAt()
        );
    }
}
