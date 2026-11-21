package io.releasehub.domain.run;

/**
 * 代码合并状态
 */
public enum MergeStatus {
    SUCCESS,    // 合并成功
    CONFLICT,   // 存在冲突，需要人工解决
    BLOCKED,    // 流水线、审批、保护分支或权限阻塞
    FAILED,     // 已确认拒绝或失败
    UNKNOWN     // 请求可能已生效，必须先核对
}
