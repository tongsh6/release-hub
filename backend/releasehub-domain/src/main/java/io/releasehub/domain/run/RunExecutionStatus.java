package io.releasehub.domain.run;

/** 所有 Run 消费者使用的持久化执行状态。 */
public enum RunExecutionStatus {
    PLANNED,
    RUNNING,
    DONE,
    PARTIAL_FAILED,
    FAILED,
    NEEDS_REVIEW
}