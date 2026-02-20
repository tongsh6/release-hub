# PostgreSQL JPQL null 参数 bytea 推断

## 问题

JPQL 查询中使用 `:keyword` 参数，当传入 `null` 时，PostgreSQL JDBC 驱动将参数类型推断为 `bytea`，导致 `lower()` 函数报错：

```
ERROR: function lower(bytea) does not exist
```

这与 `postgres-lower-bytea-fix.md` 记录的列类型问题不同 —— 这里列类型是正确的 `varchar`，问题出在 **null 参数的类型推断**。

## 根因

PostgreSQL JDBC 驱动在参数为 null 时，无法从 Java `null` 推断 SQL 类型，默认使用 `bytea`。当这个 null 参与字符串拼接 `concat('%', :keyword, '%')` 时，整个表达式被当作 bytea 处理。

## 修复方案

在 JPQL 中使用 `cast` 显式指定参数类型，**必须使用 SQL 标准类型名 `varchar`**，不能用 `string`：

```java
// 错误 — null 会被推断为 bytea
lower(v.name) like lower(concat('%', :keyword, '%'))

// 错误 — string 是 JPQL 扩展，PostgreSQL 方言不识别，会报 type "string" does not exist
lower(v.name) like lower(concat('%', cast(:keyword as string), '%'))

// 正确 — varchar 是 SQL 标准类型名，PostgreSQL 兼容
lower(v.name) like lower(concat('%', cast(:keyword as varchar), '%'))
```

### H2 回归盲区

**H2 兼容 `cast as string`，PostgreSQL 不兼容。** 这意味着：
- 用 H2 跑单元测试通过 ≠ PostgreSQL 没问题
- `cast as string` 的错误只会在连接真实 PostgreSQL（TestContainers 或生产环境）时暴露
- **每次修改 JPQL，必须在 TestContainers E2E 测试中回归验证**

## 与列类型问题的区别

| 维度 | 列类型问题 (postgres-lower-bytea-fix) | 参数推断问题 (本文) |
|------|---------------------------------------|---------------------|
| 根因 | Hibernate ddl-auto 建错列类型 | JDBC null 参数类型推断 |
| 报错条件 | 所有查询都报错 | 仅 keyword=null 时报错 |
| 修复方式 | Flyway ALTER COLUMN | JPQL cast(:param as varchar) |
| 影响范围 | 整个表结构 | 单个查询 |

## 预防措施

- 所有 JPQL 关键词搜索查询，如果参数可能为 null，都使用 `cast(:param as varchar)`
- 不要在关键词搜索中包含枚举类型字段（scheme、bumpRule 等），它们不适合文本匹配
- 修改 JPQL 后，必须在 TestContainers E2E 测试（真实 PostgreSQL）中回归，不能只跑 H2 测试
