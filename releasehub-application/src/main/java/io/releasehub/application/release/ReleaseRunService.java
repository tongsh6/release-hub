package io.releasehub.application.release;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.run.RunTaskPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunId;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskStatus;
import io.releasehub.domain.run.RunTaskType;
import io.releasehub.domain.run.RunType;
import io.releasehub.domain.run.TargetType;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 发布运行服务
 * 负责创建和管理发布窗口的运行任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseRunService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private final RunPort runPort;
    private final RunTaskPort runTaskPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationAppService iterationAppService;
    private final RunTaskExecutorRegistry executorRegistry;
    private final Clock clock = Clock.systemUTC();

    /**
     * 为发布窗口创建运行任务
     */
    @Transactional
    public Run createReleaseRun(String windowId, String windowKey, String operator) {
        Instant now = Instant.now(clock);

        // 创建 Run
        Run run = Run.start(RunType.VERSION_UPDATE, operator, now);
        runPort.save(run);

        // 获取窗口关联的所有迭代
        List<WindowIteration> iterations = windowIterationPort.listByWindow(ReleaseWindowId.of(windowId));

        if (iterations.isEmpty()) {
            log.warn("No iterations attached to window: {}", windowId);
            return run;
        }

        // 生成任务列表
        List<RunTask> tasks = generateReleaseTasks(run.getId(), windowId, windowKey, iterations, now);

        // 保存所有任务
        for (RunTask task : tasks) {
            runTaskPort.save(task);
        }

        log.info("Created release run {} with {} tasks for window {}", run.getId().value(), tasks.size(), windowKey);

        return run;
    }

    /**
     * 生成发布任务列表
     * 任务顺序：
     * 1. CLOSE_ITERATION - 关闭所有迭代
     * 2. ARCHIVE_FEATURE_BRANCH - 归档所有 feature 分支
     * 3. UPDATE_POM_VERSION - 更新版本号（去除 SNAPSHOT）
     * 4. MERGE_RELEASE_TO_MASTER - 合并到 master
     * 5. CREATE_TAG - 创建标签
     * 6. TRIGGER_CI_BUILD - 触发构建
     */
    private List<RunTask> generateReleaseTasks(RunId runId, String windowId, String windowKey,
                                               List<WindowIteration> iterations, Instant now) {
        List<RunTask> tasks = new ArrayList<>();
        int order = 0;

        // 1. 关闭迭代任务
        for (WindowIteration wi : iterations) {
            tasks.add(RunTask.create(runId, RunTaskType.CLOSE_ITERATION, ++order,
                    TargetType.ITERATION, wi.getIterationKey().value(), DEFAULT_MAX_RETRIES, now));
        }

        // 2-6. 对每个迭代的每个仓库创建任务
        for (WindowIteration wi : iterations) {
            Iteration iteration = iterationAppService.get(wi.getIterationKey().value());

            for (RepoId repoId : iteration.getRepos()) {
                // 归档 feature 分支
                tasks.add(RunTask.create(runId, RunTaskType.ARCHIVE_FEATURE_BRANCH, ++order,
                        TargetType.REPOSITORY, repoId.value(), DEFAULT_MAX_RETRIES, now));

                // 更新 POM 版本
                tasks.add(RunTask.create(runId, RunTaskType.UPDATE_POM_VERSION, ++order,
                        TargetType.REPOSITORY, repoId.value(), DEFAULT_MAX_RETRIES, now));

                // 合并到 master
                tasks.add(RunTask.create(runId, RunTaskType.MERGE_RELEASE_TO_MASTER, ++order,
                        TargetType.REPOSITORY, repoId.value(), DEFAULT_MAX_RETRIES, now));

                // 创建标签
                tasks.add(RunTask.create(runId, RunTaskType.CREATE_TAG, ++order,
                        TargetType.REPOSITORY, repoId.value(), DEFAULT_MAX_RETRIES, now));

                // 触发构建
                tasks.add(RunTask.create(runId, RunTaskType.TRIGGER_CI_BUILD, ++order,
                        TargetType.REPOSITORY, repoId.value(), DEFAULT_MAX_RETRIES, now));
            }
        }

        return tasks;
    }

    /**
     * 异步执行运行任务
     */
    @Async
    public CompletableFuture<Void> executeRunAsync(String runId) {
        log.info("Starting async execution of run: {}", runId);

        List<RunTask> tasks = runTaskPort.findByRunId(runId);

        for (RunTask task : tasks) {
            if (task.isPending()) {
                boolean success = executeTaskWithRetry(task);
                if (!success) {
                    log.error("Task {} failed, stopping execution", task.getId().value());
                    break;
                }
            }
        }

        // 完成 Run
        Run run = runPort.findById(runId).orElse(null);
        if (run != null) {
            run.finish(Instant.now(clock));
            runPort.save(run);
        }

        log.info("Finished execution of run: {}", runId);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 执行单个任务（带重试）
     */
    private boolean executeTaskWithRetry(RunTask task) {
        Instant now = Instant.now(clock);
        task.start(now);
        runTaskPort.save(task);

        while (task.canRetry()) {
            try {
                RunTaskExecutorPort executor = executorRegistry.getExecutor(task.getTaskType());
                if (executor == null) {
                    log.warn("No executor found for task type: {}", task.getTaskType());
                    task.markSkipped(Instant.now(clock));
                    runTaskPort.save(task);
                    return true;
                }

                executor.execute(task);
                task.markCompleted(Instant.now(clock));
                runTaskPort.save(task);
                return true;

            } catch (Exception e) {
                log.error("Task {} execution failed: {}", task.getId().value(), e.getMessage(), e);
                task.incrementRetry();

                if (!task.canRetry()) {
                    task.markFailed(e.getMessage(), Instant.now(clock));
                    runTaskPort.save(task);
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * 手动重试失败的任务
     */
    @Transactional
    public RunTask retryTask(String taskId) {
        RunTask task = runTaskPort.findById(taskId)
                                  .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!task.isFailed()) {
            throw new IllegalStateException("Only failed tasks can be retried");
        }

        // 重置状态
        Instant now = Instant.now(clock);

        // 创建新的任务记录（保持原有任务记录）
        RunTask newTask = RunTask.rehydrate(
                task.getId(), task.getRunId(), task.getTaskType(), task.getTaskOrder(),
                task.getTargetType(), task.getTargetId(), RunTaskStatus.PENDING,
                0, task.getMaxRetries(), null, null, null, task.getCreatedAt(), now);

        runTaskPort.save(newTask);

        // 异步执行
        CompletableFuture.runAsync(() -> executeTaskWithRetry(newTask));

        return newTask;
    }

    /**
     * 获取运行任务列表
     */
    public List<RunTask> getRunTasks(String runId) {
        return runTaskPort.findByRunId(runId);
    }
}
