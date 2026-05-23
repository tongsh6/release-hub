# Execution Roadmap / 执行路线图

> 本文件只回答一个问题：下一步做什么。
> 事实来源以 `docs/reports/scenario-acceptance-matrix.md` 和 `docs/project-ledger.md` 为准；本文件不得复制长篇证据、历史记录或实现细节。

---

## 1. 当前唯一主线

场景矩阵驱动收口。

权威来源：

- `docs/reports/scenario-acceptance-matrix.md`
- `docs/project-ledger.md`
- `tasks/records/`

执行规则：

- 每次用户要求“挑任务执行”时，只能选择第 2 节中标记为 `HEAD` 的队首任务。
- 不得从“后续保持回归”的事项中挑任务。
- 如果队首任务已完成，必须先更新本文件，再继续挑下一个任务。

---

## 2. 当前执行队列

| 顺序 | 标记 | SA | 任务 | 来源 | 选择理由 |
|---|---|---|---|---|---|
| 1 | HEAD | SA-016 | 关闭窗口后 tag/merge/archive 真实 GitLab 收尾证据 | `scenario-acceptance-matrix.md` Phase 2 缺口池 | SA-016 已覆盖关闭、重复关闭、关闭后关键操作禁止、收尾 Run、报告制品和 CI 触发状态；当前仍缺关闭窗口后 tag、merge to main 和分支归档的真实 GitLab 可复核证据 |

---

## 3. 当前队首任务

任务：SA-016 关闭窗口后 tag/merge/archive 真实 GitLab 收尾证据。

验收出口：

- 定义关闭窗口后 tag、merge to main 和 release/feature 分支归档的真实 GitLab 观察口径。
- 样本必须能按 `windowKey`、`iterationKey` 和 `repoId` 追溯关闭前后的 tag、目标分支、release 分支和归档分支状态。
- 验收证据应优先覆盖后端应用服务或真实 GitLab 验收脚本；如页面已有闭环，本轮不强行扩展 UI。
- 不改变已闭环的关闭幂等、关闭后关键操作禁止、报告导出、CI 触发状态和 retry 语义。
- 覆盖必要门禁；如果本轮只完成设计，必须把方案、非目标和后续 Slice 写入中文任务记录与 spec。
- 完成后同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md` 和 `tasks/records/`。
- 完成后运行 `bash scripts/dev/check-roadmap.sh`，确保下一个 `HEAD` 唯一且可追溯。

非目标：

- 不做 RBAC。
- 不做通知。
- 不做批量组织重构或资源迁移向导。
- 不做仓库自动拆分、跨分组批量迁移或自动容量规划。
- 不改变分支创建、发布编排、关闭窗口、CI 触发状态和 retry 语义。
- 不引入新的全局工具安装或系统级依赖；若需要新依赖，必须先按本机策略确认。
- 不通过数据库脚本绕过应用层不变量来伪造关闭后 GitLab 状态。

---

## 4. 暂缓项

| 事项 | 原因 |
|---|---|
| RBAC | `docs/project-ledger.md` 明确当前阶段不做 |
| 通知 | `docs/project-ledger.md` 明确当前阶段不做 |
| CI 深集成 | 当前阶段不做；SA-016 发布证据归档形态已按制品包收口 |
| 批量组织重构/资源迁移向导 | SA-003 已按受控空叶子分组移动收口；批量资源迁移、跨 Git provider 迁移和重写发布范围不进入当前阶段 |
| 自动批量删除存量数据 | SA-002 当前只允许以 dry-run、人工复核和最小可审计动作推进 |

---

## 5. 防腐败规则

- 本文件只保留任务队列指针，不沉淀验收证据。
- `HEAD` 必须且只能有一个。
- `HEAD` 行必须包含一个 `SA-xxx` 编号，且该编号必须存在于 `scenario-acceptance-matrix.md`。
- `HEAD` 行不得包含“或”“任选”“待定”“二选一”等不确定词。
- `HEAD` 行不得是“后续保持回归”。
- 队首任务完成后必须出队；不能把已完成任务长期留在 `HEAD`。
- 每次修改本文件后运行：

```bash
bash scripts/dev/check-roadmap.sh
```
