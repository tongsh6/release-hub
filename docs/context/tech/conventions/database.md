# 数据库迁移规范

> 适用范围：`release-hub/**/db/migration/**`, `release-hub/**/*.sql`

**重要：所有 AI 响应必须使用中文。**

## Flyway 迁移位置
`releasehub-infrastructure/src/main/resources/db/migration/`

## 命名约定
```
V{版本号}__{描述}.sql
```
示例：
- `V1__create_release_window_table.sql`
- `V2__add_branch_rule_table.sql`
- `V3__alter_release_window_add_frozen.sql`

## 各环境行为

| 环境 | 数据库 | Flyway | DDL Auto |
|------|--------|--------|----------|
| local（本地） | PostgreSQL | 禁用 | update |
| test（测试） | H2 (PostgreSQL 模式) | 启用 | - |
| prd（生产） | PostgreSQL | validate | - |

## Schema 指南

### 表命名
- 使用 snake_case：`release_window`、`branch_rule`
- 使用单数名词：`release_window` 而非 `release_windows`

### 列命名
- 使用 snake_case：`created_at`、`target_version`
- 主键：`id`（UUID 或 BIGSERIAL）
- 外键：`{表名}_id`（如 `release_window_id`）
- 时间戳：`created_at`、`updated_at`
- 软删除：`deleted_at`（可空）

### 常用模式
```sql
-- 标准审计列
created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
created_by VARCHAR(255),
updated_by VARCHAR(255)

-- 枚举用 VARCHAR
status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',

-- JSON 列 (PostgreSQL)
metadata JSONB,
```

## 迁移最佳实践
1. **永远不要修改现有迁移** - 创建新的迁移
2. **本地先测试**，使用 H2 的 PostgreSQL 模式
3. **为复杂变更添加回滚注释**
4. **使用事务**处理多语句迁移
5. **为常用查询列添加索引**

## 本地开发库清理

本地开发阶段如果数据库积累了验收、联调或历史脏数据，使用统一脚本清理，不手写临时 SQL：

```bash
bash scripts/dev/cleanup-dev-database.sh
bash scripts/dev/cleanup-dev-database.sh --execute
```

清理边界：

- 只允许清理本机 Docker 容器 `releasehub-postgres` 中的 `release_hub` 数据库。
- 默认同时扫描当前应用 schema `release_hub` 和历史遗留 schema `public`。
- 默认保留 `flyway_schema_history`、`users`、`system_settings`，避免丢失迁移元数据、登录账号和本地 GitLab 配置。
- 默认 dry-run 并输出 `.ai/reports/dev-db-cleanup/<timestamp>/summary.md`；只有显式 `--execute` 才执行 `TRUNCATE ... RESTART IDENTITY CASCADE`。
- 不触碰 GitLab 远端资源，不替代 SA-002 的生产/验收数据质量复核流程。
