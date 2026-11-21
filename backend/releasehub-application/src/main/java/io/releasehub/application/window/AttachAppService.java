package io.releasehub.application.window;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 窗口范围配置；分支、合并和执行记录由显式编排负责。 */
@Service
@RequiredArgsConstructor
public class AttachAppService {
    private final ReleaseWindowPort releaseWindowPort;
    private final IterationPort iterationPort;
    private final WindowIterationPort windowIterationPort;
    private final Clock clock;

    @Transactional
    public List<WindowIteration> attach(String windowId, List<String> iterationKeys) {
        ReleaseWindow releaseWindow = findWindow(windowId);
        ensureWindowCanChangeIterations(releaseWindow);
        List<Iteration> iterations = iterationKeys.stream()
                .map(IterationKey::of)
                .distinct()
                .map(key -> iterationPort.findByKey(key)
                        .orElseThrow(() -> NotFoundException.iteration(key.value())))
                .toList();
        iterations.forEach(iteration -> ensureIterationInWindowGroup(releaseWindow, iteration));

        Instant now = clock.instant();
        return iterations.stream()
                .map(iteration -> windowIterationPort
                        .findByWindowIdAndIterationKey(releaseWindow.getId(), iteration.getId())
                        .orElseGet(() -> windowIterationPort.attach(releaseWindow.getId(), iteration.getId(), now)))
                .toList();
    }

    @Transactional
    public void detach(String windowId, String iterationKey) {
        ReleaseWindow releaseWindow = findWindow(windowId);
        ensureWindowCanChangeIterations(releaseWindow);
        windowIterationPort.detach(releaseWindow.getId(), IterationKey.of(iterationKey));
    }

    @Transactional(readOnly = true)
    public List<WindowIteration> list(String windowId) {
        ReleaseWindow releaseWindow = findWindow(windowId);
        return windowIterationPort.listByWindow(releaseWindow.getId());
    }

    @Transactional(readOnly = true)
    public PageResult<WindowIteration> listPaged(String windowId, int page, int size) {
        ReleaseWindow releaseWindow = findWindow(windowId);
        return windowIterationPort.listByWindowPaged(releaseWindow.getId(), page, size);
    }

    private ReleaseWindow findWindow(String windowId) {
        return releaseWindowPort.findById(ReleaseWindowId.of(windowId))
                .orElseThrow(() -> NotFoundException.releaseWindow(windowId));
    }

    private void ensureWindowCanChangeIterations(ReleaseWindow releaseWindow) {
        if (releaseWindow.isFrozen()) {
            throw BusinessException.rwAlreadyFrozen();
        }
        if (releaseWindow.getStatus() != ReleaseWindowStatus.DRAFT) {
            throw BusinessException.rwInvalidState(releaseWindow.getStatus());
        }
    }

    private void ensureIterationInWindowGroup(ReleaseWindow releaseWindow, Iteration iteration) {
        if (!releaseWindow.getGroupCode().equals(iteration.getGroupCode())) {
            throw BusinessException.rwIterationGroupMismatch(
                    iteration.getId().value(),
                    releaseWindow.getGroupCode(),
                    iteration.getGroupCode());
        }
    }
}
