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
| 1 | HEAD | SA-016 | release 分支累积冲突清理执行保护证据 | `scenario-acceptance-matrix.md` Phase 2 缺口池 | 种子分支清理脚本已有 dry-run 与 `--execute` 开关；当前仍缺执行前候选、执行后 GitLab 分支状态和保护边界的场景化证据 |

---

## 3. 当前队首任务

任务：SA-016 release 分支累积冲突清理执行保护证据。

验收出口：

- 复核 `scripts/e2e/reset-gitlab-seed-branches.sh` 的 dry-run 候选清单，明确哪些 release/feature 临时分支会被清理、哪些 main 和 seed feature 分支必须保留。
- 在真实本地 GitLab 种子仓库中执行受控清理，记录执行前候选、执行结果和执行后分支状态。
- 验收证据应覆盖 dry-run/execute 对照、main 分支保护、seed feature 分支保护、非种子临时分支删除和重复执行幂等。
- 不允许删除默认分支、种子 feature 分支、未被脚本候选规则识别的业务分支或数据库记录。
- 覆盖必要门禁；如果本轮只完成设计，必须把方案、非目标和后续 Slice 写入中文任务记录与 spec。
- 完成后同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md` 和 `tasks/records/`。
- 完成后运行 `bash scripts/dev/check-roadmap.sh`，确保下一个 `HEAD` 唯一且可追溯。

非目标：

- 不做 RBAC。
- 不做通知。
- 不做批量组织重构或资源迁移向导。
- 不做仓库自动拆分、跨分组批量迁移或自动容量规划。
- 不改变分支创建、发布编排、关闭窗口、CI 触发状态和 retry 语义。
- 不把种子分支清理脚本扩展成通用生产分支清理工具。
- 不引入新的全局工具安装或系统级依赖；若需要新依赖，必须先按本机策略确认。
- 不通过数据库脚本绕过应用层不变量来执行清理动作。

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
