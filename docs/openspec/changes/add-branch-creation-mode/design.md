# Design: 迭代-仓库-分支三层关联与 feature 分支创建模式

## 架构形态

### 新增领域概念

```
domain/
  └── io.releasehub.domain.iteration
        └── BranchCreationMode.java          // 枚举: AUTO, NAMED, EXISTING
```

### 数据模型变更

**IterationRepoVersionInfo 新增字段：**

```
branchCreationMode: BranchCreationMode   // 创建模式，默认 AUTO
```

`featureBranch` 字段已有（存储分支名），无论哪种模式最终都写入此字段。`branchCreationMode` 记录该值的来源方式。

**数据库迁移：**

```sql
ALTER TABLE iteration_repo_version_info 
  ADD COLUMN branch_creation_mode VARCHAR(16) DEFAULT 'AUTO';
```

### API 变更

**修改的 API 请求体：**

```java
// IterationController.CreateIterationRequest 新增
private List<RepoBranchConfig> repoConfigs;  // 新增，与 repoIds 互斥

record RepoBranchConfig {
    String repoId;
    BranchCreationMode branchCreationMode;   // 默认 AUTO
    String customBranchName;                 // NAMED/EXISTING 时必填；存入 featureBranch
}
// 注：customBranchName 是请求参数，不是存储字段。NAMED/EXISTING 模式下
// 其值经验证后写入 IterationRepoVersionInfo.featureBranch

// IterationController.RepoChangeRequest（addRepos 专用）
// 保留原有 repoIds 字段，新增：
private BranchCreationMode branchCreationMode;  // 默认 AUTO
private String customBranchName;                // NAMED/EXISTING 时必填
```

**新增 API：**

```
GET /api/v1/repositories/{id}/branches?prefix=feature/
→ ApiResponse<List<String>>
```

后端调用：`CodeRepositoryPort.findBranchesByPrefix(repoId, "feature/")` → `GitLabBranchPort.listBranches(cloneUrl, token, prefix)`

**API 向后兼容：**

当 `repoConfigs` 为空时，回退到旧的 `repoIds` 字段行为（AUTO 模式），保证已有前端调用不 break。

### 核心逻辑重构

**`IterationAppService.setupRepoForIteration()` 新签名：**

```java
private void setupRepoForIteration(
    IterationKey iterationKey, 
    RepoId repoId, 
    BranchCreationMode mode,        // AUTO / NAMED / EXISTING
    String customBranchName,        // NAMED 或 EXISTING 时使用
    Instant now
) {
    // 1. 确定 feature 分支名
    String featureBranch = switch (mode) {
        case AUTO -> "feature/" + iterationKey.value();
        case NAMED -> {
            validateFeaturePrefix(customBranchName);
            validateBranchRule(customBranchName);
            yield customBranchName;
        }
        case EXISTING -> {
            validateFeaturePrefix(customBranchName);
            validateBranchExists(repo, customBranchName);
            yield customBranchName;
        }
    };
    
    // 2. 非 EXISTING 模式：创建分支
    if (mode != EXISTING) {
        gitLabBranchPort.createBranch(repo.getCloneUrl(), featureBranch, repo.getDefaultBranch());
    }
    
    // 3. 推导版本
    String baseVersion = codeRepositoryPort.getInitialVersion(repoId.value()).orElse("1.0.0-SNAPSHOT");
    String devVersion = versionDeriverUseCase.deriveDevVersion(baseVersion);
    String targetVersion = versionDeriverUseCase.deriveTargetVersion(devVersion);
    
    // 4. 写入 versionInfo（所有模式统一执行）
    iterationRepoPort.saveWithVersion(iterationKey.value(), repoId.value(),
        baseVersion, devVersion, targetVersion, featureBranch, VersionSource.SYSTEM.name(), now);
}
```

**保护分支校验：**

```java
// feature/ 前缀是系统级保护——所有 feature 分支操作必须在此路径下
// master/main/develop 天然被 feature/ 前缀要求排除，无需额外检查
// release/、hotfix/ 作为独立分支路径已被 feature/ 前缀排除
// 以下黑名单防止 feature/ 路径下出现含保护分支前缀的命名：
private static final Set<String> BLOCKED_SEGMENTS =
    Set.of("release/", "hotfix/");

private void validateFeaturePrefix(String branchName) {
    if (branchName == null || !branchName.startsWith("feature/")) {
        throw ValidationException.invalidParameter("分支必须在 feature/ 路径下");
    }
    for (String blocked : BLOCKED_SEGMENTS) {
        if (branchName.startsWith(blocked)) {
            // feature/ 前缀已保证此路径不会进入，作为防御性编程保留
            throw ValidationException.invalidParameter("不可操作保护分支: " + branchName);
        }
    }
    // 额外校验：分支名不包含 "../" 路径遍历攻击
    if (branchName.contains("../") || branchName.contains("..\\")) {
        throw ValidationException.invalidParameter("非法分支名");
    }
}
```

### 三个入口统一调用

```
create()  ──┐
addRepos() ─┼──→ setupRepoForIteration(iterationKey, repoId, mode, customBranchName, now)
update()  ──┘
```

### 前端组件

- 迭代创建表单：仓库列表每行新增 `BranchCreationMode` 下拉选择器
- 选项 1：自动创建（默认）— 显示预览名 `feature/{iterationKey}`
- 选项 2：自定义命名 — 展开文本输入框，限制 `feature/` 前缀
- 选项 3：选择已有分支 — 调用分支列表 API，展示 `feature/` 下的分支下拉列表

## 非目标

- 不在此设计中处理已有的旧数据迁移（`branch_creation_mode` 默认 AUTO，与旧行为兼容）
- 不改变 Attach / Orchestrate / Cleanup 后续流程
- 不引入 MR 创建或 Code Review 集成
- 不改变 release 分支创建逻辑

## 风险与回滚

| 风险 | 缓解 | 回滚 |
|------|------|------|
| `repoConfigs` 新字段导致前端不兼容 | 保留 `repoIds` 兼容，新字段可选 | 前端继续用 `repoIds` |
| EXISTING 模式分支列表 API 超时 | 加超时和缓存 | 返回空列表，提示手动输入分支名 |
| 保护分支校验误拦截合理分支名 | 黑名单保守，只拦截明确前缀 | 修改 PROTECTED_PREFIXES |
