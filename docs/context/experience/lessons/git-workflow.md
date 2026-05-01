# Git 工作流：GitFlow 规范

## 核心原则

**所有变更必须通过 feature 分支 → PR → merge。禁止直接 push main/release。**

---

## GitFlow 模型

```
main
 │
 ├─── feature/pagination-fix     ─┐
 ├─── feature/gitflow-lifecycle  ─┼─→ release/v0.3.0 ─→ tag v0.3.0 ─→ main
 └─── feature/branch-dashboard  ─┘
```

- **`main`**：唯一稳定主干，只接受 release 分支的合并
- **`feature/*`**：从 main 创建，开发单个功能，PR 合并到 release 分支
- **`release/*`**：从 main 创建，聚合多个 feature，打标签后合并回 main

---

## 完整 GitFlow 流程

### 步骤 1：创建 feature 分支

```bash
scripts/dev/git-flow.sh feature:start <name>
# 等同于: git checkout main && git pull && git checkout -b feature/<name>
```

### 步骤 2：开发 & 提交

```bash
git add <files>
git commit -m "feat: ..."
```

### 步骤 3：推送 feature，创建 PR 到 release 分支

```bash
scripts/dev/git-flow.sh feature:finish <name>
# 推送后创建 PR:
gh pr create --title "feat: <name>" --base release/vX.Y.Z --delete-branch
```

### 步骤 4：创建 release 分支（聚合多个 feature 时）

```bash
scripts/dev/git-flow.sh release:start 0.3.0
# 等同于: git checkout main && git pull && git checkout -b release/v0.3.0 && git push
```

### 步骤 5：合并所有 feature PR 到 release

通过 GitHub PR，将各 feature 分支合并到 `release/vX.Y.Z`。

### 步骤 6：完成 release（打标签 + 合并 main + 清理）

```bash
scripts/dev/git-flow.sh release:finish 0.3.0
# 执行: tag v0.3.0 → push tag → merge release → main → delete release branch
```

### 步骤 7：删除已合并的 feature 分支

```bash
scripts/dev/git-flow.sh feature:delete <name>
# 或 GitHub PR merge 时勾选 "Delete branch"
```

---

## 快速参考：helper 脚本

```bash
scripts/dev/git-flow.sh feature:start <name>     # 从 main 创建 feature 分支
scripts/dev/git-flow.sh feature:finish <name>    # 推送，引导创建 PR
scripts/dev/git-flow.sh feature:delete <name>    # 删除已合并的 feature 分支
scripts/dev/git-flow.sh release:start <version>  # 从 main 创建 release 分支
scripts/dev/git-flow.sh release:finish <version> # 打标签 + 合并 main + 清理
scripts/dev/git-flow.sh status                   # 查看当前 GitFlow 状态
```

---

## 分支命名约定

| 前缀        | 用途                              |
|-------------|-----------------------------------|
| `feature/`  | 新功能开发                        |
| `fix/`      | Bug 修复                          |
| `release/`  | 发布版本聚合（格式：`release/vX.Y.Z`） |
| `chore/`    | 依赖升级、配置调整                |
| `docs/`     | 纯文档更新（无代码变更）          |

---

## PR 类型与 base 分支

| 变更类型                  | PR base 分支          |
|---------------------------|----------------------|
| 新功能（在 release 中）   | `release/vX.Y.Z`     |
| 紧急 Bug 修复             | `main`（hotfix）     |
| 纯文档/经验归档           | `main`               |

---

## 关键规则

1. **feature 分支从 main 创建**，不从 release 分支创建
2. **release 分支从 main 创建**，然后接收 feature PR
3. **打标签在 release:finish 时自动完成**（在 release 分支上打）
4. **release 合并到 main 用 --no-ff**，保留合并历史
5. **feature/release 分支合并后必须删除**
6. worktree 中的变更同样需要走 PR，不因在 worktree 中操作就豁免
7. 经验归档（`.ai/summaries/`、`context/experience/`）也需走 PR

---

## 错误假设

> "这只是文档/注释/格式，直接提交没关系。"

文档同样承载项目决策、规范和历史，需要可审查的变更记录。

---

## 辅助工具

- **查看状态**：`scripts/dev/git-flow.sh status`
- **完整帮助**：`scripts/dev/git-flow.sh help`
