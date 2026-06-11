package io.releasehub.interfaces.api.dataquality;

import io.releasehub.application.dataquality.DispositionCaseTransitionCommand;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispositionCaseTransitionRequest {
    @NotBlank
    private String operator;

    private String preStateSnapshot;
    private String postStateSnapshot;
    private String failureReason;
    private String rollbackNote;

    DispositionCaseTransitionCommand toCommand() {
        return new DispositionCaseTransitionCommand(
                operator,
                preStateSnapshot,
                postStateSnapshot,
                failureReason,
                rollbackNote);
    }
}
