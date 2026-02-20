# Git 工作流：所有变更必须走 PR

## 规则

**无论变更类型，一律通过 feature 分支 → PR → merge to main。禁止直接 push main。**

| 变更类型 | 是否需要 PR |
|----------|------------|
| 功能代码 | ✅ 必须 |
| Bug 修复 | ✅ 必须 |
| 配置变更 | ✅ 必须 |
| 文档更新 | ✅ 必须 |
| 经验归档 | ✅ 必须 |

## 错误假设

> "这只是文档/注释/格式，直接提交没关系。"

这是最常见的绕过 PR 的理由，也是最危险的习惯。文档同样承载项目决策、规范和历史，需要可审查的变更记录。

## 正确流程

```bash
# 1. 从 main 创建 feature 分支
git checkout main && git pull
git checkout -b feat/<topic>

# 2. 进行变更并提交
git add <files>
git commit -m "..."

# 3. 推送并创建 PR
git push -u origin feat/<topic>
gh pr create --title "..." --base main

# 4. PR 合并后删除分支
gh pr merge <n> --squash --delete-branch
git checkout main && git pull
```

## 分支命名约定

| 前缀 | 用途 |
|------|------|
| `feat/` | 新功能、文档新增 |
| `fix/` | Bug 修复 |
| `chore/` | 依赖升级、配置调整 |
| `docs/` | 纯文档更新（无代码变更） |

## 关联规则

- worktree 中的变更同样需要走 PR，不因在 worktree 中操作就豁免
- 经验归档（`.ai/summaries/`、`context/experience/`）也需走 PR
