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
| 1 | HEAD | SA-007 | 版本策略真实页面验收 | `scenario-acceptance-matrix.md` SA-007 P1/P2 缺口 | SA-006 分支规则真实端到端证据已补齐并出队；当前最小可执行缺口是 SA-007 scoped policy 管理的外部 Playwright 真实页面验收 |

---

## 3. 当前队首任务

任务：SA-007 版本策略真实页面验收。

验收出口：

- 管理员能在真实版本策略页创建、编辑、删除 GLOBAL / PROJECT / SUB_PROJECT 版本策略。
- 页面能阻止缺失 scope 必填项，并能在列表中复核 scope 明细。
- scoped policy 的 applicable 选择仍按 `SUB_PROJECT > PROJECT > GLOBAL` 工作；如果从版本更新入口复核，必须使用真实后端数据，不用 route-level stub 伪装验收。
- 覆盖必要的后端/前端回归；真实用户旅程必须由外部 Playwright 驱动真实页面。
- 完成后同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md` 和 `tasks/records/`。
- 完成后运行 `bash scripts/dev/check-roadmap.sh`，确保下一个 `HEAD` 唯一且可追溯。

非目标：

- 不做 RBAC。
- 不做通知。
- 不做历史不合规分支自动修复或批量重命名；历史治理入口可作为后续切片。
- 不扩展版本更新批量写回能力；该能力归 SA-014。
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
