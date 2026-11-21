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
    private String operator;
    private int executionVersion;
    private String status;
    private long startedAt;
    private Long finishedAt;
    private List<Item> items;

    @Data
    public static class Item {
        private String id;
        private String windowKey;
        private String repo;
        private String iterationKey;
        private int plannedOrder;
        private int executedOrder;
        private String finalResult;
        private java.util.Map<String, String> metadata = java.util.Map.of();
        private List<Step> steps;

        public java.util.Map<String, String> getMetadata() { return java.util.Map.copyOf(metadata); }
        public void setMetadata(java.util.Map<String, String> metadata) {
            this.metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
        }
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
        v.setOperator(run.getOperator());
        v.setExecutionVersion(run.getExecutionVersion());
        v.setStatus(run.getExecutionStatus().name());
        v.setStartedAt(run.getStartedAt().toEpochMilli());
        v.setFinishedAt(run.getFinishedAt() == null ? null : run.getFinishedAt().toEpochMilli());
        v.setItems(run.getItems().stream().map(RunJsonView::mapItem).toList());
        return v;
    }

    private static Item mapItem(RunItem item) {
        Item i = new Item();
        i.setId(item.getId().value());
        i.setMetadata(item.getMetadata());
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
