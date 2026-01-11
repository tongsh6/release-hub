package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.application.window.DryPlanAppService;
import io.releasehub.application.window.DryPlanItemView;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
@Tag(name = "发布窗口 - 干运行计划")
public class DryPlanController {
    private final DryPlanAppService dryPlanAppService;

    @GetMapping("/{id}/dry-plan")
    @Operation(summary = "Get dry-run plan by window")
    public ApiResponse<List<DryPlanItemView>> dryPlan(@PathVariable("id") String windowId) {
        return ApiResponse.success(dryPlanAppService.dryPlanByWindow(windowId));
    }
}
