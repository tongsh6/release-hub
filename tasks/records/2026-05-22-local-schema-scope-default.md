# 本地 scope 列默认值自恢复

## 背景

- 推进 SA-006 真实页面验收时，本地全链路环境启动失败。
- 后端日志显示 Hibernate `ddl-auto:update` 试图为已有数据的 `version_policy` 表新增 `scope_level varchar(255) not null`，但没有默认值，PostgreSQL 因存量行存在而拒绝：
  `ERROR: column "scope_level" of relation "version_policy" contains null values`。
- Flyway V31 已在正式迁移里定义 `scope_level VARCHAR(32) NOT NULL DEFAULT 'GLOBAL'`，但本地 `local` profile 仍关闭 Flyway，依赖 JPA `ddl-auto:update` 自恢复。

## 变更

- 在 `VersionPolicyJpaEntity.scopeLevel` 上补齐 Hibernate `@ColumnDefault("'GLOBAL'")` 与 `VARCHAR(32)` 映射。
- 在 `BranchRuleJpaEntity.scopeLevel` 上同步补齐同样的默认语义，避免分支规则存量表在本地自恢复时遇到同类问题。
- 将 scope project / sub-project id 映射长度对齐迁移脚本的 `VARCHAR(128)`。

## 验收

```bash
scripts/dev/start-local-env.sh hold
```

- 后端在 hold 模式下完成启动，`/actuator/health` 返回 `UP`。

## 后续

- 本轮只修复本地 schema 自恢复阻断；不改变 Flyway V31 的正式迁移语义。
- SA-006 真实 GitLab scoped rule 证据仍需继续补齐。
