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
| 1 | HEAD | SA-016 | 发布报告制品包归档 | `scenario-acceptance-matrix.md` SA-016 P2 缺口 | SA-016 P0 已闭环，JSON/CSV/Markdown 报告、CI 触发状态和部分失败重试证据已补；当前仍可执行的矩阵缺口是更正式制品包归档 |

---

## 3. 当前队首任务

任务：SA-016 发布报告制品包归档。

验收出口：

- 定义发布报告制品包的产品边界：面向发布证据归档，不替代现有 JSON/CSV/Markdown API。
- 保持现有报告契约兼容：`report.json`、`report.csv`、`report.md` 的接口、内容和前端入口不得回退。
- 必须明确包内文件清单、命名、内容类型和下载入口。
- 覆盖后端报告生成/下载测试、前端入口测试和必要的门禁；如果本轮只完成设计，必须把方案、非目标和后续 Slice 写入中文任务记录与 spec。
- 完成后同步更新 `scenario-acceptance-matrix.md`、`docs/project-ledger.md` 和 `tasks/records/`。
- 完成后运行 `bash scripts/dev/check-roadmap.sh`，确保下一个 `HEAD` 唯一且可追溯。

非目标：

- 不做 RBAC。
- 不做通知。
- 不做 CI 深集成或远程制品库上传。
- 不改变发布收尾、关闭窗口、CI 触发状态和 retry 语义。
- 不引入新的全局工具安装或系统级依赖；若需要新依赖，必须先按本机策略确认。
- 不删除或弱化现有 JSON/CSV/Markdown 报告能力。
- 不做 PDF 生成；PDF 保留为制品包之后的扩展。

---

## 4. 暂缓项

| 事项 | 原因 |
|---|---|
| RBAC | `docs/project-ledger.md` 明确当前阶段不做 |
| 通知 | `docs/project-ledger.md` 明确当前阶段不做 |
| CI 深集成 | 当前阶段不做；SA-016 本轮只处理发布证据归档形态 |
| 批量组织重构/资源迁移向导 | SA-003 已按受控空叶子分组移动收口；批量资源迁移、跨 Git provider 迁移和重写发布范围不进入当前阶段 |

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
