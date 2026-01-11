package io.releasehub.application.window;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanAppService {
    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationPort iterationPort;

    public List<PlanItemView> getPlanByWindow(String windowId) {
        ReleaseWindow rw = releaseWindowPort.findById(new ReleaseWindowId(windowId)).orElseThrow();
        List<WindowIteration> bindings = windowIterationPort.listByWindow(new ReleaseWindowId(windowId));
        bindings.sort(Comparator.comparing(WindowIteration::getAttachAt));
        Map<IterationKey, Integer> order = computePlannedOrder(bindings);

        List<PlanItemView> views = new ArrayList<>();
        for (WindowIteration wi : bindings) {
            Iteration iteration = iterationPort.findByKey(wi.getIterationKey()).orElseThrow();
            for (RepoId repoId : iteration.getRepos()) {
                views.add(new PlanItemView(rw.getWindowKey(), repoId.value(), wi.getIterationKey().value(), order.get(wi.getIterationKey()), null));
            }
        }
        return views;
    }

    private Map<IterationKey, Integer> computePlannedOrder(List<WindowIteration> bindings) {
        return bindings.stream()
                .sorted(Comparator.comparing(WindowIteration::getAttachAt))
                .map(WindowIteration::getIterationKey)
                .distinct()
                .collect(Collectors.toMap(k -> k, k -> {
                    int idx = 1;
                    for (IterationKey key : bindings.stream().map(WindowIteration::getIterationKey).distinct().toList()) {
                        if (key.equals(k)) return idx;
                        idx++;
                    }
                    return idx;
                }));
    }
}
