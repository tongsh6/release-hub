package io.releasehub.domain.version;

/**
 * 版本冲突类型
 */
public enum ConflictType {
    // 已有：版本号冲突
    MISMATCH,         // 版本不匹配
    REPO_AHEAD,       // 代码仓库版本较新
    SYSTEM_AHEAD,     // 系统版本较新

    // 新增：分支冲突
    BRANCH_EXISTS,              // 目标分支已存在
    BRANCH_NONCOMPLIANT,        // 分支名不符合规则

    // 新增：跨仓库冲突
    CROSS_REPO_VERSION_MISMATCH, // 跨仓库版本不一致

    // 新增：合并冲突
    MERGE_CONFLICT              // Git 合并冲突
}
