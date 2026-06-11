package io.releasehub.interfaces.api.dataquality;

import io.releasehub.application.dataquality.BranchCreationModeMigrationDryRunCommand;
import jakarta.validation.constraints.NotBlank;

public record BranchCreationModeMigrationDryRunRequest(
        @NotBlank String requestedBy,
        String sourceReport
) {
    public BranchCreationModeMigrationDryRunCommand toCommand() {
        return new BranchCreationModeMigrationDryRunCommand(requestedBy, sourceReport);
    }
}
