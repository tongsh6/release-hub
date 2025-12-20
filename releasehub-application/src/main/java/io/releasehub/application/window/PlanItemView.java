package io.releasehub.application.window;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlanItemView {
    private String windowKey;
    private String repoId;
    private String iterationKey;
    private int plannedOrder;
    private Integer lastExecutedOrder;
}
