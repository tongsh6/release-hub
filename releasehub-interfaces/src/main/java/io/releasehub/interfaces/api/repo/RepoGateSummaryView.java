package io.releasehub.interfaces.api.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepoGateSummaryView {
    private boolean protectedBranch;
    private boolean approvalRequired;
    private boolean pipelineGate;
    private boolean permissionDenied;
}
