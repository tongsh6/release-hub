package io.releasehub.interfaces.api.run;

import io.releasehub.application.release.ReleaseRunService;
import io.releasehub.application.run.RunTaskView;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.domain.run.RunTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "运行任务", description = "运行任务相关接口")
@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunTaskController {

    private final ReleaseRunService releaseRunService;

    @Operation(summary = "获取运行任务列表")
    @GetMapping("/{runId}/tasks")
    public ApiResponse<List<RunTaskView>> getTasks(@PathVariable String runId) {
        List<RunTask> tasks = releaseRunService.getRunTasks(runId);
        List<RunTaskView> views = tasks.stream()
                                       .map(RunTaskView::from)
                                       .collect(Collectors.toList());
        return ApiResponse.success(views);
    }

    @Operation(summary = "重试失败的任务")
    @PostMapping("/{runId}/tasks/{taskId}/retry")
    public ApiResponse<RunTaskView> retryTask(
            @PathVariable String runId,
            @PathVariable String taskId) {
        RunTask task = releaseRunService.retryTask(taskId);
        return ApiResponse.success(RunTaskView.from(task));
    }
}
