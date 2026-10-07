package io.releasehub.interfaces.api.run;

import io.releasehub.application.run.plan.RunPlanAppService;
import io.releasehub.application.run.plan.RunPlanSummary;
import io.releasehub.application.run.plan.SavedRunPlan;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.run.RunPlanSnapshot;
import io.releasehub.interfaces.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RunPlanController {
    private final RunPlanAppService service;

    @PostMapping("/release-windows/{id}/run-plans")
    @Operation(summary = "保存不可执行的本地计划预览")
    public ApiResponse<PlanView> create(@PathVariable("id") String windowId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody(required = false) CreateRequest request) {
        if (request == null) throw ValidationException.invalidParameter("request");
        return ApiResponse.success(PlanView.from(service.create(windowId, request.repoIds(), request.iterationKeys(),
                request.failFast() == null || request.failFast(), SecurityUtils.getCurrentUsername(), key)));
    }

    @GetMapping("/release-windows/{id}/run-plans")
    @Operation(summary = "查询窗口最近 20 条已保存预览")
    public ApiResponse<List<RunPlanSummary>> list(@PathVariable("id") String windowId) {
        return ApiResponse.success(service.list(windowId));
    }

    @GetMapping("/runs/{id}/plan")
    @Operation(summary = "按 Run ID 查询不可变计划预览")
    public ApiResponse<PlanView> get(@PathVariable("id") String runId) {
        return ApiResponse.success(PlanView.from(service.get(runId)));
    }

    public record CreateRequest(List<String> repoIds, List<String> iterationKeys, Boolean failFast) {
        public CreateRequest {
            repoIds = copy(repoIds);
            iterationKeys = copy(iterationKeys);
        }
        @Override public List<String> repoIds() { return copy(repoIds); }
        @Override public List<String> iterationKeys() { return copy(iterationKeys); }
        private static List<String> copy(List<String> source) {
            // 保留非法空元素供应用校验返回 400，同时隔离调用方对集合的修改。
            return source == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(source));
        }
    }
    public record PlanView(String runId, String windowId, String operator, Instant createdAt, String planHash,
                           int executionVersion, String status, String evidenceLevel, boolean executionPermitted,
                           RunPlanSnapshot snapshot) {
        static PlanView from(SavedRunPlan plan) {
            return new PlanView(plan.runId(), plan.windowId(), plan.operator(), plan.createdAt(), plan.planHash(),
                    2, "PLANNED", "LOCAL_ONLY", false, plan.snapshot());
        }
    }
}
