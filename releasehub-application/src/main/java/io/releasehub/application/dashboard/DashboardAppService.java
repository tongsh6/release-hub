package io.releasehub.application.dashboard;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.run.Run;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Dashboard 应用服务
 * <p>
 * 提供仪表盘所需的统计数据
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardAppService {

    private final ReleaseWindowPort releaseWindowPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final IterationPort iterationPort;
    private final RunPort runPort;

    /**
     * 获取仪表盘统计数据
     */
    public DashboardStats getStats() {
        long totalRepositories = codeRepositoryPort.findAll().size();
        long totalIterations = iterationPort.findAll().size();
        long activeWindows = countActiveWindows();
        long totalRuns = runPort.findAll().size();
        long recentRuns = countRecentRuns();

        return new DashboardStats(
                totalRepositories,
                totalIterations,
                activeWindows,
                totalRuns,
                recentRuns
        );
    }

    private long countActiveWindows() {
        List<ReleaseWindow> all = releaseWindowPort.findAll();
        return all.stream()
                .filter(w -> w.getStatus() == ReleaseWindowStatus.DRAFT || w.getStatus() == ReleaseWindowStatus.PUBLISHED)
                .count();
    }

    private long countRecentRuns() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Run> runs = runPort.findAll();
        return runs.stream()
                .filter(r -> r.getCreatedAt().isAfter(sevenDaysAgo))
                .count();
    }

    /**
     * Dashboard 统计数据 DTO
     */
    public record DashboardStats(
            long totalRepositories,
            long totalIterations,
            long activeWindows,
            long totalRuns,
            long recentRuns
    ) {}
}
