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
| 3 | DONE | SA-013 | S3-C1 持久四动作与安全中断 | `44e742c`、`3766686`、`4c6b3e9`、`e5094ba` 已推送；Full-Link 44899945002 为 5/5 | 唯一持久四动作、请求幂等、MR 检查点、原子结果和安全中断已完成本阶段验收 |
| 4 | HEAD | SA-013 | S3-C2-A 持久扫描、异步受理与进度观察 | 本地实现、后端/PG/HTTP/进程/前端及静态门禁通过 | 本次分组提交和固定 SHA Full-Link 待收口；仅 PENDING 自动继续，不重领 RUNNING/UNKNOWN |
| 5 | QUEUED | SA-013 | S3-C2-B 已领取任务接管与证据驱动恢复 | C2-A 与现有操作账本 | 证明无发送或精确完成后才继续；租约过期不等于可重发，保留历史 HTTP 事实 |
| 6 | QUEUED | SA-002 | BranchCreationMode 执行计划评审门禁 | 原队列保留 | 历史数据处置仍先核实计划，不自动迁移或清库 |

## 3. 当前队首验收出口

S3-A/B1 已完成单元、H2/PostgreSQL/API、前端门禁、静态扫描与交叉审查，并推送到 feature。A/B1 提交前 Git 基础独立树 194 项测试全过、六模块 SpotBugs 0；两个提交前组合缺陷有真实 RED→GREEN 证据。main/develop 保持共同基线。

B2 已完成：普通后端 613 项（608 PASS、5 既有 SKIP）、六类数据库/API/HTTP fixture 70/70、前端 38 文件/149 项及全部门禁通过。最终静态扫描 `.ai/reports/static-scan/20261121-153304/summary.md`：SpotBugs 0、lint 0 errors/8 既有 fixture warnings，逐项处置已记录。关闭受理与 CLOSED 同事务；共享仓库只执行一次；Tag/CI 失败零后继写；release 缺失零发布副作用；共享 Iteration 和引用保留；重复/并发 close 返回原 Run；在途编排与关闭通过同一窗口锁互斥。

C1 的本地最后候选验证已通过：普通后端 57 类 657 项（652 PASS/5 SKIP），最新契约 9 类 148/148，前端 170/170，以及 MR 检查点提交后独立 JVM 强杀、重启同 key 零新增 HTTP。受理回执已收窄为 runId；迭代配置建分支/归档旧入口已删除，repoConfigs 范围已校验；E2E fixture 使用隔离项目与已验证 Settings。最终扫描 `20261121-175548` 为 SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings，TopN 与 EOF 修正复验已留痕。四组提交已推送，固定 `e5094ba` 的 Full-Link 44899945002 为 5/5，C1 标记 DONE；其硬杀验收边界仍是可查询且安全停住。

C2-A 本地验收已完成：202 与 Git I/O 解耦，默认数据库扫描无需内存唤醒；有界游标、池拒绝/取消、同 Run 双仓真实进展及同仓前序限制均验证。API/PG 67/67、真实 HTTP 29/29、独立 JVM 1/1，后补 worker 7/7；普通后端 677 项（672 PASS/5 SKIP）、前端 39 文件 214/214 和全部门禁通过，重叠测试不累计。MR 检查点后重启只继续原 PENDING TRY_MERGE，MR POST=1、MERGE PUT=1；已领取任务仍安全停住。当前出口剩余本次分组提交/推送及固定 SHA Full-Link。

最终静态报告为 `.ai/reports/static-scan/20261121-203554/summary.md`：SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings，TopN 逐项处理且 EOF 修正复查通过。任意中断点接管与真实 UI 黄金旅程仍未验收。CI profile/Python/fixture 修复链及 C1 5/5 保留为历史；C2-A 必须用本次 SHA 复验，Slice4/5 的较弱覆盖也不扩写为完整清理/真实冲突验收。

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
