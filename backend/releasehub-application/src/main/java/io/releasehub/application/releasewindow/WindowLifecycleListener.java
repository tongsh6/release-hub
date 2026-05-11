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
     * 发布提交后自动触发编排。
     *
     * <p>注意：本方法<b>不</b>声明 {@code @Transactional}。
     * 历史曾使用 {@code @Transactional(REQUIRES_NEW)}，但当 {@link RunAppService#startOrchestrate} 内部
     * 抛出 BusinessException（例如 GitLab Settings 缺失）时，REQUIRES_NEW 事务被标 rollback-only，
     * 即便 try-catch 吞掉异常，listener 返回时事务 commit 仍会触发 UnexpectedRollbackException
     * 污染日志。{@code AFTER_COMMIT} 阶段已脱离原事务，{@code startOrchestrate} 自身也声明了 @Transactional，
     * 因此 listener 不需要再开事务。
     */
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
