package io.releasehub.domain.run;

public enum ActionType {
    ENSURE_FEATURE,
    ENSURE_RELEASE,
    ENSURE_MR,
    TRY_MERGE,
    UPDATE_VERSION,
    CLOSE_ITERATION,
    ARCHIVE_BRANCH,
    MERGE_TO_MASTER,
    CREATE_TAG,
    TRIGGER_CI;

    public static java.util.List<ActionType> orchestrationSequence() {
        return java.util.List.of(ENSURE_FEATURE, ENSURE_RELEASE, ENSURE_MR, TRY_MERGE);
    }
}
