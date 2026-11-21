package io.releasehub.infrastructure.run;

import io.releasehub.application.run.RunAppService;
import io.releasehub.application.run.RunPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 数据库只提供候选；每个已开始的 worker 调用唯一执行器领取一个任务。 */
@Slf4j
public final class RunTaskWorker implements AutoCloseable {
    private final RunPort runs;
    private final RunAppService service;
    private final ThreadPoolExecutor executor;
    private final int batchSize;
    private String afterId;
    private volatile boolean closed;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "RunAppService 是共享的无状态 Spring 协作者；worker 仅调用单任务入口，不暴露引用，复制服务会破坏事务装配")
    public RunTaskWorker(RunPort runs, RunAppService service, int parallelism, int queueCapacity, int batchSize) {
        if (parallelism < 1 || parallelism > 32 || queueCapacity < 0 || queueCapacity > 1000
                || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Invalid bounded run worker configuration");
        }
        this.runs = runs;
        this.service = service;
        this.batchSize = batchSize;
        this.executor = new ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.MILLISECONDS,
                queueCapacity == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("releasehub-run-task-", 1).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Scheduled(fixedDelayString = "${releasehub.run.worker.scan-delay-ms:1000}",
            initialDelayString = "${releasehub.run.worker.scan-delay-ms:1000}")
    public void scan() {
        try {
            scanOnce();
        } catch (RuntimeException error) {
            log.warn("Run task candidate scan failed: {}", error.getClass().getSimpleName());
        }
    }

    /** 仅发现并提交有界候选，不在扫描线程领取；返回本轮已提交的命令数。 */
    public synchronized int scanOnce() {
        if (closed) return 0;
        var candidates = runs.findTaskCandidateRunIds(afterId, batchSize);
        if (closed) return 0;
        if (candidates.isEmpty() && afterId != null) {
            afterId = null;
            candidates = runs.findTaskCandidateRunIds(null, batchSize);
        }
        int submitted = 0;
        for (String runId : candidates) {
            if (closed) break;
            try {
                executor.execute(() -> execute(runId));
            } catch (RejectedExecutionException fullOrStopped) {
                // 未领取；保留游标位置，下一轮从未成功投递的候选继续。
                break;
            }
            afterId = runId;
            submitted++;
        }
        return submitted;
    }

    private void execute(String runId) {
        if (closed) return;
        try {
            service.executeNextTask(runId);
        } catch (RuntimeException error) {
            // 保留已经领取的事实，不转回 PENDING，不从内存重试。
            log.warn("Run task execution stopped: run={} error={}", runId, error.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        // 不等待候选查询占用的扫描锁；关闭后提交由线程池拒绝。
        closed = true;
        // 未开始的命令尚未领取；取消队列不会改变持久任务。
        executor.shutdownNow();
    }
}
