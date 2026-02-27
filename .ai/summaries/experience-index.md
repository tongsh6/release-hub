# 经验索引

> 根据任务关键词自动检索相关历史经验，避免重复踩坑。

## 使用说明

- **维护原则**：每次解决问题后，及时添加索引条目
- **检索方式**：AI 根据任务关键词自动匹配
- **更新频率**：新增经验时立即更新索引

---

## 经验索引

### 问题类别：状态管理

- **问题**：如何实现 ReleaseWindow 的冻结/解冻功能
- **解决方案**：使用领域事件 + 状态机模式，避免直接修改状态
- **相关文件**：`context/experience/lessons/release-window-freeze-pattern.md`
- **标签**：`state-management`, `domain-event`, `release-window`
- **相关度关键词**：freeze, unfreeze, frozen, state, 状态管理

### 问题类别：版本策略

- **问题**：版本号格式验证的边界情况
- **解决方案**：使用策略模式，支持多种版本格式（SemVer, CalVer）
- **相关文件**：`context/experience/lessons/version-policy-validation.md`
- **标签**：`version-policy`, `validation`, `strategy-pattern`
- **相关度关键词**：version, policy, validation, format, 版本号

### 问题类别：DDD 架构

- **问题**：如何确保 Domain 层不依赖其他层
- **解决方案**：使用 ArchUnit 门禁测试，强制架构约束
- **相关文件**：`context/experience/reports/backend-structure.md`
- **标签**：`ddd`, `architecture`, `archunit`
- **相关度关键词**：domain, layer, dependency, architecture, DDD

### 问题类别：数据库迁移

- **问题**：Flyway 迁移脚本的命名和版本管理
- **解决方案**：使用 V{数字}__{描述}.sql 格式，避免版本冲突
- **相关文件**：`context/tech/conventions/database.md`
- **标签**：`flyway`, `migration`, `database`
- **相关度关键词**：migration, flyway, database, schema, 迁移

### 问题类别：API 设计

- **问题**：REST API 的响应格式统一
- **解决方案**：使用 ApiResponse 统一包装，包含 code, message, data
- **相关文件**：`context/tech/api/release-window.md`
- **标签**：`api`, `rest`, `response`
- **相关度关键词**：api, response, rest, controller

### 问题类别：前端测试

- **问题**：`pnpm test` 误把 e2e 脚本当作 Vitest 用例执行
- **解决方案**：在 Vitest 配置中排除 `e2e/**`，将 e2e 固定走 `pnpm test:e2e`
- **相关文件**：`context/experience/lessons/frontend-vitest-e2e-separation.md`
- **标签**：`vitest`, `e2e`, `workflow`, `ci`
- **相关度关键词**：vitest, e2e, pnpm test, No test suite found, exclude

---

## 业务经验

### 问题类别：发布流程

- **问题**：发布窗口生命周期如何设计，状态和冻结如何区分
- **解决方案**：三态流转（DRAFT→PUBLISHED→CLOSED）+ 冻结作为横切机制
- **相关文件**：`context/experience/lessons/release-window-lifecycle.md`
- **标签**：`release-window`, `lifecycle`, `state-machine`, `freeze`
- **相关度关键词**：发布窗口, 状态流转, 生命周期, publish, close, draft, 发布, 上线, lifecycle

### 问题类别：分支管理

- **问题**：多仓库场景下分支命名和归档策略
- **解决方案**：类型前缀 + 业务Key（feature/hotfix/release）+ archive/<reason>/<original>归档
- **相关文件**：`context/experience/lessons/branch-naming-strategy.md`
- **标签**：`branch`, `naming`, `archive`, `git`
- **相关度关键词**：分支, branch, feature, hotfix, release, archive, 归档, 命名, naming, git

### 问题类别：迭代管理

- **问题**：如何设计迭代与发布窗口、仓库的挂载关系
- **解决方案**：两层N:N模型（窗口←→迭代←→仓库），挂载/解除时自动管理分支
- **相关文件**：`context/experience/lessons/iteration-attach-detach.md`
- **标签**：`iteration`, `attach`, `detach`, `release-window`, `repository`
- **相关度关键词**：迭代, iteration, 挂载, attach, detach, 解除, 关联, 仓库, repository, 发布范围

---

### 问题类别：JPQL null 参数类型推断

- **问题**：JPQL 查询中 null 参数被 PostgreSQL JDBC 推断为 bytea，导致 lower()/concat() 报错；`cast as string` 在 H2 通过但 PostgreSQL 报 type "string" does not exist
- **解决方案**：使用 `cast(:keyword as varchar)` 显式指定参数类型（SQL 标准类型名）；修改 JPQL 后必须在 TestContainers E2E（真实 PostgreSQL）回归
- **相关文件**：`context/experience/lessons/jpql-null-param-bytea.md`
- **标签**：`PostgreSQL`, `JPQL`, `bytea`, `null`, `cast`, `varchar`, `H2`
- **相关度关键词**：bytea, lower, null, cast, varchar, string, JPQL, parameter, keyword search, 类型推断, H2 PostgreSQL 差异

### 问题类别：常量修改纪律

- **问题**：修改错误码后忘记更新测试中的断言，导致测试失败
- **解决方案**：修改任何常量/错误码/枚举值时，必须全局搜索所有引用（含测试），一次性全部更新
- **相关文件**：`context/experience/lessons/constant-change-global-search.md`
- **标签**：`error-code`, `constant`, `refactor`, `global-search`
- **相关度关键词**：错误码, error code, 常量, constant, 枚举, enum, 重命名, rename, 全局搜索

### 问题类别：E2E 测试工程化

- **问题**：用一次性 curl 命令做 E2E 测试，不可复现、无自动断言、数据残留；写测试前未读 API 实现导致反复遭遇"端点不存在"或"业务前置条件未满足"
- **解决方案**：写 E2E 测试前先通读 Controller + AppService；使用 TestContainers 自动化，带断言和隔离
- **相关文件**：`context/experience/lessons/e2e-testing-workflow.md`
- **标签**：`e2e`, `testing`, `automation`, `TestContainers`, `前置检查`
- **相关度关键词**：e2e, 端到端, 测试, test, curl, 自动化, automation, 可复现, reproducible, 业务前置条件, 端点不存在

### 问题类别：临时产物管理

- **问题**：worktree 或临时分支中的报告、脚本未提交，随 worktree 删除永久丢失
- **解决方案**：完成即归档（.ai/summaries 或 context/experience），关闭 worktree 前先 `git status` 确认无未跟踪文件
- **相关文件**：`context/experience/lessons/temp-artifacts-archiving.md`
- **标签**：`worktree`, `git`, `archiving`, `workflow`
- **相关度关键词**：worktree, 未跟踪, untracked, 临时文件, 归档, 丢失, 报告, 测试脚本

### 问题类别：TestContainers macOS Docker Desktop 配置

- **问题**：TestContainers 1.20.x 在 Docker Desktop 29.x 下 Ryuk 无法通过属性文件禁用；多测试类间容器重启导致端口变化报 500
- **解决方案**：在 Surefire 插件注入 `TESTCONTAINERS_RYUK_DISABLED=true`；改用静态初始化块 Singleton 容器模式；`~/.docker-java.properties` 设置 `api.version=1.44`
- **相关文件**：`context/experience/lessons/testcontainers-docker-setup.md`
- **标签**：`TestContainers`, `Docker`, `macOS`, `Ryuk`, `Singleton`, `Surefire`
- **相关度关键词**：testcontainers, ryuk, docker.raw.sock, api.version, TESTCONTAINERS_RYUK_DISABLED, Singleton container, @DynamicPropertySource, 容器重启, 端口变化, surefire

---

### 问题类别：AppService fallback 导致推导结果固定

- **问题**：`VersionValidationAppService.validateVersion()` 在 `currentVersion` 为空时 fallback 到 `"0.0.0"`，MINOR 策略推导出 `"0.1.0"`，所有缺少参数的请求都返回相同的错误值（`derivedVersion` 始终为 "0.1.0"）
- **解决方案**：为 DTO 字段添加 `@NotBlank`（Bean Validation），使其在接口层被拦截返回 400，移除 AppService 中的 fallback；用 `mvn clean test` 强制重编译多模块确认修复生效
- **相关文件**：`releasehub-interfaces/src/main/java/io/releasehub/interfaces/api/releasewindow/VersionValidationRequest.java`
- **标签**：`version`, `validation`, `fallback`, `bean-validation`, `AppService`, `maven-clean`
- **相关度关键词**：derivedVersion, 推导版本, fallback, @NotBlank, currentVersion, 必填, 400, AppService, mvn clean, 多模块编译缓存

---

## 添加新经验

### 模板

```markdown
### 问题类别：{category}

- **问题**：{问题描述}
- **解决方案**：{解决方案摘要}
- **相关文件**：`context/experience/lessons/{filename}.md`
- **标签**：`tag1`, `tag2`, `tag3`
- **相关度关键词**：keyword1, keyword2, keyword3
```

### 注意事项

1. **关键词要全面**：包含中英文、同义词、相关术语
2. **类别要准确**：便于后续检索和分类
3. **解决方案要简洁**：一句话概括核心思路
4. **及时更新**：发现问题或解决方案改进时，及时更新索引
