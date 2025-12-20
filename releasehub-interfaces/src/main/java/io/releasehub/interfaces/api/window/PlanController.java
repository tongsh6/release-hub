package io.releasehub.interfaces.api.window;

import io.releasehub.application.window.PlanAppService;
import io.releasehub.application.window.PlanItemView;
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
public class PlanController {
    private final PlanAppService planAppService;

    @GetMapping("/{id}/plan")
    public ApiResponse<List<PlanItemView>> getPlan(@PathVariable("id") String windowId) {
        return ApiResponse.success(planAppService.getPlanByWindow(windowId));
    }
}
