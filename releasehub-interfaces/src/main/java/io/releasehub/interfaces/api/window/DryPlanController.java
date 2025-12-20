package io.releasehub.interfaces.api.window;

import io.releasehub.application.window.DryPlanAppService;
import io.releasehub.application.window.DryPlanItemView;
import io.releasehub.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/windows")
@RequiredArgsConstructor
public class DryPlanController {
    private final DryPlanAppService dryPlanAppService;

    @GetMapping("/{id}/dry-plan")
    public ApiResponse<List<DryPlanItemView>> dryPlan(@PathVariable("id") String windowId) {
        return ApiResponse.success(dryPlanAppService.dryPlanByWindow(windowId));
    }
}
