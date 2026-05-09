package io.releasehub.domain.iteration;

/**
 * Feature 分支创建模式。
 *
 * <p>定义在迭代关联仓库时，feature 分支的来源方式。
 * 第一层选择仓库，第二层确定分支名，第三层决定分支的创建方式。
 */
public enum BranchCreationMode {
    /** 系统自动创建 {@code feature/{iterationKey}} */
    AUTO,
    /** 用户自定义分支名（必须在 {@code feature/} 路径下），系统创建 */
    NAMED,
    /** 关联 GitLab 上已存在的分支（必须在 {@code feature/} 路径下），系统不创建 */
    EXISTING
}
