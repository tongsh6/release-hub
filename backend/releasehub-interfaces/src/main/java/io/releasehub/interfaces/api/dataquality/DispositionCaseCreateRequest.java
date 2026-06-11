package io.releasehub.interfaces.api.dataquality;

import io.releasehub.application.dataquality.CreateDispositionCaseCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispositionCaseCreateRequest {
    @NotBlank
    private String requestedBy;

    private String sourceReport;

    @Valid
    @NotNull
    private DispositionCaseActionRequest action;

    CreateDispositionCaseCommand toCommand() {
        return new CreateDispositionCaseCommand(requestedBy, sourceReport, action.toCommand());
    }
}
