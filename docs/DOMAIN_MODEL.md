# ReleaseHub 领域模型文档

> 最后更新：2026-01-12

## 一、领域概述

ReleaseHub 采用 DDD（领域驱动设计）架构，核心解决多仓库发布协调的三大成本：
- **认知成本**：版本、分支、窗口分散在各仓库，信息不可追溯
- **协调成本**：发布窗口冲突、跨仓版本不一致
- **执行成本**：版本号更新依赖人工，易错且难规模化

## 二、核心聚合根

### 2.1 ReleaseWindow（发布窗口）

**职责**：定义一次发布的时间窗口，管理发布状态流转。

```java
public class ReleaseWindow extends BaseEntity<ReleaseWindowId> {
    private final String windowKey;      // 唯一标识 key
    private final String name;           // 名称
    private ReleaseWindowStatus status;  // 状态
    private Instant startAt;             // 开始时间
    private Instant endAt;               // 结束时间
    private boolean frozen;              // 是否冻结
    private Instant publishedAt;         // 发布时间
}
```

**状态流转**：
```
DRAFT（草稿）→ PUBLISHED（已发布）→ RELEASED（已发布完成）→ CLOSED（已关闭）
```

**业务规则**：
- `windowKey` 和 `name` 必填，长度限制
- `startAt` 必须早于 `endAt`
- 冻结后禁止配置变更
- 仅 DRAFT 可发布，需先配置时间窗口
- 仅 PUBLISHED 可 release
- 仅 RELEASED 可 close

---

### 2.2 Iteration（迭代）

**职责**：代表一个冲刺/迭代周期，聚合一组相关的代码仓库。

```java
public class Iteration extends BaseEntity<IterationKey> {
    private final String description;    // 描述
    private final Set<RepoId> repos;     // 包含的仓库集合
}
```

**业务规则**：
- `IterationKey` 必填
- 同一迭代可被多个发布窗口复用
- 迭代作为发布窗口和仓库之间的桥梁

---

### 2.3 CodeRepository（代码仓库）

**职责**：代表一个 Git 仓库实体，集成 GitLab。

```java
public class CodeRepository extends BaseEntity<RepoId> {
    private final ProjectId projectId;       // 所属项目
    private Long gitlabProjectId;            // GitLab 项目 ID
    private String name;                     // 名称
    private String cloneUrl;                 // 克隆地址
    private String defaultBranch;            // 默认分支
    private boolean monoRepo;                // 是否单体仓库
    // 统计信息
    private int branchCount;
    private int activeBranchCount;
    private int nonCompliantBranchCount;
    private int mrCount;
    private int openMrCount;
    private int mergedMrCount;
    private int closedMrCount;
    private Instant lastSyncAt;
}
```

**业务规则**：
- `projectId`、`gitlabProjectId`、`cloneUrl`、`defaultBranch` 必填
- 名称长度限制 128 字符
- URL 长度限制 512 字符

---

### 2.4 Group（分组）

**职责**：按层级组织项目/发布。

```java
public class Group extends BaseEntity<GroupId> {
    private final String code;           // 分组代码
    private String name;                 // 名称
    private String parentCode;           // 父分组代码
}
```

**业务规则**：
- `parentCode` 不能等于自身 `code`
- 支持父子层级关系

---

### 2.5 Run（运行记录）

**职责**：记录发布运行与步骤，保留历史。

```java
public class Run extends BaseEntity<RunId> {
    private final RunType runType;           // 运行类型
    private final String operator;           // 操作者
    private RunStatus status;                // 状态
    private final List<RunItem> items;       // 执行项列表
    private Instant startAt;
    private Instant endAt;
}

public class RunItem {
    private final String windowName;         // 窗口名称
    private final RepoId repoId;             // 仓库 ID
    private final IterationKey iterationKey; // 迭代 Key
    private final int plannedOrder;          // 计划顺序
    private int executedOrder;               // 执行顺序
    private RunItemResult result;            // 结果
    private final List<RunStep> steps;       // 步骤列表
}

public class RunStep {
    private final ActionType actionType;     // 动作类型
    private final RunItemResult result;      // 结果
    private final Instant startAt;
    private final Instant endAt;
    private final String message;            // 消息（包含 diff）
}
```

**运行类型**：
- `WINDOW_ORCHESTRATION`：窗口编排
- `VERSION_UPDATE`：版本更新

---

### 2.6 VersionPolicy（版本策略）

**职责**：定义版本号递增策略。

```java
public class VersionPolicy {
    private final VersionPolicyId id;
    private final String name;               // 策略名称
    private final VersionScheme scheme;      // 版本方案（SEMVER/DATE）
    private final BumpRule bumpRule;         // 递增规则
}
```

**内置策略**：
- `MAJOR`：主版本递增（1.0.0 → 2.0.0）
- `MINOR`：次版本递增（1.0.0 → 1.1.0）
- `PATCH`：补丁版本递增（1.0.0 → 1.0.1）
- `DATE`：日期版本（如 2026.01.12）

---

## 三、关联实体

### 3.1 WindowIteration（窗口迭代关联）

**职责**：连接发布窗口和迭代的多对多关系。

```java
public class WindowIteration extends BaseEntity<String> {
    private final ReleaseWindowId windowId;     // 发布窗口 ID
    private final IterationKey iterationKey;    // 迭代 Key
    private final Instant attachAt;             // 关联时间
}
```

**业务规则**：
- ID 为 `windowId::iterationKey` 组合
- `attachAt` 用于排序执行顺序

---

## 四、实体关系图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           ReleaseHub 领域模型关系                             │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────────────┐        N:N           ┌─────────────────┐               │
│   │  ReleaseWindow  │◄────────────────────►│    Iteration    │               │
│   │    (发布窗口)    │   WindowIteration    │     (迭代)       │               │
│   │                 │                      │                 │               │
│   │  - windowKey    │                      │  - key          │               │
│   │  - name         │                      │  - description  │               │
│   │  - status       │                      │  - repos: Set   │               │
│   │  - startAt      │                      └────────┬────────┘               │
│   │  - endAt        │                               │                        │
│   │  - frozen       │                               │ N:N (包含)              │
│   └────────┬────────┘                               ▼                        │
│            │                                ┌─────────────────┐              │
│            │ 1:N (执行)                     │  CodeRepository │   N:1        │
│            ▼                                │   (代码仓库)     │◄────────┐    │
│   ┌─────────────────┐                       │                 │         │    │
│   │      Run        │                       │  - projectId    │   ┌─────┴──┐ │
│   │   (运行记录)     │                       │  - gitlabId     │   │ Project│ │
│   │                 │                       │  - name         │   │  (项目) │ │
│   │  - runType      │                       │  - cloneUrl     │   └────────┘ │
│   │  - operator     │                       │  - defaultBranch│              │
│   │  - status       │                       └─────────────────┘              │
│   │  - items[]      │                                                        │
│   └─────────────────┘                                                        │
│            │                                                                 │
│            │ 1:N                                                             │
│            ▼                                                                 │
│   ┌─────────────────┐         1:N          ┌─────────────────┐               │
│   │    RunItem      │─────────────────────►│    RunStep      │               │
│   │   (执行项)       │                      │    (执行步骤)    │               │
│   │                 │                      │                 │               │
│   │  - repoId       │                      │  - actionType   │               │
│   │  - iterationKey │                      │  - result       │               │
│   │  - result       │                      │  - message      │               │
│   └─────────────────┘                      └─────────────────┘               │
│                                                                              │
│                                                                              │
│   ┌─────────────────┐                      ┌─────────────────┐               │
│   │  VersionPolicy  │                      │     Group       │               │
│   │   (版本策略)     │                      │     (分组)       │               │
│   │                 │                      │                 │               │
│   │  - name         │                      │  - code         │               │
│   │  - scheme       │                      │  - name         │               │
│   │  - bumpRule     │                      │  - parentCode   │               │
│   └─────────────────┘                      └─────────────────┘               │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 五、核心业务流程

### 5.1 发布窗口执行流程

```
1. 创建发布窗口（DRAFT）
   └── ReleaseWindow.createDraft(key, name)

2. 配置时间窗口
   └── ReleaseWindow.configureWindow(startAt, endAt)

3. 关联迭代（Attach）
   └── WindowIteration.attach(windowId, iterationKey)
   └── 可关联多个迭代，按 attachAt 排序

4. 发布窗口（DRAFT → PUBLISHED）
   └── ReleaseWindow.publish()

5. 执行版本更新
   └── RunAppService.executeVersionUpdate(windowId, repoId, targetVersion, ...)
   └── 或批量：RunAppService.executeBatchVersionUpdate(...)
   └── 创建 Run 记录，包含 RunItem 和 RunStep

6. 查看执行结果
   └── Run 详情页展示 diff 和执行状态

7. 完成发布（PUBLISHED → RELEASED → CLOSED）
   └── ReleaseWindow.release()
   └── ReleaseWindow.close()
```

### 5.2 版本更新流程

```
1. 接收版本更新请求
   └── VersionUpdateController.executeVersionUpdate()

2. 验证发布窗口和仓库存在
   └── releaseWindowPort.findById()
   └── codeRepositoryPort.findById()

3. 创建版本更新请求
   └── VersionUpdateRequest.forMaven() 或 .forGradle()

4. 选择版本更新器
   └── VersionUpdateAppService.updateVersion()
   └── 根据 BuildTool 选择 MavenVersionUpdater 或 GradleVersionUpdater

5. 执行版本更新
   └── MavenVersionUpdater: 解析 pom.xml，更新 <version>，生成 diff
   └── GradleVersionUpdater: 更新 gradle.properties 中的 version

6. 记录执行结果
   └── 创建 Run、RunItem、RunStep
   └── 保存 diff 到 RunStep.message

7. 返回执行结果
   └── 包含 runId、状态、diff
```

---

## 六、DDD 分层架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              releasehub-bootstrap                            │
│                         （Spring Boot 启动模块）                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                        releasehub-interfaces                         │   │
│   │                     （REST 控制器、DTO）                              │   │
│   │   - ReleaseWindowController                                          │   │
│   │   - VersionUpdateController                                          │   │
│   │   - RunController                                                    │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                        releasehub-application                        │   │
│   │               （应用服务、用例编排、Port 接口）                        │   │
│   │   - ReleaseWindowAppService                                          │   │
│   │   - RunAppService                                                    │   │
│   │   - VersionUpdateAppService                                          │   │
│   │   - AttachAppService                                                 │   │
│   │   - Port 接口：ReleaseWindowPort, CodeRepositoryPort, RunPort...     │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                          releasehub-domain                           │   │
│   │              （聚合根、实体、值对象、领域服务）                         │   │
│   │   - ReleaseWindow（聚合根）                                          │   │
│   │   - Iteration（聚合根）                                              │   │
│   │   - CodeRepository（聚合根）                                         │   │
│   │   - Run（聚合根）                                                    │   │
│   │   - VersionPolicy、Group...                                          │   │
│   │   ⚠️ 禁止依赖 Spring/JPA/Hibernate                                   │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                       releasehub-infrastructure                      │   │
│   │              （JPA 实现、外部系统适配器）                              │   │
│   │   - ReleaseWindowPersistenceAdapter                                  │   │
│   │   - CodeRepositoryPersistenceAdapter                                 │   │
│   │   - MavenVersionUpdater                                              │   │
│   │   - GradleVersionUpdater                                             │   │
│   │   - GitLabAdapter                                                    │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                          releasehub-common                           │   │
│   │                    （通用工具、异常定义）                              │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**架构约束**（由 ArchUnit + Maven Enforcer 强制执行）：
- `domain` 层禁止依赖 Spring/JPA/Hibernate
- `infrastructure` 禁止依赖 `interfaces`
- `bootstrap` 禁止直接依赖 `domain/application/common`

---

## 七、术语表（统一语言）

| 术语 | 英文 | 描述 |
|------|------|------|
| 发布窗口 | ReleaseWindow | 具有目标版本、状态流转和范围的有时限发布 |
| 窗口迭代 | WindowIteration | 关联发布窗口与迭代（N:N 关系） |
| 迭代 | Iteration | 代表一个冲刺/迭代及其关联的仓库 |
| 分支规则 | BranchRule | 用于分支命名验证的模板/正则表达式 |
| 版本策略 | VersionPolicy | 定义版本递增策略（语义化版本、日期版本） |
| 运行记录 | Run | 版本更新的执行记录 |
| 执行项 | RunItem | 单个仓库的执行记录 |
| 执行步骤 | RunStep | 执行项中的具体步骤 |
| 代码仓库 | CodeRepository | Git 仓库实体（集成 GitLab） |
| 分组 | Group | 项目的层级组织单元 |
| 版本更新器 | VersionUpdater | 版本更新执行器（Maven/Gradle） |
