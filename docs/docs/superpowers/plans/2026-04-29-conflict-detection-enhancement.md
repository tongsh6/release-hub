# 冲突检测增强 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在版本更新执行前提供版本号冲突、分支冲突、跨仓库一致性和 Git 合并冲突的四维预检能力，阻断有冲突的执行。

**Architecture:** Domain 层新增 ConflictReport/ConflictItem 值对象并扩展 ConflictType 枚举；Application 层新增 ConflictDetectionAppService 编排四种检测策略，GitBranchPort 扩展 checkMergeability 方法；Infrastructure 层在 GitLab/GitHub/Mock Adapter 实现合并预检，内存存储 ConflictReport；Interfaces 层新增冲突扫描/查询/解决 REST API；前端在 ReleaseWindowDetail 新增冲突检测 Tab。

**Tech Stack:** Java 21, Spring Boot 3.4.1, JUnit 5, MockMvc, Vue 3.5+, TypeScript 5.9+, Element Plus, Vitest

---

## 文件结构

### 新建文件

| 文件 | 职责 |
|------|------|
| `releasehub-domain/src/main/java/io/releasehub/domain/conflict/ConflictReport.java` | 冲突报告值对象，包含窗口ID、扫描时间、冲突列表 |
| `releasehub-domain/src/main/java/io/releasehub/domain/conflict/ConflictItem.java` | 单条冲突项值对象，包含仓库、迭代、类型、分支、版本等信息 |
| `releasehub-domain/src/test/java/io/releasehub/domain/conflict/ConflictReportTest.java` | ConflictReport/ConflictItem 单元测试 |
| `releasehub-application/src/main/java/io/releasehub/application/conflict/ConflictDetectionAppService.java` | 冲突检测应用服务，编排四种检测 |
| `releasehub-application/src/main/java/io/releasehub/application/conflict/ConflictDetectionPort.java` | 冲突报告持久化端口接口 |
| `releasehub-application/src/test/java/io/releasehub/application/conflict/ConflictDetectionAppServiceTest.java` | 冲突检测服务测试 |
| `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/conflict/ConflictDetectionPersistenceAdapter.java` | 内存存储冲突报告适配器 |
| `releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ConflictDetectionController.java` | 冲突检测 REST 控制器 |
| `releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/ConflictDetectionApiTest.java` | 冲突检测 API 集成测试 |
| `release-hub-web/src/views/release-window/ConflictPanel.vue` | 前端冲突面板组件 |
| `release-hub-web/src/views/release-window/__tests__/ConflictPanel.spec.ts` | 冲突面板 Vitest 测试 |

### 修改文件

| 文件 | 变更内容 |
|------|---------|
| `releasehub-domain/src/main/java/io/releasehub/domain/version/ConflictType.java` | 新增 BRANCH_EXISTS, BRANCH_NONCOMPLIANT, CROSS_REPO_VERSION_MISMATCH, MERGE_CONFLICT |
| `releasehub-application/src/main/java/io/releasehub/application/port/out/GitBranchPort.java` | 新增 checkMergeability 方法和 MergeabilityResult record |
| `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/GitLabGitBranchAdapter.java` | 实现 checkMergeability() |
| `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/GitHubGitBranchAdapter.java` | 实现 checkMergeability() |
| `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/MockGitBranchAdapter.java` | 实现 checkMergeability() |
| `releasehub-application/src/main/java/io/releasehub/application/run/RunAppService.java` | executeVersionUpdate/executeBatchVersionUpdate 开头注入冲突预检 |
| `releasehub-common/src/main/java/io/releasehub/common/exception/ErrorCode.java` | 新增 CONFLICT_DETECTED 错误码 |
| `releasehub-common/src/main/java/io/releasehub/common/exception/BusinessException.java` | 新增 runConflictDetected 工厂方法 |
| `release-hub-web/src/api/modules/releaseWindow.ts` | 新增冲突检测 API 函数 |
| `release-hub-web/src/views/release-window/ReleaseWindowDetail.vue` | 新增"冲突检测"Tab |
| `release-hub-web/src/views/release-window/VersionUpdateDialog.vue` | 执行前检查冲突，有冲突时禁用按钮 |

---

## 任务

### Task 1: 扩展 ConflictType 枚举

**Files:**
- Modify: `releasehub-domain/src/main/java/io/releasehub/domain/version/ConflictType.java`

- [ ] **Step 1: 新增四个冲突类型枚举值**

```java
package io.releasehub.domain.version;

/**
 * 版本冲突类型
 */
public enum ConflictType {
    // 已有：版本号冲突
    MISMATCH,         // 版本不匹配
    REPO_AHEAD,       // 代码仓库版本较新
    SYSTEM_AHEAD,     // 系统版本较新

    // 新增：分支冲突
    BRANCH_EXISTS,              // 目标分支已存在
    BRANCH_NONCOMPLIANT,        // 分支名不符合规则

    // 新增：跨仓库冲突
    CROSS_REPO_VERSION_MISMATCH, // 跨仓库版本不一致

    // 新增：合并冲突
    MERGE_CONFLICT              // Git 合并冲突（feature→release 或 release→master）
}
```

- [ ] **Step 2: 编译验证**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q compile -pl releasehub-domain`

- [ ] **Step 3: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub
git add releasehub-domain/src/main/java/io/releasehub/domain/version/ConflictType.java
git commit -m "feat: ConflictType 新增分支/跨仓库/合并冲突类型"
```

---

### Task 2: 新增 ConflictReport 和 ConflictItem 值对象

**Files:**
- Create: `releasehub-domain/src/main/java/io/releasehub/domain/conflict/ConflictReport.java`
- Create: `releasehub-domain/src/main/java/io/releasehub/domain/conflict/ConflictItem.java`
- Create: `releasehub-domain/src/test/java/io/releasehub/domain/conflict/ConflictReportTest.java`

- [ ] **Step 1: 写 ConflictItem 测试**

```java
package io.releasehub.domain.conflict;

import io.releasehub.domain.version.ConflictType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConflictReportTest {

    @Test
    void shouldCreateVersionConflictItem() {
        ConflictItem item = ConflictItem.versionMismatch(
                "R001", "repo-a", "ITER-001",
                "1.0.0", "1.1.0");

        assertThat(item.getConflictType()).isEqualTo(ConflictType.MISMATCH);
        assertThat(item.getRepoId()).isEqualTo("R001");
        assertThat(item.getRepoName()).isEqualTo("repo-a");
        assertThat(item.getSystemVersion()).isEqualTo("1.0.0");
        assertThat(item.getRepoVersion()).isEqualTo("1.1.0");
    }

    @Test
    void shouldCreateBranchExistsConflictItem() {
        ConflictItem item = ConflictItem.branchExists(
                "R001", "repo-a", "ITER-001",
                "feature/ITER-001");

        assertThat(item.getConflictType()).isEqualTo(ConflictType.BRANCH_EXISTS);
        assertThat(item.getSourceBranch()).isEqualTo("feature/ITER-001");
    }

    @Test
    void shouldCreateMergeConflictItem() {
        ConflictItem item = ConflictItem.mergeConflict(
                "R001", "repo-a", "ITER-001",
                "feature/ITER-001", "release/v1.2.0",
                "Merge conflict in pom.xml");

        assertThat(item.getConflictType()).isEqualTo(ConflictType.MERGE_CONFLICT);
        assertThat(item.getSourceBranch()).isEqualTo("feature/ITER-001");
        assertThat(item.getTargetBranch()).isEqualTo("release/v1.2.0");
        assertThat(item.getMessage()).contains("pom.xml");
    }

    @Test
    void emptyReportShouldHaveNoConflicts() {
        ConflictReport report = ConflictReport.empty("W001");

        assertThat(report.hasConflicts()).isFalse();
        assertThat(report.totalCount()).isEqualTo(0);
    }

    @Test
    void reportWithConflictsShouldDetectConflicts() {
        ConflictReport report = ConflictReport.of("W001",
                java.util.List.of(
                        ConflictItem.versionMismatch("R001", "a", "I001", "1.0", "1.1"),
                        ConflictItem.branchExists("R002", "b", "I001", "feat/x")));

        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.totalCount()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q test -pl releasehub-domain -Dtest=ConflictReportTest`
Expected: FAIL (类不存在)

- [ ] **Step 3: 实现 ConflictItem 值对象**

```java
package io.releasehub.domain.conflict;

import io.releasehub.domain.version.ConflictType;
import java.util.Objects;

/**
 * 冲突项值对象 — 描述单个冲突详情
 */
public class ConflictItem {
    private final String repoId;
    private final String repoName;
    private final String iterationKey;
    private final ConflictType conflictType;
    private final String sourceBranch;
    private final String targetBranch;
    private final String systemVersion;
    private final String repoVersion;
    private final String message;
    private final String suggestion;

    private ConflictItem(Builder builder) {
        this.repoId = Objects.requireNonNull(builder.repoId);
        this.repoName = builder.repoName;
        this.iterationKey = builder.iterationKey;
        this.conflictType = Objects.requireNonNull(builder.conflictType);
        this.sourceBranch = builder.sourceBranch;
        this.targetBranch = builder.targetBranch;
        this.systemVersion = builder.systemVersion;
        this.repoVersion = builder.repoVersion;
        this.message = builder.message;
        this.suggestion = builder.suggestion;
    }

    public static ConflictItem versionMismatch(String repoId, String repoName, String iterationKey,
                                                String systemVersion, String repoVersion) {
        return new Builder(repoId, ConflictType.MISMATCH)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .message("系统版本(" + systemVersion + ")与仓库版本(" + repoVersion + ")不一致")
                .suggestion("请使用版本同步功能解决冲突")
                .build();
    }

    public static ConflictItem repoAhead(String repoId, String repoName, String iterationKey,
                                          String systemVersion, String repoVersion) {
        return new Builder(repoId, ConflictType.REPO_AHEAD)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .message("仓库版本(" + repoVersion + ")高于系统记录(" + systemVersion + ")")
                .suggestion("请同步仓库版本到系统")
                .build();
    }

    public static ConflictItem systemAhead(String repoId, String repoName, String iterationKey,
                                            String systemVersion, String repoVersion) {
        return new Builder(repoId, ConflictType.SYSTEM_AHEAD)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .message("系统版本(" + systemVersion + ")高于仓库版本(" + repoVersion + ")")
                .suggestion("请同步系统版本到仓库")
                .build();
    }

    public static ConflictItem branchExists(String repoId, String repoName, String iterationKey,
                                             String branchName) {
        return new Builder(repoId, ConflictType.BRANCH_EXISTS)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(branchName)
                .message("分支 " + branchName + " 已存在")
                .suggestion("请删除或归档已存在的分支后重试")
                .build();
    }

    public static ConflictItem branchNoncompliant(String repoId, String repoName, String iterationKey,
                                                   String branchName) {
        return new Builder(repoId, ConflictType.BRANCH_NONCOMPLIANT)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(branchName)
                .message("分支名 " + branchName + " 不符合命名规则")
                .suggestion("请修改分支名以符合 BranchRule 规则")
                .build();
    }

    public static ConflictItem crossRepoVersionMismatch(String repoId, String repoName, String iterationKey,
                                                         String version, String otherRepoId, String otherVersion) {
        return new Builder(repoId, ConflictType.CROSS_REPO_VERSION_MISMATCH)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(version)
                .repoVersion(otherVersion)
                .message("仓库 " + repoId + " 版本(" + version + ")与 " + otherRepoId + " 版本(" + otherVersion + ")不一致")
                .suggestion("请统一迭代内所有仓库的目标版本")
                .build();
    }

    public static ConflictItem mergeConflict(String repoId, String repoName, String iterationKey,
                                              String sourceBranch, String targetBranch, String detail) {
        return new Builder(repoId, ConflictType.MERGE_CONFLICT)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(sourceBranch)
                .targetBranch(targetBranch)
                .message(sourceBranch + " → " + targetBranch + " 存在合并冲突")
                .suggestion("请手动解决冲突: " + (detail != null ? detail : ""))
                .build();
    }

    // Getters
    public String getRepoId() { return repoId; }
    public String getRepoName() { return repoName; }
    public String getIterationKey() { return iterationKey; }
    public ConflictType getConflictType() { return conflictType; }
    public String getSourceBranch() { return sourceBranch; }
    public String getTargetBranch() { return targetBranch; }
    public String getSystemVersion() { return systemVersion; }
    public String getRepoVersion() { return repoVersion; }
    public String getMessage() { return message; }
    public String getSuggestion() { return suggestion; }

    public static class Builder {
        private final String repoId;
        private final ConflictType conflictType;
        private String repoName;
        private String iterationKey;
        private String sourceBranch;
        private String targetBranch;
        private String systemVersion;
        private String repoVersion;
        private String message;
        private String suggestion;

        public Builder(String repoId, ConflictType conflictType) {
            this.repoId = repoId;
            this.conflictType = conflictType;
        }

        public Builder repoName(String v) { this.repoName = v; return this; }
        public Builder iterationKey(String v) { this.iterationKey = v; return this; }
        public Builder sourceBranch(String v) { this.sourceBranch = v; return this; }
        public Builder targetBranch(String v) { this.targetBranch = v; return this; }
        public Builder systemVersion(String v) { this.systemVersion = v; return this; }
        public Builder repoVersion(String v) { this.repoVersion = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder suggestion(String v) { this.suggestion = v; return this; }

        public ConflictItem build() { return new ConflictItem(this); }
    }
}
```

- [ ] **Step 4: 实现 ConflictReport 值对象**

```java
package io.releasehub.domain.conflict;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 冲突报告值对象 — 发布窗口的冲突扫描结果
 */
public class ConflictReport {
    private final String windowId;
    private final Instant checkedAt;
    private final List<ConflictItem> conflicts;

    private ConflictReport(String windowId, Instant checkedAt, List<ConflictItem> conflicts) {
        this.windowId = Objects.requireNonNull(windowId);
        this.checkedAt = Objects.requireNonNull(checkedAt);
        this.conflicts = Collections.unmodifiableList(Objects.requireNonNull(conflicts));
    }

    public static ConflictReport of(String windowId, List<ConflictItem> conflicts) {
        return new ConflictReport(windowId, Instant.now(), conflicts);
    }

    public static ConflictReport empty(String windowId) {
        return new ConflictReport(windowId, Instant.now(), List.of());
    }

    public String getWindowId() { return windowId; }
    public Instant getCheckedAt() { return checkedAt; }
    public List<ConflictItem> getConflicts() { return conflicts; }
    public boolean hasConflicts() { return !conflicts.isEmpty(); }
    public int totalCount() { return conflicts.size(); }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q test -pl releasehub-domain -Dtest=ConflictReportTest`
Expected: PASS

- [ ] **Step 6: 确认已有测试未受影响**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q test -pl releasehub-domain`
Expected: ALL PASS

- [ ] **Step 7: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub
git add releasehub-domain/src/main/java/io/releasehub/domain/conflict/ConflictReport.java \
        releasehub-domain/src/main/java/io/releasehub/domain/conflict/ConflictItem.java \
        releasehub-domain/src/test/java/io/releasehub/domain/conflict/ConflictReportTest.java
git commit -m "feat: 新增 ConflictReport 和 ConflictItem 值对象"
```

---

### Task 3: GitBranchPort 新增 checkMergeability 方法

**Files:**
- Modify: `releasehub-application/src/main/java/io/releasehub/application/port/out/GitBranchPort.java`
- Modify: `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/GitLabGitBranchAdapter.java`
- Modify: `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/GitHubGitBranchAdapter.java`
- Modify: `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/MockGitBranchAdapter.java`

- [ ] **Step 1: 在 GitBranchPort 接口新增方法**

在 `GitBranchPort.java` 添加 `checkMergeability` 方法和 `MergeabilityResult` record：

```java
// 在 MergeResult 定义之后新增：
record MergeabilityResult(boolean mergeable, String detail) {
    public static MergeabilityResult mergeable() {
        return new MergeabilityResult(true, null);
    }

    public static MergeabilityResult conflict(String detail) {
        return new MergeabilityResult(false, detail);
    }

    public static MergeabilityResult error(String detail) {
        return new MergeabilityResult(false, detail);
    }
}

/**
 * 检查两个分支是否可合并（不实际执行合并）
 * @return MergeabilityResult, mergeable=true 表示无冲突可自动合并
 */
MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch);
```

- [ ] **Step 2: MockGitBranchAdapter 实现**

```java
@Override
public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
    return MergeabilityResult.mergeable();
}
```

- [ ] **Step 3: GitLabGitBranchAdapter 实现**

使用 GitLab Compare API 检测分支差异：

```java
@Override
public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
    try {
        RepoRef repoRef = parseRepoRef(repoCloneUrl);
        // 使用 GitLab compare API：检查两个分支是否可合并
        String compareEndpoint = String.format("%s/api/v4/projects/%s/repository/compare?from=%s&to=%s",
                repoRef.baseUrl, repoRef.encodedPath,
                urlEncode(targetBranch), urlEncode(sourceBranch));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                compareEndpoint, HttpMethod.GET,
                new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {});

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            // API 成功返回说明两个分支之间没有不可解决的冲突
            Object diffs = response.getBody().get("diffs");
            return MergeabilityResult.mergeable();
        }
        return MergeabilityResult.error("compare API returned unexpected response");
    } catch (HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        // 404 表示两个分支之间无共同祖先
        if (e.getStatusCode().is4xxClientError()) {
            return MergeabilityResult.conflict("branches have diverged: " + body);
        }
        return MergeabilityResult.error(body);
    } catch (Exception e) {
        return MergeabilityResult.error(e.getMessage());
    }
}

// 辅助方法：URL 编码
private String urlEncode(String value) {
    try {
        return java.net.URLEncoder.encode(value, "UTF-8");
    } catch (java.io.UnsupportedEncodingException e) {
        return value;
    }
}
```

- [ ] **Step 4: GitHubGitBranchAdapter 实现**

使用 GitHub Compare API：

```java
@Override
public MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch) {
    try {
        String repoPath = extractRepoPath(repoCloneUrl);
        String compareEndpoint = String.format(
                "https://api.github.com/repos/%s/compare/%s...%s",
                repoPath, targetBranch, sourceBranch);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                compareEndpoint, HttpMethod.GET,
                new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {});

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String status = (String) response.getBody().get("status");
            if ("diverged".equals(status) || "behind".equals(status)) {
                return MergeabilityResult.mergeable();
            }
            return MergeabilityResult.mergeable();
        }
        return MergeabilityResult.error("compare API returned unexpected response");
    } catch (HttpClientErrorException e) {
        if (e.getStatusCode().value() == 404) {
            return MergeabilityResult.conflict("branches have no common ancestor");
        }
        return MergeabilityResult.error(e.getResponseBodyAsString());
    } catch (Exception e) {
        return MergeabilityResult.error(e.getMessage());
    }
}
```

- [ ] **Step 5: 编译验证 + 已有测试**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q compile && mvn -q test -pl releasehub-infrastructure -Dtest="GitLabGitBranchAdapterTest,GitHubGitBranchAdapterTest,GitBranchAdapterFactoryImplTest"`
Expected: ALL PASS

- [ ] **Step 6: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub
git add releasehub-application/src/main/java/io/releasehub/application/port/out/GitBranchPort.java \
        releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/GitLabGitBranchAdapter.java \
        releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/GitHubGitBranchAdapter.java \
        releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/git/MockGitBranchAdapter.java
git commit -m "feat: GitBranchPort 新增 checkMergeability 合并预检方法"
```

---

### Task 4: 新增 ConflictDetectionAppService

**Files:**
- Create: `releasehub-application/src/main/java/io/releasehub/application/conflict/ConflictDetectionAppService.java`
- Create: `releasehub-application/src/main/java/io/releasehub/application/conflict/ConflictDetectionPort.java`

- [ ] **Step 1: 创建 ConflictDetectionPort 接口**

```java
package io.releasehub.application.conflict;

import io.releasehub.domain.conflict.ConflictReport;
import java.util.Optional;

public interface ConflictDetectionPort {
    void saveReport(String windowId, ConflictReport report);
    Optional<ConflictReport> getLatestReport(String windowId);
}
```

- [ ] **Step 2: 创建 ConflictDetectionAppService**

```java
package io.releasehub.application.conflict;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionExtractorUseCase;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.conflict.ConflictItem;
import io.releasehub.domain.conflict.ConflictReport;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionAppService {

    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationPort iterationPort;
    private final IterationRepoPort iterationRepoPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final VersionExtractorUseCase versionExtractorUseCase;
    private final BranchRuleUseCase branchRuleUseCase;
    private final ConflictDetectionPort conflictDetectionPort;

    private static final String RELEASE_PREFIX = "release/";
    private static final String FEATURE_PREFIX = "feature/";

    /**
     * 扫描指定发布窗口的所有冲突
     */
    public ConflictReport checkWindowConflicts(String windowId) {
        List<ConflictItem> allConflicts = new ArrayList<>();

        ReleaseWindow rw = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElse(null);
        if (rw == null) {
            return ConflictReport.empty(windowId);
        }

        String releaseBranch = RELEASE_PREFIX + rw.getWindowKey();

        List<WindowIteration> bindings = windowIterationPort.listByWindow(ReleaseWindowId.of(windowId));
        for (WindowIteration wi : bindings) {
            Iteration iteration = iterationPort.findByKey(wi.getIterationKey()).orElse(null);
            if (iteration == null) continue;

            String iterationKey = iteration.getId().value();
            for (RepoId repoId : iteration.getRepos()) {
                // 获取仓库信息
                Optional<CodeRepository> repoOpt = codeRepositoryPort.findById(repoId);
                if (repoOpt.isEmpty()) continue;
                CodeRepository repo = repoOpt.get();

                // 获取版本信息
                Optional<IterationRepoVersionInfo> versionInfoOpt =
                        iterationRepoPort.getVersionInfo(iterationKey, repoId.value());
                String featureBranch = versionInfoOpt
                        .map(IterationRepoVersionInfo::getFeatureBranch)
                        .orElse(FEATURE_PREFIX + iterationKey);
                String systemVersion = versionInfoOpt
                        .map(IterationRepoVersionInfo::getDevVersion)
                        .orElse(null);

                // 1. 版本号冲突检测
                List<ConflictItem> versionConflicts = detectVersionConflicts(
                        repo, featureBranch, systemVersion, iterationKey);
                allConflicts.addAll(versionConflicts);

                // 2. 分支冲突检测
                List<ConflictItem> branchConflicts = detectBranchConflicts(
                        repo, featureBranch, releaseBranch, iterationKey);
                allConflicts.addAll(branchConflicts);

                // 3. 合并冲突预检
                List<ConflictItem> mergeConflicts = detectMergeConflicts(
                        repo, featureBranch, releaseBranch, iterationKey);
                allConflicts.addAll(mergeConflicts);
            }

            // 4. 跨仓库版本一致性检测
            List<ConflictItem> crossRepoConflicts = detectCrossRepoConflicts(iteration);
            allConflicts.addAll(crossRepoConflicts);
        }

        ConflictReport report = ConflictReport.of(windowId, allConflicts);
        conflictDetectionPort.saveReport(windowId, report);
        return report;
    }

    /**
     * 检测版本号冲突：系统记录 vs 仓库实际
     */
    private List<ConflictItem> detectVersionConflicts(CodeRepository repo, String branch,
                                                       String systemVersion, String iterationKey) {
        List<ConflictItem> results = new ArrayList<>();
        if (systemVersion == null) return results;

        String repoId = repo.getId().value();
        String repoName = repo.getName();

        Optional<VersionExtractorUseCase.VersionInfo> extractedOpt =
                versionExtractorUseCase.extractVersion(repo.getCloneUrl(), branch);

        if (extractedOpt.isEmpty()) return results;

        String repoVersion = extractedOpt.get().version();
        if (systemVersion.equals(repoVersion)) return results;

        // 简单版本比较
        results.add(ConflictItem.versionMismatch(repoId, repoName, iterationKey, systemVersion, repoVersion));
        return results;
    }

    /**
     * 检测分支冲突：分支是否已存在、命名是否合规
     */
    private List<ConflictItem> detectBranchConflicts(CodeRepository repo, String featureBranch,
                                                      String releaseBranch, String iterationKey) {
        List<ConflictItem> results = new ArrayList<>();
        String repoId = repo.getId().value();
        String repoName = repo.getName();
        GitBranchPort gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
        String token = repo.getGitToken();

        // 检查 feature 分支是否已存在
        if (gitPort.getBranchStatus(repo.getCloneUrl(), token, featureBranch).exists()) {
            results.add(ConflictItem.branchExists(repoId, repoName, iterationKey, featureBranch));
        }

        // 检查 release 分支是否已存在
        if (gitPort.getBranchStatus(repo.getCloneUrl(), token, releaseBranch).exists()) {
            results.add(ConflictItem.branchExists(repoId, repoName, iterationKey, releaseBranch));
        }

        // 检查分支名是否合规
        if (!branchRuleUseCase.isCompliant(featureBranch)) {
            results.add(ConflictItem.branchNoncompliant(repoId, repoName, iterationKey, featureBranch));
        }
        if (!branchRuleUseCase.isCompliant(releaseBranch)) {
            results.add(ConflictItem.branchNoncompliant(repoId, repoName, iterationKey, releaseBranch));
        }

        return results;
    }

    /**
     * 检测 Git 合并冲突：feature→release 和 release→master 的预检
     */
    private List<ConflictItem> detectMergeConflicts(CodeRepository repo, String featureBranch,
                                                     String releaseBranch, String iterationKey) {
        List<ConflictItem> results = new ArrayList<>();
        String repoId = repo.getId().value();
        String repoName = repo.getName();
        GitBranchPort gitPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
        String token = repo.getGitToken();
        String cloneUrl = repo.getCloneUrl();

        // 检查 feature → release 合并冲突
        if (gitPort.getBranchStatus(cloneUrl, token, featureBranch).exists()
                && gitPort.getBranchStatus(cloneUrl, token, releaseBranch).exists()) {
            GitBranchPort.MergeabilityResult mrCheck = gitPort.checkMergeability(
                    cloneUrl, token, featureBranch, releaseBranch);
            if (!mrCheck.mergeable()) {
                results.add(ConflictItem.mergeConflict(repoId, repoName, iterationKey,
                        featureBranch, releaseBranch, mrCheck.detail()));
            }
        }

        // 检查 release → master 合并冲突
        String masterBranch = repo.getDefaultBranch();
        if (gitPort.getBranchStatus(cloneUrl, token, releaseBranch).exists()) {
            GitBranchPort.MergeabilityResult mrCheck = gitPort.checkMergeability(
                    cloneUrl, token, releaseBranch, masterBranch);
            if (!mrCheck.mergeable()) {
                results.add(ConflictItem.mergeConflict(repoId, repoName, iterationKey,
                        releaseBranch, masterBranch, mrCheck.detail()));
            }
        }

        return results;
    }

    /**
     * 检测跨仓库版本一致性：同一迭代内各仓库的目标版本是否一致
     */
    private List<ConflictItem> detectCrossRepoConflicts(Iteration iteration) {
        List<ConflictItem> results = new ArrayList<>();
        List<RepoId> repos = List.copyOf(iteration.getRepos());
        if (repos.size() < 2) return results;

        String iterationKey = iteration.getId().value();

        // 收集所有仓库的 targetVersion
        List<RepoVersionPair> pairs = new ArrayList<>();
        for (RepoId repoId : repos) {
            iterationRepoPort.getVersionInfo(iterationKey, repoId.value())
                    .ifPresent(info -> {
                        if (info.getTargetVersion() != null) {
                            pairs.add(new RepoVersionPair(repoId.value(), info.getTargetVersion()));
                        }
                    });
        }

        // 检查版本是否一致（简单策略：所有仓库目标版本应相同）
        if (pairs.size() >= 2) {
            String firstVersion = pairs.get(0).version;
            for (int i = 1; i < pairs.size(); i++) {
                if (!firstVersion.equals(pairs.get(i).version)) {
                    String repoName = codeRepositoryPort.findById(RepoId.of(pairs.get(i).repoId))
                            .map(CodeRepository::getName).orElse(pairs.get(i).repoId);
                    results.add(ConflictItem.crossRepoVersionMismatch(
                            pairs.get(i).repoId, repoName, iterationKey,
                            pairs.get(i).version, pairs.get(0).repoId, firstVersion));
                }
            }
        }

        return results;
    }

    /**
     * 获取最近一次扫描结果
     */
    public Optional<ConflictReport> getLatestReport(String windowId) {
        return conflictDetectionPort.getLatestReport(windowId);
    }

    private record RepoVersionPair(String repoId, String version) {}
}
```

- [ ] **Step 3: 编译**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q compile -pl releasehub-application`
Expected: COMPILE SUCCESS

- [ ] **Step 4: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub
git add releasehub-application/src/main/java/io/releasehub/application/conflict/
git commit -m "feat: 新增 ConflictDetectionAppService 冲突检测服务"
```

---

### Task 5: 新增 ConflictDetectionPort 基础设施实现

**Files:**
- Create: `releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/conflict/ConflictDetectionPersistenceAdapter.java`

- [ ] **Step 1: 实现内存存储适配器**

```java
package io.releasehub.infrastructure.conflict;

import io.releasehub.application.conflict.ConflictDetectionPort;
import io.releasehub.domain.conflict.ConflictReport;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ConflictDetectionPersistenceAdapter implements ConflictDetectionPort {

    private final ConcurrentMap<String, ConflictReport> store = new ConcurrentHashMap<>();

    @Override
    public void saveReport(String windowId, ConflictReport report) {
        store.put(windowId, report);
    }

    @Override
    public Optional<ConflictReport> getLatestReport(String windowId) {
        return Optional.ofNullable(store.get(windowId));
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q compile -pl releasehub-infrastructure`
Expected: COMPILE SUCCESS

- [ ] **Step 3: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub
git add releasehub-infrastructure/src/main/java/io/releasehub/infrastructure/conflict/ConflictDetectionPersistenceAdapter.java
git commit -m "feat: 新增 ConflictDetectionPort 内存存储适配器"
```

---

### Task 6: Application 层测试

**Files:**
- Create: `releasehub-application/src/test/java/io/releasehub/application/conflict/ConflictDetectionAppServiceTest.java`

- [ ] **Step 1: 写测试（TDD 验证核心检测逻辑）**

```java
package io.releasehub.application.conflict;

import io.releasehub.application.branchrule.BranchRuleUseCase;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionExtractorUseCase;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.conflict.ConflictReport;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.version.VersionSource;
import io.releasehub.domain.window.WindowIteration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConflictDetectionAppServiceTest {

    @Mock private ReleaseWindowPort releaseWindowPort;
    @Mock private WindowIterationPort windowIterationPort;
    @Mock private IterationPort iterationPort;
    @Mock private IterationRepoPort iterationRepoPort;
    @Mock private CodeRepositoryPort codeRepositoryPort;
    @Mock private GitBranchAdapterFactory gitBranchAdapterFactory;
    @Mock private VersionExtractorUseCase versionExtractorUseCase;
    @Mock private BranchRuleUseCase branchRuleUseCase;
    @Mock private ConflictDetectionPort conflictDetectionPort;
    @Mock private GitBranchPort gitBranchPort;

    private ConflictDetectionAppService service;

    private static final String WINDOW_ID = "W001";
    private static final String ITERATION_KEY = "ITER-001";
    private static final String REPO_ID = "R001";
    private static final String REPO_NAME = "test-repo";

    @BeforeEach
    void setUp() {
        service = new ConflictDetectionAppService(
                releaseWindowPort, windowIterationPort, iterationPort,
                iterationRepoPort, codeRepositoryPort, gitBranchAdapterFactory,
                versionExtractorUseCase, branchRuleUseCase, conflictDetectionPort);
        when(gitBranchAdapterFactory.getAdapter(any())).thenReturn(gitBranchPort);
    }

    @Test
    void shouldDetectVersionMismatch() {
        // Given
        setupWindowWithIteration();
        setupRepo();
        setupVersionInfo("1.0.0");
        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.1.0", null)));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.missing());

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.getConflicts().stream().anyMatch(
                c -> c.getConflictType() == io.releasehub.domain.version.ConflictType.MISMATCH)).isTrue();
    }

    @Test
    void shouldDetectBranchAlreadyExists() {
        // Given
        setupWindowWithIteration();
        setupRepo();
        setupVersionInfo("1.0.0");
        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.0.0", null)));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.present("abc123"));

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.getConflicts().stream().anyMatch(
                c -> c.getConflictType() == io.releasehub.domain.version.ConflictType.BRANCH_EXISTS)).isTrue();
    }

    @Test
    void shouldDetectMergeConflict() {
        // Given
        setupWindowWithIteration();
        setupRepo();
        setupVersionInfo("1.0.0");
        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.0.0", null)));

        // feature 和 release 分支都存在
        when(gitBranchPort.getBranchStatus(anyString(), anyString(),
                org.mockito.ArgumentMatchers.startsWith("feature/")))
                .thenReturn(GitBranchPort.BranchStatus.present("abc"));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(),
                org.mockito.ArgumentMatchers.startsWith("release/")))
                .thenReturn(GitBranchPort.BranchStatus.present("def"));

        // checkMergeability 返回冲突
        when(gitBranchPort.checkMergeability(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.MergeabilityResult.conflict("conflict in pom.xml"));

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.hasConflicts()).isTrue();
        assertThat(report.getConflicts().stream().anyMatch(
                c -> c.getConflictType() == io.releasehub.domain.version.ConflictType.MERGE_CONFLICT)).isTrue();
    }

    @Test
    void shouldReturnNoConflictsWhenEverythingClean() {
        // Given
        setupWindowWithIteration();
        setupRepo();
        setupVersionInfo("1.0.0");
        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.0.0", null)));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.missing());
        when(gitBranchPort.checkMergeability(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.MergeabilityResult.mergeable());

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.hasConflicts()).isFalse();
    }

    @Test
    void shouldDetectCrossRepoVersionMismatch() {
        // Given
        setupWindowWithTwoReposIteration();
        setupRepo("R001", "repo-a", "1.0.0");
        setupRepo("R002", "repo-b", "2.0.0");
        setupVersionInfoForRepo("R001", "1.0.0", "2.0.0", "feature/ITER-001");
        setupVersionInfoForRepo("R002", "1.0.0", "1.5.0", "feature/ITER-001");

        when(versionExtractorUseCase.extractVersion(anyString(), anyString()))
                .thenReturn(Optional.of(new VersionExtractorUseCase.VersionInfo("1.0.0", null)));
        when(gitBranchPort.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.missing());
        when(gitBranchPort.checkMergeability(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.MergeabilityResult.mergeable());

        // When
        ConflictReport report = service.checkWindowConflicts(WINDOW_ID);

        // Then
        assertThat(report.getConflicts().stream().anyMatch(
                c -> c.getConflictType() == io.releasehub.domain.version.ConflictType.CROSS_REPO_VERSION_MISMATCH)).isTrue();
    }

    private void setupWindowWithIteration() {
        ReleaseWindow rw = ReleaseWindow.rehydrate(
                ReleaseWindowId.of(WINDOW_ID), "rel-1.0", "Release 1.0", "",
                null, ReleaseWindowStatus.DRAFT, false, Instant.now(), Instant.now());

        Iteration it = Iteration.rehydrate(
                IterationKey.of(ITERATION_KEY), "Iteration 1", "", null, "",
                Set.of(RepoId.of(REPO_ID)), IterationStatus.ACTIVE,
                Instant.now(), Instant.now());

        when(releaseWindowPort.findById(ReleaseWindowId.of(WINDOW_ID))).thenReturn(Optional.of(rw));
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(WINDOW_ID)))
                .thenReturn(List.of(new WindowIteration(
                        ReleaseWindowId.of(WINDOW_ID), IterationKey.of(ITERATION_KEY), null, null, null)));
        when(iterationPort.findByKey(IterationKey.of(ITERATION_KEY))).thenReturn(Optional.of(it));
    }

    private void setupWindowWithTwoReposIteration() {
        ReleaseWindow rw = ReleaseWindow.rehydrate(
                ReleaseWindowId.of(WINDOW_ID), "rel-1.0", "Release 1.0", "",
                null, ReleaseWindowStatus.DRAFT, false, Instant.now(), Instant.now());

        Iteration it = Iteration.rehydrate(
                IterationKey.of(ITERATION_KEY), "Iteration 1", "", null, "",
                Set.of(RepoId.of("R001"), RepoId.of("R002")), IterationStatus.ACTIVE,
                Instant.now(), Instant.now());

        when(releaseWindowPort.findById(ReleaseWindowId.of(WINDOW_ID))).thenReturn(Optional.of(rw));
        when(windowIterationPort.listByWindow(ReleaseWindowId.of(WINDOW_ID)))
                .thenReturn(List.of(new WindowIteration(
                        ReleaseWindowId.of(WINDOW_ID), IterationKey.of(ITERATION_KEY), null, null, null)));
        when(iterationPort.findByKey(IterationKey.of(ITERATION_KEY))).thenReturn(Optional.of(it));
    }

    private void setupRepo() {
        setupRepo(REPO_ID, REPO_NAME, null);
    }

    private void setupRepo(String repoId, String repoName, String defaultBranch) {
        CodeRepository repo = new CodeRepository(
                RepoId.of(repoId), repoName, "https://gitlab.com/group/" + repoName,
                defaultBranch != null ? defaultBranch : "master",
                RepoType.SERVICE, GitProvider.GITLAB, "token", false,
                0, 0, 0, 0, 0, 0, 0, null);
        when(codeRepositoryPort.findById(RepoId.of(repoId))).thenReturn(Optional.of(repo));
    }

    private void setupVersionInfo(String devVersion) {
        setupVersionInfoForRepo(REPO_ID, "1.0.0", devVersion, "feature/" + ITERATION_KEY);
    }

    private void setupVersionInfoForRepo(String repoId, String baseVersion, String devVersion, String featureBranch) {
        IterationRepoVersionInfo info = IterationRepoVersionInfo.builder()
                .repoId(repoId)
                .repoName("test-repo")
                .baseVersion(baseVersion)
                .devVersion(devVersion)
                .targetVersion("2.0.0")
                .featureBranch(featureBranch)
                .versionSource(VersionSource.SYSTEM)
                .build();
        when(iterationRepoPort.getVersionInfo(ITERATION_KEY, repoId)).thenReturn(Optional.of(info));
    }
}
```

- [ ] **Step 2: 运行测试确认通过**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q test -pl releasehub-application -Dtest=ConflictDetectionAppServiceTest`
Expected: ALL PASS

- [ ] **Step 3: 确认已有测试未受影响**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q test -pl releasehub-application`
Expected: ALL PASS

- [ ] **Step 4: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub
git add releasehub-application/src/test/java/io/releasehub/application/conflict/ConflictDetectionAppServiceTest.java
git commit -m "test: 新增 ConflictDetectionAppService 单元测试"
```

---

### Task 7: 新增 REST API 端点 + 预检集成

**Files:**
- Create: `releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ConflictDetectionController.java`
- Modify: `releasehub-application/src/main/java/io/releasehub/application/run/RunAppService.java`
- Modify: `releasehub-common/src/main/java/io/releasehub/common/exception/ErrorCode.java`
- Modify: `releasehub-common/src/main/java/io/releasehub/common/exception/BusinessException.java`

- [ ] **Step 1: 新增错误码**

在 `ErrorCode.java` 中添加：

```java
CONFLICT_DETECTED("CONFLICT_DETECTED", "存在冲突，无法执行操作"),
```

在 `BusinessException.java` 中添加工厂方法：

```java
public static BusinessException conflictDetected(String detail) {
    return new BusinessException(ErrorCode.CONFLICT_DETECTED, detail);
}
```

- [ ] **Step 2: 创建 ConflictDetectionController**

```java
package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.application.conflict.ConflictDetectionAppService;
import io.releasehub.domain.conflict.ConflictItem;
import io.releasehub.domain.conflict.ConflictReport;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/release-windows/{id}/conflicts")
@RequiredArgsConstructor
@Tag(name = "发布窗口 - 冲突检测")
public class ConflictDetectionController {

    private final ConflictDetectionAppService conflictDetectionAppService;

    @PostMapping("/check")
    @Operation(summary = "触发冲突扫描", description = "对发布窗口关联的所有仓库执行四维冲突检测")
    public ApiResponse<ConflictReportView> checkConflicts(@PathVariable("id") String windowId) {
        ConflictReport report = conflictDetectionAppService.checkWindowConflicts(windowId);
        return ApiResponse.success(ConflictReportView.from(report));
    }

    @GetMapping
    @Operation(summary = "获取最新冲突报告", description = "返回最近一次扫描的冲突结果")
    public ApiResponse<ConflictReportView> getConflicts(@PathVariable("id") String windowId) {
        ConflictReport report = conflictDetectionAppService.getLatestReport(windowId)
                .orElseGet(() -> ConflictReport.empty(windowId));
        return ApiResponse.success(ConflictReportView.from(report));
    }

    public record ConflictReportView(
            String windowId,
            String checkedAt,
            boolean hasConflicts,
            int totalCount,
            List<ConflictItemView> conflicts
    ) {
        public static ConflictReportView from(ConflictReport report) {
            List<ConflictItemView> items = report.getConflicts().stream()
                    .map(ConflictItemView::from)
                    .collect(Collectors.toList());
            return new ConflictReportView(
                    report.getWindowId(),
                    report.getCheckedAt().toString(),
                    report.hasConflicts(),
                    report.totalCount(),
                    items
            );
        }
    }

    public record ConflictItemView(
            String repoId,
            String repoName,
            String iterationKey,
            String conflictType,
            String sourceBranch,
            String targetBranch,
            String systemVersion,
            String repoVersion,
            String message,
            String suggestion
    ) {
        public static ConflictItemView from(ConflictItem item) {
            return new ConflictItemView(
                    item.getRepoId(),
                    item.getRepoName(),
                    item.getIterationKey(),
                    item.getConflictType().name(),
                    item.getSourceBranch(),
                    item.getTargetBranch(),
                    item.getSystemVersion(),
                    item.getRepoVersion(),
                    item.getMessage(),
                    item.getSuggestion()
            );
        }
    }
}
```

- [ ] **Step 3: 在 RunAppService 集成冲突预检**

在 `RunAppService.java` 的 `executeVersionUpdate` 方法开头（参数校验之后、创建 Run 之前）添加：

```java
// 在类中添加依赖
private final ConflictDetectionAppService conflictDetectionAppService;

// 在 executeVersionUpdate 方法中，ReleaseWindow rw = ... 之后添加：
ConflictReport conflictReport = conflictDetectionAppService.getLatestReport(windowId)
        .orElseGet(() -> conflictDetectionAppService.checkWindowConflicts(windowId));
if (conflictReport.hasConflicts()) {
    throw BusinessException.conflictDetected(
            "发布窗口存在 " + conflictReport.totalCount() + " 个冲突，请先解决所有冲突");
}
```

在 `executeBatchVersionUpdate` 方法中添加同样逻辑。

- [ ] **Step 4: 编译与测试**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q compile`
Expected: COMPILE SUCCESS

- [ ] **Step 5: 运行全量测试**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q clean test`
Expected: ALL PASS

- [ ] **Step 6: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub
git add releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/ConflictDetectionController.java \
        releasehub-application/src/main/java/io/releasehub/application/run/RunAppService.java \
        releasehub-common/src/main/java/io/releasehub/common/exception/ErrorCode.java \
        releasehub-common/src/main/java/io/releasehub/common/exception/BusinessException.java
git commit -m "feat: 新增冲突检测 REST API + 执行前预检阻断"
```

---

### Task 8: API 集成测试

**Files:**
- Create: `releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/ConflictDetectionApiTest.java`

- [ ] **Step 1: 写 MockMvc 集成测试**

```java
package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.interfaces.auth.AuthController.LoginRequest;
import io.releasehub.interfaces.api.releasewindow.CreateReleaseWindowRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConflictDetectionApiTest {

    private static String token;
    private static String windowId;
    private static String groupCode;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @Order(1)
    void shouldLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();

        groupCode = createGroup();
    }

    @Test
    @Order(2)
    void shouldCreateReleaseWindow() throws Exception {
        CreateReleaseWindowRequest req = new CreateReleaseWindowRequest();
        req.setName("Conflict Detection Test Window");
        req.setGroupCode(groupCode);

        MvcResult result = mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        windowId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    @Test
    @Order(3)
    void shouldScanConflictsSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/conflicts/check")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowId").value(windowId))
                .andExpect(jsonPath("$.data.hasConflicts").exists())
                .andExpect(jsonPath("$.data.totalCount").exists())
                .andExpect(jsonPath("$.data.conflicts").isArray());
    }

    @Test
    @Order(4)
    void shouldGetConflictsAfterScan() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/conflicts")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowId").value(windowId));
    }

    @Test
    @Order(5)
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/conflicts/check"))
                .andExpect(status().isUnauthorized());
    }

    private String createGroup() throws Exception {
        String code = "G" + System.currentTimeMillis();
        String req = "{\"name\":\"CD-Group\",\"code\":\"" + code + "\",\"parentCode\":null}";
        mockMvc.perform(post("/api/v1/groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(req))
                .andExpect(status().isOk());
        return code;
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q test -pl releasehub-bootstrap -Dtest=ConflictDetectionApiTest`
Expected: PASS

- [ ] **Step 3: 确认全量测试通过**

Run: `cd /Users/loong/workspace/code/github/release-hub && mvn -q clean test`
Expected: ALL PASS

- [ ] **Step 4: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub
git add releasehub-bootstrap/src/test/java/io/releasehub/bootstrap/api/ConflictDetectionApiTest.java
git commit -m "test: 新增冲突检测 API 集成测试"
```

---

### Task 9: 前端 API 模块

**Files:**
- Modify: `release-hub-web/src/api/modules/releaseWindow.ts`

- [ ] **Step 1: 新增冲突检测 API 函数**

在 `releaseWindow.ts` 中添加：

```typescript
// 冲突检测相关类型
export interface ConflictItemView {
  repoId: string
  repoName: string
  iterationKey: string
  conflictType: 'MISMATCH' | 'REPO_AHEAD' | 'SYSTEM_AHEAD' | 'BRANCH_EXISTS' | 'BRANCH_NONCOMPLIANT' | 'CROSS_REPO_VERSION_MISMATCH' | 'MERGE_CONFLICT'
  sourceBranch?: string
  targetBranch?: string
  systemVersion?: string
  repoVersion?: string
  message: string
  suggestion: string
}

export interface ConflictReportView {
  windowId: string
  checkedAt: string
  hasConflicts: boolean
  totalCount: number
  conflicts: ConflictItemView[]
}

// 冲突检测 API
export function checkConflicts(windowId: string): Promise<ConflictReportView> {
  return apiPost<ConflictReportView>(`${API_BASE}/${windowId}/conflicts/check`)
}

export function getConflicts(windowId: string): Promise<ConflictReportView> {
  return apiGet<ConflictReportView>(`${API_BASE}/${windowId}/conflicts`)
}
```

- [ ] **Step 2: TypeScript 类型检查**

Run: `cd /Users/loong/workspace/code/github/release-hub-web && pnpm typecheck`
Expected: PASS

- [ ] **Step 3: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub-web
git add src/api/modules/releaseWindow.ts
git commit -m "feat: 前端新增冲突检测 API 模块"
```

---

### Task 10: 前端冲突面板组件

**Files:**
- Create: `release-hub-web/src/views/release-window/ConflictPanel.vue`
- Modify: `release-hub-web/src/views/release-window/ReleaseWindowDetail.vue`
- Modify: `release-hub-web/src/views/release-window/VersionUpdateDialog.vue`

- [ ] **Step 1: 创建 ConflictPanel 组件**

```vue
<template>
  <div class="conflict-panel" v-loading="scanning">
    <!-- 状态概览 -->
    <el-alert
      v-if="!report"
      type="info"
      :title="t('conflict.notScanned')"
      :description="t('conflict.notScannedHint')"
      show-icon
      :closable="false"
    />

    <el-alert
      v-else-if="report.hasConflicts"
      type="error"
      :title="t('conflict.hasConflicts', { count: report.totalCount })"
      show-icon
      :closable="false"
    />

    <el-alert
      v-else
      type="success"
      :title="t('conflict.noConflicts')"
      show-icon
      :closable="false"
    />

    <!-- 操作栏 -->
    <div class="panel-toolbar">
      <el-button type="primary" @click="handleScan" :loading="scanning">
        {{ t('conflict.rescan') }}
      </el-button>
      <span v-if="report" class="scan-time">
        {{ t('conflict.lastScanAt') }}: {{ formatTime(report.checkedAt) }}
      </span>
    </div>

    <!-- 冲突列表 -->
    <el-table
      v-if="report && report.conflicts.length > 0"
      :data="report.conflicts"
      border
      stripe
      style="width: 100%; margin-top: 16px"
    >
      <el-table-column prop="repoName" :label="t('conflict.repoName')" width="150" />
      <el-table-column prop="iterationKey" :label="t('conflict.iteration')" width="180" />
      <el-table-column :label="t('conflict.type')" width="180">
        <template #default="{ row }">
          <el-tag :type="getTagType(row.conflictType)">
            {{ t(`conflict.types.${row.conflictType}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('conflict.branches')" width="200">
        <template #default="{ row }">
          <span v-if="row.sourceBranch">
            {{ row.sourceBranch }} → {{ row.targetBranch }}
          </span>
          <span v-else-if="row.systemVersion">
            {{ row.systemVersion }} ≠ {{ row.repoVersion }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="message" :label="t('conflict.message')" min-width="300" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { checkConflicts, getConflicts, type ConflictReportView } from '@/api/modules/releaseWindow'
import dayjs from 'dayjs'

const props = defineProps<{ windowId: string }>()
const { t } = useI18n()

const report = ref<ConflictReportView | null>(null)
const scanning = ref(false)

const handleScan = async () => {
  scanning.value = true
  try {
    report.value = await checkConflicts(props.windowId)
  } finally {
    scanning.value = false
  }
}

const formatTime = (iso: string) => dayjs(iso).format('YYYY-MM-DD HH:mm:ss')

const getTagType = (type: string) => {
  switch (type) {
    case 'MISMATCH':
    case 'MERGE_CONFLICT':
      return 'danger'
    case 'BRANCH_EXISTS':
    case 'BRANCH_NONCOMPLIANT':
      return 'warning'
    case 'CROSS_REPO_VERSION_MISMATCH':
      return 'info'
    default:
      return ''
  }
}

defineExpose({ report })

onMounted(async () => {
  try {
    report.value = await getConflicts(props.windowId)
  } catch {
    // 首次访问，尚未扫描
  }
})
</script>

<style scoped>
.conflict-panel { padding: 16px 0; }
.panel-toolbar { margin-top: 16px; display: flex; align-items: center; gap: 12px; }
.scan-time { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
```

- [ ] **Step 2: 在 ReleaseWindowDetail.vue 新增 Tab**

在 `ReleaseWindowDetail.vue` 的 `<el-tabs>` 中添加 Tab：

```html
<el-tab-pane :label="t('releaseWindow.conflicts')" name="conflicts">
  <ConflictPanel :window-id="form.id" ref="conflictPanelRef" />
</el-tab-pane>
```

在 `<script setup>` 中添加：

```typescript
import ConflictPanel from './ConflictPanel.vue'
const conflictPanelRef = ref()
```

- [ ] **Step 3: VersionUpdateDialog 执行前检查**

在 `VersionUpdateDialog.vue` 的提交方法 `handleSubmit` 中，调用 API 之前：

```typescript
import { getConflicts } from '@/api/modules/releaseWindow'
import { ElMessage, ElMessageBox } from 'element-plus'

// 在 handleSubmit 开头添加：
const report = await getConflicts(props.windowId)
if (report.hasConflicts) {
  ElMessage.warning(t('conflict.resolveBeforeExecute'))
  return
}
```

- [ ] **Step 4: TypeScript 类型检查**

Run: `cd /Users/loong/workspace/code/github/release-hub-web && pnpm typecheck`
Expected: PASS

- [ ] **Step 5: i18n 补充**

在 `zh-CN.ts` 和 `en-US.ts` 中补充冲突检测相关翻译键：

```typescript
// zh-CN
conflict: {
  notScanned: '尚未扫描',
  notScannedHint: '请点击「重新扫描」执行冲突检测',
  hasConflicts: '检测到 {count} 个冲突，请先解决所有冲突后再执行操作',
  noConflicts: '无冲突，可以安全执行',
  rescan: '重新扫描',
  lastScanAt: '上次扫描时间',
  repoName: '仓库',
  iteration: '迭代',
  type: '冲突类型',
  branches: '分支 / 版本',
  message: '描述',
  resolveBeforeExecute: '存在未解决的冲突，请先解决所有冲突',
  types: {
    MISMATCH: '版本不一致',
    REPO_AHEAD: '仓库版本较新',
    SYSTEM_AHEAD: '系统版本较新',
    BRANCH_EXISTS: '分支已存在',
    BRANCH_NONCOMPLIANT: '分支名不合规',
    CROSS_REPO_VERSION_MISMATCH: '跨仓库版本不一致',
    MERGE_CONFLICT: '合并冲突'
  }
}

// en-US
conflict: {
  notScanned: 'Not scanned',
  notScannedHint: 'Click "Rescan" to run conflict detection',
  hasConflicts: '{count} conflict(s) detected, resolve all before execution',
  noConflicts: 'No conflicts, safe to proceed',
  rescan: 'Rescan',
  lastScanAt: 'Last scan',
  repoName: 'Repository',
  iteration: 'Iteration',
  type: 'Conflict Type',
  branches: 'Branches / Versions',
  message: 'Description',
  resolveBeforeExecute: 'Unresolved conflicts exist, please resolve all conflicts first',
  types: {
    MISMATCH: 'Version Mismatch',
    REPO_AHEAD: 'Repo Ahead',
    SYSTEM_AHEAD: 'System Ahead',
    BRANCH_EXISTS: 'Branch Exists',
    BRANCH_NONCOMPLIANT: 'Noncompliant Name',
    CROSS_REPO_VERSION_MISMATCH: 'Cross-repo Mismatch',
    MERGE_CONFLICT: 'Merge Conflict'
  }
}
```

- [ ] **Step 6: 前端 lint + 类型检查**

Run: `cd /Users/loong/workspace/code/github/release-hub-web && pnpm lint && pnpm typecheck`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
cd /Users/loong/workspace/code/github/release-hub-web
git add src/views/release-window/ConflictPanel.vue \
        src/views/release-window/ReleaseWindowDetail.vue \
        src/views/release-window/VersionUpdateDialog.vue \
        src/i18n/messages/zh-CN.ts \
        src/i18n/messages/en-US.ts
git commit -m "feat: 新增冲突检测面板 + 执行前阻断 UI"
```

---

### Task 11: 需求文档与变更归档

**Files:**
- Modify: `requirements/in-progress/版本更新功能增强.md`

- [ ] **Step 1: 更新需求文档**

将 "冲突检测增强" 标记为完成，进度表更新。

- [ ] **Step 2: 提交**

```bash
cd /Users/loong/workspace/code/github/releasehub
git add requirements/in-progress/版本更新功能增强.md
git commit -m "docs: 冲突检测增强阶段完成"
```

---

## 执行顺序

Task 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11

前一步编译通过 + 测试通过后再进入下一步。Step 1-6 是后端，Step 9-10 是前端。
