# ReleaseHub 启动指南

## 快速启动

### 方式一：使用自动化脚本（推荐）

```bash
cd /Users/tongshuanglong/releasehub
./start_backend_with_db.sh
```

这个脚本会自动：
1. ✅ 检查并启动 Docker Desktop（如需要）
2. ✅ 检查并启动 PostgreSQL 容器
3. ✅ 创建数据库（如不存在）
4. ✅ 启动后端服务（使用 local profile）
5. ✅ 等待服务就绪并验证

### 方式二：手动启动

#### 1. 启动 PostgreSQL 容器

```bash
# 使用 docker-compose
docker-compose up -d postgres

# 或直接使用 docker
docker run -d \
  --name postgres \
  -e POSTGRES_USER=release_hub \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=release_hub \
  -p 5432:5432 \
  postgres:18.1
```

#### 2. 启动后端服务

```bash
cd release-hub
mvn spring-boot:run -pl releasehub-bootstrap -Dspring-boot.run.profiles=local
```

#### 3. 启动前端服务

```bash
cd release-hub-web
pnpm dev
```

## 服务地址

- **后端 API**: http://localhost:8080/api/v1
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **前端应用**: http://localhost:5173

## 数据库配置

### 本地开发环境（local profile）

- **数据库类型**: PostgreSQL
- **主机**: localhost
- **端口**: 5432
- **数据库名**: release_hub
- **用户名**: release_hub
- **密码**: 123456
- **Schema**: release_hub

### 配置文件位置

```
release-hub/releasehub-bootstrap/src/main/resources/application-local.yml
```

## 常见问题

### 1. Docker Desktop 未运行

**问题**: `Cannot connect to the Docker daemon`

**解决**: 
- macOS: 打开 Docker Desktop 应用
- 或使用脚本自动启动：`./start_backend_with_db.sh`

### 2. 端口 8080 已被占用

**问题**: `Port 8080 is already in use`

**解决**:
```bash
# 查找占用端口的进程
lsof -ti:8080

# 停止进程
kill -9 $(lsof -ti:8080)
```

### 3. 数据库连接失败

**问题**: `Connection refused` 或 `Authentication failed`

**解决**:
1. 检查 PostgreSQL 容器是否运行：
   ```bash
   docker ps | grep postgres
   ```

2. 如果容器未运行，启动它：
   ```bash
   docker start postgres
   ```

3. 检查数据库是否存在：
   ```bash
   docker exec postgres psql -U postgres -l
   ```

4. 如果数据库不存在，创建它：
   ```bash
   docker exec postgres psql -U postgres -c "CREATE DATABASE release_hub;"
   docker exec postgres psql -U postgres -c "CREATE USER release_hub WITH PASSWORD '123456';"
   docker exec postgres psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE release_hub TO release_hub;"
   ```

### 4. 后端服务启动失败

**检查日志**:
```bash
tail -f /tmp/releasehub-backend-local.log
```

**常见原因**:
- 数据库连接配置错误
- 端口被占用
- 依赖缺失（运行 `mvn clean install`）

## 停止服务

### 停止后端服务

```bash
# 查找进程 ID
lsof -ti:8080

# 停止进程
kill $(lsof -ti:8080)
```

### 停止前端服务

按 `Ctrl+C` 或查找进程并停止：
```bash
lsof -ti:5173 | xargs kill
```

### 停止 PostgreSQL 容器

```bash
docker stop postgres
```

## 开发建议

1. **首次启动前**:
   ```bash
   cd release-hub
   mvn clean install -DskipTests
   ```

2. **前端依赖安装**:
   ```bash
   cd release-hub-web
   pnpm install
   ```

3. **API 类型生成**（后端 API 变更后）:
   ```bash
   cd release-hub-web
   pnpm gen:api
   ```

## 测试

### 运行单元测试

```bash
cd release-hub
mvn test
```

### 运行集成测试

```bash
cd release-hub
mvn test -pl releasehub-bootstrap
```

### API 测试

使用 Swagger UI: http://localhost:8080/swagger-ui.html

或使用 curl:
```bash
# 登录
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['token'])")

# 测试 API
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/release-windows
```

## 日志查看

- **后端日志**: `/tmp/releasehub-backend-local.log`
- **前端日志**: 终端输出
- **PostgreSQL 日志**: `docker logs postgres`

## 更多信息

- 项目文档: `release-hub/release_hub_项目总体规划书.md`
- 集成测试报告: `INTEGRATION_TEST_REPORT.md`
