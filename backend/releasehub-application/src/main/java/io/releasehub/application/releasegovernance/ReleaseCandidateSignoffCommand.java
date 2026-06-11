package io.releasehub.application.releasegovernance;

import java.util.List;

public record ReleaseCandidateSignoffCommand(
        String candidateId,
        String reviewer,
        String decision,
        String note,
        List<ReleaseCandidateSignoffPort.ChecklistDecision> checklist
) {
    public ReleaseCandidateSignoffCommand {
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
    }
}
