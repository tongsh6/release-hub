# ReleaseHub 部署指南

> 最后更新：2026-05-02

## 部署架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    ReleaseHub 部署架构                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌──────────────┐     ┌──────────────┐                     │
│   │   Nginx      │────▶│  Vue 3 SPA   │  前端（静态文件）    │
│   │  (反向代理)   │     │  (Port 5173) │                     │
│   └──────┬───────┘     └──────────────┘                     │
│          │                                                   │
│          │ /api/* 代理                                       │
│          ▼                                                   │
│   ┌──────────────┐     ┌──────────────┐                     │
│   │ Spring Boot  │────▶│ PostgreSQL   │  后端 + 数据库       │
│   │  (Port 8080) │     │  (Port 5432) │                     │
│   └──────────────┘     └──────────────┘                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 一、快速开始（Docker Compose）

### 1.1 PostgreSQL 容器

```bash
cd docs
docker-compose up -d
```

等待 PostgreSQL 就绪后，启动后端。

> **注意**：`docs/docker-compose.yml` 仅包含 PostgreSQL。Java 后端和 Vue 前端建议在宿主机运行以便于开发调试。生产环境参见第五节。

### 1.2 验证数据库

```bash
# 检查容器状态
docker ps | grep postgres

# 连接测试
docker exec -it postgres psql -U release_hub -d release_hub
```

---

## 二、后端部署

### 2.1 环境要求

| 组件 | 版本要求 |
|------|---------|
| JDK | 21+ |
| Maven | 3.9+（或使用项目自带的 mvnw） |
| PostgreSQL | 18.1（也兼容 15+） |

### 2.2 配置 Profile

ReleaseHub 使用 Spring Boot 多 Profile 机制：

| Profile | 配置文件 | 用途 | Flyway | Seed 数据 |
|---------|---------|------|--------|----------|
| `local` | `application-local.yml` | 本地开发 | 关闭（JPA ddl-auto: update） | 开启 |
| `test` | `application-test.yml` | 集成测试 | 开启 | 开启 |
| `e2e` | `application-e2e.yml` | E2E 测试（TestContainers） | 开启 | 开启 |
| `prd` | `application-prd.yml` | 生产环境 | 开启 | 关闭 |

**切换方式**：修改 `application.yml` 中的 `spring.profiles.active` 或通过环境变量/启动参数覆盖。

### 2.3 启动步骤

```bash
# 1. 进入后端目录
cd backend

# 2. 编译项目
./mvnw clean install -DskipTests

# 3. 启动（默认 local profile）
./mvnw spring-boot:run -pl releasehub-bootstrap

# 4. 指定 profile 启动
./mvnw spring-boot:run -pl releasehub-bootstrap -Dspring-boot.run.profiles=prd

# 5. 生产环境 Jar 包启动
cd releasehub-bootstrap
java -jar target/releasehub-bootstrap-0.1.0-SNAPSHOT.jar --spring.profiles.active=prd
```

### 2.4 关键配置项

#### 数据库连接

```yaml
# application-{profile}.yml
spring:
  datasource:
    url: jdbc:postgresql://<HOST>:<PORT>/<DB_NAME>
    username: <USER>
    password: <PASSWORD>
```

#### JWT 安全配置

```yaml
security:
  jwt:
    # 生产环境必须使用强随机密钥，至少 256 位
    secret: ${JWT_SECRET:your-strong-secret-here}
    ttlMinutes: 120
```

> **生产环境**：JWT Secret 必须通过环境变量 `JWT_SECRET` 注入，不要在配置文件中硬编码。

#### CORS 配置

```yaml
cors:
  # 开发环境
  allowedOrigins: "http://localhost:5173"
  # 生产环境
  allowedOrigins: "https://releasehub.your-company.com"
```

### 2.5 API 文档

启动后访问：
- Swagger UI：`http://localhost:8080/swagger-ui.html`（local/test 默认开启，prd 默认关闭）
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

### 2.6 默认账号

| 用户名 | 密码 | 说明 |
|--------|------|------|
| admin | admin | 种子数据自动创建，生产环境需修改 |

---

## 三、前端部署

### 3.1 环境要求

| 组件 | 版本要求 |
|------|---------|
| Node.js | >= 20.19 或 >= 22.12 |
| pnpm | 最新稳定版 |

### 3.2 构建步骤

```bash
# 1. 进入前端目录
cd frontend

# 2. 安装依赖
pnpm install

# 3. 开发环境启动（含 HMR）
pnpm dev

# 4. 生产构建
pnpm build:prd

# 5. 预览生产构建
pnpm preview
```

### 3.3 环境变量

前端通过 `.env.*` 文件管理配置：

| 变量 | 说明 | 开发默认值 | 生产推荐值 |
|------|------|-----------|-----------|
| `VITE_API_BASE_URL` | API 基础路径 | `/` | `/` 或完整 URL |
| `VITE_APP_TITLE` | 应用标题 | `ReleaseHub` | `ReleaseHub` |
| `VITE_PROXY_TARGET` | Vite 代理目标 | `http://localhost:8080` | —（生产不使用） |

**配置文件对应关系**：

| 文件 | 构建命令 | 用途 |
|------|---------|------|
| `.env.development` | `pnpm dev` | 本地开发 |
| `.env.test` | `pnpm build:test` | 测试环境 |
| `.env.prd` | `pnpm build:prd` | 生产环境 |

### 3.4 Nginx 静态部署

构建产物位于 `frontend/dist/`，部署到 Nginx：

```nginx
server {
    listen 80;
    server_name releasehub.your-company.com;

    # 前端静态文件
    root /var/www/releasehub;
    index index.html;

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 静态资源缓存
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

部署步骤：

```bash
# 1. 构建前端
cd frontend && pnpm build:prd

# 2. 拷贝到 Nginx 目录
cp -r dist/* /var/www/releasehub/

# 3. 重载 Nginx
nginx -t && nginx -s reload
```

---

## 四、Flyway 数据库迁移

### 4.1 迁移策略

| Profile | Flyway | DDL | 说明 |
|---------|--------|-----|------|
| local | 关闭 | JPA `ddl-auto: update` | 开发便利，自动建表 |
| test/e2e | 开启 | JPA `ddl-auto: validate` | 迁移脚本验证 |
| prd | 开启 | JPA `ddl-auto: validate` | 严格迁移管控 |

### 4.2 迁移脚本位置

```
backend/releasehub-bootstrap/src/main/resources/db/migration/
├── V1__xxx.sql
├── V2__xxx.sql
├── ...
└── V27__upgrade_branch_rule_model.sql
```

### 4.3 添加新迁移

```bash
# 命名规范：V{序号}__{描述}.sql
# 示例：V28__add_deployment_config.sql
```

> **注意事项**：
> - `local` profile 下 Flyway 关闭，JPA 会直接按实体更新表结构，新字段/新表会自动创建
> - 从 local 切换到 prd 前，必须确保 Flyway 迁移脚本是 DDL 的完整超集

---

## 五、Docker 全栈部署（可选）

### 5.1 项目结构

```
release-hub/
├── docs/
│   └── docker-compose.yml       # PostgreSQL 基础配置
├── backend/
│   └── Dockerfile               # 后端镜像（需自行创建）
└── frontend/
    └── Dockerfile               # 前端镜像（需自行创建）
```

### 5.2 后端 Dockerfile 示例

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/releasehub-bootstrap/target/releasehub-bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prd"]
```

构建与运行：

```bash
# 构建 Jar
cd backend && ./mvnw clean package -DskipTests -pl releasehub-bootstrap

# 构建镜像
docker build -t releasehub-backend:latest -f backend/Dockerfile .

# 运行
docker run -d --name releasehub-backend \
  --network releasehub-net \
  -p 8080:8080 \
  -e JWT_SECRET=your-strong-secret \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/release_hub \
  releasehub-backend:latest
```

### 5.3 前端 Dockerfile 示例

```dockerfile
# 多阶段构建
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY frontend/ .
RUN pnpm build:prd

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

---

## 六、生产环境检查清单

### 6.1 安全配置

- [ ] 修改默认 admin 密码
- [ ] JWT Secret 通过环境变量注入，不少于 256 位
- [ ] CORS `allowedOrigins` 限制为实际域名
- [ ] Swagger UI 在生产环境关闭（prd profile 默认关闭）
- [ ] PostgreSQL 密码通过环境变量或密钥管理服务注入
- [ ] 启用 HTTPS（Nginx 配置 SSL 证书）
- [ ] `releasehub.seed.enabled` 设为 `false`

### 6.2 数据库

- [ ] 使用 `prd` profile（Flyway 开启，JPA ddl-auto: validate）
- [ ] 数据库备份策略已配置
- [ ] 连接池参数已按预期负载调整

### 6.3 监控

- [ ] 健康检查端点可访问：`GET /actuator/health`
- [ ] 应用日志输出到 stdout/文件
- [ ] 配置 JVM 内存参数（建议 `-Xms512m -Xmx1g` 起）

### 6.4 前端

- [ ] Vite build 无报错
- [ ] API 代理路径正确
- [ ] 静态资源缓存策略配置
- [ ] Gzip/Brotli 压缩启用

---

## 七、常见问题

### Q1：local profile 下表不存在

`local` profile 下 Flyway 关闭，JPA hibernate `ddl-auto: update` 会在启动时自动建表。确保 PostgreSQL 已启动且连接信息正确。

### Q2：prd profile 启动失败 "migration checksum mismatch"

Flyway 校验迁移脚本与数据库中的记录不一致。检查是否有手动修改过的迁移脚本。解决方法：
1. 确认所有迁移脚本是正确的
2. 执行 `REPAIR`：Flyway 会重新计算 checksum

### Q3：前端代理不生效

检查 `.env.development` 中 `VITE_PROXY_TARGET` 是否指向正确的后端地址。Vite 只在开发模式使用代理，生产环境由 Nginx 处理。

### Q4：CORS 报错

检查 `application-{profile}.yml` 中的 `cors.allowedOrigins` 是否包含前端域名。多个域名用逗号分隔。

---

## 八、相关文档

- [后端架构](../backend/docs/architecture.md)
- [前端架构](context/tech/architecture/frontend.md)
- [API 文档](context/tech/api/)
- [领域模型](context/business/domain-model.md)
- [项目总体规划](context/business/project-plan.md)
