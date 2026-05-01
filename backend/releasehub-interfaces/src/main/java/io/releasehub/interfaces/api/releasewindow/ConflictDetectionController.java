package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.application.conflict.ConflictDetectionAppService;
import io.releasehub.domain.conflict.ConflictItem;
import io.releasehub.domain.conflict.ConflictReport;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/release-windows/{id}/conflicts")
@RequiredArgsConstructor
@Tag(name = "发布窗口 - 冲突检测")
public class ConflictDetectionController {

    private final ConflictDetectionAppService conflictDetectionAppService;

    @PostMapping("/check")
    @Operation(summary = "触发冲突扫描", description = "对发布窗口关联的所有仓库执行四维冲突检测")
    public ApiResponse<ConflictReportView> checkConflicts(@PathVariable("id") String windowId) {
        ConflictReport report = conflictDetectionAppService.checkWindowConflicts(windowId);
        return ApiResponse.success(ConflictReportView.from(report));
    }

    @GetMapping
    @Operation(summary = "获取最新冲突报告", description = "返回最近一次扫描的冲突结果")
    public ApiResponse<ConflictReportView> getConflicts(@PathVariable("id") String windowId) {
        ConflictReport report = conflictDetectionAppService.getLatestReport(windowId)
                .orElseGet(() -> ConflictReport.empty(windowId));
        return ApiResponse.success(ConflictReportView.from(report));
    }

    public record ConflictReportView(
            String windowId,
            String checkedAt,
            boolean hasConflicts,
            int totalCount,
            List<ConflictItemView> conflicts
    ) {
        public static ConflictReportView from(ConflictReport report) {
            List<ConflictItemView> items = report.getConflicts().stream()
                    .map(ConflictItemView::from)
                    .collect(Collectors.toList());
            return new ConflictReportView(
                    report.getWindowId(),
                    report.getCheckedAt().toString(),
                    report.hasConflicts(),
                    report.totalCount(),
                    items
            );
        }
    }

    public record ConflictItemView(
            String repoId,
            String repoName,
            String iterationKey,
            String conflictType,
            String sourceBranch,
            String targetBranch,
            String systemVersion,
            String repoVersion,
            String message,
            String suggestion
    ) {
        public static ConflictItemView from(ConflictItem item) {
            return new ConflictItemView(
                    item.getRepoId(),
                    item.getRepoName(),
                    item.getIterationKey(),
                    item.getConflictType().name(),
                    item.getSourceBranch(),
                    item.getTargetBranch(),
                    item.getSystemVersion(),
                    item.getRepoVersion(),
                    item.getMessage(),
                    item.getSuggestion()
            );
        }
    }
}
