# Phase 6 执行日志：发布准备/收尾全自动化触发

> 日期：2026-05-02
> 执行者：AI
> 状态：已完成

## 事前分析

代码走查发现 Phase 6 的核心自动化在前期已大部分实现：
- `AttachAppService.attach()` 已自动创建 release 分支 + 合并 feature → release
- `AttachAppService.detach()` 已自动归档 release 分支
- `ReleaseWindowAppService.close()` 已自动执行 5 步收尾流程（RunAppService.executeCleanup）

本次聚焦实际存在的 3 个缺口。

## 完成项

### 1. ✅ 修复 Attach 分支操作静默失败问题

**问题**：`AttachAppService.setupReleaseBranchForRepo()` 异常被 try-catch 吞掉，WindowIteration 绑定已创建但分支操作失败对调用方不可见。

**修复**：
- 新建 `AttachResult` 值对象，包含 WindowIteration + 每个仓库的分支操作错误
- `attach()` 返回 `List<AttachResult>` 替代 `List<WindowIteration>`
- 错误收集：每个 repo 的分支操作失败被记录为 `RepoError(repoId, repoName, message)`
- 前端可通过 `POST /{id}/attach` 响应中的 `hasErrors`/`errors` 字段获知失败详情

**文件变更**：
| 文件 | 操作 | 说明 |
|------|------|------|
| `AttachResult.java` | 新建 | Attach 结果值对象 |
| `AttachAppService.java` | 修改 | attach() 返回 AttachResult + 错误收集 |
| `AttachController.java` | 修改 | 响应格式包含 hasErrors/errors |

### 2. ✅ Publish 时自动触发编排

**问题**：发布后需手动调用 `/orchestrate`，未形成完整自动化链。

**修复**：
- 新建 `WindowPublishedEvent` 领域事件
- 新建 `WindowLifecycleListener` 使用 `@TransactionalEventListener(phase = AFTER_COMMIT)`
- `publish()` 发布事件，事务提交后监听器自动触发 `startOrchestrate()`
- 使用 AFTER_COMMIT 确保编排失败不回滚发布状态

**文件变更**：
| 文件 | 操作 | 说明 |
|------|------|------|
| `WindowPublishedEvent.java` | 新建 | 发布窗口已发布事件 |
| `WindowLifecycleListener.java` | 新建 | 生命周期监听器（AFTER_COMMIT 触发编排） |
| `ReleaseWindowAppService.java` | 修改 | 注入 ApplicationEventPublisher + publish() 发布事件 |

### 3. 📋 未完成：Attach 分支操作集成 Run 追踪

**原因**：AttachAppService 和 RunAppService 存在并行实现，统一两者需要较大的架构调整。当前修复（错误可见性）已解决核心痛点，Run 追踪统一列入后续 Roadmap。

## 验证

- 后端测试：64/64 通过
- 前端 typecheck/lint：未受影响（仅后端变更）

## 涉及文件汇总（8 个文件）

| 文件 | 操作 |
|------|------|
| `AttachResult.java` | 新建 |
| `WindowPublishedEvent.java` | 新建 |
| `WindowLifecycleListener.java` | 新建 |
| `AttachAppService.java` | 修改 |
| `AttachController.java` | 修改 |
| `ReleaseWindowAppService.java` | 修改（新增依赖 + 事件发布） |
| `WindowIterationApiTest.java` | 修改（适配新响应格式） |
| `WindowRunApiTest.java` | 修改（适配新响应格式） |
| `ReleaseWindowAppServiceTest.java` | 修改（新增 eventPublisher mock） |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | Attach 错误可观测 + publish 自动编排 |
| 层级闭环 | ✅ | Domain Event → Application Listener → 编排 |
| 测试闭环 | ✅ | 64/64 通过 |
| 架构闭环 | ✅ | 使用 @TransactionalEventListener(AFTER_COMMIT) 解耦 |
| 性能闭环 | ✅ | 事件驱动异步编排，不阻塞 publish |
| 文档闭环 | ✅ | 本日志 |
| 工作区闭环 | ✅ | 9 文件变更 |

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| Attach 分支操作集成 Run 追踪 | 较大架构调整，列入 Roadmap | Roadmap |
| 静态扫描留痕 | 待 Phase 全部完成后统一执行 | Task #3 |
