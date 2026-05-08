package io.releasehub.application.releasewindow;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.run.RunAppService;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 发布窗口生命周期监听器，在关键状态变更的事务提交后触发后置流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WindowLifecycleListener {

    private final WindowIterationPort windowIterationPort;
    private final IterationPort iterationPort;
    private final RunAppService runAppService;

    /**
     * 发布提交后自动触发编排，确保所有关联迭代的分支操作已完成。
     * 使用 AFTER_COMMIT 确保发布状态已落库，REQUIRES_NEW 确保编排在独立事务中执行。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWindowPublished(WindowPublishedEvent event) {
        String windowId = event.getWindowId();
        try {
            List<WindowIteration> iterations = windowIterationPort.listByWindow(ReleaseWindowId.of(windowId));
            List<String> iterationKeys = iterations.stream()
                    .map(wi -> wi.getIterationKey().value())
                    .distinct()
                    .toList();
            List<String> repoIds = iterations.stream()
                    .flatMap(wi -> {
                        Iteration it = iterationPort.findByKey(wi.getIterationKey()).orElse(null);
                        return it != null ? it.getRepos().stream().map(RepoId::value) : java.util.stream.Stream.empty();
                    })
                    .distinct()
                    .toList();

            if (!repoIds.isEmpty()) {
                var run = runAppService.startOrchestrate(windowId, repoIds, iterationKeys, true, "system");
                log.info("Auto-orchestration run {} started for published window {}", run.getId().value(), windowId);
            }
        } catch (Exception e) {
            log.warn("Auto-orchestration failed for published window {}: {}", windowId, e.getMessage());
        }
    }
}
