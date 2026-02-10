package io.releasehub.application.run;

import io.releasehub.domain.run.RunTask;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RunTaskView {
    private String id;
    private String runId;
    private String taskType;
    private int taskOrder;
    private String targetType;
    private String targetId;
    private String status;
    private int retryCount;
    private int maxRetries;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    
    public static RunTaskView from(RunTask task) {
        return RunTaskView.builder()
                .id(task.getId().value())
                .runId(task.getRunId().value())
                .taskType(task.getTaskType().name())
                .taskOrder(task.getTaskOrder())
                .targetType(task.getTargetType() != null ? task.getTargetType().name() : null)
                .targetId(task.getTargetId())
                .status(task.getStatus().name())
                .retryCount(task.getRetryCount())
                .maxRetries(task.getMaxRetries())
                .errorMessage(task.getErrorMessage())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }
}
