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
| 1 | HEAD | SA-013 | 发布编排结果复核与失败 Run 观察 | `scenario-acceptance-matrix.md` SA-013 P1 缺口 | 上一队首任务已补齐并出队；当前仍可执行的矩阵缺口是从真实页面复核发布编排后的 Run 结果、失败态入口和执行记录可追溯性 |

---

## 3. 当前队首任务

任务：SA-013 发布编排结果复核与失败 Run 观察。

验收出口：

- 管理员能从真实页面触发或复核发布编排后看到对应 Run 结果，而不是只依赖后端接口或日志。
- 失败态必须有用户可见入口：能看到失败 Run、失败原因或失败步骤，并能追溯到发布窗口/迭代/仓库上下文。
- 不改变发布编排业务语义：无阻塞冲突后才允许编排，冲突未解决时仍拒绝。
- 覆盖必要的后端契约和前端回归；如补真实用户旅程，必须由外部 Playwright 驱动真实页面，不用 route-level stub 伪装验收。
- 完成后同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md` 和 `tasks/records/`。
- 完成后运行 `bash scripts/dev/check-roadmap.sh`，确保下一个 `HEAD` 唯一且可追溯。

非目标：

- 不做 RBAC。
- 不做通知。
- 不重写发布编排状态机或 Run 存储模型。
- 不新增 CI 深集成或自动回滚。
- 不把失败 Run 自动重试作为本切片默认动作。
- 不改变既有冲突检测和发布准入语义。
- 不把 route-level stub 回归记录为场景化验收通过。

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
