# ReleaseHub 发布候选收口报告（2026-05-23）

## 结论

当前分支可进入受控发布候选评审。核心发布链路、风险扫描、版本写回、关闭收尾、数据质量审计和真实 GitLab 证据均已有自动化证据；本阶段不建议继续扩大 Phase 2 功能范围。

当前判断不是“可以直接无条件 GA”，而是：

- 可以作为受控环境 / dogfood / staging 发布候选。
- 进入发布前人工评审时，必须确认 SA-002 数据质量复核队列的处理策略。
- 188 条历史 DRAFT / attach 残留为非阻断风险，不影响本轮新建场景闭环，但需要进入下一阶段治理。

## 2026-05-24 受控验证补充

SA-001 受控发布候选 dogfood/staging 验证已完成。

本轮按候选验证而非功能扩展推进，复核结果如下：

- 后端/真实 GitLab 全量场景验收通过：170 PASS / 0 FAIL / 0 SKIP。
- 首轮完整前端 E2E 暴露 2 个稳定失败和 1 个 flaky，均为前端用户旅程验证路径稳定性问题；已修复并复跑。
- 前端三组重点旅程复跑通过：37 PASS / 0 FAIL。
- 前端完整 E2E 复跑通过：49 PASS / 0 FAIL。
- 关键页面组件回归通过：21 PASS / 0 FAIL；静态扫描通过，报告 `.ai/reports/static-scan/20260524-144034/summary.md`。

受控验证结论保持不变：当前分支可继续作为 dogfood/staging 发布候选推进，但仍不是无条件 GA。SA-002 存量数据风险继续保持只读 dry-run 与人工复核边界，不自动删除数据库记录、不关闭发布窗口、不触碰 GitLab 远端资源。下一阶段应优先形成数据质量人工复核后的受控处置策略和验收出口。

## 发布候选能力清单

| 能力域 | 当前状态 | 关键证据 |
|---|---|---|
| GitLab Settings 与 token 安全 | 已闭环 | 全量验收 SA-001/SA-004；仓库 token 和 Settings token 明文审计为 0 |
| 三层分组与资源归属 | 已闭环 | 非叶子分组创建仓库、迭代、窗口均被拒绝；资源挂载到叶子分组 |
| 仓库纳管与版本解析 | 已闭环 | Clone URL 纳管保护、组织筛选、版本解析诊断、真实空仓库 `VERSION_FILE_MISSING` 证据 |
| 分支规则与迭代仓库 | 已闭环 | AUTO/NAMED/EXISTING、非法 NAMED 写入前拒绝、scoped BranchRule 真实 GitLab 前置拒绝 |
| 发布窗口与发布计划 | 已闭环 | Attach/Detach、release 分支创建/归档、多窗口并行可观测、发布后计划变更锁定 |
| 风险扫描与冲突解决 | 已闭环 | `MERGE_CONFLICT`、`CROSS_REPO_VERSION_MISMATCH`、`REPO_AHEAD`、`SYSTEM_AHEAD`、`GIT_PERMISSION_DENIED`、`GIT_UNAVAILABLE` 强证据 |
| 发布编排与 Run 证据 | 已闭环 | 干净窗口编排 `COMPLETED`，RunItem / RunStep 可追溯，失败 Run 可复核 |
| 版本更新 | 已闭环 | Maven 单模块、多模块、Gradle、批量部分失败和失败项 retry 均有后端/GitLab 证据 |
| 发布关闭与收尾 | 已闭环 | CLOSED 状态、关闭后关键操作禁止、merge/tag/archive/CI 状态、报告制品包 |
| 数据质量治理 | 已具备受控入口 | dry-run 188 条待复核动作，人工复核入口拒绝直接执行，`--execute` 继续拒绝 |

## 验收证据索引

| 证据 | 结果 | 路径 |
|---|---|---|
| 全量场景验收 | 170 PASS / 0 FAIL / 0 SKIP | `tasks/records/2026-05-23-sa-001-full-baseline-rerun.md` |
| 静态扫描 | SpotBugs 0、frontend lint PASS、typecheck PASS | `.ai/reports/static-scan/20260523-193829/summary.md` |
| SA-002 dry-run 复核口径 | 188 条待复核动作 | `.ai/reports/sa002-safe-cleanup/20260523-aligned-baseline/summary.md` |
| SA-014 空仓库真实 GitLab 证据 | 23 PASS / 0 FAIL | `.ai/reports/sa014-empty-repo-version/20260523-113039/summary.md` |
| SA-016 种子分支清理执行保护 | dry-run / execute / repeat execute 均有报告 | `.ai/reports/gitlab-seed-branch-reset/sa016-execute/summary.md` |
| 场景矩阵真源 | 最新队列、缺口清账、验证记录 | `docs/reports/scenario-acceptance-matrix.md` |
| 项目台账 | 已验证事项、Top Priority、关键证据 | `docs/project-ledger.md` |

## 数据质量状态

SA-002 当前不阻断受控发布候选，但必须明确运营边界：

- `sa002-safe-cleanup.sh` 当前报告 188 条待复核动作。
- 风险分布：`DRAFT_WINDOW_REMAINS=187`、`ATTACH_BRANCH_NOT_CREATED=1`。
- 每条动作包含应用入口、执行前检查、执行后复核和人工复核决策。
- `--execute` 继续拒绝，脚本不直接修改数据库、不关闭窗口、不触碰 GitLab。
- 下一阶段应把 dry-run 动作转为可筛选、可分派、可审计的人工复核队列。

## 残留非目标

| 事项 | 当前判断 |
|---|---|
| RBAC | 当前阶段不做；需要独立权限模型设计 |
| 通知 | 当前阶段不做；不能以消息提醒替代发布证据闭环 |
| 批量组织重构 / 批量资源迁移 | 当前阶段不做；若重启必须先有独立设计、迁移边界和回滚证据 |
| 自动批量清理历史数据 | 当前阶段不做；必须先走 dry-run、人工复核和应用层入口 |
| PDF 报告 | 不进入当前队首；JSON/CSV/Markdown/ZIP 制品包已满足归档 |
| 生产级 GitLab 分支清理工具 | 不做；现有清理脚本只服务本地验收种子仓库 |

## 下一阶段候选排序

| 排序 | 类别 | 候选 | 为什么不是本阶段继续做 | 下一步验收出口 |
|---|---|---|---|---|
| 1 | 运维治理 / 产品体验 | SA-002 数据质量复核队列页面化 | 当前已能生成 dry-run 动作，但用户仍要读 Markdown/JSONL；继续在本阶段做会扩大 RC 范围 | 从 dry-run 动作生成应用内复核队列，支持筛选、人工决策、审计导出；仍不自动执行 |
| 2 | 产品体验 | 发布候选评审页 / 发布经理检查清单 | 当前证据分散在矩阵、台账和任务记录；不是核心链路阻断 | 页面聚合最新验收状态、数据质量风险和发布候选结论，支持人工签核记录 |
| 3 | 运维治理 | 验收数据命名空间与保留策略 | 当前历史数据可见但噪声较大；不影响新场景闭环 | 为验收数据增加批次标识、保留策略和只读归档视图，减少 DRAFT 噪声 |
| 4 | 技术债 | 验收脚本与应用 API 的数据源口径统一 | SA-002 已先对齐最明显口径；更彻底统一会触达脚本基础设施 | 把资产统计、脏数据检测和报告生成抽成共享查询/导出口径 |
| 5 | 产品体验 | 更完整的前端场景复跑 | 当前 Playwright 基线较早，后端强证据更新更密集 | 按最新矩阵重跑并更新 Slice-1/Slice-2 页面证据 |

## 建议路线

1. 当前发布候选进入人工评审，不继续扩大 Phase 2 功能。
2. 下一开发 HEAD 选择 SA-002 数据质量复核队列页面化，先解决“有动作清单但缺少产品化复核界面”的问题。
3. 若发布评审要求更强签核，再把发布候选评审页作为下一项，而不是把 RBAC、通知或批量迁移混入当前阶段。
