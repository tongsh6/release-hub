# Fix Postgres lower(bytea) Error

> 归档自 `.trae/documents/Fix Postgres lower(bytea) Error.md`（Trae 会话产出）

## 问题

`version_policy` 表的 `name/scheme/bump_rule` 列在本地库中被 Hibernate `ddl-auto:update` 错误地建成了 `bytea` 类型，PostgreSQL 不支持 `lower(bytea)`，导致关键词搜索查询失败。

## 根因

- 本地配置 Flyway 关闭、`ddl-auto: update` 开启且 `hibernate.default_schema=release_hub`
- 历史表结构不会被自动纠正，导致代码/迁移与实际库表不一致

## 修复方案

### 1. 自修复数据库迁移

新增 Flyway migration `V14__fix_version_policy_text_columns.sql`：
- 检测列类型，若为 `bytea` 则 `ALTER COLUMN TYPE varchar(...) USING convert_from(col, 'UTF8')`
- 若已是 varchar/text 则跳过（幂等）

### 2. 统一本地启动配置

调整 `application-local.yml`：
- `spring.flyway.enabled: true`
- `spring.jpa.hibernate.ddl-auto: validate`（或 `none`）
- 配置 Flyway 默认 schema

## 关键教训

- **永远优先 Flyway 管控表结构**，避免 Hibernate ddl-auto 造成漂移
- 本地开发环境配置应与测试/生产一致
- 新增迁移要保证幂等（检测后再 ALTER）
