package io.releasehub.interfaces.api.dataquality;

import io.releasehub.application.dataquality.CleanupActionReview;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispositionCaseActionRequest {
    @NotBlank
    private String resourceType;

    @NotBlank
    private String resourceId;

    @NotBlank
    private String riskType;

    private String dataNamespace;
    private String reviewBatchId;
    private String assetScope;
    private String retentionPolicy;
    private String reviewStatus;
    private String reason;
    private String applicationEntry;
    private String preExecutionCheck;
    private String postExecutionVerification;
    private String dispositionLevel;
    private String allowedAction;
    private String rollbackBoundary;
    private String auditRecord;

    CleanupActionReview toCommand() {
        return new CleanupActionReview(
                resourceType,
                resourceId,
                riskType,
                dataNamespace,
                reviewBatchId,
                assetScope,
                retentionPolicy,
                reviewStatus,
                reason,
                applicationEntry,
                preExecutionCheck,
                postExecutionVerification,
                dispositionLevel,
                allowedAction,
                rollbackBoundary,
                auditRecord,
                false);
    }
}
