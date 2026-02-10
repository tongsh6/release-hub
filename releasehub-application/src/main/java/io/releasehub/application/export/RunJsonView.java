package io.releasehub.application.export;

import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunStep;
import lombok.Data;

import java.util.List;

@Data
public class RunJsonView {
    private String runId;
    private String runType;
    private long startedAt;
    private Long finishedAt;
    private List<Item> items;

    @Data
    public static class Item {
        private String windowKey;
        private String repo;
        private String iterationKey;
        private int plannedOrder;
        private int executedOrder;
        private String finalResult;
        private List<Step> steps;
    }

    @Data
    public static class Step {
        private String actionType;
        private String result;
        private long startAt;
        private long endAt;
        private String message;
    }

    public static RunJsonView from(Run run) {
        RunJsonView v = new RunJsonView();
        v.setRunId(run.getId().value());
        v.setRunType(run.getRunType().name());
        v.setStartedAt(run.getStartedAt().toEpochMilli());
        v.setFinishedAt(run.getFinishedAt() == null ? null : run.getFinishedAt().toEpochMilli());
        v.setItems(run.getItems().stream().map(RunJsonView::mapItem).toList());
        return v;
    }

    private static Item mapItem(RunItem item) {
        Item i = new Item();
        i.setWindowKey(item.getWindowKey());
        i.setRepo(item.getRepo().value());
        i.setIterationKey(item.getIterationKey() == null ? null : item.getIterationKey().value());
        i.setPlannedOrder(item.getPlannedOrder());
        i.setExecutedOrder(item.getExecutedOrder());
        i.setFinalResult(item.getFinalResult() == null ? null : item.getFinalResult().name());
        i.setSteps(item.getSteps().stream().map(RunJsonView::mapStep).toList());
        return i;
    }

    private static Step mapStep(RunStep step) {
        Step s = new Step();
        s.setActionType(step.actionType().name());
        s.setResult(step.result().name());
        s.setStartAt(step.startAt().toEpochMilli());
        s.setEndAt(step.endAt().toEpochMilli());
        s.setMessage(step.message());
        return s;
    }
}
