# Docker Compose 源码部署

本指南是 ReleaseHub 面向开源用户的标准单机部署方式：用户从源码构建前后端镜像，在一台 Docker 主机上运行 ReleaseHub 和 PostgreSQL，并连接已有的外部 GitLab。

## 架构

```text
用户服务器
├─ frontend: Nginx + Vue 静态文件，暴露 FRONTEND_PORT
├─ backend: Spring Boot，使用 prd,real profile
└─ postgres: ReleaseHub 数据库，Docker volume 持久化

外部
└─ GitLab: 用户已有实例，在 ReleaseHub Settings 页面配置
```

本部署不内置 GitLab。GitLab token 不写入 compose 文件，部署后在页面设置中录入。

## 前置条件

- Docker Engine 和 Docker Compose plugin 可用
- 服务器可以访问外部 GitLab
- 服务器可从互联网下载 Docker base image、Maven 依赖和 pnpm 依赖

## 首次部署

```bash
git clone https://github.com/tongsh6/release-hub.git
cd release-hub

cp deploy/compose/.env.example deploy/compose/.env
$EDITOR deploy/compose/.env

docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml up -d --build
deploy/scripts/healthcheck.sh
```

打开 `.env` 中的 `APP_BASE_URL`。默认本机访问地址是 `http://localhost:8090`。

## 必改配置

| 变量 | 说明 |
|---|---|
| `POSTGRES_PASSWORD` | PostgreSQL 密码，必须改掉示例值 |
| `JWT_SECRET` | JWT 签名密钥，至少 32 字节随机值 |
| `CORS_ALLOWED_ORIGINS` | 浏览器访问前端的完整 origin，例如 `https://releasehub.example.com` |
| `FRONTEND_PORT` | 前端暴露端口 |
| `BACKEND_PORT` | 后端健康检查/API 调试端口；Compose 默认只绑定 `127.0.0.1` |

## GitLab 配置

部署完成后登录 ReleaseHub，在 Settings 页面配置外部 GitLab。

Token 至少需要：

- `api`
- `read_repository`
- `write_repository`

各代码仓也可以配置独立 token，用于分支、MR、tag、版本写回等真实 Git 操作。

## 常用操作

```bash
# 查看服务状态
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml ps

# 查看后端日志
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml logs -f backend

# 停止
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml down

# 保留数据库 volume，重新构建并启动
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml up -d --build
```

## 数据持久化

PostgreSQL 数据保存在 Docker volume `postgres_data`。不要用 `docker compose down -v`，除非你明确要删除数据库。

备份：

```bash
deploy/scripts/backup-db.sh
```

恢复：

```bash
deploy/scripts/restore-db.sh deploy/backups/releasehub-db-YYYYmmdd-HHMMSS.sql.gz
```

## 健康检查

```bash
deploy/scripts/healthcheck.sh
```

脚本会检查：

- `docker compose ps`
- 后端 `/actuator/health`
- 前端首页

## 生产边界

- `SPRING_PROFILES_ACTIVE` 默认为 `prd,real`，Flyway 开启，seed 关闭。
- 前端 `VITE_API_BASE_URL` 默认为 `/api`，由 Nginx 代理到 backend。
- Swagger UI 在 `prd` profile 下关闭。
- 默认账号和密码不应长期保留，首次登录后应按项目实际安全策略处理。
