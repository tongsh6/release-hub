package io.releasehub.interfaces.api.run;

import io.releasehub.application.run.RunAppService;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
@Tag(name = "运行记录 - 重试管理")
public class RunRetryController {
    private final RunAppService runAppService;

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry run with selected items")
    public ApiResponse<String> retry(@PathVariable("id") String runId, @RequestBody RetryRequest request) {
        var run = runAppService.retry(runId, request.getItems(), request.getOperator());
        return ApiResponse.success(run.getId());
    }

    @Data
    public static class RetryRequest {
        private List<String> items;
        private String operator;
    }
}
