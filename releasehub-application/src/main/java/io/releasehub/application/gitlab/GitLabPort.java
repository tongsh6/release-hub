package io.releasehub.application.gitlab;

import java.util.Optional;

public interface GitLabPort {
    boolean branchExists(long projectId, String ref);
    Optional<MrInfo> ensureMrInfo(long projectId, String source, String target);
    GateSummary fetchGateSummary(long projectId);
    BranchStatistics fetchBranchStatistics(long projectId);
    MrStatistics fetchMrStatistics(long projectId);

    record MrInfo(boolean exists, boolean merged, Integer iid, String url) {}
    record GateSummary(boolean protectedBranch, boolean approvalRequired, boolean pipelineGate, boolean permissionDenied) {}
    record BranchStatistics(int total, int active, int nonCompliant) {}
    record MrStatistics(int total, int open, int merged, int closed) {}
}
