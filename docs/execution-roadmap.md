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
| 1 | HEAD | SA-009 | 移除迭代仓库真实 GitLab 归档证据 | `scenario-acceptance-matrix.md` SA-009 P1 缺口 | 上一队首任务已补齐并出队；当前最小可执行缺口是移除迭代仓库后 feature 分支归档的真实 GitLab 证据 |

---

## 3. 当前队首任务

任务：SA-009 移除迭代仓库真实 GitLab 归档证据。

验收出口：

- 技术负责人从迭代中移除未挂载发布窗口的仓库后，系统归档该仓库对应 feature 分支。
- 真实 GitLab 证据能证明原 feature 分支不再作为活跃分支存在，归档分支按既有 `archive/...` 约定存在。
- 已挂载发布窗口的迭代仍禁止变更仓库集合，不得绕过既有锁定保护。
- 覆盖必要的后端/GitLab 验收；如补真实用户旅程，必须由外部 Playwright 驱动真实页面，不用 route-level stub 伪装验收。
- 完成后同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md` 和 `tasks/records/`。
- 完成后运行 `bash scripts/dev/check-roadmap.sh`，确保下一个 `HEAD` 唯一且可追溯。

非目标：

- 不做 RBAC。
- 不做通知。
- 不做历史不合规分支自动修复或批量重命名；历史治理入口可作为后续切片。
- 不扩展 release 分支归档；该能力归 SA-010。
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
