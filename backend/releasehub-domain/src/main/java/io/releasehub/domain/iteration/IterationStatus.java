package io.releasehub.domain.iteration;

/**
 * 迭代状态
 */
public enum IterationStatus {
    /**
     * 进行中 - 迭代正在开发中
     */
    ACTIVE,
    
    /**
     * 已关闭 - 迭代已完成发布并关闭
     */
    CLOSED
}
