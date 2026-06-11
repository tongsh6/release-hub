# Git 工作流：GitFlow 规范

## 核心原则

**所有变更必须通过 feature 分支 → PR → develop → release → main。禁止直接 push develop/main/release。**

---

## GitFlow 模型

```
main
 │
 └── develop
      ├── feature/pagination-fix     ─┐
      ├── feature/gitflow-lifecycle  ─┼─→ develop ─→ release/v0.3.0 ─→ tag v0.3.0 ─→ main
      └── feature/branch-dashboard  ─┘                         └──────────────→ develop
```

- **`main`**：生产稳定主干，只接受 release/hotfix 的受控合并和 tag。
- **`develop`**：日常集成主线，承接 feature PR，也是 release 分支的唯一来源。
- **`feature/*`**：从 develop 创建，开发单个功能，PR 合并回 develop。
- **`release/*`**：从 develop 创建，用于候选验证、修复和版本冻结；完成后合并到 main 并同步回 develop。

---

## 完整 GitFlow 流程

### 步骤 1：创建 feature 分支

```bash
git switch develop
git pull --ff-only origin develop
git switch -c feature/<name>
```

### 步骤 2：开发 & 提交

```bash
git add <files>
git commit -m "feat: ..."
```

### 步骤 3：推送 feature，创建 PR 到 develop

```bash
git push -u origin feature/<name>
gh pr create --title "feat: <name>" --base develop --delete-branch
```

### 步骤 4：从 develop 创建 release 分支

```bash
git switch develop
git pull --ff-only origin develop
git switch -c release/v0.3.0
git push -u origin release/v0.3.0
```

### 步骤 5：稳定 release 分支

只允许候选验证、阻断修复、版本号/CHANGELOG/发布证据等 release 收口变更进入 `release/vX.Y.Z`。不再把新的 feature 直接塞入 release；新功能继续进 develop，等待下一轮 release。

### 步骤 6：完成 release（打标签 + 合并 main + 回灌 develop + 清理）

```bash
git switch release/v0.3.0
git tag v0.3.0
git push origin v0.3.0

git switch main
git pull --ff-only origin main
git merge --no-ff release/v0.3.0
git push origin main

git switch develop
git pull --ff-only origin develop
git merge --no-ff release/v0.3.0
git push origin develop

git push origin --delete release/v0.3.0
git branch -d release/v0.3.0
```

### 步骤 7：删除已合并的 feature 分支

```bash
git branch -d feature/<name>
git push origin --delete feature/<name>
```

---

## 快速参考

当前仓库没有 `scripts/dev/git-flow.sh` helper，按上面的原生命令执行。若后续新增 helper，脚本行为必须与本文件保持一致。

---

## 分支命名约定

| 前缀        | 用途                              |
|-------------|-----------------------------------|
| `feature/`  | 新功能开发                        |
| `fix/`      | Bug 修复                          |
| `release/`  | 发布版本聚合（格式：`release/vX.Y.Z`） |
| `hotfix/`   | 从 main 拉出的紧急修复             |
| `chore/`    | 依赖升级、配置调整                |
| `docs/`     | 纯文档更新（无代码变更）          |

---

## PR 类型与 base 分支

| 变更类型                  | PR base 分支          |
|---------------------------|----------------------|
| 新功能                    | `develop`            |
| release 稳定修复          | `release/vX.Y.Z`     |
| 紧急 Bug 修复             | `main`（hotfix 后回灌 develop） |
| 纯文档/经验归档           | `develop`            |

---

## 关键规则

1. **feature 分支从 develop 创建**，不从 main/release 分支创建。
2. **feature PR 只合并到 develop**，不直接合并 main/release。
3. **release 分支从 develop 创建**，只接收 release 稳定修复和发布证据收口。
4. **release 合并到 main 用 --no-ff**，保留合并历史，并在 release 分支上打 tag。
5. **release 完成后必须回灌 develop**，确保 release 修复不丢失。
6. **feature/release 分支合并后必须删除**。
7. worktree 中的变更同样需要走 PR，不因在 worktree 中操作就豁免。
8. 经验归档（`.ai/summaries/`、`context/experience/`）也需走 PR。

---

## 错误假设

> "这只是文档/注释/格式，直接提交没关系。"

文档同样承载项目决策、规范和历史，需要可审查的变更记录。

---

## 辅助工具

当前无专用 GitFlow helper。状态检查使用：

```bash
git status --short --branch
git branch -vv
git rev-list --left-right --count origin/main...origin/develop
```
