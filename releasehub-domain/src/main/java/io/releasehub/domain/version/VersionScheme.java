package io.releasehub.domain.version;

/**
 * 版本策略方案枚举
 */
public enum VersionScheme {
    /**
     * 语义化版本（Semantic Versioning）
     * 格式：MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]
     */
    SEMVER,

    /**
     * 日期版本
     * 格式：YYYY.MM.DD
     */
    DATE,

    /**
     * 自定义策略
     */
    CUSTOM
}
