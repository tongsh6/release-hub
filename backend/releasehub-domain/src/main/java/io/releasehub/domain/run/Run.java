package io.releasehub.domain.run;

import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author tongshuanglong
 */
@Getter
@NoArgsConstructor
public class Run extends BaseEntity<RunId> {
    private RunType runType;
    private String operator;
    private Instant startedAt;
    private Instant finishedAt;
    private int executionVersion = 2;
    private RunExecutionStatus executionStatus = RunExecutionStatus.RUNNING;
    private List<RunItem> items = new ArrayList<>();

    private Run(RunId id, RunType runType, String operator, Instant startedAt, Instant finishedAt, List<RunItem> items, Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt != null ? createdAt : startedAt, updatedAt != null ? updatedAt : startedAt, version);
        this.runType = runType;
        this.operator = operator;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        if (items != null) {
            this.items = new ArrayList<>(items);
        }
        this.executionStatus = finishedAt == null ? RunExecutionStatus.RUNNING : aggregateStatus();
    }

    public static Run rehydrate(RunId id, RunType runType, String operator, Instant startedAt, Instant finishedAt, List<RunItem> items, Instant createdAt, Instant updatedAt, long version) {
        return new Run(id, runType, operator, startedAt, finishedAt, items, createdAt, updatedAt, version);
    }

    public static Run start(RunType runType, String operator, Instant now) {
        RunId id = RunId.generate(runType, now);
        return new Run(id, runType, operator, now, null, null, now, now, 0);
    }

    public static Run preview(String operator, Instant now) {
        Run run = start(RunType.WINDOW_ORCHESTRATION, operator, now);
        run.executionVersion = 2;
        run.executionStatus = RunExecutionStatus.PLANNED;
        return run;
    }

    public static Run rehydrate(RunId id, RunType type, String operator, Instant startedAt, Instant finishedAt,
                                List<RunItem> items, Instant createdAt, Instant updatedAt, long version,
                                int executionVersion, RunExecutionStatus executionStatus) {
        Run run = rehydrate(id, type, operator, startedAt, finishedAt, items, createdAt, updatedAt, version);
        run.executionVersion = executionVersion;
        if (executionStatus != null) run.executionStatus = executionStatus;
        return run;
    }

    public void finish(Instant now) {
        if (executionStatus == RunExecutionStatus.PLANNED) {
            throw new IllegalStateException("计划预览不可执行或结束");
        }
        this.executionStatus = aggregateStatus();
        this.finishedAt = now;
        touch(now);
    }

    private RunExecutionStatus aggregateStatus() {
        if (items.isEmpty()) return RunExecutionStatus.FAILED;
        if (items.stream().anyMatch(item -> item.getFinalResult() == RunItemResult.UNKNOWN
                || item.getSteps().stream().anyMatch(step -> step.result() == RunItemResult.UNKNOWN))) {
            return RunExecutionStatus.NEEDS_REVIEW;
        }
        var repositories = items.stream().collect(java.util.stream.Collectors.groupingBy(RunItem::getRepo));
        long completed = repositories.values().stream().filter(group -> group.stream().allMatch(this::isComplete)).count();
        if (completed == repositories.size()) return RunExecutionStatus.DONE;
        return completed == 0 ? RunExecutionStatus.FAILED : RunExecutionStatus.PARTIAL_FAILED;
    }

    private boolean isComplete(RunItem item) {
        if (item.getSteps().stream().anyMatch(step -> step.result() == null || step.result().isFailure())) return false;
        RunItemResult result = item.getFinalResult();
        if (result == null) return false;
        return switch (runType) {
            case WINDOW_ORCHESTRATION, ATTACH_ITERATION ->
                    result == RunItemResult.MERGED || result == RunItemResult.ALREADY_MERGED;
            case VERSION_UPDATE -> result == RunItemResult.VERSION_UPDATE_SUCCESS;
            case WINDOW_CLEANUP -> result == RunItemResult.SUCCESS || result == RunItemResult.CI_NOT_CONFIGURED;
        };
    }

    public List<RunItem> getItems() {
        return List.copyOf(items);
    }

    public void addItem(RunItem item) {
        items.add(item);
    }
}
