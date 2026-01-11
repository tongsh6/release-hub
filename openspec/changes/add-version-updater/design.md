# 版本更新功能技术设计

## Context
ReleaseHub 需要支持自动更新 Maven/Gradle 项目的版本号，这是核心 MVP 功能。版本更新需要：
1. 支持多种构建工具（Maven、Gradle）
2. 生成更新前后的 diff
3. 集成到现有的 Run 执行流程
4. 可扩展以支持未来更多构建工具

## Goals / Non-Goals

### Goals
- 实现 Maven pom.xml 版本更新（单模块和多模块）
- 实现 Gradle gradle.properties 版本更新
- 生成清晰的 diff 输出
- 集成到 Run 执行流程，支持执行记录和错误处理
- 遵循 DDD 分层架构（Port/Adapter 模式）

### Non-Goals
- Git 操作（commit、push）- 仅做文件改写，Git 操作后续实现
- 复杂的 Gradle build.gradle AST 解析 - MVP 先支持 properties，build.gradle 给出提示
- 版本回滚机制 - MVP 先不做，后续扩展
- 子项目版本独立管理 - MVP 先支持统一版本

## Decisions

### Decision 1: VersionUpdater Port 接口设计
**What**: 在 `releasehub-application` 层定义 `VersionUpdater` Port 接口

**Why**: 
- 遵循 DDD Port/Adapter 模式
- 支持多种构建工具的可插拔实现
- 保持领域层和应用层的纯净

**接口设计**:
```java
public interface VersionUpdater {
    VersionUpdateResult update(VersionUpdateRequest request);
    boolean supports(BuildTool buildTool);
}
```

**Alternatives considered**:
- 直接在 infrastructure 实现：违反 DDD 分层原则
- 使用策略模式在 domain 层：domain 层不应依赖基础设施概念

### Decision 2: Maven 版本更新实现策略
**What**: 使用 DOM 解析 pom.xml，更新 `<version>` 标签

**Why**:
- DOM 解析稳定可靠
- 可以精确控制更新位置
- 支持多模块场景

**实现细节**:
- 单模块：直接更新 `<project><version>`
- 多模块：更新父 POM 版本，子模块继承（如果子模块有显式 version，提供策略：保持/统一/报错）

**Alternatives considered**:
- SAX 解析：流式处理，但代码复杂度更高
- 正则表达式：不够可靠，容易出错
- Maven Model API：需要 Maven 依赖，增加耦合

### Decision 3: Gradle 版本更新实现策略
**What**: MVP 优先支持 `gradle.properties`，`build.gradle` 给出明确提示

**Why**:
- gradle.properties 是标准做法，解析简单
- build.gradle 需要 AST 解析，复杂度高
- MVP 先保证核心功能可用

**实现细节**:
- gradle.properties: 使用 Properties API 更新 `version=` 属性
- build.gradle: 检测到版本定义时返回错误提示

**Alternatives considered**:
- 使用 Gradle Tooling API：需要 Gradle 运行时，复杂度高
- AST 解析：需要第三方库，增加依赖

### Decision 4: Diff 生成策略
**What**: 使用字符串对比生成 diff（如 diff-match-patch 或简单的前后对比）

**Why**:
- 简单直接，易于实现
- 可以展示清晰的变更内容
- 支持在 UI 中展示

**实现细节**:
- 存储更新前后的文件内容
- 生成行级别的 diff
- 在 RunItem 中存储 diff 信息

**Alternatives considered**:
- 使用专业的 diff 库（如 java-diff-utils）：功能强大但增加依赖
- 仅存储变更摘要：信息不够详细

### Decision 5: 版本更新与 Run 流程集成
**What**: 版本更新作为 Run 的一个执行步骤（RunStep）

**Why**:
- 复用现有的 Run 执行记录机制
- 统一的错误处理和状态追踪
- 支持执行历史查看

**实现细节**:
- RunType: `VERSION_UPDATE`
- RunStep actionType: `UPDATE_VERSION`
- RunStep 存储版本更新结果和 diff

**Alternatives considered**:
- 独立的版本更新记录表：增加复杂度，不利于统一管理
- 仅记录成功/失败：信息不够详细

## Risks / Trade-offs

### Risk 1: 多模块 Maven 项目版本一致性
**Risk**: 不同项目可能有不同的版本管理策略（统一版本 vs 独立版本）

**Mitigation**: 
- MVP 先支持统一版本场景
- 复杂场景提供明确的错误提示和文档
- 后续版本支持配置化策略

### Risk 2: Gradle 版本定义方式多样
**Risk**: Gradle 项目可能使用不同的版本定义方式（properties、build.gradle、settings.gradle）

**Mitigation**:
- MVP 先支持最常见的 gradle.properties
- 其他方式给出明确提示
- 后续版本逐步支持

### Risk 3: 文件解析错误
**Risk**: pom.xml 或 gradle.properties 格式异常导致解析失败

**Mitigation**:
- 完善的错误处理和异常捕获
- 详细的错误信息提示
- 单元测试覆盖异常场景

### Risk 4: 性能问题
**Risk**: 大量仓库同时更新版本可能导致性能问题

**Mitigation**:
- MVP 先支持单仓库更新
- 后续版本支持批量更新和异步处理
- 监控执行时间和资源使用

## Migration Plan

### Phase 1: 接口和基础实现
1. 创建 VersionUpdater Port 接口
2. 实现 Maven VersionUpdater（单模块）
3. 实现基础测试

### Phase 2: 多模块和 Gradle 支持
1. 扩展 Maven 支持多模块
2. 实现 Gradle VersionUpdater
3. 集成测试

### Phase 3: 服务集成和 API
1. 创建 VersionUpdateAppService
2. 集成到 Run 流程
3. 实现 API 接口

### Phase 4: 前端集成
1. 版本更新 UI
2. 执行结果展示
3. Diff 查看

## Open Questions
1. 是否需要支持版本回滚？→ MVP 先不做
2. 子项目版本是否独立管理？→ MVP 先统一版本
3. 是否需要支持版本号预览？→ 后续版本考虑
4. Git 操作何时集成？→ 后续版本，当前仅做文件改写
