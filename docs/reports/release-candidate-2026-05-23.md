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

## 2026-05-24 数据质量处置策略补充

SA-002 数据质量人工复核处置策略已完成。

本轮把历史风险从“可见、可复核”推进为“可解释处置边界”：

- 复核 API 和数据质量复核队列为每条动作返回处置等级、允许动作、失败回滚边界和审计记录口径。
- 仓库 token 明文、系统设置 token 明文、featureBranch 缺失、cloneUrl 异常和 DRAFT 发布窗口残留进入应用层人工处置策略。
- BranchCreationMode 缺失或非法要求独立迁移服务设计。
- attach 分支未创建保持只读观察。
- 所有结果继续 `executionPermitted=false`，不自动删除数据库记录、不自动关闭发布窗口、不迁移业务数据、不触碰 GitLab 远端资源。

受控处置执行审计设计、最小处置 case 和页面场景验收已形成，见 `docs/openspec/changes/update-data-quality-disposition-audit/` 与 `tasks/records/2026-05-24-sa-002-disposition-case-ui-evidence.md`。下一阶段若要真实处理历史风险，仍必须从处置 case 跳转到既有应用入口处理；处置 case 只记录审计状态，不执行清理。

## 2026-05-24 交付证据包补充

SA-001 发布候选交付证据已收口，当前可进入人工评审。

当前交付证据包包含：

- 后端/真实 GitLab 全量场景验收：170 PASS / 0 FAIL / 0 SKIP。
- 完整前端 E2E：49 PASS / 0 FAIL。
- 关键页面组件：21 PASS / 0 FAIL。
- SA-002 数据质量处置 case 页面验收：1 PASS / 0 FAIL。
- 最新静态扫描：`.ai/reports/static-scan/20260524-155040/summary.md`，SpotBugs 0，frontend lint PASS，frontend typecheck PASS。
- 发布候选评审页已可聚合候选结论、验收证据、数据质量风险、检查清单和人工签核记录。

人工评审出口：

- `APPROVE_CONTROLLED_REVIEW`：进入受控评审或 dogfood/staging 推进。
- `HOLD_FOR_FOLLOW_UP`：暂缓推进，要求补充证据或处理非阻断治理项。
- `REQUEST_MORE_EVIDENCE`：当前证据不足，需补充指定验收或复核材料后再评审。

这些出口只表达人工评审结论，不触发发布窗口、仓库、GitLab、数据质量清理或发布编排状态变更。

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
| 数据质量治理 | 已具备受控处置 case 页面证据 | dry-run 188 条待复核动作，人工复核入口拒绝直接执行，复核结果展示处置等级、允许动作、回滚边界和审计记录；真实页面可创建并复核处置 case，`--execute` 继续拒绝 |

## 验收证据索引

| 证据 | 结果 | 路径 |
|---|---|---|
| 全量场景验收 | 170 PASS / 0 FAIL / 0 SKIP | `tasks/records/2026-05-23-sa-001-full-baseline-rerun.md` |
| 静态扫描 | SpotBugs 0、frontend lint PASS、typecheck PASS | `.ai/reports/static-scan/20260523-193829/summary.md` |
| SA-002 dry-run 复核口径 | 188 条待复核动作 | `.ai/reports/sa002-safe-cleanup/20260523-aligned-baseline/summary.md` |
| SA-002 处置 case 页面验收 | 1 PASS / 0 FAIL | `tasks/records/2026-05-24-sa-002-disposition-case-ui-evidence.md` |
| 最新静态扫描 | SpotBugs 0、frontend lint PASS、typecheck PASS | `.ai/reports/static-scan/20260524-155040/summary.md` |
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
- dry-run 动作已转为可筛选、可分派、可审计的人工复核队列，并具备处置等级、允许动作、失败回滚边界、审计记录口径、处置 case 状态记录和真实页面验收证据。
- 后续仍不能把复核队列直接升级为自动清理入口；需要真实处理时，必须从处置 case 跳转既有应用入口并保留前后复核证据。

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
| 1 | 发布治理 | 发布候选交付证据收口与人工评审准备 | 受控验证、发布候选评审页、数据质量复核队列和处置 case 页面证据均已完成；需要把报告从“下一阶段待做”更新为当前交付真源 | 汇总当前可交付证据、非目标边界和人工评审出口；仍标记为 dogfood/staging 候选，不声明无条件 GA |
| 2 | 运维治理 | BranchCreationMode 独立迁移服务设计 | 当前仍被明确标记为 `MIGRATION_REQUIRED`；贸然执行会越过 SA-002 审计边界 | 另建 OpenSpec proposal，定义迁移范围、回滚计划和验收证据 |
| 3 | 产品体验 | 更强发布签核治理 | 当前发布候选评审页只记录签核，不含 RBAC 或通知 | 若人工评审要求更强约束，再单独设计权限、通知和审批流 |

## 建议路线

1. 当前发布候选进入人工评审，不继续扩大 Phase 2 功能。
2. SA-001 受控验证、发布候选评审页和 SA-002 数据质量处置 case 页面证据均已完成，交付证据包已可作为当前人工评审真源。
3. 下一开发 HEAD 转向 BranchCreationMode 独立迁移服务设计；该事项必须先形成 proposal、迁移范围、回滚计划和验收证据，不得直接在复核队列或脚本里执行。
4. 若发布评审要求更强签核，再另建权限、通知或审批流设计，而不是把 RBAC、通知或批量迁移混入当前阶段。
