package io.releasehub.application.port.out;

import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;

public interface GitBranchPort {

    boolean supports(GitProvider provider);

    boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch);

    boolean deleteBranch(String repoCloneUrl, String token, String branchName);

    MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage);

    boolean createTag(String repoCloneUrl, String token, String tagName, String ref, String message);

    BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName);

    record MergeResult(MergeStatus status, String detail) {
        public static MergeResult success() {
            return new MergeResult(MergeStatus.SUCCESS, null);
        }

        public static MergeResult conflict(String detail) {
            return new MergeResult(MergeStatus.CONFLICT, detail);
        }

        public static MergeResult failed(String detail) {
            return new MergeResult(MergeStatus.FAILED, detail);
        }
    }

    record BranchStatus(boolean exists, String latestCommit, int ahead, int behind) {
        public static BranchStatus missing() {
            return new BranchStatus(false, null, 0, 0);
        }

        public static BranchStatus present(String latestCommit) {
            return new BranchStatus(true, latestCommit, 0, 0);
        }
    }
}
