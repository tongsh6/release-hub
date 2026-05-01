package io.releasehub.application.window;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.window.WindowIteration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WindowIterationPort {
    WindowIteration attach(ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt);

    void detach(ReleaseWindowId windowId, IterationKey iterationKey);

    List<WindowIteration> listByWindow(ReleaseWindowId windowId);

    PageResult<WindowIteration> listByWindowPaged(ReleaseWindowId windowId, int page, int size);

    // 新增方法
    Optional<WindowIteration> findByWindowIdAndIterationKey(ReleaseWindowId windowId, IterationKey iterationKey);

    void updateReleaseBranch(String windowId, String iterationKey, String releaseBranch, Instant now);

    void updateLastMergeAt(String windowId, String iterationKey, Instant lastMergeAt);

    String getReleaseBranch(String windowId, String iterationKey);
}
