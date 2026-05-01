# E2E 测试应自动化、可复现

## 问题

使用一次性 curl 命令进行 E2E API 测试，存在以下问题：
1. **不可复现** — 下次回归需要重新手动编写所有请求
2. **数据残留** — 测试数据留在数据库中，影响后续开发
3. **覆盖不全** — 手动测试容易遗漏边界情况
4. **无断言** — 人工肉眼判断结果，易出错

## 正确做法

### 后端 API E2E 测试
- 使用 Spring Boot `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`
- 连接真实 PostgreSQL（Docker TestContainers），而非 H2
- 每个测试方法独立事务或 setUp/tearDown 清理数据
- 测试完整业务流程链路：创建 → 查询 → 修改 → 删除

### 前端 E2E 测试
- 使用 Playwright 或 Cypress 进行页面级别测试
- 覆盖用户可见的操作流程
- 项目中已有 `pnpm test:e2e` 入口（参考 frontend-vitest-e2e-separation）

### 测试数据管理
- 使用唯一前缀（如时间戳或 UUID）避免数据冲突
- 测试结束后清理创建的数据
- 或使用 TestContainers 隔离整个数据库实例

## 写 E2E 测试前的前置检查

**先读实现，再写测试。** 不读代码直接写 E2E 测试，会反复遭遇"接口不存在"或"业务规则未满足"。

写第一行测试前，通读目标 Controller + AppService，确认：

1. **端点是否存在** — 不要假设端点存在，看 Controller 列表
2. **业务前置条件** — AppService 里有哪些 `if` 校验？状态机转换需要哪些先决步骤？
   - 例：`publish` 需要先 attach 迭代；`close` 需要 PUBLISHED 状态
3. **空对象序列化** — Jackson 无法序列化没有属性的空类，用 `"{}"` 字符串代替
4. **认证要求** — API 是否需要 JWT？是否有测试用户需要初始化？

## 关键教训

| 错误做法 | 正确做法 |
|----------|----------|
| 一次性 curl 命令 | 持久化测试脚本/代码 |
| 手动判断结果 | 自动断言 |
| 共享开发数据库 | TestContainers 隔离 |
| 测试完不清理 | setUp/tearDown 自动清理 |
| 只测 API 层 | API + 前端页面都覆盖 |
