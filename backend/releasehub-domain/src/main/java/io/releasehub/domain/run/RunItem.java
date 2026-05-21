package io.releasehub.domain.run;

import io.releasehub.domain.base.BaseEntity;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunItem extends BaseEntity<RunItemId> {
    private final String windowKey;
    private final RepoId repo;
    private final IterationKey iterationKey;
    private final int plannedOrder;
    private int executedOrder;
    private final List<RunStep> steps = new ArrayList<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private RunItemResult finalResult;

    private RunItem(RunItemId id, String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, int executedOrder, RunItemResult finalResult, List<RunStep> steps, Map<String, String> metadata, Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt, updatedAt, version);
        this.windowKey = windowKey;
        this.repo = repo;
        this.iterationKey = iterationKey;
        this.plannedOrder = plannedOrder;
        this.executedOrder = executedOrder;
        this.finalResult = finalResult;
        if (steps != null) {
            this.steps.addAll(steps);
        }
        if (metadata != null) {
            this.metadata.putAll(metadata);
        }
    }

    private RunItem(RunItemId id, String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, Instant now) {
        super(id, now);
        this.windowKey = windowKey;
        this.repo = repo;
        this.iterationKey = iterationKey;
        this.plannedOrder = plannedOrder;
        this.executedOrder = 0;
        this.finalResult = null;
    }

    public static RunItem create(String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, Instant now) {
        RunItemId id = RunItemId.generate(windowKey, repo, iterationKey);
        return new RunItem(id, windowKey, repo, iterationKey, plannedOrder, now);
    }

    public static RunItem createRetry(String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, String sourceRunId, Instant now) {
        RunItemId baseId = RunItemId.generate(windowKey, repo, iterationKey);
        RunItemId id = RunItemId.of(baseId.value() + "::retry::" + sourceRunId);
        return new RunItem(id, windowKey, repo, iterationKey, plannedOrder, now);
    }

    public static RunItem rehydrate(RunItemId id, String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, int executedOrder, RunItemResult finalResult, List<RunStep> steps, Instant createdAt, Instant updatedAt, long version) {
        return new RunItem(id, windowKey, repo, iterationKey, plannedOrder, executedOrder, finalResult, steps, null, createdAt, updatedAt, version);
    }

    public static RunItem rehydrate(RunItemId id, String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, int executedOrder, RunItemResult finalResult, List<RunStep> steps, Map<String, String> metadata, Instant createdAt, Instant updatedAt, long version) {
        return new RunItem(id, windowKey, repo, iterationKey, plannedOrder, executedOrder, finalResult, steps, metadata, createdAt, updatedAt, version);
    }

    public String getWindowKey() {
        return windowKey;
    }

    public RepoId getRepo() {
        return repo;
    }

    public IterationKey getIterationKey() {
        return iterationKey;
    }

    public int getPlannedOrder() {
        return plannedOrder;
    }

    public int getExecutedOrder() {
        return executedOrder;
    }

    public void setExecutedOrder(int executedOrder) {
        this.executedOrder = executedOrder;
    }

    public List<RunStep> getSteps() {
        return List.copyOf(steps);
    }

    public void addStep(RunStep step) {
        steps.add(step);
    }

    public Map<String, String> getMetadata() {
        return Map.copyOf(metadata);
    }

    public void putMetadata(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        metadata.put(key, value);
    }

    public RunItemResult getFinalResult() {
        return finalResult;
    }

    public void finishWith(RunItemResult result, Instant now) {
        this.finalResult = result;
        touch(now);
    }
}
