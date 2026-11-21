package io.releasehub.domain.iteration;

/**
 * Feature 分支创建模式。
 *
 * <p>迭代关联仓库时保存 feature 分支的名称和来源模式。
 * AUTO/NAMED 的远端分支由显式 Orchestrate 确保存在；EXISTING 仅只读验证并复用。
 */
public enum BranchCreationMode {
    /** 使用 {@code feature/{iterationKey}}，在显式编排时创建缺失分支 */
    AUTO,
    /** 保存自定义分支名（必须在 {@code feature/} 路径下），在显式编排时创建缺失分支 */
    NAMED,
    /** 关联 GitLab 上已存在的分支（必须在 {@code feature/} 路径下），系统不创建 */
    EXISTING
}
