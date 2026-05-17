package io.releasehub.application.export;

import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunStep;

import java.util.List;
import java.util.Map;

public record ReleaseWindowReportView(
        String windowId,
        String windowKey,
        String name,
        String status,
        String groupCode,
        String plannedReleaseAt,
        String publishedAt,
        long runCount,
        long itemCount,
        long stepCount,
        Map<String, Long> resultCounts,
        List<RunReport> runs
) {
    public ReleaseWindowReportView {
        resultCounts = Map.copyOf(resultCounts);
        runs = List.copyOf(runs);
    }

    public static ReleaseWindowReportView from(ReleaseWindow window, List<Run> runs) {
        List<RunReport> runReports = runs.stream()
                .map(run -> RunReport.from(run, window.getWindowKey()))
                .toList();
        long itemCount = runReports.stream().mapToLong(run -> run.items().size()).sum();
        long stepCount = runReports.stream()
                .flatMap(run -> run.items().stream())
                .mapToLong(item -> item.steps().size())
                .sum();
        Map<String, Long> resultCounts = runReports.stream()
                .flatMap(run -> run.items().stream())
                .filter(item -> item.finalResult() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        ItemReport::finalResult,
                        java.util.stream.Collectors.counting()));

        return new ReleaseWindowReportView(
                window.getId().value(),
                window.getWindowKey(),
                window.getName(),
                window.getStatus().name(),
                window.getGroupCode(),
                window.getPlannedReleaseAt() == null ? null : window.getPlannedReleaseAt().toString(),
                window.getPublishedAt() == null ? null : window.getPublishedAt().toString(),
                runReports.size(),
                itemCount,
                stepCount,
                resultCounts,
                runReports
        );
    }

    public record RunReport(
            String runId,
            String runType,
            String status,
            String operator,
            String startedAt,
            String finishedAt,
            List<ItemReport> items
    ) {
        public RunReport {
            items = List.copyOf(items);
        }

        static RunReport from(Run run, String windowKey) {
            List<ItemReport> itemReports = run.getItems().stream()
                    .filter(item -> windowKey.equals(item.getWindowKey()))
                    .map(ItemReport::from)
                    .toList();
            return new RunReport(
                    run.getId().value(),
                    run.getRunType().name(),
                    determineStatus(itemReports, run.getFinishedAt() != null),
                    run.getOperator(),
                    run.getStartedAt() == null ? null : run.getStartedAt().toString(),
                    run.getFinishedAt() == null ? null : run.getFinishedAt().toString(),
                    itemReports
            );
        }

        private static String determineStatus(List<ItemReport> items, boolean finished) {
            if (items.isEmpty()) {
                return finished ? "COMPLETED" : "RUNNING";
            }
            boolean hasFailed = items.stream()
                    .anyMatch(item -> item.finalResult() != null
                            && (item.finalResult().contains("FAILED")
                            || item.finalResult().equals("MERGE_BLOCKED")));
            if (hasFailed) {
                return "FAILED";
            }
            boolean allSuccess = items.stream()
                    .allMatch(item -> item.finalResult() != null
                            && item.finalResult().contains("SUCCESS"));
            if (allSuccess) {
                return "SUCCESS";
            }
            return finished ? "COMPLETED" : "RUNNING";
        }
    }

    public record ItemReport(
            String windowKey,
            String repo,
            String iterationKey,
            int plannedOrder,
            int executedOrder,
            String finalResult,
            List<StepReport> steps
    ) {
        public ItemReport {
            steps = List.copyOf(steps);
        }

        static ItemReport from(RunItem item) {
            return new ItemReport(
                    item.getWindowKey(),
                    item.getRepo().value(),
                    item.getIterationKey() == null ? null : item.getIterationKey().value(),
                    item.getPlannedOrder(),
                    item.getExecutedOrder(),
                    item.getFinalResult() == null ? null : item.getFinalResult().name(),
                    item.getSteps().stream().map(StepReport::from).toList()
            );
        }
    }

    public record StepReport(
            String actionType,
            String result,
            String startAt,
            String endAt,
            String message
    ) {
        static StepReport from(RunStep step) {
            return new StepReport(
                    step.actionType().name(),
                    step.result().name(),
                    step.startAt() == null ? null : step.startAt().toString(),
                    step.endAt() == null ? null : step.endAt().toString(),
                    step.message()
            );
        }
    }
}
