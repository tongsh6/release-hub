package io.releasehub.interfaces.api.dataquality;

import io.releasehub.application.dataquality.CleanupActionInput;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CleanupActionReviewRequest {
    @NotBlank
    private String resourceType;

    @NotBlank
    private String resourceId;

    @NotBlank
    private String riskType;

    private String suggestedAction;
    private Boolean executed;
    private String source;
    private String dataNamespace;
    private String reviewBatchId;
    private String assetScope;
    private String retentionPolicy;
    private String applicationEntry;
    private String preExecutionCheck;
    private String postExecutionVerification;
    private String reviewDecision;

    CleanupActionInput toCommand() {
        return new CleanupActionInput(
                resourceType,
                resourceId,
                riskType,
                suggestedAction,
                executed,
                source,
                dataNamespace,
                reviewBatchId,
                assetScope,
                retentionPolicy,
                applicationEntry,
                preExecutionCheck,
                postExecutionVerification,
                reviewDecision);
    }
}
