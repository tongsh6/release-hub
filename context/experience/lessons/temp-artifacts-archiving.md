# 临时产物即时归档

## 问题

在 worktree 或临时分支中生成的报告、测试脚本、开发计划等文件，未及时提交就被遗忘。当 worktree 被删除时，这些内容永久丢失；即使被发现，也因"不确定是否有用"而只能强制删除。

## 具体案例

三个废弃 worktree（dcp/qhq/zco）中存有：
- `FUNCTION_DEVELOPMENT_PLAN.md`
- `INTEGRATION_TEST_REPORT.md`
- `test_version_update.sh` / `test_version_updater_direct.sh`
- `docker-compose.yml`

这些文件在 1 月 11 日生成，一个月后被发现时已无上下文，只能强制删除。

## 正确做法

### 原则：完成即归档，不留未跟踪文件

| 产物类型 | 归档位置 |
|----------|----------|
| 集成测试报告 | `.ai/summaries/YYYY-MM-DD-<topic>.md` |
| 开发计划 | `context/experience/lessons/` 或 `requirements/` |
| 一次性测试脚本 | 提交到仓库或明确删除，不留在工作区 |
| docker-compose 等配置 | 提交到项目根目录或 `scripts/` |

### worktree 关闭前检查清单

1. `git status` — 是否有未跟踪文件？
2. 有价值的内容 → 立即提交或移入主仓库归档
3. 无价值的内容 → 明确删除
4. 确认干净后再执行 `git worktree remove`

## 关键教训

> 未提交的文件不存在。worktree 被删除时，未跟踪文件会一起消失，没有任何提示。
