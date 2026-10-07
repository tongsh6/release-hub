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
    private int executionVersion = 1;
    private RunExecutionStatus executionStatus;
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
        run.executionStatus = executionStatus;
        return run;
    }

    public void finish(Instant now) {
        if (executionStatus == RunExecutionStatus.PLANNED) {
            throw new IllegalStateException("计划预览不可执行或结束");
        }
        this.finishedAt = now;
        touch(now);
    }

    public List<RunItem> getItems() {
        return List.copyOf(items);
    }

    public void addItem(RunItem item) {
        items.add(item);
    }
}
