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
