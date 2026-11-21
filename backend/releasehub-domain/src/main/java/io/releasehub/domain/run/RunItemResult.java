package io.releasehub.domain.run;

public enum RunItemResult {
    CREATED,
    ALREADY_EXISTS,
    MR_REUSED,
    MERGED,
    ALREADY_MERGED,
    MERGE_BLOCKED,
    UNKNOWN,
    FAILED,
    SKIPPED_DUE_TO_BLOCK,
    SKIPPED,
    SUCCESS,
    VERSION_UPDATE_SUCCESS,
    VERSION_UPDATE_FAILED,
    BRANCH_EXISTS,
    BRANCH_CREATED,
    TAG_CREATED,
    CI_TRIGGERED,
    CI_NOT_CONFIGURED,
    ITERATION_CLOSED;

    public boolean isFailure() {
        return this == FAILED || this == VERSION_UPDATE_FAILED || this == MERGE_BLOCKED
                || this == SKIPPED_DUE_TO_BLOCK;
    }
}
