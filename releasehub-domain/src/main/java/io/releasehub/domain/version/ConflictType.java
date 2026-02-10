package io.releasehub.domain.version;

/**
 * 版本冲突类型
 */
public enum ConflictType {
    MISMATCH,       // 版本不匹配
    REPO_AHEAD,     // 代码仓库版本较新
    SYSTEM_AHEAD    // 系统版本较新
}
