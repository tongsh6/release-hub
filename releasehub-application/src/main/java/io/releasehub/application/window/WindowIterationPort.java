package io.releasehub.application.window;

import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.window.WindowIteration;

import java.time.Instant;
import java.util.List;

public interface WindowIterationPort {
    WindowIteration attach(ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt);
    void detach(ReleaseWindowId windowId, IterationKey iterationKey);
    List<WindowIteration> listByWindow(ReleaseWindowId windowId);
}
