## ADDED Requirements

### Requirement: 日历周视图

系统 SHALL 提供周视图模式，允许用户切换月视图与周视图查看发布窗口。

#### Scenario: 切换到周视图

- **WHEN** 用户点击「周」切换按钮
- **THEN** 日历切换为当周 7 天的列式布局，每列显示对应日期的所有发布窗口（无数量截断），并展示 `plannedReleaseAt` 的小时:分钟

#### Scenario: 周视图导航

- **WHEN** 用户点击左/右箭头
- **THEN** 日历按周前进或后退，标题更新为「YYYY年M月 第N周」

#### Scenario: 切换回月视图

- **WHEN** 用户点击「月」切换按钮
- **THEN** 日历恢复月视图，导航恢复按月翻页

#### Scenario: 今日跳转

- **WHEN** 用户点击「今天」按钮（任意视图模式）
- **THEN** 日历跳转至包含今日的月份或周次
