package io.releasehub.domain.run;

/**
 * 运行任务类型
 */
public enum RunTaskType {
    // 发布时执行的任务
    CLOSE_ITERATION,           // 关闭迭代
    ARCHIVE_FEATURE_BRANCH,    // 归档 feature 分支
    MERGE_RELEASE_TO_MASTER,   // release → master
    CREATE_TAG,                // 创建标签
    UPDATE_POM_VERSION,        // 更新 POM 版本（去除 SNAPSHOT）
    TRIGGER_CI_BUILD,          // 触发 CI 构建
    
    // 迭代关联窗口时执行的任务
    CREATE_RELEASE_BRANCH,     // 从 master 创建 release 分支
    MERGE_FEATURE_TO_RELEASE   // feature → release 合并
}
