package io.releasehub.domain.version;

/**
 * 版本冲突解决方案
 */
public enum ConflictResolution {
    USE_SYSTEM,    // 使用系统版本，更新代码仓库
    USE_REPO,      // 使用代码仓库版本，更新系统
    CANCEL         // 取消操作
}
