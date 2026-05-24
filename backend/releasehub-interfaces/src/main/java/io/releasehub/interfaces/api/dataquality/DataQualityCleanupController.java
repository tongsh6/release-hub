package io.releasehub.interfaces.api.dataquality;

import io.releasehub.application.dataquality.CleanupReviewResult;
import io.releasehub.application.dataquality.BranchCreationModeMigrationDryRunAppService;
import io.releasehub.application.dataquality.BranchCreationModeMigrationDryRunResult;
import io.releasehub.application.dataquality.DataQualityCleanupReviewAppService;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-quality")
@RequiredArgsConstructor
@Tag(name = "数据质量 - 存量清理复核")
public class DataQualityCleanupController {
    private final DataQualityCleanupReviewAppService reviewAppService;
    private final BranchCreationModeMigrationDryRunAppService branchModeDryRunAppService;

    @PostMapping("/cleanup-review")
    @Operation(summary = "Review SA-002 cleanup actions before application-level handling")
    public ApiResponse<CleanupReviewResult> review(@RequestBody @Valid CleanupReviewRequest request) {
        return ApiResponse.success(reviewAppService.review(request.toCommand()));
    }

    @PostMapping("/branch-creation-mode-migrations/dry-run")
    @Operation(summary = "Run read-only BranchCreationMode migration dry-run")
    public ApiResponse<BranchCreationModeMigrationDryRunResult> branchCreationModeDryRun(
            @RequestBody @Valid BranchCreationModeMigrationDryRunRequest request) {
        return ApiResponse.success(branchModeDryRunAppService.dryRun(request.toCommand()));
    }
}
