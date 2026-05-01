package io.releasehub.application.window;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DryPlanItemView {
    private String windowKey;
    private String repoId;
    private String iterationKey;
    private boolean featureBranchExists;
    private boolean releaseBranchExists;
    private boolean mrExists;
    private boolean mrMerged;
    private Integer mrIid;
    private String mrUrl;
    private boolean protectedBranch;
    private boolean approvalRequired;
    private boolean pipelineGate;
    private boolean permissionDenied;
}
