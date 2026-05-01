# Design: 发布窗口发布自动化

## Context

ReleaseHub 是一个多仓库发布协调平台，核心流程是管理发布窗口、关联迭代、执行版本更新。本设计实现完整的版本管理和发布自动化流程。

### 完整版本管理流程

```
1. 仓库新建
   └─ 从 master 获取初始版本号（如 1.2.3）
   
2. 仓库关联迭代
   └─ 从 master 创建 feature/{iteration-key} 分支
   └─ 版本升级：1.2.3 → 1.3.0-SNAPSHOT
   └─ 保存：基准版本(1.2.3)、开发版本(1.3.0-SNAPSHOT)、目标版本(1.3.0)

3. 开发阶段
   └─ 开发人员在 feature 分支开发
   └─ 版本号可能被手动修改（需要冲突检测）

4. 迭代关联发布窗口
   └─ 从 master 创建 release/{window-key} 分支
   └─ 将 feature 分支合并到 release 分支
   └─ 记录关联的 release 分支名

5. 开发阶段（迭代已关联窗口后）
   └─ 开发人员继续在 feature 分支开发
   └─ 发布窗口提供"代码合并"功能，随时将 feature 合并到 release

6. 发布窗口发布
   └─ 创建 Run，执行发布任务
   └─ 归档 feature 分支
   └─ release 分支合并到 master
   └─ 版本号：1.3.0-SNAPSHOT → 1.3.0
   └─ 打标签、触发构建
```

### 分支策略图

```
master ───────────────────────────────────────────────────────► master
   │                                                              ▲
   │ (仓库关联迭代)                                               │
   ▼                                                              │
feature/{iter-key} ──────────────────────────────────────►(归档)  │
   │                         │                                    │
   │ (迭代关联窗口)           │ (代码合并功能)                     │
   ▼                         ▼                                    │
release/{window-key} ◄───────┴───────────────────────────────────►│
                                       (发布时合并)
```

## Goals / Non-Goals

### Goals
- 仓库新建时获取/设置初始版本号
- 仓库关联迭代时自动创建 feature 分支并设置版本
- 迭代关联发布窗口时自动创建 release 分支并合并 feature
- 发布窗口提供代码合并功能（feature → release）
- 支持版本号冲突检测和解决
- 发布窗口发布后自动化执行所有发布任务
- 支持异步执行和失败重试

### Non-Goals
- 本次不实现 GitLab API 的实际调用（使用模拟实现）
- 本次不实现 CI/CD 触发的实际调用（使用模拟实现）
- 本次不实现并行任务执行（串行执行）

## Decisions

### 1. 数据模型设计

**代码仓库表扩展**
```sql
ALTER TABLE code_repository ADD COLUMN initial_version VARCHAR(50);
ALTER TABLE code_repository ADD COLUMN version_source VARCHAR(20); -- POM, GRADLE, MANUAL
```

**迭代-仓库关联表扩展**
```sql
ALTER TABLE iteration_repo ADD COLUMN base_version VARCHAR(50);      -- 关联时 master 版本
ALTER TABLE iteration_repo ADD COLUMN dev_version VARCHAR(50);       -- feature 分支开发版本
ALTER TABLE iteration_repo ADD COLUMN target_version VARCHAR(50);    -- 发布目标版本
ALTER TABLE iteration_repo ADD COLUMN feature_branch VARCHAR(200);   -- feature 分支名
ALTER TABLE iteration_repo ADD COLUMN version_source VARCHAR(20);    -- SYSTEM, REPO
ALTER TABLE iteration_repo ADD COLUMN version_synced_at TIMESTAMP;   -- 最后同步时间
```

**窗口-迭代关联表扩展**
```sql
ALTER TABLE window_iteration ADD COLUMN release_branch VARCHAR(200);   -- release 分支名
ALTER TABLE window_iteration ADD COLUMN branch_created BOOLEAN DEFAULT FALSE; -- 分支是否已创建
ALTER TABLE window_iteration ADD COLUMN last_merge_at TIMESTAMP;       -- 最后合并时间
```

**运行任务表**
```sql
CREATE TABLE run_task (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    task_order INT NOT NULL,
    target_type VARCHAR(50),          -- ITERATION, REPOSITORY
    target_id VARCHAR(36),
    status VARCHAR(20) NOT NULL,      -- PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (run_id) REFERENCES run(id)
);
```

### 2. 版本号升级规则

| 场景 | 基准版本 | 开发版本 | 目标版本 |
|------|----------|----------|----------|
| 常规升级 | 1.2.3 | 1.3.0-SNAPSHOT | 1.3.0 |
| 已有SNAPSHOT | 1.2.3-SNAPSHOT | 1.3.0-SNAPSHOT | 1.3.0 |
| 主版本升级 | 1.9.9 | 1.10.0-SNAPSHOT | 1.10.0 |

**升级逻辑：**
```java
public String deriveDevVersion(String baseVersion) {
    // 去除 -SNAPSHOT 后缀
    String cleanVersion = baseVersion.replace("-SNAPSHOT", "");
    String[] parts = cleanVersion.split("\\.");
    // 中间版本号 +1，末尾版本号归零
    int minor = Integer.parseInt(parts[1]) + 1;
    return parts[0] + "." + minor + ".0-SNAPSHOT";
}

public String deriveTargetVersion(String devVersion) {
    return devVersion.replace("-SNAPSHOT", "");
}
```

### 3. 版本冲突检测

```java
public class VersionConflict {
    private String systemVersion;      // 系统存储的版本
    private String repoVersion;        // 代码仓库的版本
    private ConflictType type;         // MISMATCH, REPO_AHEAD, SYSTEM_AHEAD
}

public enum ConflictResolution {
    USE_SYSTEM,    // 使用系统版本，更新代码仓库
    USE_REPO,      // 使用代码仓库版本，更新系统
    CANCEL         // 取消操作
}
```

### 4. 任务类型枚举

```java
public enum RunTaskType {
    // 发布时执行的任务
    CLOSE_ITERATION,           // 关闭迭代
    ARCHIVE_FEATURE_BRANCH,    // 归档 feature 分支
    MERGE_RELEASE_TO_MASTER,   // release → master
    CREATE_TAG,                // 创建标签
    UPDATE_POM_VERSION,        // 更新 POM 版本（去除 SNAPSHOT）
    TRIGGER_CI_BUILD,          // 触发 CI 构建
    
    // 迭代关联窗口时执行的任务
    CREATE_RELEASE_BRANCH,     // 从 master 创建 release 分支
    MERGE_FEATURE_TO_RELEASE   // feature → release 合并
}
```

### 4.1 代码合并功能设计

发布窗口提供"代码合并"功能，用于将迭代的 feature 分支代码合并到 release 分支：

**触发场景：**
1. 迭代关联发布窗口时：自动创建 release 分支并执行首次合并
2. 开发过程中：用户手动触发合并，解决中途修改的代码同步问题

**合并策略：**
```java
public class CodeMergeService {
    
    /**
     * 合并指定迭代的所有 feature 分支到 release 分支
     */
    public MergeResult mergeFeatureToRelease(String windowId, String iterationKey) {
        // 1. 获取迭代关联的所有仓库
        // 2. 对每个仓库执行 feature → release 合并
        // 3. 记录合并结果和时间
        // 4. 处理合并冲突（返回冲突信息，需要人工介入）
    }
    
    /**
     * 批量合并：发布窗口下所有迭代的 feature 分支合并到 release
     */
    public List<MergeResult> mergeAllFeaturesToRelease(String windowId) {
        // 遍历所有关联迭代，执行合并
    }
}
```

**合并结果：**
```java
public class MergeResult {
    private String repoId;
    private String sourceBranch;    // feature 分支
    private String targetBranch;    // release 分支
    private MergeStatus status;     // SUCCESS, CONFLICT, FAILED
    private String conflictInfo;    // 冲突详情（如有）
    private Instant mergedAt;
}

public enum MergeStatus {
    SUCCESS,    // 合并成功
    CONFLICT,   // 存在冲突，需要人工解决
    FAILED      // 合并失败（网络错误等）
}
```

### 5. 任务执行顺序

**迭代关联发布窗口时的任务：**
```
1. CREATE_RELEASE_BRANCH（从 master 创建 release/{window-key} 分支）
2. MERGE_FEATURE_TO_RELEASE（将 feature 分支合并到 release）
```

**发布窗口发布时的任务：**
```
1. CLOSE_ITERATION（关闭所有关联迭代）
2. ARCHIVE_FEATURE_BRANCH（归档所有 feature 分支）
3. UPDATE_POM_VERSION（更新 release 分支版本号，去除 SNAPSHOT）
4. MERGE_RELEASE_TO_MASTER（release 合并到 master）
5. CREATE_TAG（在 master 上创建版本标签）
6. TRIGGER_CI_BUILD（触发 CI 构建）
```

**代码合并（手动触发）：**
```
1. MERGE_FEATURE_TO_RELEASE（将 feature 分支最新代码合并到 release）
```

### 6. 异步执行方案

使用 Spring `@Async` + `CompletableFuture` 实现异步执行：

```java
@Async
public CompletableFuture<Void> executeRunAsync(String runId) {
    for (RunTask task : tasks) {
        executeTaskWithRetry(task);
        if (task.isFailed()) {
            break;
        }
    }
    return CompletableFuture.completedFuture(null);
}
```

### 7. 重试机制

```java
public void executeTaskWithRetry(RunTask task) {
    while (task.getRetryCount() < task.getMaxRetries()) {
        try {
            executeTask(task);
            task.markCompleted();
            return;
        } catch (Exception e) {
            task.incrementRetry();
            task.setErrorMessage(e.getMessage());
            if (task.getRetryCount() >= task.getMaxRetries()) {
                task.markFailed();
            }
        }
    }
}
```

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|----------|
| GitLab API 调用失败 | 重试机制 + 错误日志 |
| 版本号冲突频繁 | 提供清晰的冲突解决 UI |
| 分支创建失败 | 预检查分支是否存在 |
| 任务执行超时 | 设置超时时间 + 状态标记 |

## Migration Plan

1. **Phase 1**：数据库迁移，添加新表和字段
2. **Phase 2**：实现仓库初始版本号获取
3. **Phase 3**：实现仓库关联迭代时的 feature 分支创建和版本设置
4. **Phase 4**：实现版本冲突检测和解决
5. **Phase 5**：实现迭代关联发布窗口时的 release 分支创建和首次合并
6. **Phase 6**：实现发布窗口代码合并功能（feature → release）
7. **Phase 7**：实现 Run 和 RunTask 创建逻辑
8. **Phase 8**：实现异步任务执行框架
9. **Phase 9**：实现各任务类型的执行逻辑（模拟）
10. **Phase 10**：前端页面（迭代详情版本展示、执行记录页面、代码合并功能）

## Open Questions

1. feature 分支命名规则：`feature/{iteration-key}` 还是 `feature/{iteration-name}`？
   **决定**：使用 `feature/{iteration-key}`，保证唯一性
2. release 分支命名规则：`release/{window-key}` 还是 `release/{window-name}`？
   **决定**：使用 `release/{window-key}`，保证唯一性
3. 版本冲突时是否需要发送通知？
4. 是否需要支持批量解决版本冲突？
5. 代码合并冲突时的处理流程？
   **决定**：返回冲突信息，提示用户在 GitLab 中手动解决冲突后再重试
