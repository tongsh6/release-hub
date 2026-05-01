package io.releasehub.interfaces.api.dashboard;

import io.releasehub.application.dashboard.DashboardAppService;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard API Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "仪表盘统计 API")
public class DashboardController {

    private final DashboardAppService dashboardAppService;

    @GetMapping("/stats")
    @Operation(summary = "获取仪表盘统计数据")
    public ApiResponse<DashboardStatsView> getStats() {
        DashboardAppService.DashboardStats stats = dashboardAppService.getStats();
        
        DashboardStatsView view = new DashboardStatsView(
                stats.totalRepositories(),
                stats.totalIterations(),
                stats.activeWindows(),
                stats.totalRuns(),
                stats.recentRuns()
        );
        
        return ApiResponse.success(view);
    }

    /**
     * Dashboard 统计数据视图
     */
    public record DashboardStatsView(
            long totalRepositories,
            long totalIterations,
            long activeWindows,
            long totalRuns,
            long recentRuns
    ) {}
}
