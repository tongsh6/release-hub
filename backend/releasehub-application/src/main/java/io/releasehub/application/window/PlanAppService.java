package io.releasehub.application.window;

import io.releasehub.application.run.plan.RunPlanAppService;
import io.releasehub.domain.run.RunPlanSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanAppService {
    private final RunPlanAppService runPlanAppService;

    public List<PlanItemView> getPlanByWindow(String windowId) {
        return runPlanAppService.previewWindow(windowId);
    }

    public RunPlanSnapshot getSnapshotByWindow(String windowId) {
        return runPlanAppService.previewSnapshot(windowId);
    }
}
