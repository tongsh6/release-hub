package io.releasehub.domain.run;

/**
 * 代码合并状态
 */
public enum MergeStatus {
    SUCCESS,    // 合并成功
    CONFLICT,   // 存在冲突，需要人工解决
    FAILED      // 合并失败（网络错误等）
}
