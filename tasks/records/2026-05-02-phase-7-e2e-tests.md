# Phase 7 执行日志：E2E 测试补齐（TestContainers PostgreSQL）

> 日期：2026-05-02
> 执行者：AI
> 状态：已完成

## 事前分析

E2E 测试基础设施（TestContainers + PostgreSQL + AbstractE2ETest + e2e profile）已存在，但仅有 4 个测试类约 12 个用例。

## 补齐内容

### 新增 E2E 测试类（9 个，70 个用例）

| 测试类 | 用例数 | 覆盖内容 |
|--------|:------:|---------|
| `AuthE2eTest` | 5 | login成功/失败, me/401, me/200, 保护端点/401 |
| `GroupE2eTest` | 13 | CRUD, 父子层级, tree, top-level, children, 重复code, 带子节点删除失败 |
| `RepositoryE2eTest` | 10 | CRUD, paged, keyword搜索, initial-version, 非叶子节点创建失败(B2) |
| `IterationE2eTest` | 9 | CRUD, repo add/remove, listRepos, update, delete |
| `ReleaseWindowE2eTest` | 10 | CRUD, freeze/unfreeze, attach+publish, close, 无迭代发布失败(B2), CLOSED后冻结失败(B4) |
| `BranchRuleE2eTest` | 6 | list, 创建+getById, paged, 更新, 合规检查, 删除 |
| `SettingsE2eTest` | 4 | gitlab 保存/获取, naming 保存/获取, ref, blocking 保存/获取 |
| `DashboardE2eTest` | 1 | stats 端点 |
| `ReleaseFlowE2eTest` | 12 | 完整发布流程 + B3 regression (null iterationKeys → validation error 而非 NPE) |

### 修复的 Bug

| Bug | 文件 | 修复内容 |
|-----|------|---------|
| PostgreSQL `lower(bytea)` 错误 | `ReleaseWindowJpaRepository.java` | JPQL 中 `CONCAT('%', CAST(:name AS string), '%')` 替代 `CONCAT('%', :name, '%')`，修复参数为 NULL 时的类型推断错误 |

### 测试统计

| 范围 | 测试数 |
|------|:------:|
| 原有后端测试（单元+集成） | 52 |
| 原有 E2E 测试 | 12 |
| 新增 E2E 测试 | 70 |
| **总计** | **134** |

### 验证

- 全部 134 个测试通过
- 前端 typecheck/lint 不受影响（仅后端变更）

## 涉及文件

| 文件 | 操作 |
|------|------|
| `AuthE2eTest.java` | 新建 |
| `GroupE2eTest.java` | 新建 |
| `RepositoryE2eTest.java` | 新建 |
| `IterationE2eTest.java` | 新建 |
| `ReleaseWindowE2eTest.java` | 新建 |
| `BranchRuleE2eTest.java` | 新建 |
| `SettingsE2eTest.java` | 新建 |
| `DashboardE2eTest.java` | 新建 |
| `ReleaseFlowE2eTest.java` | 新建 |
| `ReleaseWindowJpaRepository.java` | 修改（修复 PostgreSQL lower(bytea) bug） |

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ✅ | 全部 8 个核心模块 E2E 覆盖 |
| 层级闭环 | ✅ | REST API → Service → PostgreSQL 全链路 |
| 测试闭环 | ✅ | 134/134 通过 |
| 架构闭环 | ✅ | 遵循现有 AbstractE2ETest + MockMvc 模式 |
| 性能闭环 | ✅ | Singleton PostgreSQL 容器，共享连接 |
| 文档闭环 | ✅ | 本日志 |
| 工作区闭环 | ✅ | 10 个文件变更 |
