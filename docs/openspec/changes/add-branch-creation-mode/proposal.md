# Change: 迭代-仓库-分支三层关联与 feature 分支创建模式

## Why

当前迭代规范（`iteration/spec.md`）定义为"仓库关联迭代时系统 SHALL 从 master 创建 `feature/{iterationKey}` 分支"。这个行为有三个问题：

1. **只有一种模式**：真实场景中开发者可能已经创建了 feature 分支（用自定义名称如 `feature/JIRA-4521-fix-login`），系统硬编码 `feature/{iterationKey}` 无法匹配
2. **入口不一致**：`create()` 带 repoIds 不建分支不写 versionInfo，只有 `addRepos()` 走 `setupRepoForIteration`。同一个"仓库绑定迭代"的语义有不同副作用
3. **无兜底校验**：系统没有限制用户只能选择/输入 `feature/` 路径下的分支，存在误操作 release/hotfix/master 等保护分支的风险

## What Changes

将迭代-仓库关联升级为三层模型（迭代 → 仓库 → 分支 → 创建方式），新增 `BranchCreationMode` 枚举（AUTO / NAMED / EXISTING），在所有入口统一行为。

- **AUTO**：保持现有行为——系统创建 `feature/{iterationKey}`，写入 versionInfo
- **NAMED**：用户输入自定义分支名，校验 `feature/` 前缀 + BranchRule 规则，系统创建
- **EXISTING**：系统列出仓库 `feature/*` 分支，用户选择，系统只建立映射不创建

所有模式兜底校验：分支必须在 `feature/` 路径下，不可涉及 `release/`、`hotfix/` 等保护分支（`master`/`main`/`develop` 已被 `feature/` 前缀要求天然排除）。

## Impact

- 新增枚举：`BranchCreationMode`（domain 层）
- 影响 API：`POST /api/v1/iterations`（create）、`POST /api/v1/iterations/{key}/repos/add`（addRepos）、`PUT /api/v1/iterations/{key}`（update）
- 新增 API：`GET /api/v1/repositories/{id}/branches?prefix=feature/`
- 影响 `IterationAppService.setupRepoForIteration()`：接受 `BranchCreationMode` + `customBranchName` 参数
- 影响 `IterationRepoVersionInfo`：新增 `branchCreationMode` 字段
- 影响前端：迭代创建/编辑页面新增分支模式选择器

## 需求文档

`docs/requirements/in-progress/迭代仓库分支三层关联.md`
