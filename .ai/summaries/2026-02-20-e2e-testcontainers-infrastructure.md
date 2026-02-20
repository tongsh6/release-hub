# 会话摘要: TestContainers E2E 测试基础设施

**日期**: 2026-02-20
**任务类型**: feature / testing / bugfix

## 目标

将 2026-02-17 手动 curl E2E 测试升级为可复现的自动化 E2E 测试套件，基于 TestContainers 驱动真实 PostgreSQL 容器，覆盖核心链路、版本策略和错误场景。

## 变更摘要

- 新增 `application-e2e.yml` E2E Profile 配置
- 新增 `AbstractE2ETest.java` Singleton 容器模式基类
- 新增 3 个 E2E 测试类（链路/策略/错误场景）
- 修复 `ReleaseWindowFlowIT` JWT 认证、迭代关联
- 修复 `ReleaseWindow.close()` PUBLISHED 状态前置校验缺失
- 修复 `VersionPolicyJpaRepository` 使用 `cast(:keyword as varchar)` 替代 `cast(:keyword as string)`
- `releasehub-bootstrap/pom.xml` Surefire 插件注入 `TESTCONTAINERS_RYUK_DISABLED=true`

## 关键文件

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `releasehub-bootstrap/src/main/resources/application-e2e.yml` | 新增 | E2E Profile，datasource 由 @DynamicPropertySource 注入 |
| `releasehub-bootstrap/src/test/java/.../e2e/AbstractE2ETest.java` | 新增 | Singleton PostgreSQL 容器基类 |
| `releasehub-bootstrap/src/test/java/.../e2e/ReleaseWorkflowE2ETest.java` | 新增 | 核心链路：Group→Repo→Iteration→RW→Attach→Freeze→Publish |
| `releasehub-bootstrap/src/test/java/.../e2e/VersionPolicyE2ETest.java` | 新增 | 验证 PostgreSQL cast 修复，分页 + keyword 搜索 |
| `releasehub-bootstrap/src/test/java/.../e2e/ErrorHandlingE2ETest.java` | 新增 | GROUP_014 / RW_009 / 空迭代列表错误场景 |
| `releasehub-bootstrap/src/test/java/.../it/ReleaseWindowFlowIT.java` | 修改 | 补充 JWT 认证 header、迭代关联步骤 |
| `releasehub-bootstrap/pom.xml` | 修改 | Surefire 注入 TESTCONTAINERS_RYUK_DISABLED=true |
| `infrastructure/.../VersionPolicyJpaRepository.java` | 修改 | cast as varchar（PostgreSQL 要求） |
| `domain/.../ReleaseWindow.java` | 修改 | close() 添加 PUBLISHED 状态守卫 |

## 关键决策

- **Singleton 容器模式（静态初始化块）**: 代替 `@Testcontainers + @Container` 注解管理生命周期，避免多测试类间容器重启（端口变化导致 500）
  - 原因：`@Testcontainers` 在每个测试类结束时停止容器，下一个类启动时得到新端口，`@DynamicPropertySource` 已注入旧端口导致连接失败

- **在 pom.xml Surefire 插件中注入 TESTCONTAINERS_RYUK_DISABLED**: 代替 `~/.testcontainers.properties` 的 `ryuk.disabled=true`
  - 原因：TestContainers 1.20.x `ResourceReaper` 直接读取 `System.getenv("TESTCONTAINERS_RYUK_DISABLED")`，不通过配置文件；属性文件中的 `ryuk.disabled` 在此版本对 Ryuk 启动无效

- **cast(:keyword as varchar) 而非 cast as string**: PostgreSQL 要求使用标准 SQL 类型名 `varchar`，`string` 是 JPQL 扩展，在 PostgreSQL 方言下不被识别

## 验证结果

- [x] 全量测试：52 passed, 0 failures（H2 单元测试 + E2E 容器测试共存）
- [x] E2E 核心链路：RW DRAFT→Freeze→Publish 完整流转
- [x] E2E 错误场景：GROUP_014 / RW_009 / 空迭代列表均正确返回
- [x] H2 原有测试：40 个，不受影响
- [x] PR 创建：https://github.com/tongsh6/release-hub/pull/4

## 后续待办

- [ ] `derivedVersion` 始终返回 `0.1.0`，版本推导逻辑需修复
- [ ] Flyway `baseline-on-migrate` 在生产环境的处理策略
- [ ] CI/CD 流水线中 Docker-in-Docker 的 TestContainers 配置
- [ ] 前端 E2E 测试（Playwright）尚未实现

## 经验沉淀建议

- `context/experience/lessons/testcontainers-docker-setup.md` — TestContainers 1.20.x 在 macOS Docker Desktop 的配置陷阱
  - 关键词：ryuk, docker-java, api.version, Surefire, TESTCONTAINERS_RYUK_DISABLED
