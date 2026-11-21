package io.releasehub.application.gitoperation;

/** 仅传播本次同步调用的来源，不用于持久恢复或作为仓库锁。 */
public final class GitOperationContext {
    private static final ThreadLocal<String> RUN = new ThreadLocal<>();
    private GitOperationContext() {}
    public static String runId() { return RUN.get(); }
    public static Scope withRun(String runId) {
        String previous = RUN.get(); RUN.set(runId); return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final String previous;
        private Scope(String previous) { this.previous=previous; }
        @Override public void close() { if(previous==null)RUN.remove();else RUN.set(previous); }
    }
}
