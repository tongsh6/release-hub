package io.releasehub.application.release;

import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunTask;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 发布运行用例接口
 * 负责创建和管理发布窗口的运行任务
 */
public interface ReleaseRunUseCase {

    /**
     * 为发布窗口创建运行任务
     */
    Run createReleaseRun(String windowId, String windowKey, String operator);

    /**
     * 异步执行运行任务
     */
    CompletableFuture<Void> executeRunAsync(String runId);

    /**
     * 手动重试失败的任务
     */
    RunTask retryTask(String taskId);

    /**
     * 获取运行任务列表
     */
    List<RunTask> getRunTasks(String runId);
}