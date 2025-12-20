package io.releasehub.domain.run;

import io.releasehub.domain.base.BaseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Run extends BaseEntity<String> {
    private final RunType runType;
    private final String operator;
    private final Instant startedAt;
    private Instant finishedAt;
    private final List<RunItem> items = new ArrayList<>();

    private Run(String id, RunType runType, String operator, Instant startedAt, Instant finishedAt, Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.runType = runType;
        this.operator = operator;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    private Run(String id, RunType runType, String operator, Instant now) {
        super(id, now);
        this.runType = runType;
        this.operator = operator;
        this.startedAt = now;
        this.finishedAt = null;
    }

    public static Run start(RunType runType, String operator, Instant now) {
        String id = runType.name() + "::" + now.toEpochMilli();
        return new Run(id, runType, operator, now);
    }

    public static Run rehydrate(String id, RunType runType, String operator, Instant startedAt, Instant finishedAt, Instant createdAt, Instant updatedAt) {
        return new Run(id, runType, operator, startedAt, finishedAt, createdAt, updatedAt);
    }

    public RunType getRunType() {
        return runType;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void finish(Instant now) {
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
