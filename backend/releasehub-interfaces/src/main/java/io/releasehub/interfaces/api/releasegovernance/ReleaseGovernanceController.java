package io.releasehub.interfaces.api.releasegovernance;

import io.releasehub.application.releasegovernance.ReleaseCandidateReviewSummary;
import io.releasehub.application.releasegovernance.ReleaseCandidateSignoffView;
import io.releasehub.application.releasegovernance.ReleaseGovernanceAppService;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.interfaces.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/release-governance")
@RequiredArgsConstructor
@Tag(name = "发布治理")
public class ReleaseGovernanceController {
    private final ReleaseGovernanceAppService appService;

    @GetMapping("/candidate-review")
    @Operation(summary = "获取发布候选评审摘要")
    public ApiResponse<ReleaseCandidateReviewSummary> getCandidateReview() {
        return ApiResponse.success(appService.getCandidateReview());
    }

    @PostMapping("/candidate-review/signoffs")
    @Operation(summary = "记录发布候选人工签核")
    public ApiResponse<ReleaseCandidateSignoffView> signoff(@RequestBody @Valid ReleaseCandidateSignoffRequest request) {
        String reviewer = request.getReviewer() == null || request.getReviewer().isBlank()
                ? SecurityUtils.getCurrentUsername()
                : request.getReviewer();
        return ApiResponse.success(appService.signoff(request.toCommand(reviewer)));
    }
}
