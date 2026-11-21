package io.releasehub.application.gitoperation;

/** 单次外部写请求的结果；未收到可确认结果时不得当作可重试失败。 */
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
