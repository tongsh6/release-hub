package io.releasehub.interfaces.api.dataquality;

import io.releasehub.application.dataquality.CleanupReviewCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CleanupReviewRequest {
    @NotBlank
    private String reviewer;

    private String sourceReport;
    private String resourceTypeFilter;
    private String riskTypeFilter;
    private String reviewStatusFilter;
    private String assetScopeFilter;

    @Valid
    @NotEmpty
    private List<CleanupActionReviewRequest> actions;

    CleanupReviewCommand toCommand() {
        return new CleanupReviewCommand(
                reviewer,
                sourceReport,
                actions.stream().map(CleanupActionReviewRequest::toCommand).toList(),
                resourceTypeFilter,
                riskTypeFilter,
                reviewStatusFilter,
                assetScopeFilter);
    }
}
