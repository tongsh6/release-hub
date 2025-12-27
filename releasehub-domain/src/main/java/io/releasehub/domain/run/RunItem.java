package io.releasehub.domain.run;

import io.releasehub.domain.base.BaseEntity;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RunItem extends BaseEntity<String> {
    private final String windowKey;
    private final RepoId repo;
    private final IterationKey iterationKey;
    private final int plannedOrder;
    private int executedOrder;
    private final List<RunStep> steps = new ArrayList<>();
    private RunItemResult finalResult;

    private RunItem(String id, String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, int executedOrder, RunItemResult finalResult, List<RunStep> steps, Instant createdAt, Instant updatedAt, long version) {
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
    }

    private RunItem(String id, String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, Instant now) {
        super(id, now);
        this.windowKey = windowKey;
        this.repo = repo;
        this.iterationKey = iterationKey;
        this.plannedOrder = plannedOrder;
        this.executedOrder = 0;
        this.finalResult = null;
    }

    public static RunItem create(String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, Instant now) {
        String id = windowKey + "::" + repo.value() + "::" + iterationKey.value();
        return new RunItem(id, windowKey, repo, iterationKey, plannedOrder, now);
    }

    public static RunItem rehydrate(String id, String windowKey, RepoId repo, IterationKey iterationKey, int plannedOrder, int executedOrder, RunItemResult finalResult, List<RunStep> steps, Instant createdAt, Instant updatedAt, long version) {
        return new RunItem(id, windowKey, repo, iterationKey, plannedOrder, executedOrder, finalResult, steps, createdAt, updatedAt, version);
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

    public RunItemResult getFinalResult() {
        return finalResult;
    }

    public void finishWith(RunItemResult result, Instant now) {
        this.finalResult = result;
        touch(now);
    }
}
