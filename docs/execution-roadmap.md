# Execution Roadmap / 执行路线图

> 本文件只回答下一步做什么；正式生命周期与责任读取 `docs/context/PROJECT_PROFILE.yaml`，实现和验收事实以 `docs/project-ledger.md`、`docs/reports/scenario-acceptance-matrix.md` 和任务记录为准。

## 1. 当前唯一主线

2026-11-21 用户授权持续研发、必要重构、分组提交与分阶段推送。先统一可核实的编排语义，再接入可恢复执行。已完成分支收敛：main/develop 同指 `d4534cc`，研发从 `feature/orchestration-consistency` 推进。

- 任务只从下表唯一 HEAD 选择；HEAD 是当前实施队首，不是生命周期或已完成标记。按确认目标与完整蓝图推进，不从阶段标签自动裁剪范围。
- 每个切片独立定义正反例，核实已有职责与实际消费者，验收后继续下一切片。
- 历史验收不能替代新行为验证；测试替身不等于真实 GitLab 或完整页面旅程。

## 2. 当前执行队列

| 顺序 | 标记 | SA | 任务 | 来源 | 选择理由 |
|---|---|---|---|---|---|
| 1 | DONE | SA-013 | S3-A/B1 唯一编排语义与阶段交付 | 提交 53eed67、a4007f2、71a1487 已分阶段推送 | 纯配置、不可变计划、MR-first、精确重试、统一状态与写入证据完成目标验收 |
| 2 | DONE | SA-013 | S3-B2 仓库级关闭受理、收尾失败传播与共享引用保护 | e127023、5ad0666 已推送 | 重复 close 返回原 Run；精确 Tag/显式 CI；共享引用保留；生命周期并发屏障通过验证 |
| 3 | HEAD | SA-013 | S3-C1 持久四动作与安全中断 | 复用 run_task、Run/Item/Step、计划和操作账本 | 唯一编排路径按任务提交；请求幂等、MR 分段、结果 CAS 与 guard 同事务；先验证中断后安全停住 |
| 4 | QUEUED | SA-013 | S3-C2 后台驱动、核对与恢复 | C1 同一执行器 | 有界 worker、202/轮询、持久扫描和证据驱动恢复；不增加第二条执行路径 |
| 5 | QUEUED | SA-002 | BranchCreationMode 执行计划评审门禁 | 原队列保留 | 历史数据处置仍先核实计划，不自动迁移或清库 |

## 3. 当前队首验收出口

S3-A/B1 已完成单元、H2/PostgreSQL/API、前端门禁、静态扫描与交叉审查，并推送到 feature。A/B1 提交前 Git 基础独立树 194 项测试全过、六模块 SpotBugs 0；两个提交前组合缺陷有真实 RED→GREEN 证据。main/develop 保持共同基线。

B2 已完成：普通后端 613 项（608 PASS、5 既有 SKIP）、六类数据库/API/HTTP fixture 70/70、前端 38 文件/149 项及全部门禁通过。最终静态扫描 `.ai/reports/static-scan/20261121-153304/summary.md`：SpotBugs 0、lint 0 errors/8 既有 fixture warnings，逐项处置已记录。关闭受理与 CLOSED 同事务；共享仓库只执行一次；Tag/CI 失败零后继写；release 缺失零发布副作用；共享 Iteration 和引用保留；重复/并发 close 返回原 Run；在途编排与关闭通过同一窗口锁互斥。

当前 C1 仍在研发，尚未联合编译闭合或验收；目标是将请求线程接入唯一持久任务路径，固定 ENSURE_FEATURE / ENSURE_RELEASE / ENSURE_MR / TRY_MERGE 四动作。先定义同 key 并发、MR 已创建但提交失败、Git 成功但投影失败、过期领取者及不同仓库同时提交的反例；再用真实 PostgreSQL、HTTP fixture 和独立进程中断验证。C1 的验收是可查询且安全停住，C2 才交付自动扫描和继续执行。

完整范围及分阶段依赖见 `docs/openspec/changes/add-durable-run-recovery/design.md` 与 `tasks.md`。后台 worker、自动 resume、独立进程硬中断、真实 GitLab/UI 全旅程尚未验收；B2 的 E2E 夹具只完成迁移、编译与脚本语法检查。当前真实外部写验证限明确隔离 fixture，未对业务 GitLab 仓库执行写操作。

## 4. 暂缓项

| 事项 | 原因 |
|---|---|
| RBAC、通知、CI 深集成 | 与当前编排可靠性目标无关 |
| 批量组织重构或跨 provider 迁移 | 不引入无实际消费者的通用平台 |
| 自动批量删除存量数据 | 仍需明确范围和可核实处置依据 |
| 扩展新的版本更新能力 | 按已确认蓝图修已有文件写入的数据一致性；不因阶段标签扩大或缩小产品范围 |

## 5. 队列约束

- HEAD 必须且只能有一个，SA 编号必须存在于场景矩阵。
- 验收完成后先更新队列再接下一项；未验收能力不得标记完成。
- 修改后运行 `bash scripts/dev/check-roadmap.sh`。
