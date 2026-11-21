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
| 4 | DONE | SA-013 | S3-C2-A 持久扫描、异步受理与进度观察 | 四组提交已推送；固定 `1761572` 的 Full-Link 44905843754 为 5/5 | 202、持久扫描、PENDING 重启继续与原回执观察完成本阶段验收；不冒称 RUNNING/UNKNOWN 接管 |
| 5 | DONE | SA-013 | S3-C2-B1 无写意图的过期 RUNNING 接管 | 3f810c8、5441df6 已推送；固定 5441df6 的 Full-Link 44911634004 为 5/5 | 无 operation/Step 的完整过期领取按原 token 接管，原任务/首次时间/实际顺序与 MR 检查点保持 |
| 6 | HEAD | SA-013 | S3-C2-B2 建分支成功证明与 APPLIED 结果投影恢复 | 同一源码树组合 189/189、API 48/48、普通 695 PASS/5 SKIP、静态通过；待分组推送及本片固定 SHA CI | 先修正 commit.id 被旁支字段覆盖的错误成功风险，再沿现有事件证明仅补原分支任务结果；恢复过程零网络 |
| 7 | QUEUED | SA-013 | S3-C2-B 后续：MR 证据、发送权与未知结果恢复 | C2-B2 之后独立验收 | MR 完整检查点、INTENT 发送权、SEND/UNKNOWN 精确核对与观察版本分别处理 |
| 8 | QUEUED | SA-002 | BranchCreationMode 执行计划评审门禁 | 原队列保留 | 历史数据处置仍先核实计划，不自动迁移或清库 |

## 3. 当前队首验收出口

S3-A/B1 已完成单元、H2/PostgreSQL/API、前端门禁、静态扫描与交叉审查，并推送到 feature。A/B1 提交前 Git 基础独立树 194 项测试全过、六模块 SpotBugs 0；两个提交前组合缺陷有真实 RED→GREEN 证据。main/develop 保持共同基线。

B2 已完成：普通后端 613 项（608 PASS、5 既有 SKIP）、六类数据库/API/HTTP fixture 70/70、前端 38 文件/149 项及全部门禁通过。最终静态扫描 `.ai/reports/static-scan/20261121-153304/summary.md`：SpotBugs 0、lint 0 errors/8 既有 fixture warnings，逐项处置已记录。关闭受理与 CLOSED 同事务；共享仓库只执行一次；Tag/CI 失败零后继写；release 缺失零发布副作用；共享 Iteration 和引用保留；重复/并发 close 返回原 Run；在途编排与关闭通过同一窗口锁互斥。

C1 的本地最后候选验证已通过：普通后端 57 类 657 项（652 PASS/5 SKIP），最新契约 9 类 148/148，前端 170/170，以及 MR 检查点提交后独立 JVM 强杀、重启同 key 零新增 HTTP。受理回执已收窄为 runId；迭代配置建分支/归档旧入口已删除，repoConfigs 范围已校验；E2E fixture 使用隔离项目与已验证 Settings。最终扫描 `20261121-175548` 为 SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings，TopN 与 EOF 修正复验已留痕。四组提交已推送，固定 `e5094ba` 的 Full-Link 44899945002 为 5/5，C1 标记 DONE；其硬杀验收边界仍是可查询且安全停住。

C2-A 本地验收已完成：202 与 Git I/O 解耦，默认数据库扫描无需内存唤醒；有界游标、池拒绝/取消、同 Run 双仓真实进展及同仓前序限制均验证。API/PG 67/67、真实 HTTP 29/29、独立 JVM 1/1，后补 worker 7/7；普通后端 677 项（672 PASS/5 SKIP）、前端 39 文件 214/214 和全部门禁通过，重叠测试不累计。MR 检查点后重启只继续原 PENDING TRY_MERGE，MR POST=1、MERGE PUT=1；已领取任务仍安全停住。四组提交 `06aee3b`、`b37397f`、`e0c6356`、`1761572` 已推送；固定最后 SHA 的 Full-Link 44905843754 为 5/5，C2-A 出队。E2E fixture 最后补验 16/16，包含原 RunId 值比较。

C2-A 最终静态报告为 `.ai/reports/static-scan/20261121-203554/summary.md`：SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 warnings，TopN 逐项处理且 EOF 修正复查通过。任意中断点接管与真实 UI 黄金旅程仍未验收。CI profile/Python/fixture 修复链及 C1 5/5 保留为历史；C2-A 已用本次 SHA 独立复验，Slice4/5 的较弱覆盖不扩写为完整清理/真实冲突验收。

当前 C2-B1 已在同一路径实现：API 48/48、普通后端 680 项（675 PASS、5 既有 SKIP）在静态整理前各自源码稳定的树通过；整理仅涉及 RunJpaPersistenceAdapter，最终代码已重跑覆盖它的组合 76/76，源码前后 hash 一致，重叠不累计。末尾仅 RUNNING、双 worker 原子接管、旧 begin/complete 拒绝、全部 operation/当前 task Step 排除、拿锁后 DB 期限、首次时间及已有顺序保留均有真实 RED→GREEN；PENDING 和已领取但无 operation 两个独立 JVM 强杀重启场景均 MR POST=1、MERGE PUT=1。默认期限 60 分钟、无心跳；V40 NULL 不回填为资格。最终静态 `.ai/reports/static-scan/20261121-214959/summary.md` 为六模块 SpotBugs 0、typecheck PASS、lint 0 errors/8 既有 fixture warnings；首扫 4 项已修复或精确说明并复扫。后端 3f810c8、文档 5441df6 已推送；固定完整 5441df6bdc971953dee390c9470d4786fe0eab44 的 [Full-Link 44911634004](https://github.com/tongsh6/release-hub/actions/runs/44911634004) 为 5/5，B1 正式出队，未借用 C2-A CI。

当前 C2-B2 已在本地完成 CREATE_BRANCH 成功证明与原分支 Task 的 APPLIED 投影。真实 RED 先复现错误 commit.id 被旁支字段覆盖而错误 CREATED/APPLIED 并清 guard，再统一 201/name/确切 commit.id 校验。复用原事件、operation、Task 与 guard，同一结果事务补记；真实进程 exit=137 后默认 worker 恢复，投影阶段 HTTP=0、分支 POST 总数 1、原 token/三条事件保持，原 Run 随后完成。同一 backend 树组合 189/189、API 48/48、普通 695 PASS/5 既有 SKIP 和静态 20261121-224714 通过，TopN 8 条既有 fixture 告警逐项留痕；详细证据见任务记录 15.2。当前待分组推送与本片固定 SHA CI，仍保留 HEAD；MR、INTENT/SEND/UNKNOWN 及完整 UI 旅程分别排队。

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
