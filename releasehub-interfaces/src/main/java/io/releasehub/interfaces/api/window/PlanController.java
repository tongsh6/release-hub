package io.releasehub.interfaces.api.window;

import io.releasehub.application.window.PlanAppService;
import io.releasehub.application.window.PlanItemView;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/windows")
@RequiredArgsConstructor
@Tag(name = "Windows")
public class PlanController {
    private final PlanAppService planAppService;

    @GetMapping("/{id}/plan")
    @Operation(summary = "Get execution plan by window")
    public ApiResponse<List<PlanItemView>> getPlan(@PathVariable("id") String windowId) {
        return ApiResponse.success(planAppService.getPlanByWindow(windowId));
    }
}
