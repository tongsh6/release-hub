package io.releasehub.domain.run;

import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;

/**
 * 运行任务领域实体
 */
@Getter
public class RunTask extends BaseEntity<RunTaskId> {
    private final RunId runId;
    private final RunTaskType taskType;
    private final int taskOrder;
    private final TargetType targetType;
    private final String targetId;
    private RunTaskStatus status;
    private int retryCount;
    private final int maxRetries;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;

    private RunTask(RunTaskId id, RunId runId, RunTaskType taskType, int taskOrder,
                    TargetType targetType, String targetId, RunTaskStatus status,
                    int retryCount, int maxRetries, String errorMessage,
                    Instant startedAt, Instant finishedAt, Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.runId = runId;
        this.taskType = taskType;
        this.taskOrder = taskOrder;
        this.targetType = targetType;
        this.targetId = targetId;
        this.status = status;
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static RunTask create(RunId runId, RunTaskType taskType, int taskOrder,
                                  TargetType targetType, String targetId, int maxRetries, Instant now) {
        return new RunTask(
                RunTaskId.generate(), runId, taskType, taskOrder,
                targetType, targetId, RunTaskStatus.PENDING,
                0, maxRetries, null, null, null, now, now
        );
    }

    public static RunTask rehydrate(RunTaskId id, RunId runId, RunTaskType taskType, int taskOrder,
                                     TargetType targetType, String targetId, RunTaskStatus status,
                                     int retryCount, int maxRetries, String errorMessage,
                                     Instant startedAt, Instant finishedAt, Instant createdAt, Instant updatedAt) {
        return new RunTask(id, runId, taskType, taskOrder, targetType, targetId, status,
                retryCount, maxRetries, errorMessage, startedAt, finishedAt, createdAt, updatedAt);
    }

    public void start(Instant now) {
        this.status = RunTaskStatus.RUNNING;
        this.startedAt = now;
        touch(now);
    }

    public void markCompleted(Instant now) {
        this.status = RunTaskStatus.COMPLETED;
        this.finishedAt = now;
        touch(now);
    }

    public void markFailed(String errorMessage, Instant now) {
        this.status = RunTaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = now;
        touch(now);
    }

    public void markSkipped(Instant now) {
        this.status = RunTaskStatus.SKIPPED;
        this.finishedAt = now;
        touch(now);
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }

    public boolean isFailed() {
        return this.status == RunTaskStatus.FAILED;
    }

    public boolean isCompleted() {
        return this.status == RunTaskStatus.COMPLETED;
    }

    public boolean isPending() {
        return this.status == RunTaskStatus.PENDING;
    }
}
