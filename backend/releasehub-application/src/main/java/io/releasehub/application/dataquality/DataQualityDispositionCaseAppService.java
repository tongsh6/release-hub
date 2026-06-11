package io.releasehub.application.dataquality;

import io.releasehub.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataQualityDispositionCaseAppService {
    private static final String STATUS_PLANNED = "PLANNED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String LEVEL_APPLICATION_MANUAL = "APPLICATION_MANUAL";
    private static final String LEVEL_OBSERVE_ONLY = "OBSERVE_ONLY";
    private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_VERIFIED, STATUS_FAILED, STATUS_CANCELLED);

    private final DataQualityDispositionCasePort casePort;

    @Transactional
    public DataQualityDispositionCaseView create(CreateDispositionCaseCommand command) {
        if (command == null || command.action() == null) {
            throw ValidationException.invalidParameter("action");
        }
        CleanupActionReview action = command.action();
        if (isBlank(command.requestedBy())) {
            throw ValidationException.invalidParameter("requestedBy");
        }
        if (!"ACCEPTED".equals(action.reviewStatus())) {
            throw ValidationException.invalidParameter("reviewStatus");
        }
        if (isBlank(action.resourceType()) || isBlank(action.resourceId()) || isBlank(action.riskType())) {
            throw ValidationException.invalidParameter("action");
        }
        if (isBlank(action.dispositionLevel())) {
            throw ValidationException.invalidParameter("dispositionLevel");
        }

        String actionSnapshot = actionSnapshot(action);
        String caseKey = caseKey(command.sourceReport(), action.reviewBatchId(), action.resourceType(),
                action.resourceId(), action.riskType(), actionSnapshot);
        return casePort.findByCaseKey(caseKey)
                .map(DataQualityDispositionCaseView::from)
                .orElseGet(() -> createNewCase(command, action, actionSnapshot, caseKey));
    }

    @Transactional(readOnly = true)
    public List<DataQualityDispositionCaseView> list() {
        return casePort.findAll().stream().map(DataQualityDispositionCaseView::from).toList();
    }

    @Transactional(readOnly = true)
    public DataQualityDispositionCaseView get(String id) {
        return DataQualityDispositionCaseView.from(findRequired(id));
    }

    @Transactional
    public DataQualityDispositionCaseView start(String id, DispositionCaseTransitionCommand command) {
        DataQualityDispositionCasePort.DataQualityDispositionCaseRecord record = findRequired(id);
        requireOperator(command);
        if (!STATUS_PLANNED.equals(record.status())) {
            throw ValidationException.invalidParameter("status");
        }
        if (!LEVEL_APPLICATION_MANUAL.equals(record.dispositionLevel())) {
            throw ValidationException.invalidParameter("dispositionLevel");
        }
        if (isBlank(command.preStateSnapshot())) {
            throw ValidationException.invalidParameter("preStateSnapshot");
        }
        Instant now = Instant.now();
        return save(copy(record,
                STATUS_IN_PROGRESS,
                trim(command.preStateSnapshot()),
                record.postStateSnapshot(),
                trim(command.operator()),
                record.verifiedBy(),
                null,
                null,
                record.retryOfCaseId(),
                now,
                now,
                record.verifiedAt(),
                null,
                null));
    }

    @Transactional
    public DataQualityDispositionCaseView verify(String id, DispositionCaseTransitionCommand command) {
        DataQualityDispositionCasePort.DataQualityDispositionCaseRecord record = findRequired(id);
        requireOperator(command);
        if (TERMINAL_STATUSES.contains(record.status())) {
            throw ValidationException.invalidParameter("status");
        }
        if (!LEVEL_APPLICATION_MANUAL.equals(record.dispositionLevel())
                && !LEVEL_OBSERVE_ONLY.equals(record.dispositionLevel())) {
            throw ValidationException.invalidParameter("dispositionLevel");
        }
        if (LEVEL_APPLICATION_MANUAL.equals(record.dispositionLevel()) && !STATUS_IN_PROGRESS.equals(record.status())) {
            throw ValidationException.invalidParameter("status");
        }
        if (isBlank(command.postStateSnapshot())) {
            throw ValidationException.invalidParameter("postStateSnapshot");
        }
        Instant now = Instant.now();
        return save(copy(record,
                STATUS_VERIFIED,
                record.preStateSnapshot(),
                trim(command.postStateSnapshot()),
                record.handledBy(),
                trim(command.operator()),
                null,
                null,
                record.retryOfCaseId(),
                now,
                record.startedAt(),
                now,
                null,
                null));
    }

    @Transactional
    public DataQualityDispositionCaseView fail(String id, DispositionCaseTransitionCommand command) {
        DataQualityDispositionCasePort.DataQualityDispositionCaseRecord record = findRequired(id);
        requireOperator(command);
        if (TERMINAL_STATUSES.contains(record.status())) {
            throw ValidationException.invalidParameter("status");
        }
        if (isBlank(command.failureReason())) {
            throw ValidationException.invalidParameter("failureReason");
        }
        Instant now = Instant.now();
        return save(copy(record,
                STATUS_FAILED,
                record.preStateSnapshot(),
                record.postStateSnapshot(),
                isBlank(record.handledBy()) ? trim(command.operator()) : record.handledBy(),
                record.verifiedBy(),
                trim(command.failureReason()),
                trim(command.rollbackNote()),
                record.retryOfCaseId(),
                now,
                record.startedAt(),
                record.verifiedAt(),
                now,
                null));
    }

    @Transactional
    public DataQualityDispositionCaseView cancel(String id, DispositionCaseTransitionCommand command) {
        DataQualityDispositionCasePort.DataQualityDispositionCaseRecord record = findRequired(id);
        requireOperator(command);
        if (TERMINAL_STATUSES.contains(record.status())) {
            throw ValidationException.invalidParameter("status");
        }
        Instant now = Instant.now();
        return save(copy(record,
                STATUS_CANCELLED,
                record.preStateSnapshot(),
                record.postStateSnapshot(),
                isBlank(record.handledBy()) ? trim(command.operator()) : record.handledBy(),
                record.verifiedBy(),
                record.failureReason(),
                trim(command.rollbackNote()),
                record.retryOfCaseId(),
                now,
                record.startedAt(),
                record.verifiedAt(),
                record.failedAt(),
                now));
    }

    private DataQualityDispositionCaseView createNewCase(CreateDispositionCaseCommand command,
                                                         CleanupActionReview action,
                                                         String actionSnapshot,
                                                         String caseKey) {
        Instant now = Instant.now();
        DataQualityDispositionCasePort.DataQualityDispositionCaseRecord record = new DataQualityDispositionCasePort.DataQualityDispositionCaseRecord(
                UUID.randomUUID().toString(),
                caseKey,
                trim(command.sourceReport()),
                trim(action.dataNamespace()),
                trim(action.reviewBatchId()),
                trim(action.assetScope()),
                trim(action.retentionPolicy()),
                trim(action.resourceType()),
                trim(action.resourceId()),
                trim(action.riskType()),
                trim(action.dispositionLevel()),
                trim(action.applicationEntry()),
                trim(action.allowedAction()),
                trim(action.preExecutionCheck()),
                trim(action.postExecutionVerification()),
                trim(action.rollbackBoundary()),
                trim(action.auditRecord()),
                actionSnapshot,
                "",
                "",
                STATUS_PLANNED,
                trim(command.requestedBy()),
                "",
                "",
                "",
                "",
                "",
                now,
                now,
                null,
                null,
                null,
                null
        );
        return save(record);
    }

    private DataQualityDispositionCaseView save(DataQualityDispositionCasePort.DataQualityDispositionCaseRecord record) {
        return DataQualityDispositionCaseView.from(casePort.save(record));
    }

    private DataQualityDispositionCasePort.DataQualityDispositionCaseRecord findRequired(String id) {
        if (isBlank(id)) {
            throw ValidationException.invalidParameter("id");
        }
        return casePort.findById(trim(id)).orElseThrow(() -> ValidationException.invalidParameter("id"));
    }

    private static void requireOperator(DispositionCaseTransitionCommand command) {
        if (command == null || isBlank(command.operator())) {
            throw ValidationException.invalidParameter("operator");
        }
    }

    private static DataQualityDispositionCasePort.DataQualityDispositionCaseRecord copy(
            DataQualityDispositionCasePort.DataQualityDispositionCaseRecord record,
            String status,
            String preStateSnapshot,
            String postStateSnapshot,
            String handledBy,
            String verifiedBy,
            String failureReason,
            String rollbackNote,
            String retryOfCaseId,
            Instant updatedAt,
            Instant startedAt,
            Instant verifiedAt,
            Instant failedAt,
            Instant cancelledAt) {
        return new DataQualityDispositionCasePort.DataQualityDispositionCaseRecord(
                record.id(),
                record.caseKey(),
                record.sourceReport(),
                record.dataNamespace(),
                record.reviewBatchId(),
                record.assetScope(),
                record.retentionPolicy(),
                record.resourceType(),
                record.resourceId(),
                record.riskType(),
                record.dispositionLevel(),
                record.applicationEntry(),
                record.allowedAction(),
                record.preExecutionCheck(),
                record.postExecutionVerification(),
                record.rollbackBoundary(),
                record.auditRecord(),
                record.actionSnapshot(),
                preStateSnapshot,
                postStateSnapshot,
                status,
                record.requestedBy(),
                handledBy,
                verifiedBy,
                failureReason,
                rollbackNote,
                retryOfCaseId,
                record.createdAt(),
                updatedAt,
                startedAt,
                verifiedAt,
                failedAt,
                cancelledAt
        );
    }

    private static String actionSnapshot(CleanupActionReview action) {
        return String.join(System.lineSeparator(),
                "resourceType=%s".formatted(trim(action.resourceType())),
                "resourceId=%s".formatted(trim(action.resourceId())),
                "riskType=%s".formatted(trim(action.riskType())),
                "dataNamespace=%s".formatted(trim(action.dataNamespace())),
                "reviewBatchId=%s".formatted(trim(action.reviewBatchId())),
                "assetScope=%s".formatted(trim(action.assetScope())),
                "retentionPolicy=%s".formatted(trim(action.retentionPolicy())),
                "reviewStatus=%s".formatted(trim(action.reviewStatus())),
                "applicationEntry=%s".formatted(trim(action.applicationEntry())),
                "dispositionLevel=%s".formatted(trim(action.dispositionLevel())),
                "allowedAction=%s".formatted(trim(action.allowedAction())),
                "rollbackBoundary=%s".formatted(trim(action.rollbackBoundary())),
                "auditRecord=%s".formatted(trim(action.auditRecord())));
    }

    private static String caseKey(String sourceReport, String reviewBatchId, String resourceType, String resourceId,
                                  String riskType, String actionSnapshot) {
        String material = String.join("|",
                trim(sourceReport),
                trim(reviewBatchId),
                trim(resourceType),
                trim(resourceId),
                trim(riskType),
                actionSnapshot);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build data quality disposition case key", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
