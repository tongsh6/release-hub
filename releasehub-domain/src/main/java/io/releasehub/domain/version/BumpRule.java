package io.releasehub.domain.version;

/**
 * 版本递增规则
 */
public enum BumpRule {
    /**
     * 主版本号递增（1.0.0 -> 2.0.0）
     */
    MAJOR,

    /**
     * 次版本号递增（1.0.0 -> 1.1.0）
     */
    MINOR,

    /**
     * 补丁版本号递增（1.0.0 -> 1.0.1）
     */
    PATCH,

    /**
     * 不递增，使用指定版本
     */
    NONE
}
