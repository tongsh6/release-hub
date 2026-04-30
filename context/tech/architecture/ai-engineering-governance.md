# AI 工程治理准则

> 目标：让任何 AI 进入 ReleaseHub 后，都能按同一套工程原则推进长期演进，而不是只完成眼前代码改动。

## 一、适用范围

本准则适用于所有实现类任务：新功能、Bug 修复、重构、性能优化、架构调整、前后端联调、测试补齐、数据库迁移。

纯查询可以只回答问题，但如果回答会引导后续开发，也必须引用本准则中的原则。

## 二、不可跳过的核心原则

### 1. DRY：消除业务规则重复，不追求表面去重

- 重复的业务判断、状态流转、错误码、分页查询、执行记录、Git 操作、版本解析、冲突检测必须收敛到单一模块。
- 不为了少几行代码抽象；只有当重复代表同一业务规则或同一变化原因时才抽象。
- 每次新增能力前，必须搜索现有实现，优先复用已有 Port、策略、值对象、组合式函数和通用组件。

### 2. 开闭原则：新增变化点走扩展，不改主流程

以下变化必须优先用策略、注册表或 Port/Adapter 扩展：

- 新 Git Provider
- 新构建工具或版本更新器
- 新冲突检测类型
- 新 RunTask 执行器
- 新分支规则类型
- 新导出格式或通知渠道

主编排服务只负责组织流程，不直接写分支判断矩阵。

### 3. 正交性：不同变化轴不得互相污染

ReleaseHub 至少有这些独立变化轴：

| 变化轴 | 应放置位置 | 禁止行为 |
|---|---|---|
| 发布窗口生命周期 | `domain/releasewindow` | 在 Controller 或 UI 中硬编码状态流转 |
| 窗口范围与迭代挂载 | `domain/window` + `application/window` | 挂载时直接混入不可恢复的 Git 副作用 |
| Git 操作 | `application/port/out` + `infrastructure/git` | Application 绑定 GitLab/GitHub 具体 API |
| 版本语义 | `domain/version` + `application/version` | Maven/Gradle 文件细节进入领域层 |
| 冲突检测 | `domain/conflict` + `application/conflict` | 多类冲突堆在一个巨型方法中 |
| 执行记录与任务 | `domain/run` + `application/release` | 用一次性脚本替代可恢复任务 |
| UI 展示 | `release-hub-web/src/views` | 前端推导后端业务真相 |

### 4. 切面化：横切能力不得散落

以下能力必须作为切面、管道或统一基础设施处理：

- 认证与权限
- 审计日志
- 幂等
- 事务边界
- 锁与并发控制
- 重试、超时、失败恢复
- 指标、耗时、Trace ID
- 国际化错误消息
- API 响应格式

如果同一横切逻辑出现在三个以上用例中，必须提取统一机制。

### 5. 深模块：窄接口承载复杂内部实现

模块应该“接口窄、内部深”。新增或重构模块时必须检查：

- 对外入口是否少而稳定
- 内部复杂性是否被封装
- 调用方是否不需要知道内部步骤
- 模块是否有明确不变量
- 是否可以独立测试

典型深模块目标：

- `ConflictDetectionAppService` 对外只暴露扫描/查询，内部由多个 `ConflictDetector` 策略组成。
- `ReleaseOrchestrator` 对外只暴露创建计划/执行计划，内部处理 DAG、依赖、重试和恢复。
- `VersionUpdateAppService` 对外只暴露版本更新，内部选择 Maven/Gradle/未来构建工具。

### 6. 复杂时序必须显式建模

发布、合并、版本更新、归档、打标、CI、通知都属于复杂时序。禁止只靠方法调用顺序表达业务流程。

复杂时序必须至少显式建模：

- 状态机：允许状态、状态流转、终态、幂等行为
- 任务：任务类型、目标对象、状态、重试次数、错误
- 依赖：哪些任务完成后才能执行
- 运行记录：开始、结束、操作者、结果、日志或 diff

### 7. 性能是执行模型的一等约束

涉及多仓库、多迭代、Git 远程调用、批量版本扫描时，必须考虑：

- 同一仓库任务串行，不同仓库任务可并行
- 远程 Git/API 查询要避免重复调用
- 读模型/API 聚合要避免前端 N+1 请求
- 长任务不得阻塞 HTTP 请求线程
- 执行进度必须可查询、可恢复

### 8. 业务建模采用前沿研究范式

复杂业务优先使用下列建模方式，而不是贫血 CRUD：

- DDD 聚合和值对象表达业务不变量
- 状态机表达生命周期
- 领域事件表达重要业务事实
- Process Manager / Saga 表达跨聚合长流程
- Policy / Strategy 表达可替换规则
- CQRS 风格读模型表达复杂查询和 UI 聚合
- DAG 表达可并行、可恢复的执行任务

## 三、AI 接手任务的强制流程

### Step 0：事实校准

开始实现前必须：

1. 查看 `git status --short`，不得覆盖用户已有改动。
2. 读取 `context/experience/INDEX.md`，按关键词加载相关经验。
3. 读取相关业务、架构、测试、前端或数据库规范。
4. 对照真实源码，不只依赖文档记忆。

### Step 1：任务分类

| 类型 | 处理方式 |
|---|---|
| 新能力、破坏性变更、架构变化、性能模型变化 | 先走 requirements + OpenSpec |
| Bug 修复 | 先写复现测试，再修复 |
| 重构 | 明确不改变外部行为，先补架构/行为测试 |
| 文档治理 | 直接修改，但要同步入口索引 |

### Step 2：把大任务拆成 DAG

任何跨模块任务都必须先写出任务 DAG。格式可以是文字，也可以是 Mermaid。

示例：

```text
确认规格
  -> Domain 模型测试
  -> Domain 实现
  -> Application Port/Service 测试
  -> Infrastructure Adapter
  -> REST API
  -> Frontend API 封装
  -> UI 垂直入口
  -> 集成/E2E 验证
  -> 文档/经验沉淀
```

如果任务存在并行可能，标出并行边；如果必须串行，说明依赖原因。

### Step 3：按垂直切片分批

优先线性分批，每批必须形成可验证闭环。

一个合格垂直切片通常包含：

- 需求/规格或验收标准
- Domain 或应用模型
- Application 用例
- Infrastructure 适配
- API
- Frontend 最小可用入口
- 自动化测试
- 文档同步

禁止先横向铺大量底层框架，长期没有用户可验证结果。

### Step 4：设计模块边界

编码前必须给出模块边界判断：

| 问题 | 必须回答 |
|---|---|
| 新规则属于哪个领域概念？ | 聚合、值对象、领域服务、策略还是应用服务 |
| 是否引入新变化点？ | 如果是，优先 Port/Strategy/Registry |
| 是否跨聚合或跨系统？ | 如果是，优先 Process Manager / RunTask / DAG |
| 是否横切？ | 如果是，提取切面或统一管道 |
| 是否影响性能？ | 如果是，说明并行、缓存、锁或分页策略 |

### Step 5：TDD 实施

执行顺序：

1. RED：先写失败测试或复现用例。
2. GREEN：最小实现通过。
3. REFACTOR：消除重复、收敛命名、强化边界。
4. VERIFY：运行最小必要测试，再按风险扩大验证范围。

### Step 6：完成后沉淀

任务结束前检查：

- 是否更新需求、OpenSpec、上下文或 API 文档
- 是否新增可复用经验到 `context/experience/lessons/`
- 是否需要更新 `context/tech/REPO_SNAPSHOT.md`
- 是否有临时文件、截图、报告需要归档或清理

## 四、ReleaseHub 当前演进方向

### 方向 1：执行体系 DAG 化

当前发布执行已有 `RunTask` 和执行器，但仍以 `taskOrder` 表达线性顺序。长期目标是演进为：

```text
RunPlan
  -> RunTask[]
  -> RunTaskDependency[]
  -> TaskScheduler
  -> TaskExecutor
  -> TaskResultHandler
```

第一阶段可以串行执行 DAG；第二阶段再做按仓库并行。

### 方向 2：冲突检测策略化

冲突检测长期结构：

```text
ConflictDetector
  -> VersionConflictDetector
  -> BranchConflictDetector
  -> MergeConflictDetector
  -> CrossRepoVersionConflictDetector
```

新增冲突类型时新增 detector，不修改主扫描流程。

### 方向 3：Git Provider 双轨收敛

长期只保留统一的 `GitBranchPort` / `GitBranchAdapterFactory` 抽象。任何新代码不得直接依赖特定 GitLab/GitHub Port，除非该模块就是对应 Infrastructure Adapter。

### 方向 4：前端读模型聚合

发布窗口详情、迭代仓库、分支状态、冲突状态等复杂页面，应逐步由后端提供聚合读模型，减少前端 N+1 请求和业务推导。

## 五、AI 输出要求

实现类任务开始前，AI 的回复或内部计划必须包含：

- 已加载的关键上下文
- 相关经验摘要
- 任务 DAG 或垂直切片拆分
- 模块边界判断
- 测试计划

最终回复必须包含：

- 变更范围
- 验证结果
- 未解决风险
- 是否沉淀经验或更新文档
