package io.releasehub.interfaces.api.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepoBranchSummaryView {
    private int totalBranches;
    private int activeBranches;
    private int nonCompliantBranches;
    private int activeMrs;
    private int mergedMrs;
    private int closedMrs;
}
