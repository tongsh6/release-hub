package io.releasehub.infrastructure.persistence.window;

import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.window.WindowIteration;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class WindowIterationPersistenceAdapter implements WindowIterationPort {
    private final Map<ReleaseWindowId, List<WindowIteration>> store = new ConcurrentHashMap<>();

    @Override
    public WindowIteration attach(ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt) {
        List<WindowIteration> list = store.computeIfAbsent(windowId, k -> new ArrayList<>());
        WindowIteration wi = WindowIteration.attach(windowId, iterationKey, attachAt, Instant.now());
        list.removeIf(x -> x.getIterationKey().equals(iterationKey));
        list.add(wi);
        return wi;
    }

    @Override
    public void detach(ReleaseWindowId windowId, IterationKey iterationKey) {
        List<WindowIteration> list = store.computeIfAbsent(windowId, k -> new ArrayList<>());
        list.removeIf(x -> x.getIterationKey().equals(iterationKey));
    }

    @Override
    public List<WindowIteration> listByWindow(ReleaseWindowId windowId) {
        return new ArrayList<>(store.getOrDefault(windowId, List.of()));
    }
}
