package io.releasehub.application.gitoperation;

import io.releasehub.application.run.RunPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import java.util.Objects;

/** Thread-local provenance for the current action; durable ownership is checked against the task row. */
public final class GitOperationContext {
    private static final ThreadLocal<Invocation> CURRENT = new ThreadLocal<>();
    private GitOperationContext() {}
    private record Invocation(String runId, String operator, RunPort.TaskFence taskFence,
                              String repoId, String expectedProjectId,
                              GitOperationJournalPort.Operation unsentOperation) {}

    public static String runId() { return CURRENT.get() == null ? null : CURRENT.get().runId(); }
    public static String operator() { return CURRENT.get() == null ? null : CURRENT.get().operator(); }
    public static RunPort.TaskFence taskFence() { return CURRENT.get() == null ? null : CURRENT.get().taskFence(); }
    public static String repoId() { return CURRENT.get() == null ? null : CURRENT.get().repoId(); }
    public static String expectedProjectId() { return CURRENT.get() == null ? null : CURRENT.get().expectedProjectId(); }
    public static GitOperationJournalPort.Operation unsentOperation() {
        return CURRENT.get() == null ? null : CURRENT.get().unsentOperation();
    }

    /** First verified read binds this task; another project can never replace it. */
    public static void bindProjectId(String projectId) {
        Invocation current = CURRENT.get();
        if (current == null || current.taskFence() == null) throw new IllegalStateException("Project identity requires a task scope");
        if (projectId == null || !projectId.matches("[1-9][0-9]*")
                || current.expectedProjectId() != null && !current.expectedProjectId().equals(projectId)) {
            throw BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);
        }
        CURRENT.set(new Invocation(current.runId(), current.operator(), current.taskFence(), current.repoId(),
                projectId, current.unsentOperation()));
    }

    /** Non-task consumers such as cleanup still report their Run provenance. */
    public static Scope withRun(String runId) {
        if (taskFence() != null) throw new IllegalStateException("Cannot replace an active task scope");
        return install(new Invocation(runId, null, null, null, null, null));
    }

    public static Scope withTask(RunPort.TaskClaim claim) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(claim.fence(), "task fence");
        if (taskFence() != null) throw new IllegalStateException("Cannot replace an active task scope");
        String projectId = claim.metadata().get("git.projectId");
        if (projectId != null && !projectId.matches("[1-9][0-9]*")) throw BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);
        var original = claim.unsentOperation();
        if (original != null) {
            String action = switch (claim.actionType()) {
                case ENSURE_FEATURE, ENSURE_RELEASE -> "CREATE_BRANCH";
                case ENSURE_MR -> "CREATE_MR";
                case TRY_MERGE -> "MERGE_MR";
                default -> "";
            };
            if (!Objects.equals(claim.runId(), original.runId())
                    || !Objects.equals(claim.repo().value(), original.repoId())
                    || !Objects.equals(claim.operator(), original.operator())
                    || !action.equals(original.action()) || !"GITLAB".equals(original.provider())
                    || !"INTENT_RECORDED".equals(original.state()) || original.reason() != null) {
                throw BusinessException.of(ErrorCode.GIT_OPERATION_STALE);
            }
            if (original.projectId() == null || !original.projectId().matches("[1-9][0-9]*")
                    || projectId != null && !projectId.equals(original.projectId())) {
                throw BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);
            }
            projectId = original.projectId();
        }
        return install(new Invocation(claim.runId(), claim.operator(), claim.fence(), claim.repo().value(),
                projectId, original));
    }

    private static Scope install(Invocation invocation) {
        Invocation previous = CURRENT.get();
        CURRENT.set(invocation);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final Invocation previous;
        private Scope(Invocation previous) { this.previous = previous; }
        @Override public void close() {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
