package io.releasehub.domain.run;

public enum RunItemResult {
    MERGED,
    ALREADY_MERGED,
    MERGE_BLOCKED,
    FAILED,
    SKIPPED_DUE_TO_BLOCK,
    SKIPPED,
    SUCCESS,
    VERSION_UPDATE_SUCCESS,
    VERSION_UPDATE_FAILED
}
