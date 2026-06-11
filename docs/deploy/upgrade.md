# 版本升级与回滚

本指南适用于 `deploy/compose/docker-compose.yml` 源码部署方式。

## 升级原则

- 升级前必须备份 PostgreSQL。
- 应用启动时由 Flyway 自动执行数据库迁移。
- 应用版本可以回退；已经执行过的数据库迁移不承诺自动降级。
- 如果需要完整回滚数据库状态，必须恢复升级前备份。

## 标准升级

```bash
git fetch --tags
git checkout v0.1.13

deploy/scripts/backup-db.sh

docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml build
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml up -d

deploy/scripts/healthcheck.sh
```

也可以使用封装脚本：

```bash
deploy/scripts/upgrade.sh v0.1.13
```

## 回滚应用版本

如果新版本容器启动失败，且数据库迁移未造成不兼容变化，可以先回滚应用版本：

```bash
git checkout v0.1.12
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml up -d --build
deploy/scripts/healthcheck.sh
```

## 恢复数据库备份

如果数据库迁移已经执行且需要完整回退：

```bash
deploy/scripts/restore-db.sh deploy/backups/releasehub-db-YYYYmmdd-HHMMSS.sql.gz
deploy/scripts/healthcheck.sh
```

恢复脚本会停止前后端、重建数据库并导入备份，然后重新启动服务。

## 升级后检查

- 前端首页可访问
- 后端 `/actuator/health` 返回 UP
- 登录成功
- Settings 页面中的 GitLab 配置仍存在
- 仓库列表、发布窗口列表和 Run 列表可查询
- 真实 GitLab 操作前先使用 Settings 连接测试
