# 经验索引

> ReleaseHub 项目经验的可检索索引。AI 执行任务前应检索此索引，加载相关经验，避免重复踩坑。

## 使用说明

### AI 检索流程

```
1. 提取任务关键词
2. 匹配下方索引的「关键词」字段
3. 加载相关经验文档
4. 在执行任务前展示经验摘要
```

---

## 经验列表

### ReleaseWindow 冻结/解冻：领域事件 + 状态机模式

- **类别**：设计决策
- **日期**：2026-01
- **关键词**：`freeze`, `unfreeze`, `frozen`, `状态机`, `state machine`, `领域事件`, `domain event`, `release-window`, `状态流转`
- **摘要**：用聚合根内的状态机表达允许的状态跳转，通过领域事件描述状态变更语义。应用层只编排用例与事务边界，不直接修改状态细节。
- **文档**：[lessons/release-window-freeze-pattern.md](lessons/release-window-freeze-pattern.md)

---

### 发布窗口生命周期设计

- **类别**：设计决策
- **日期**：2026-01-29
- **关键词**：`lifecycle`, `生命周期`, `status`, `状态`, `DRAFT`, `PUBLISHED`, `CLOSED`, `release-window`, `状态流转`
- **摘要**：三态流转模型（DRAFT → PUBLISHED → CLOSED），冻结作为独立的横切关注点，任何状态下都可冻结/解冻。
- **文档**：[lessons/release-window-lifecycle.md](lessons/release-window-lifecycle.md)

---

### 迭代挂载与解除设计

- **类别**：设计决策
- **日期**：2026-01-29
- **关键词**：`attach`, `detach`, `挂载`, `解除`, `iteration`, `迭代`, `window-iteration`, `N:N`, `多对多`, `分支`, `branch`
- **摘要**：两层挂载模型（ReleaseWindow ←N:N→ Iteration ←N:N→ CodeRepository），解除挂载时要考虑分支归档等副作用。
- **文档**：[lessons/iteration-attach-detach.md](lessons/iteration-attach-detach.md)

---

### 分支命名与归档策略

- **类别**：设计决策
- **日期**：2026-01-29
- **关键词**：`branch`, `分支`, `naming`, `命名`, `archive`, `归档`, `feature`, `hotfix`, `release`, `git`
- **摘要**：分支命名约定（feature/hotfix/release + businessKey），归档策略（archive/reason/original）。自动化触发分支创建和归档。
- **文档**：[lessons/branch-naming-strategy.md](lessons/branch-naming-strategy.md)

---

### 版本策略校验：策略模式 + 边界用例回归

- **类别**：设计决策
- **日期**：2026-01
- **关键词**：`version`, `版本`, `policy`, `策略`, `SemVer`, `CalVer`, `validation`, `校验`, `正则`, `regex`
- **摘要**：把版本格式建模为策略，每种格式负责自身校验与比较语义。通过回归用例集固化边界，避免改一处坏一片。
- **文档**：[lessons/version-policy-validation.md](lessons/version-policy-validation.md)

---

### 前端测试分层：Vitest 单测与 e2e 脚本解耦

- **类别**：踩坑记录
- **日期**：2026-01
- **关键词**：`vitest`, `e2e`, `test`, `测试`, `单测`, `分层`, `puppeteer`, `前端`
- **摘要**：Vitest 只收集 src/ 下的单测用例，e2e 使用独立命令入口（pnpm test:e2e），避免互相污染。
- **文档**：[lessons/frontend-vitest-e2e-separation.md](lessons/frontend-vitest-e2e-separation.md)

---

### 列表筛选功能全栈实现模式

- **类别**：实现模式
- **日期**：2026-02
- **关键词**：`filter`, `筛选`, `列表`, `list`, `分页`, `paged`, `status`, `状态`, `全栈`, `full-stack`, `JPA`, `Repository`, `Port`, `Vue`
- **摘要**：从「发布窗口状态筛选」提炼的 5 层后端 + 3 层前端修改清单，包含 JPA 查询方法命名、条件组合策略、前端状态管理。
- **文档**：[lessons/list-filter-full-stack.md](lessons/list-filter-full-stack.md)

---

### 会话技能点提取与分类归纳

- **类别**：实现模式
- **日期**：2026-01（归档自 Trae 会话）
- **关键词**：`skill`, `技能`, `pattern`, `模式`, `OpenSpec`, `DDD`, `GitLab`, `Vue3`, `测试`, `问题解决`
- **摘要**：从仓库管理功能开发中提炼的 7 大技能领域：规范驱动、后端 DDD、数据库迁移、GitLab 集成、前端工程化、测试验证、通用问题解决。
- **文档**：[lessons/skill-extraction-pattern.md](lessons/skill-extraction-pattern.md)

---

### PostgreSQL lower(bytea) 修复

- **类别**：踩坑记录
- **日期**：2026-01（归档自 Trae 会话）
- **关键词**：`PostgreSQL`, `bytea`, `lower`, `Flyway`, `ddl-auto`, `迁移`, `migration`, `version_policy`, `类型转换`
- **摘要**：Hibernate ddl-auto 错误建列为 bytea 类型，导致 lower() 失败。修复：新增幂等 Flyway migration + 统一本地配置为 Flyway 管控。
- **文档**：[lessons/postgres-lower-bytea-fix.md](lessons/postgres-lower-bytea-fix.md)

---

### JPQL null 参数 bytea 类型推断

- **类别**：踩坑记录
- **日期**：2026-02-17
- **关键词**：`PostgreSQL`, `bytea`, `JPQL`, `null`, `cast`, `parameter`, `type inference`, `lower`, `concat`, `keyword search`
- **摘要**：JDBC 驱动对 null 参数推断为 bytea 类型，导致 lower()/concat() 报错。修复：JPQL 中用 `cast(:param as varchar)` 显式指定类型（`string` 是 JPQL 扩展，PostgreSQL 不识别，H2 不报错）。
- **文档**：[lessons/jpql-null-param-bytea.md](lessons/jpql-null-param-bytea.md)

---

### 修改常量/错误码时必须全局搜索

- **类别**：工程纪律
- **日期**：2026-02-17
- **关键词**：`error code`, `错误码`, `constant`, `常量`, `enum`, `枚举`, `rename`, `重命名`, `global search`, `全局搜索`, `refactor`
- **摘要**：修改错误码/常量/枚举值时，必须全局搜索所有引用（含测试代码），一次性全部更新。不要复用语义不匹配的错误码。
- **文档**：[lessons/constant-change-global-search.md](lessons/constant-change-global-search.md)

---

### E2E 测试应自动化、可复现

- **类别**：工程纪律
- **日期**：2026-02-17
- **关键词**：`e2e`, `端到端`, `测试`, `test`, `curl`, `自动化`, `automation`, `TestContainers`, `可复现`, `reproducible`
- **摘要**：E2E 测试不应使用一次性 curl 命令，应编写持久化的自动化测试代码（Spring Boot IT + TestContainers 或 Playwright）。
- **文档**：[lessons/e2e-testing-workflow.md](lessons/e2e-testing-workflow.md)

---

### TestContainers 1.20.x macOS Docker Desktop 配置

- **类别**：踩坑记录
- **日期**：2026-02-20
- **关键词**：`TestContainers`, `Docker`, `macOS`, `Ryuk`, `ryuk.disabled`, `TESTCONTAINERS_RYUK_DISABLED`, `Singleton container`, `Surefire`, `api.version`, `DynamicPropertySource`, `端口变化`
- **摘要**：TestContainers 1.20.x 只能通过环境变量禁用 Ryuk（属性文件无效）；多测试类应用 Singleton 静态初始化块模式避免容器重启；Docker 29.x 需 `~/.docker-java.properties` 设置 `api.version=1.44`；在 Surefire 插件注入 `TESTCONTAINERS_RYUK_DISABLED=true` 使构建自包含。
- **文档**：[lessons/testcontainers-docker-setup.md](lessons/testcontainers-docker-setup.md)

---

### 临时产物即时归档

- **类别**：工程纪律
- **日期**：2026-02-20
- **关键词**：`worktree`, `untracked`, `未跟踪`, `归档`, `临时文件`, `git status`, `artifact`
- **摘要**：worktree 或临时分支中的报告、脚本如未提交就被遗忘，随 worktree 删除永久丢失。原则：完成即归档（`.ai/summaries/` 或 `context/experience/`），关闭 worktree 前先 `git status` 确认无未跟踪文件。
- **文档**：[lessons/temp-artifacts-archiving.md](lessons/temp-artifacts-archiving.md)

---

## 按领域索引

### ReleaseWindow（发布窗口）

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [冻结/解冻模式](lessons/release-window-freeze-pattern.md) | `freeze`, `状态机` | 领域事件 + 状态机模式 |
| [生命周期设计](lessons/release-window-lifecycle.md) | `lifecycle`, `状态流转` | 三态流转 + 冻结横切 |

### Iteration（迭代）

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [挂载与解除设计](lessons/iteration-attach-detach.md) | `attach`, `detach`, `N:N` | 两层挂载模型 |

### VersionPolicy（版本策略）

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [版本策略校验](lessons/version-policy-validation.md) | `SemVer`, `CalVer`, `validation` | 策略模式 + 边界回归 |

### Git/分支

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [分支命名与归档](lessons/branch-naming-strategy.md) | `branch`, `archive`, `naming` | 命名约定 + 归档策略 |

### 前端

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [测试分层](lessons/frontend-vitest-e2e-separation.md) | `vitest`, `e2e`, `测试` | 单测与 e2e 解耦 |
| [列表筛选全栈模式](lessons/list-filter-full-stack.md) | `filter`, `筛选`, `分页` | 5 层后端 + 3 层前端 |

### 数据库

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [PostgreSQL lower(bytea) 修复](lessons/postgres-lower-bytea-fix.md) | `bytea`, `Flyway`, `ddl-auto` | Hibernate 漂移致类型错误 |
| [JPQL null 参数 bytea 推断](lessons/jpql-null-param-bytea.md) | `bytea`, `JPQL`, `null`, `cast`, `varchar` | null 参数推断为 bytea，用 cast as varchar 修复 |

### 测试基础设施

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [TestContainers macOS Docker 配置](lessons/testcontainers-docker-setup.md) | `Ryuk`, `Singleton`, `Surefire`, `api.version` | Ryuk 禁用只走环境变量，多类共享容器 |

### 综合/跨领域

| 经验 | 关键词 | 摘要 |
|------|--------|------|
| [会话技能点提取模式](lessons/skill-extraction-pattern.md) | `skill`, `pattern`, `技能` | 7 大领域技能分类 |
| [修改常量需全局搜索](lessons/constant-change-global-search.md) | `error code`, `常量`, `全局搜索` | 改错误码忘更新测试 |
| [E2E 测试自动化](lessons/e2e-testing-workflow.md) | `e2e`, `测试`, `自动化`, `前置检查` | 写测试前先读 Controller/AppService |
| [临时产物即时归档](lessons/temp-artifacts-archiving.md) | `worktree`, `归档`, `未跟踪` | 完成即归档，关 worktree 前 git status |

---

## 按类别索引

### 设计决策

- [冻结/解冻模式](lessons/release-window-freeze-pattern.md)
- [生命周期设计](lessons/release-window-lifecycle.md)
- [挂载与解除设计](lessons/iteration-attach-detach.md)
- [分支命名与归档](lessons/branch-naming-strategy.md)
- [版本策略校验](lessons/version-policy-validation.md)

### 踩坑记录

- [前端测试分层](lessons/frontend-vitest-e2e-separation.md)
- [PostgreSQL lower(bytea) 修复](lessons/postgres-lower-bytea-fix.md)
- [JPQL null 参数 bytea 推断](lessons/jpql-null-param-bytea.md)
- [TestContainers macOS Docker 配置](lessons/testcontainers-docker-setup.md)

### 工程纪律

- [修改常量需全局搜索](lessons/constant-change-global-search.md)
- [E2E 测试自动化与前置检查](lessons/e2e-testing-workflow.md)
- [临时产物即时归档](lessons/temp-artifacts-archiving.md)

### 实现模式

- [列表筛选全栈模式](lessons/list-filter-full-stack.md)
- [会话技能点提取模式](lessons/skill-extraction-pattern.md)

---

## 新增经验

当发现有价值的经验时，按以下步骤添加：

1. 在 `lessons/` 目录创建经验文档
2. 在本索引文件中添加条目
3. 确保包含足够的关键词以便检索
4. 分类到对应的领域和类别索引中
