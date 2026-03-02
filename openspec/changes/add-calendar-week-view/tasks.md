## 1. 前端实现

- [ ] 1.1 新增视图切换状态（`viewMode: 'month' | 'week'`）
- [ ] 1.2 实现 `weekDays` computed：计算当周 7 天的日期和事件列表
- [ ] 1.3 实现周视图模板：7 列布局，每列显示日期标题 + 完整事件列表（含时间）
- [ ] 1.4 实现周视图导航：`prevWeek` / `nextWeek` 按周翻页
- [ ] 1.5 更新 `currentMonthLabel` computed：周视图显示「YYYY年M月 第N周」
- [ ] 1.6 更新导航按钮行为：根据 `viewMode` 自动选择按月/按周翻页
- [ ] 1.7 添加 i18n 文案（`calendar.week`、`calendar.month`、`calendar.weekOf`）

## 2. 验证

- [ ] 2.1 pnpm lint 通过
- [ ] 2.2 pnpm typecheck 通过
