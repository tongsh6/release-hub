# Change: 发布编排的持久执行与中断恢复

## Why

当前 Git 写入与 Run 保存不是原子动作。Run 结束才保存、传输失败缺少 UNKNOWN 语义、ID 碰撞及现有任务表未接通，使重启后无法可靠判断“尚未执行”还是“已经执行但未记录”。

需求：[SA-013 发布编排中断恢复](../../../requirements/in-progress/SA-013-发布编排中断恢复.md)。

## What Changes

- 先持久化不可变计划、任务和操作意图，再执行外部写入；使用数据库任务队列和有界 worker。
- Run / Item 使用独立唯一标识，幂等键与请求哈希分离，历史记录不覆盖。
- 以任务状态和尝试记录区分成功、失败、未知；未知结果先核对，不盲目重试。
- 引入仓库写入门禁、租约与过期 worker 数据库写入隔离，明确外部 Git 不具备通用 fencing 保证。
- 复用并扩展现有 `run_task`，补齐当前缺少的执行模型、接口和 UI 状态。
- 默认 MANUAL：Attach/Detach 和 Publish 只改变配置或生命周期，显式 Orchestrate 进入唯一计划受理入口。可选 AUTO_ON_ATTACH 未实现，不以发布事件代替用户执行意图。
- **分阶段行为变更**：S3-A/B1 先采用持久 Run/计划和同步执行，统一实际状态、精确 Item 重试及 UNKNOWN 阻断；S3-C 才改为持久任务受理与异步响应。已有事实按统一状态查询，不保留旧执行路径或自动重放。

## Complete Target Blueprint

完整目标、架构、数据/API/UI 影响、范围与非目标、分阶段计划、验收矩阵和迁移回滚见 [design.md](design.md)，逐项追踪见 [tasks.md](tasks.md)。

## Impact

- 受影响规范：run、release-window。
- 目标代码范围：Run/RunItem 身份、RunPort/JPA、编排与重试、生命周期受理、Git Port/Adapter、Run API、页面与导出。
- 数据范围：已复用 Run/Item、不可变计划与独立 Git operation/event/observation、仓库 guard；V37 将执行状态单向规范化。S3-C 对 run_task 的扩展及其他结构，须先核实实际消费者与现有对象的承责边界。
- 切换边界：项目未上线，API/UI 同步迁移并删除旧路径；不伪造已有执行证据，不自动重放。异步返回在 S3-C 完成真实故障验收后一次切换。

## 当前授权与状态

2026-11-21 用户已授权持续研发、必要重构、分组提交和分阶段推送。`main`、`develop` 已收敛到 `d4534cc`，当前开发分支为 `feature/orchestration-consistency`。S3-A/B1 已按代码、存储和界面证据验收并分阶段推送；当前 B2 收尾进入队首，C 持久 worker/恢复仍在队列，不能据授权宣称能力已完成。

历史：S1/S2 的当时范围及验收见 [S1 实施口径](s1-implementation.md)、[S2 评审方案](s2-review.md) 和 [S2 实施口径](s2-implementation.md)。当时 S2 默认关闭；本轮真实 HTTP 出口保护改为默认开启，显式关闭时拒绝写入，测试内存模式另行隔离。本轮尚未执行真实 GitLab 用户旅程或进程硬中断恢复。
