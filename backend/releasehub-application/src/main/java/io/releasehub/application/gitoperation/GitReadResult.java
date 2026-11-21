package io.releasehub.application.gitoperation;

/** 查询失败和查询不完整不能证明远端资源不存在。 */
public record GitReadResult<T>(Status status, T evidence) {
    public enum Status { FOUND, MISMATCH, NOT_FOUND, FORBIDDEN, UNAVAILABLE, INCOMPLETE, UNSUPPORTED }
    public static <T> GitReadResult<T> found(T evidence) { return new GitReadResult<>(Status.FOUND, evidence); }
    public static <T> GitReadResult<T> unavailable() { return new GitReadResult<>(Status.UNAVAILABLE, null); }
}
