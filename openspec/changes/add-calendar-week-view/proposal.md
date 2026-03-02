# Change: 添加日历周视图

## Why

发布协调日历视图（月视图）已上线，但月视图下单天事件展示受限（最多显示 3 条），难以查看当周发布窗口的详细信息。周视图可提供更细粒度的时间视角，帮助用户清晰掌握当周发布节奏与时间分布。

## What Changes

- 在 `CalendarView.vue` 中添加月/周视图切换控件（`el-button-group`）
- 实现周视图：显示当周 7 天 × 完整事件列表（无 3 条截断限制）
- 周视图中新增时间标签（`plannedReleaseAt` 精确到小时:分钟）
- 导航方式调整：月视图按月翻页，周视图按周翻页
- 标题标签自适应视图模式：月视图显示「YYYY年M月」，周视图显示「YYYY年M月 第N周」

## Impact

- 受影响 specs：`specs/calendar-view/`（新增）
- 受影响代码：`release-hub-web/src/views/calendar/CalendarView.vue`
- 无后端 API 变更，复用现有 `GET /api/v1/release-windows/paged` 接口

## 需求文档

- [发布协调日历视图](../../../../requirements/in-progress/发布协调日历视图.md)
