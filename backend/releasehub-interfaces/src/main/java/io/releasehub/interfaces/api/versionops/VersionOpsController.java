package io.releasehub.interfaces.api.versionops;

import io.releasehub.application.run.RunPort;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunStep;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/version-ops")
@RequiredArgsConstructor
@Tag(name = "版本运维 - Version Ops")
public class VersionOpsController {

    private final RunPort runPort;

    @GetMapping("/runs/paged")
    @Operation(summary = "分页查询版本运维运行记录")
    public ApiPageResponse<List<RunSummaryView>> listRuns(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "repoUrl", required = false) String repoUrl,
            @RequestParam(name = "branchName", required = false) String branchName) {

        var result = runPort.findPaged("VERSION_UPDATE", null, null, null, null, status, null, page, size);
        List<RunSummaryView> views = result.items().stream()
                .map(RunSummaryView::from)
                .toList();
        return ApiPageResponse.success(views, new PageMeta(page, size, result.total()));
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "获取版本运维运行记录详情")
    public ApiResponse<RunDetailView> getRunDetail(@PathVariable String runId) {
        return runPort.findById(runId)
                .map(run -> ApiResponse.success(RunDetailView.from(run)))
                .orElse(ApiResponse.success(null));
    }

    @GetMapping("/runs/{runId}/logs")
    @Operation(summary = "获取运行日志行")
    public ApiResponse<RunLogsView> getRunLogs(@PathVariable String runId) {
        return runPort.findById(runId)
                .map(run -> {
                    List<String> lines = new ArrayList<>();
                    lines.add("[INFO] Run: " + run.getId().value());
                    lines.add("[INFO] Type: " + run.getRunType());
                    lines.add("[INFO] Operator: " + run.getOperator());
                    lines.add("[INFO] Started: " + run.getStartedAt());
                    for (RunItem item : run.getItems()) {
                        lines.add("[INFO] --- " + item.getWindowKey() +
                                " | Repo: " + item.getRepo().value() +
                                " | Iteration: " + item.getIterationKey().value());
                        for (RunStep step : item.getSteps()) {
                            String level = step.result().name().contains("FAILED") ||
                                    step.result().name().contains("BLOCKED") ? "ERROR" : "INFO";
                            lines.add("[" + level + "] " + step.actionType() +
                                    ": " + step.message());
                        }
                    }
                    lines.add("[INFO] Finished: " + run.getFinishedAt());
                    return ApiResponse.success(new RunLogsView(runId, lines));
                })
                .orElse(ApiResponse.success(new RunLogsView(runId, List.of())));
    }

    // --- Views ---

    public record RunSummaryView(
            String runId,
            String type,
            String status,
            String repoUrl,
            String branchName,
            String startedAt,
            String finishedAt
    ) {
        public static RunSummaryView from(Run run) {
            return new RunSummaryView(
                    run.getId().value(),
                    run.getRunType().name(),
                    determineStatus(run),
                    null,
                    null,
                    run.getStartedAt() != null ? run.getStartedAt().toString() : null,
                    run.getFinishedAt() != null ? run.getFinishedAt().toString() : null
            );
        }

        private static String determineStatus(Run run) {
            if (run.getItems().isEmpty()) {
                return run.getFinishedAt() != null ? "SUCCEEDED" : "RUNNING";
            }
            boolean hasFailed = run.getItems().stream()
                    .anyMatch(item -> item.getFinalResult() != null &&
                            (item.getFinalResult().name().contains("FAILED") ||
                             item.getFinalResult().name().equals("MERGE_BLOCKED")));
            if (hasFailed) return "FAILED";
            boolean allSuccess = run.getItems().stream()
                    .allMatch(item -> item.getFinalResult() != null &&
                            item.getFinalResult().name().contains("SUCCESS"));
            if (allSuccess) return "SUCCEEDED";
            return run.getFinishedAt() != null ? "SUCCEEDED" : "RUNNING";
        }
    }

    public record RunDetailView(
            String runId,
            String type,
            String status,
            String branchName,
            String startedAt,
            String finishedAt,
            List<ItemView> items
    ) {
        public RunDetailView {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public List<ItemView> items() {
            return List.copyOf(items);
        }

        public static RunDetailView from(Run run) {
            return new RunDetailView(
                    run.getId().value(),
                    run.getRunType().name(),
                    RunSummaryView.determineStatus(run),
                    null,
                    run.getStartedAt() != null ? run.getStartedAt().toString() : null,
                    run.getFinishedAt() != null ? run.getFinishedAt().toString() : null,
                    run.getItems().stream().map(ItemView::from).toList()
            );
        }
    }

    public record ItemView(
            String windowKey,
            String repoId,
            String iterationKey,
            String result,
            List<StepView> steps
    ) {
        public ItemView {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }

        public List<StepView> steps() {
            return List.copyOf(steps);
        }

        public static ItemView from(RunItem item) {
            return new ItemView(
                    item.getWindowKey(),
                    item.getRepo().value(),
                    item.getIterationKey().value(),
                    item.getFinalResult() != null ? item.getFinalResult().name() : null,
                    item.getSteps().stream().map(StepView::from).toList()
            );
        }
    }

    public record StepView(
            String actionType,
            String result,
            String message,
            String startAt,
            String endAt
    ) {
        public static StepView from(RunStep step) {
            return new StepView(
                    step.actionType().name(),
                    step.result().name(),
                    step.message(),
                    step.startAt().toString(),
                    step.endAt().toString()
            );
        }
    }

    public record RunLogsView(
            String runId,
            List<String> lines
    ) {
        public RunLogsView {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public List<String> lines() {
            return List.copyOf(lines);
        }
    }
}
