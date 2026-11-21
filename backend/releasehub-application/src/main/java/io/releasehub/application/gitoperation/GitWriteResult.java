package io.releasehub.application.gitoperation;

/**
 * An adapter action outcome. APPLIED confirms that action's stated goal, including idempotent read-only reuse.
 * It does not prove an HTTP request occurred or that a later action completed: ENSURE_MR is not TRY_MERGE.
 * Actual transport attempts and their APPLIED/REJECTED/UNKNOWN facts are recorded separately in the journal.
 * UNKNOWN never authorizes an automatic repeat of a write.
 */
public record GitWriteResult<T>(Outcome outcome, T evidence, String reason) {
    public enum Outcome { APPLIED, REJECTED, UNKNOWN }
    public static <T> GitWriteResult<T> applied(T evidence) {
        return new GitWriteResult<>(Outcome.APPLIED, evidence, null);
    }
    public static <T> GitWriteResult<T> rejected(String reason) {
        return new GitWriteResult<>(Outcome.REJECTED, null, reason);
    }
    public static <T> GitWriteResult<T> unknown(String reason) {
        return new GitWriteResult<>(Outcome.UNKNOWN, null, reason);
    }
}
