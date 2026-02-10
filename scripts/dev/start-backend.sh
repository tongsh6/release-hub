#!/bin/bash
# 启动后端服务，自动处理数据库连接问题

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/release-hub"
LOG_FILE="/tmp/releasehub-backend-local.log"

echo "=== 启动 ReleaseHub 后端服务（使用本地 PostgreSQL）==="
echo ""

# 1. 检查 Docker Desktop 是否运行
echo "1. 检查 Docker Desktop..."
if ! docker info > /dev/null 2>&1; then
    echo "⚠️  Docker Desktop 未运行，尝试启动..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        open -a Docker
        echo "   等待 Docker Desktop 启动..."
        for i in {1..30}; do
            if docker info > /dev/null 2>&1; then
                echo "   ✅ Docker Desktop 已启动"
                break
            fi
            sleep 2
        done
    else
        echo "   ❌ 无法自动启动 Docker Desktop，请手动启动"
        exit 1
    fi
else
    echo "   ✅ Docker Desktop 正在运行"
fi
echo ""

# 2. 检查 PostgreSQL 容器
echo "2. 检查 PostgreSQL 容器..."
POSTGRES_CONTAINER=$(docker ps -a --filter "name=postgres" --format "{{.Names}}" | head -1)

if [ -z "$POSTGRES_CONTAINER" ]; then
    echo "   ⚠️  未找到 PostgreSQL 容器，尝试启动..."
    if [ -f "$SCRIPT_DIR/docker-compose.yml" ]; then
        echo "   使用 docker-compose 启动..."
        cd "$SCRIPT_DIR"
        docker-compose up -d postgres
    else
        echo "   启动 PostgreSQL 容器..."
        docker run -d --name postgres -e POSTGRES_USER=release_hub -e POSTGRES_PASSWORD=123456 -e POSTGRES_DB=release_hub -p 5432:5432 postgres:18.1 || {
            echo "   ❌ 启动 PostgreSQL 容器失败"
            exit 1
        }
    fi
    echo "   等待 PostgreSQL 启动..."
    sleep 5
    if docker ps --filter "name=postgres" --format "{{.Status}}" | grep -q "Up"; then
        echo "   ✅ PostgreSQL 容器已启动"
    else
        echo "   ❌ PostgreSQL 容器启动失败"
        docker logs postgres 2>&1 | tail -20
        exit 1
    fi
else
    if docker ps --filter "name=$POSTGRES_CONTAINER" --format "{{.Status}}" | grep -q "Up"; then
        echo "   ✅ PostgreSQL 容器正在运行: $POSTGRES_CONTAINER"
    else
        echo "   ⚠️  PostgreSQL 容器已停止，正在启动..."
        docker start "$POSTGRES_CONTAINER"
        sleep 3
        echo "   ✅ PostgreSQL 容器已启动"
    fi
fi
echo ""

# 3. 测试数据库连接
echo "3. 测试数据库连接..."
MAX_RETRIES=10
RETRY_COUNT=0
DB_CONNECTED=false

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if docker exec "$POSTGRES_CONTAINER" pg_isready -U release_hub > /dev/null 2>&1 || \
       docker exec "$POSTGRES_CONTAINER" pg_isready -U postgres > /dev/null 2>&1; then
        DB_CONNECTED=true
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "   等待数据库就绪... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 2
done

if [ "$DB_CONNECTED" = true ]; then
    echo "   ✅ 数据库连接正常"
    if ! docker exec "$POSTGRES_CONTAINER" psql -U postgres -lqt | cut -d \| -f 1 | grep -qw release_hub; then
        echo "   创建数据库 release_hub..."
        docker exec "$POSTGRES_CONTAINER" psql -U postgres -c "CREATE DATABASE release_hub;" || true
        docker exec "$POSTGRES_CONTAINER" psql -U postgres -c "CREATE USER release_hub WITH PASSWORD '123456';" || true
        docker exec "$POSTGRES_CONTAINER" psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE release_hub TO release_hub;" || true
        echo "   ✅ 数据库已创建"
    else
        echo "   ✅ 数据库 release_hub 已存在"
    fi
else
    echo "   ⚠️  数据库连接测试失败，但继续启动后端服务"
fi
echo ""

# 4. 启动后端服务
echo "4. 启动后端服务..."
cd "$BACKEND_DIR"

if lsof -ti:8080 > /dev/null 2>&1; then
    echo "   ⚠️  端口 8080 已被占用，尝试停止现有进程..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

echo "   使用 local profile 启动..."
echo "   日志文件: $LOG_FILE"
echo ""

mvn spring-boot:run -pl releasehub-bootstrap -Dspring-boot.run.profiles=local > "$LOG_FILE" 2>&1 &

BACKEND_PID=$!
echo "   后端服务 PID: $BACKEND_PID"
echo ""

# 5. 等待服务启动
echo "5. 等待服务启动..."
MAX_WAIT=60
WAIT_COUNT=0
SERVICE_READY=false

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if curl -s http://localhost:8080/api/v1/ping > /dev/null 2>&1; then
        SERVICE_READY=true
        break
    fi
    
    if ! ps -p $BACKEND_PID > /dev/null 2>&1; then
        echo "   ❌ 后端服务进程已退出"
        echo "   查看日志:"
        tail -50 "$LOG_FILE"
        exit 1
    fi
    
    WAIT_COUNT=$((WAIT_COUNT + 1))
    if [ $((WAIT_COUNT % 5)) -eq 0 ]; then
        echo "   等待中... ($WAIT_COUNT/$MAX_WAIT 秒)"
    fi
    sleep 1
done

if [ "$SERVICE_READY" = true ]; then
    echo "   ✅ 后端服务已启动"
    echo ""
    echo "=== 启动完成 ==="
    echo "后端服务: http://localhost:8080"
    echo "Swagger UI: http://localhost:8080/swagger-ui.html"
    echo "日志文件: $LOG_FILE"
    echo ""
    echo "查看日志: tail -f $LOG_FILE"
    echo "停止服务: kill $BACKEND_PID"
else
    echo "   ⚠️  服务启动超时，请检查日志: $LOG_FILE"
    tail -50 "$LOG_FILE"
    exit 1
fi
