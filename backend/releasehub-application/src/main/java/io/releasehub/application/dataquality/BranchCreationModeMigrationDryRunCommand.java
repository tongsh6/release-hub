package io.releasehub.application.dataquality;

public record BranchCreationModeMigrationDryRunCommand(
        String requestedBy,
        String sourceReport
) {
}
