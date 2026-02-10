package io.releasehub.domain.releasewindow;

/**
 * 发布窗口状态
 * DRAFT -> PUBLISHED -> CLOSED
 * 
 * @author tongshuanglong
 */

public enum ReleaseWindowStatus {
    /** 草稿 - 正在配置中 */
    DRAFT,
    /** 已发布 - 发布计划已确定 */
    PUBLISHED,
    /** 已关闭 - 归档（终态） */
    CLOSED
}
