# Execution Roadmap / 执行路线图

> 本文件只回答下一步做什么；事实以 `docs/project-ledger.md`、`docs/reports/scenario-acceptance-matrix.md` 和任务记录为准。

## 1. 当前唯一主线

2026-11-21 用户授权持续研发、必要重构、分组提交与分阶段推送。先统一可核实的编排语义，再接入可恢复执行。已完成分支收敛：main/develop 同指 `d4534cc`，研发从 `feature/orchestration-consistency` 推进。

- 任务只从下表唯一 HEAD 选择。
- 每个切片独立定义正反例，核实已有职责与实际消费者，验收后继续下一切片。
- 历史验收不能替代新行为验证；测试替身不等于真实 GitLab 或完整页面旅程。

## 2. 当前执行队列

| 顺序 | 标记 | SA | 任务 | 来源 | 选择理由 |
|---|---|---|---|---|---|
| 1 | DONE | SA-013 | S3-A/B1 唯一编排语义与阶段交付 | 提交 53eed67、a4007f2、71a1487 已分阶段推送 | 纯配置、不可变计划、MR-first、精确重试、统一状态与写入证据完成目标验收 |
| 2 | HEAD | SA-013 | S3-B2 收尾失败传播与共享引用保护 | 已授权蓝图下一切片 | 以仓库为单位受理；tag/CI 失败停止后继；共享迭代与分支保留；重复 close 不重放 |
| 3 | QUEUED | SA-013 | S3-C 持久执行、核对与恢复 | 复用 S1 计划和 S2 操作账本 | worker/resume 尚未实现，须以进程中断和真实存储验证，不能由同步测试推定 |
| 4 | QUEUED | SA-002 | BranchCreationMode 执行计划评审门禁 | 原队列保留 | 历史数据处置仍先核实计划，不自动迁移或清库 |

## 3. 当前队首验收出口

S3-A/B1 已完成单元、H2/PostgreSQL/API、前端门禁、静态扫描与交叉审查，并推送到 feature。C1 独立提交树 194 项测试全过、六模块 SpotBugs 0；两个提交前组合缺陷有真实 RED→GREEN 证据。main/develop 保持共同基线。

当前 B2 先封闭收尾错误副作用，再以现有 Run/Item/计划承载 `(window, repository)` 受理。目标反例包括共享仓库只执行一次、Tag/CI 失败零后继写、release 缺失零发布副作用、共享 Iteration 不关闭、重复/并发 close 只受理一次。没有已验证的原子删除条件，必须在创建 archive copy 前停止自动归档，不放开 DELETE。

完整范围及分阶段依赖见 `docs/openspec/changes/add-durable-run-recovery/design.md` 与 `tasks.md`。后台 worker、自动 resume、独立进程硬中断、真实 GitLab/UI 全旅程尚未验收。当前真实外部写验证限明确隔离 fixture，未对业务 GitLab 仓库执行写操作。

## 4. 暂缓项

| 事项 | 原因 |
|---|---|
| RBAC、通知、CI 深集成 | 与当前编排可靠性目标无关 |
| 批量组织重构或跨 provider 迁移 | 不引入无实际消费者的通用平台 |
| 自动批量删除存量数据 | 仍需明确范围和可核实处置依据 |
| 扩展新的版本更新能力 | 当前只修已有文件写入的数据一致性；不扩大发布 MVP |

## 5. 队列约束

- HEAD 必须且只能有一个，SA 编号必须存在于场景矩阵。
- 验收完成后先更新队列再接下一项；未验收能力不得标记完成。
- 修改后运行 `bash scripts/dev/check-roadmap.sh`。
