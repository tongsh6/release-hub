package io.releasehub.interfaces.api.run;

import io.releasehub.application.run.RunPort;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.run.Run;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
@Tag(name = "运行记录 - Runs")
public class RunController {
    private final RunPort runPort;

    @GetMapping("/{id}")
    @Operation(summary = "Get run")
    public ApiResponse<RunView> get(@PathVariable("id") String id) {
        return ApiResponse.success(runPort.findById(id).map(RunView::from).orElse(null));
    }

    @GetMapping
    @Operation(summary = "List runs")
    public ApiResponse<List<RunView>> list() {
        List<RunView> views = runPort.findAll().stream().map(RunView::from).toList();
        return ApiResponse.success(views);
    }

    @GetMapping("/paged")
    @Operation(summary = "List runs (paged)")
    public ApiPageResponse<List<RunView>> listPaged(@RequestParam(name = "page", defaultValue = "0") int page,
                                                    @RequestParam(name = "size", defaultValue = "20") int size) {
        List<Run> all = runPort.findAll();
        int from = Math.max(page * size, 0);
        int to = Math.min(from + size, all.size());
        List<RunView> slice = (from >= all.size() ? List.<Run>of() : all.subList(from, to))
                .stream().map(RunView::from).toList();
        return ApiPageResponse.success(slice, new PageMeta(page, size, all.size()));
    }

    /**
     * Run 列表视图 DTO
     */
    public record RunView(
            String id,
            String runType,
            String status,
            String startedAt,
            String finishedAt,
            String operator
    ) {
        public static RunView from(Run run) {
            String status = determineStatus(run);
            return new RunView(
                    run.getId().value(),
                    run.getRunType().name(),
                    status,
                    run.getStartedAt() != null ? run.getStartedAt().toString() : null,
                    run.getFinishedAt() != null ? run.getFinishedAt().toString() : null,
                    run.getOperator()
            );
        }

        private static String determineStatus(Run run) {
            if (run.getItems().isEmpty()) {
                return run.getFinishedAt() != null ? "COMPLETED" : "RUNNING";
            }
            boolean hasFailed = run.getItems().stream()
                    .anyMatch(item -> item.getFinalResult() != null &&
                            (item.getFinalResult().name().contains("FAILED") ||
                             item.getFinalResult().name().equals("MERGE_BLOCKED")));
            if (hasFailed) {
                return "FAILED";
            }
            boolean allSuccess = run.getItems().stream()
                    .allMatch(item -> item.getFinalResult() != null &&
                            item.getFinalResult().name().contains("SUCCESS"));
            if (allSuccess) {
                return "SUCCESS";
            }
            return run.getFinishedAt() != null ? "COMPLETED" : "RUNNING";
        }
    }
}
