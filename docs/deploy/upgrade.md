# 版本升级与回滚

本指南适用于 `deploy/compose/docker-compose.yml` 源码部署方式。

## 升级原则

- 升级前必须备份 PostgreSQL。
- 应用启动时由 Flyway 自动执行数据库迁移。
- 应用版本可以回退；已经执行过的数据库迁移不承诺自动降级。
- 如果需要完整回滚数据库状态，必须恢复升级前备份。
- PostgreSQL 18.1 的卷挂载点为 `/var/lib/postgresql`。首次部署修复调整了旧的 `/var/lib/postgresql/data` 挂载；如果卷中已有数据，先确认 PostgreSQL 主版本和实际数据目录并备份，在独立新卷演练恢复，不能把挂载点调整当作数据库升级或直接套用到旧数据卷。
- 已有用户的数据库升级后无需配置首次管理员密码；启动不会新增账户或修改原密码。空用户表必须按首次部署文档配置初始凭据。
- 升级前将原 `releasehub.crypto.secret-key` 配置到 `RELEASEHUB_CRYPTO_SECRET_KEY`，并独立安全备份。已有密文时不得生成新值替代；历史未加密实例应先备份和复核，初始化密钥不会自动迁移旧数据。

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

恢复脚本会停止前后端、重建数据库并导入备份，然后重新启动服务。SQL 导入出错时立即停止，不会继续启动服务。

执行恢复前确认数据库备份对应的原加密密钥可用，并先在独立数据库演练。数据库备份不包含密钥；只恢复数据库或重新生成密钥无法恢复已有 Token。有标识密文不能解密时，生产启动校验会拒绝启动，需核对密钥和备份，不要关闭加密绕过。

## 升级后检查

- 前端首页可访问
- 后端 `/actuator/health` 返回 UP
- 登录成功
- Settings 页面中的 GitLab 配置仍存在
- 仓库和 Settings 的有标识密文校验通过，日志中无未解决的无标识凭据提示
- 仓库列表、发布窗口列表和 Run 列表可查询
- 真实 GitLab 操作前先使用 Settings 连接测试
