package io.releasehub.domain.run;

/**
 * 运行任务状态
 */
public enum RunTaskStatus {
    PENDING,    // 待执行
    RUNNING,    // 执行中
    COMPLETED,  // 已完成
    FAILED,     // 已失败
    SKIPPED     // 已跳过
}
