#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GITLAB_URL="http://localhost:9080"
BACKEND_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:5173"
BACKEND_PID_FILE="/tmp/releasehub-backend.pid"
FRONTEND_PID_FILE="/tmp/releasehub-frontend.pid"
LOG_BACKEND="/tmp/releasehub-backend.log"
LOG_FRONTEND="/tmp/releasehub-frontend.log"
ACTION="${1:-start}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
fail() { echo -e "${RED}[✗]${NC} $1"; exit 1; }
info() { echo -e "${CYAN}[i]${NC} $1"; }
header() { echo -e "\n${BOLD}── $1 ──${NC}\n"; }

usage() {
    cat <<EOF
Usage: scripts/dev/start-local-env.sh [start|hold|stop|restart|status]

start    Start PostgreSQL, GitLab, backend, and frontend
hold     Start services and keep this process alive until interrupted
stop     Stop backend and frontend processes started by this script
restart  Stop backend/frontend, then start the full local environment
status   Print local service status
EOF
}

stop_port() {
    local port="$1"
    local name="$2"
    local pids
    pids=$(lsof -ti:"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        warn "停止 $name 端口 $port: $pids"
        echo "$pids" | xargs kill 2>/dev/null || true
        sleep 2
        pids=$(lsof -ti:"$port" 2>/dev/null || true)
        [ -n "$pids" ] && echo "$pids" | xargs kill -9 2>/dev/null || true
    fi
}

stop_pid_file() {
    local file="$1"
    local name="$2"
    if [ -f "$file" ]; then
        local pid
        pid=$(cat "$file" 2>/dev/null || true)
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            warn "停止 $name PID=$pid"
            kill "$pid" 2>/dev/null || true
        fi
        rm -f "$file"
    fi
}

stop_app_services() {
    header "停止后端和前端"
    stop_pid_file "$BACKEND_PID_FILE" "后端"
    stop_pid_file "$FRONTEND_PID_FILE" "前端"
    stop_port 8080 "后端"
    stop_port 5173 "前端"
    log "后端和前端已停止（PostgreSQL/GitLab 保持运行）"
}

status_service() {
    local name="$1"
    local url="$2"
    if curl -s -o /dev/null "$url" 2>/dev/null; then
        log "$name 就绪: $url"
    else
        warn "$name 未就绪: $url"
    fi
}

print_status() {
    header "服务状态"
    if docker ps --format '{{.Names}}' | grep -qx 'releasehub-postgres'; then
        log "PostgreSQL 运行中"
    else
        warn "PostgreSQL 未运行"
    fi
    if docker ps --format '{{.Names}}' | grep -qx 'releasehub-gitlab'; then
        log "GitLab CE 运行中"
    else
        warn "GitLab CE 未运行"
    fi
    status_service "后端" "$BACKEND_URL/actuator/health"
    status_service "前端" "$FRONTEND_URL"
}

case "$ACTION" in
    start) ;;
    hold) ;;
    stop) stop_app_services; exit 0 ;;
    restart) stop_app_services ;;
    status) print_status; exit 0 ;;
    -h|--help|help) usage; exit 0 ;;
    *) usage; exit 1 ;;
esac

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║     ReleaseHub 本地全链路环境一键启动           ║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════╝${NC}"

# ── 1. 检查 Docker Desktop ──────────────────────────────────────────
header "1/6 检查 Docker 运行时"

CURRENT_CTX=$(docker context inspect -f '{{.Name}}' 2>/dev/null || echo "unknown")
if [ "$CURRENT_CTX" != "desktop-linux" ]; then
    info "当前 context: $CURRENT_CTX → 切换到 desktop-linux"
    docker context use desktop-linux
fi

if ! docker info > /dev/null 2>&1; then
    fail "Docker Desktop 未运行，请先启动 Docker Desktop"
fi
log "Docker Desktop 就绪 (context: desktop-linux)"

# ── 2. 启动 PostgreSQL ──────────────────────────────────────────────
header "2/6 启动 PostgreSQL (localhost:5433)"

if docker ps --format '{{.Names}}' | grep -qx 'releasehub-postgres'; then
    log "PostgreSQL 已运行"
else
    info "启动 PostgreSQL..."
    docker compose -f "$PROJECT_ROOT/docs/docker-compose.yml" up -d
    log "PostgreSQL 已启动"
fi

# ── 3. 启动 GitLab CE ───────────────────────────────────────────────
header "3/6 启动 GitLab CE (localhost:9080)"

if docker ps --format '{{.Names}}' | grep -qx 'releasehub-gitlab'; then
    log "GitLab CE 已运行"
else
    info "启动 GitLab CE（首次启动需要 2-3 分钟初始化）..."
    docker compose -f "$PROJECT_ROOT/docker-compose.gitlab.yml" up -d
fi

# 等待 GitLab 健康
info "等待 GitLab 就绪..."
for i in $(seq 1 60); do
    if curl -s -o /dev/null -w "%{http_code}" "$GITLAB_URL/users/sign_in" | grep -q "200"; then
        log "GitLab 就绪"
        break
    fi
    [ $((i % 6)) -eq 0 ] && echo "   等待中... ($i/60)"
    sleep 5
done

# ── 4. 初始化 GitLab 种子数据 ──────────────────────────────────────
header "4/6 初始化 GitLab 种子数据"

bash "$PROJECT_ROOT/scripts/e2e/init-gitlab.sh"
if [ -f /tmp/e2e-gitlab.env ]; then
    set -a; source /tmp/e2e-gitlab.env; set +a
fi
log "GitLab 种子数据就绪"

# ── 5. 启动后端 ─────────────────────────────────────────────────────
header "5/6 启动后端 (localhost:8080)"

if lsof -ti:8080 > /dev/null 2>&1; then
    warn "端口 8080 已被占用，尝试停止旧进程..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

info "编译并启动 Spring Boot (profile: local,real)..."
info "日志: $LOG_BACKEND"

cd "$PROJECT_ROOT/backend"
nohup bash -c 'mvn -pl releasehub-bootstrap -am -DskipTests install && cd releasehub-bootstrap && SPRING_PROFILES_ACTIVE=local,real mvn spring-boot:run' \
    > "$LOG_BACKEND" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$BACKEND_PID_FILE"

# 等待后端就绪
for i in $(seq 1 40); do
    if curl -s -o /dev/null "$BACKEND_URL/actuator/health" 2>/dev/null; then
        log "后端就绪 (PID: $BACKEND_PID)"
        break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        fail "后端进程异常退出，请查看日志: tail -50 $LOG_BACKEND"
    fi
    [ $((i % 5)) -eq 0 ] && echo "   等待中... ($i/40)"
    sleep 3
done

# ── 6. 启动前端 ─────────────────────────────────────────────────────
header "6/6 启动前端 (localhost:5173)"

if lsof -ti:5173 > /dev/null 2>&1; then
    warn "端口 5173 已被占用，尝试停止旧进程..."
    lsof -ti:5173 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

info "启动 Vite 开发服务器 (VITE_PROXY_TARGET=$BACKEND_URL)..."
info "日志: $LOG_FRONTEND"

cd "$PROJECT_ROOT/frontend"
nohup env VITE_PROXY_TARGET="$BACKEND_URL" pnpm dev > "$LOG_FRONTEND" 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$FRONTEND_PID_FILE"

# 等待前端就绪
for i in $(seq 1 20); do
    if curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL" 2>/dev/null | grep -q "200"; then
        log "前端就绪 (PID: $FRONTEND_PID)"
        break
    fi
    sleep 2
done

# ── 启动信息 ─────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║              所有服务已启动                      ║${NC}"
echo -e "${BOLD}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  ${CYAN}前端应用${NC}                                      ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    URL:      ${GREEN}$FRONTEND_URL${NC}                   ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    默认账号: admin / admin                        ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  ${CYAN}后端 API${NC}                                       ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    URL:      ${GREEN}$BACKEND_URL${NC}                           ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    Swagger:  $BACKEND_URL/swagger-ui.html        ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    Health:   $BACKEND_URL/actuator/health        ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  ${CYAN}GitLab CE${NC}                                       ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    URL:      ${GREEN}$GITLAB_URL${NC}                           ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    root:     root / releasehub123                 ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    e2e-user: e2e-user / e2e-pass123               ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  ${CYAN}PostgreSQL${NC}                                       ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    Host:     localhost:5433                        ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    Database: release_hub                           ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    账号:     postgres / 123456                     ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    连接串:   jdbc:postgresql://localhost:5433/release_hub${BOLD}║${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  ${CYAN}测试用户 (应用内)${NC}                              ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    Admin:            admin / admin                ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    Release Manager:  rmgr / rmgr123               ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    Developer:        dev / dev123                 ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    Tester:           qa / qa123                   ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${BOLD}║${NC}  ${YELLOW}运行 E2E 测试:${NC}                                   ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    bash scripts/e2e/run-vertical-slices.sh        ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  ${YELLOW}停止服务:${NC}                                       ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    scripts/dev/start-local-env.sh stop              ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    基础设施 (PG + GitLab) 保持运行，下次启动只需  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    重新执行 start/restart 即可。                    ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  ${YELLOW}日志文件:${NC}                                       ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    后端: tail -f $LOG_BACKEND${NC}        ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}    前端: tail -f $LOG_FRONTEND${NC}       ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}                                                  ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  ${YELLOW}提示:${NC} 此环境使用端口 5433/9080/8080/5173，     ${BOLD}║${NC}"
echo -e "${BOLD}║${NC}  与 CI 全量模式 (端口 5432/9081/8081/8090) 可并行运行${BOLD}║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$ACTION" = "hold" ]; then
    trap 'stop_app_services; exit 0' INT TERM
    info "hold 模式运行中；按 Ctrl-C 停止后端和前端"
    while true; do
        if ! curl -s -o /dev/null "$BACKEND_URL/actuator/health" 2>/dev/null; then
            warn "后端健康检查失败，退出 hold 模式"
            stop_app_services
            exit 1
        fi
        if ! curl -s -o /dev/null "$FRONTEND_URL" 2>/dev/null; then
            warn "前端健康检查失败，退出 hold 模式"
            stop_app_services
            exit 1
        fi
        sleep 5
    done
fi
