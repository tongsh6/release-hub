# Docker Compose 源码部署

本指南是 ReleaseHub 面向开源用户的标准单机部署方式：用户从源码构建前后端镜像，在一台 Docker 主机上运行 ReleaseHub 和 PostgreSQL，并连接已有的外部 GitLab。

前端镜像使用 Node 24，并通过 Corepack 读取 `frontend/package.json` 中固定的 pnpm 10.33.0；安装始终使用 `--frozen-lockfile`。本地开发和 CI 使用相同的版本来源，不再安装未固定版本的全局 pnpm。

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

### 首次管理员账户

首次空库部署前，在 `.env` 中设置：

```dotenv
RELEASEHUB_BOOTSTRAP_ADMIN_USERNAME=admin
RELEASEHUB_BOOTSTRAP_ADMIN_PASSWORD=
```

密码必须由部署者自行填写，不提供默认密码。使用密码管理器生成随机密码，至少 12 个字符，UTF-8 编码不超过 72 字节。用户名为 3 至 50 位字母、数字、点、下划线或连字符，首位必须是字母或数字。`.env` 应只允许部署者读取；含 `$` 或 `#` 等特殊字符的值使用单引号包围，避免 Compose 插值或注释解析。

生产启动时会在事务锁内检查用户表：仅在空表时创建首次账户，数据库只保存 BCrypt 哈希。随后在现有登录页输入配置的用户名和密码即可登录。已有任意用户时跳过初始化，修改初始凭据或重复启动都不会新增账户或重置密码。

成功登录后，清空 `.env` 中的 `RELEASEHUB_BOOTSTRAP_ADMIN_PASSWORD` 并执行以下命令，从后端容器环境中移除初始密码：

```bash
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml up -d --force-recreate backend
```

已有账户仍可使用原密码登录。初始凭据不是密码重置入口；不要通过清空用户表来重置密码。`prd` 默认开启 `releasehub.bootstrap-admin.enabled`，不得同时开启开发 seed。

如果空用户表没有有效凭据，后端启动会明确失败；检查日志提示的 `RELEASEHUB_BOOTSTRAP_ADMIN_USERNAME` / `RELEASEHUB_BOOTSTRAP_ADMIN_PASSWORD` 配置项，不要改用开发环境固定账户。

## 必改配置

| 变量 | 说明 |
|---|---|
| `POSTGRES_PASSWORD` | PostgreSQL 密码，必须改掉示例值 |
| `JWT_SECRET` | JWT 签名密钥，至少 32 字节随机值 |
| `RELEASEHUB_CRYPTO_SECRET_KEY` | Git Token 加密密钥，Base64 编码的 32 字节随机值；已有实例必须沿用原密钥 |
| `RELEASEHUB_BOOTSTRAP_ADMIN_USERNAME` | 首次管理员用户名，默认 `admin` |
| `RELEASEHUB_BOOTSTRAP_ADMIN_PASSWORD` | 空库首次启动必须填写；成功登录后移除，已有账户不受影响 |
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

### Git Token 加密密钥

仅首次部署全新实例时，生成独立密钥：

```bash
openssl rand -base64 32
```

将结果保存到 `.env` 的 `RELEASEHUB_CRYPTO_SECRET_KEY`，同时放入独立受控的密钥备份位置。不要复用 `JWT_SECRET`，不要提交 `.env`，不要每次部署生成新值。已有加密实例应填写原来的 `releasehub.crypto.secret-key` 值。

`prd` 默认开启加密；仓库 Token 和 Settings Token 保存为带 `enc:v1:` 标识的 AES-GCM 密文。密钥缺失、Base64 非法或长度错误时拒绝启动。启动时只读校验两个存储位置的所有有标识密文；错误密钥或损坏密文会阻止启动，读取时也不会将无法解密的密文作为 Token 继续使用。

无标识历史数据保留兼容读取，启动日志仅提示数量，不输出内容，也不自动写回。无标识明文与旧密文无法可靠区分，因此本轮不能保证检测所有历史错钥情况。升级前先备份并复核，历史数据迁移及密钥轮换须单独规划，不能通过替换密钥或关闭加密处理。

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

当前镜像为 PostgreSQL 18.1，卷挂载到 `/var/lib/postgresql`，数据位于其中的 `18/docker` 子目录。旧的 `/var/lib/postgresql/data` 挂载会导致该镜像首次启动失败。已有其他 PostgreSQL 主版本数据时，必须按备份恢复或正式数据库升级流程迁移，不得直接切换镜像或移动目录。

备份：

```bash
deploy/scripts/backup-db.sh
```

数据库备份只包含数据，不包含加密密钥。必须将原密钥与数据库备份建立对应关系并分开安全保管；丢失密钥后，数据库备份无法解密原 Token。含历史明文的数据备份仍应按敏感数据保护。

恢复：

```bash
deploy/scripts/restore-db.sh deploy/backups/releasehub-db-YYYYmmdd-HHMMSS.sql.gz
```

恢复前先确认原密钥可用，并优先在独立数据库演练。恢复脚本遇到 SQL 导入错误会停止，不会继续启动应用；恢复完成后以原密钥启动，再验证登录、Settings 连接测试以及仓库分支读取。不要为了绕过启动校验而生成新密钥或关闭加密。

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
- 首次账户只从部署者提供的凭据创建；已有账户不因部署配置变化而重置。
- 生产默认启用 Token 加密和只读密文校验；历史无标识凭据迁移、密钥轮换和 RBAC 不包含在本次交付内。
