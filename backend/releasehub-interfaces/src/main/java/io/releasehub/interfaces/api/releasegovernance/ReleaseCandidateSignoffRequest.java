package io.releasehub.interfaces.api.releasegovernance;

import io.releasehub.application.releasegovernance.ReleaseCandidateSignoffCommand;
import io.releasehub.application.releasegovernance.ReleaseCandidateSignoffPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReleaseCandidateSignoffRequest {
    @NotBlank
    private String candidateId;

    private String reviewer;

    @NotBlank
    private String decision;

    private String note;

    @Valid
    @NotEmpty
    private List<ChecklistDecisionRequest> checklist;

    ReleaseCandidateSignoffCommand toCommand(String effectiveReviewer) {
        return new ReleaseCandidateSignoffCommand(
                candidateId,
                effectiveReviewer,
                decision,
                note,
                checklist.stream().map(ChecklistDecisionRequest::toCommand).toList()
        );
    }

    @Getter
    @Setter
    public static class ChecklistDecisionRequest {
        @NotBlank
        private String key;

        @NotBlank
        private String status;

        private String note;

        ReleaseCandidateSignoffPort.ChecklistDecision toCommand() {
            return new ReleaseCandidateSignoffPort.ChecklistDecision(key, status, note);
        }
    }
}
