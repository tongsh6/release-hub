# 冲突检测增强 — 设计文档

> 日期：2026-04-29
> 关联需求：`requirements/in-progress/版本更新功能增强.md`

## 一、背景

版本更新核心功能（单仓库更新、批量更新、分支推导、多模块 Maven 同步）均已实现。当前版本更新流程存在以下缺口：

- 执行版本更新前没有冲突预检，执行到一半可能失败
- 系统记录的版本与仓库实际版本可能不一致，无人发现
- feature→release、release→master 的 Git 合并冲突只在执行时暴露，用户无感知
- 同一迭代内多个仓库的版本一致性无人校验

## 二、目标

在版本更新执行之前，提供覆盖版本、分支、跨仓库、合并四个维度的冲突检测能力，阻断有冲突的执行，让用户在发布前就能发现并解决问题。

## 三、检测范围

| # | 冲突类型 | ConflictType | 检测内容 | 检测时机 |
|---|---------|-------------|---------|---------|
| 1 | 版本号冲突 | `VERSION_MISMATCH` / `REPO_AHEAD` / `SYSTEM_AHEAD` | 系统记录版本 vs 仓库实际 pom.xml/gradle.properties | 扫描时 + 执行前 |
| 2 | 分支冲突 | `BRANCH_EXISTS` / `BRANCH_NONCOMPLIANT` | 目标分支是否已存在、feature 分支命名是否违反规则 | 扫描时 + 执行前 |
| 3 | 跨仓库一致性 | `CROSS_REPO_VERSION_MISMATCH` | 同一迭代内各仓库目标版本语义兼容性 | 扫描时 |
| 4 | 合并冲突预检 | `MERGE_CONFLICT` | feature→release、release→master Git 合并冲突预检 | 扫描时 + 执行前 |

冲突处理策略：**硬阻断** — 所有冲突必须解决才能执行版本更新。

## 四、API 设计

| 方法 | 端点 | 用途 |
|------|------|------|
| `POST` | `/api/v1/release-windows/{id}/conflicts/check` | 触发全量冲突扫描 |
| `GET` | `/api/v1/release-windows/{id}/conflicts` | 获取最近一次冲突扫描结果 |
| `POST` | `/api/v1/release-windows/{id}/conflicts/resolve` | 解决指定冲突（复用已有 resolveVersionConflict） |

执行接口预检集成：

- `POST /api/v1/release-windows/{id}/execute/version-update` — 开头调用冲突检测，有冲突返回 409
- `POST /api/v1/release-windows/{id}/execute/batch-version-update` — 同上

## 五、数据模型

### ConflictType 扩展（Domain）

```java
public enum ConflictType {
    // 已有
    MISMATCH,         // 版本不匹配
    REPO_AHEAD,       // 仓库版本较新
    SYSTEM_AHEAD,     // 系统版本较新

    // 新增
    BRANCH_EXISTS,              // 目标分支已存在
    BRANCH_NONCOMPLIANT,        // 分支名不合规
    CROSS_REPO_VERSION_MISMATCH, // 跨仓库版本不一致
    MERGE_CONFLICT              // Git 合并冲突
}
```

### ConflictReport（Domain 新增值对象）

```java
public class ConflictReport {
    ReleaseWindowId windowId;
    Instant checkedAt;
    List<ConflictItem> conflicts;

    public boolean hasConflicts() { ... }
    public boolean isBlocking() { return hasConflicts(); }
    public int totalCount() { ... }
}

public class ConflictItem {
    String repoId;
    String repoName;
    String iterationKey;
    ConflictType conflictType;
    String sourceBranch;      // MERGE_CONFLICT 时使用
    String targetBranch;      // MERGE_CONFLICT 时使用
    String systemVersion;     // VERSION_MISMATCH 时使用
    String repoVersion;       // VERSION_MISMATCH 时使用
    String message;
    String suggestion;
}
```

## 六、架构设计

### 新增组件

```
ConflictDetectionAppService (Application)
├── checkWindowConflicts(windowId) → ConflictReport
│   ├── 遍历发布窗口关联的 WindowIteration[]
│   ├── 遍历每个迭代下的仓库
│   │   ├── detectVersionConflicts()      — 版本号冲突
│   │   ├── detectBranchConflicts()       — 分支命名/存在性
│   │   ├── detectCrossRepoConflicts()    — 跨仓库版本一致性
│   │   └── detectMergeConflicts()        — Git 合并冲突预检
│   └── 汇总 ConflictReport

GitBranchPort (Application Port — 已有接口，扩展方法)
+ checkMergeability(repoUrl, token, sourceBranch, targetBranch) → MergeabilityResult

ConflictDetectionPort (Application Port — 新增)
+ saveReport(windowId, ConflictReport)
+ getLatestReport(windowId) → Optional<ConflictReport>
```

### GitBranchPort 扩展：合并冲突预检

```java
// GitBranchPort 新增方法
record MergeabilityResult(boolean mergeable, String detail) {}

MergeabilityResult checkMergeability(String repoUrl, String token,
                                      String sourceBranch, String targetBranch);
```

**GitLab 实现**（`GitLabGitBranchAdapter`）：
- 调用 `POST /api/v4/projects/{id}/merge_requests` 创建临时 MR
- 读取 MR 的 `merge_status` / `has_conflicts` 字段或尝试获取 `merge_ref`
- 记录结果后关闭该 MR（不合并）
- 也可直接调用 `GET /api/v4/projects/{id}/repository/compare?...from=source&to=target`

**GitHub 实现**（`GitHubGitBranchAdapter`）：
- 调用 `GET /repos/{owner}/{repo}/compare/{base}...{head}`
- 检查返回的 `merge_commit_sha` 是否为 null

**Mock 实现**：始终返回可合并。

### 执行接口集成

```
RunAppService.executeVersionUpdate(...)
  → ConflictDetectionAppService.checkWindowConflicts(windowId)
  → 如果 hasConflicts() → throw ConflictDetectedException(conflictReport)
  → 否则继续执行更新
```

## 七、前端设计

### 冲突面板（ReleaseWindowDetail.vue 新 Tab）

- **冲突状态概览**：
  - 绿色：无冲突，可以执行
  - 红色：有 N 个冲突，执行按钮禁用
  - 灰色：尚未扫描
- **冲突列表表格**：
  - 列：仓库名、迭代、冲突类型标签（颜色区分）、源分支→目标分支、描述
  - 过滤：按冲突类型分组 Tab
  - 操作：「解决」按钮（版本冲突 → 版本同步弹窗，合并冲突 → 提示去 GitLab/GitHub 解决）
- **操作栏**：
  - 「重新扫描」按钮 → 触发 `POST .../conflicts/check`
- **执行阻断**：
  - `VersionUpdateDialog` 和 `OrchestrationPanel` 在执行前调用 `GET .../conflicts`
  - 有冲突时禁用执行按钮，tooltip 提示"请先解决所有冲突"

## 八、实施步骤

| 步骤 | 内容 | 层级 |
|------|------|------|
| 1 | 扩展 `ConflictType` 枚举（4个新类型） | Domain |
| 2 | 新增 `ConflictReport` + `ConflictItem` 值对象 | Domain |
| 3 | `GitBranchPort` 新增 `checkMergeability()` 方法 | Application |
| 4 | GitLab/GitHub/Mock Adapter 实现 `checkMergeability()` | Infrastructure |
| 5 | 新增 `ConflictDetectionAppService` | Application |
| 6 | 新增 `ConflictDetectionPort` + Infrastructure 实现 | Application+Infra |
| 7 | 新增 REST API 端点 | Interfaces |
| 8 | 集成预检到 `RunAppService` 执行流程 | Application |
| 9 | 前端冲突面板（ReleaseWindowDetail 新 Tab） | Frontend |
| 10 | 前端执行前冲突阻断 UI | Frontend |
| 11 | 测试覆盖（Domain + Application + Infrastructure + API + 前端） | 全层 |

## 九、测试策略

| 层 | 测试类型 | 内容 |
|----|---------|------|
| Domain | 单元测试 | ConflictReport 构建、ConflictItem 工厂方法 |
| Application | 单元测试 | ConflictDetectionAppService 四种检测逻辑（mock Port） |
| Infrastructure | 单元测试 | GitLab/GitHub checkMergeability（MockRestServiceServer） |
| Bootstrap | MockMvc | 三个 API 端点 + 执行接口预检阻断 |
| Frontend | Vitest | 冲突面板组件渲染 + 类型标签 |
| Frontend | E2E | 扫描 → 展示冲突 → 解决 → 重新扫描 → 执行 |
