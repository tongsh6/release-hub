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
| 1 | HEAD | SA-006 | 分支规则真实端到端证据收口 | `scenario-acceptance-matrix.md` SA-006 P1 缺口 | 分支规则作用域、核心创建链路和归档分支统计治理已补，下一步需要证明“规则配置 → 分支创建被规则约束”的真实 GitLab 端到端路径 |

---

## 3. 当前队首任务

任务：SA-006 分支规则真实端到端证据收口。

验收出口：

- 管理员在前端配置 GLOBAL / PROJECT / SUB_PROJECT 分支规则后，后续 feature / release 分支创建会按最具体规则校验。
- 不合规 NAMED 分支在写入迭代或创建 release 分支前被拒绝，且错误可被用户理解。
- 真实 GitLab 证据能证明合规分支被创建、不合规分支未创建。
- 覆盖必要的后端单测、前端 Vitest；如补真实用户旅程，必须由外部 Playwright 驱动真实页面，不用 route-level stub 伪装验收。
- 完成后同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md` 和 `tasks/records/`。
- 完成后运行 `bash scripts/dev/check-roadmap.sh`，确保下一个 `HEAD` 唯一且可追溯。

非目标：

- 不做 RBAC。
- 不做通知。
- 不做历史不合规分支自动修复或批量重命名；历史治理入口可作为后续切片。
- 不改变既有 `AUTO` / `NAMED` / `EXISTING` 分支创建模式的产品语义。
- 不新增 Git Provider。

---

## 4. 暂缓项

| 事项 | 原因 |
|---|---|
| RBAC | `docs/project-ledger.md` 明确当前阶段不做 |
| 通知 | `docs/project-ledger.md` 明确当前阶段不做 |
| CI 深集成 | 当前阶段不做；SA-016 仅保留后续扩展 |
| 完整资源移动治理 | 与 SA-005 删除保护相关但不是当前队首切片的最小闭环 |

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
