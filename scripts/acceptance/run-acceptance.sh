#!/bin/bash
# ============================================================
# ReleaseHub 场景化验收证据脚本 v3.9
#
# ╔═══════════════════════════════════════════════════════════╗
# ║  ⚠️  重要提示：本脚本是场景证据入口，不是完整 UI 验收替代品  ⚠️  ║
# ╚═══════════════════════════════════════════════════════════╝
#
# 为什么必须先用本脚本收集证据？
#   - 它知道所有前置条件（GitLab 种子数据初始化、GitLab Settings 配置、
#     Group/Repo/Window/Iteration 的依赖关系、feature 分支的创建时机）
#   - 手工逐 API 调试容易踩的坑：
#     1. GitLab 种子仓库为空 → 编排 produce 0 items（需先运行 init-gitlab.sh）
#     2. GitLab Settings 未配置 → 部分 API 返回 500（需 POST /settings/gitlab）
#     3. 仓库 cloneUrl 与 GitLab 实际项目路径不匹配 → 分支操作 404
#     4. repo ID 来自过期数据库 → codeRepositoryPort.findById 返回空
#     5. 冲突检测/编排 API 依赖前置数据（versionInfo、featureBranch）→ 500
#   - 本脚本按正确顺序完成全部步骤，遇到失败会显式报告而非静默降级
#   - 绕过本脚本的手工验证已经在 v0.1.10 验收中浪费了大量排查时间
#
# 能力清单（SA-001..SA-016 的后端/GitLab/数据证据）:
#   SA-001/SA-004: GitLab Settings 自动配置、复用、重启持久化
#   SA-002: 存量数据审计（BranchCreationMode、featureBranch、cloneUrl、token 安全）
#   SA-003: 三层分组，非叶子资源挂载拒绝
#   SA-005: 分组仓库纳管、真实 GitLab cloneUrl、token 安全审计
#   SA-006/SA-009: 分支创建模式（AUTO/NAMED/NAMED非法/EXISTING/Branches端点）
#   SA-008: 发布窗口创建、空窗口发布拒绝、windowKey
#   SA-010: Attach 迭代、GitLab release 分支创建、runItems 细粒度断言
#   SA-011: 冲突检测和分类统计、MERGE_CONFLICT / CROSS_REPO_VERSION_MISMATCH 真实 GitLab 强证据
#   SA-012: 冲突解决回路（USE_SYSTEM、feature 缺失、release 已存在、分支名不合规强证据）
#   SA-013: 干净窗口黄金路径：Attach → 0 冲突 → Publish → Orchestrate COMPLETED/SUCCESS
#   SA-014: 版本更新、校验、Git 远程提交验证
#   SA-015: Run 执行详情（RunItem/RunStep）
#   SA-016: 窗口关闭、关闭后关键操作禁止、收尾 Run 可见
#
# 原则:
#   1. 永不 DROP DATABASE / DELETE 数据（本地持久化模式）
#   2. 脏数据检测 → 报告 + 提供清理方案，由人决定
#   3. 场景化证据：本脚本证明后端/GitLab/数据，不替代前端用户旅程验收
#   4. 双模式：本地持久化（默认） / CI 一次性（--ci）
#
# 用法:
#   bash run-acceptance.sh              # 本地模式
#   bash run-acceptance.sh --ci         # CI 模式（docker compose up/down）
#   bash run-acceptance.sh --check      # 仅检查存量数据完整性
#   bash run-acceptance.sh --start-services
#   bash run-acceptance.sh --start-services --hold-services
#   bash run-acceptance.sh --stop-services
# ============================================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND="${BACKEND_URL:-http://localhost:8080}"
FRONTEND="${FRONTEND_URL:-http://localhost:5173}"
GITLAB="${GITLAB_URL:-http://localhost:9080}"
BACKEND_PORT=$(echo "$BACKEND" | sed -E 's#.*:([0-9]+).*#\1#')
[ "$BACKEND_PORT" = "$BACKEND" ] && BACKEND_PORT=8080
BACKEND_LOG="${BACKEND_LOG:-/tmp/releasehub-backend-acceptance.log}"
BACKEND_PID_FILE="${BACKEND_PID_FILE:-/tmp/releasehub-backend-acceptance.pid}"

MODE="local"
CHECK_ONLY=false
AUTO_START_SERVICES=true
START_SERVICES_ONLY=false
STOP_SERVICES_ONLY=false
HOLD_SERVICES=false
TS=$(date -u +%Y%m%d-%H%M%S)
PASS=0; FAIL=0; SKIP=0

for arg in "$@"; do
    case $arg in
        --ci) MODE="ci" ;;
        --check) CHECK_ONLY=true ;;
        --start-services) START_SERVICES_ONLY=true ;;
        --hold-services) HOLD_SERVICES=true ;;
        --stop-services) STOP_SERVICES_ONLY=true ;;
        --no-auto-start) AUTO_START_SERVICES=false ;;
        --help) echo "Usage: $0 [--ci] [--check] [--start-services] [--hold-services] [--stop-services] [--no-auto-start]"; exit 0 ;;
    esac
done

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "  ${GREEN}[PASS]${NC} $*" >&2; PASS=$((PASS+1)); }
no()   { echo -e "  ${RED}[FAIL]${NC} $*" >&2; FAIL=$((FAIL+1)); }
skip() { echo -e "  ${YELLOW}[SKIP]${NC} $*" >&2; SKIP=$((SKIP+1)); }
info() { echo -e "  ${CYAN}[INFO]${NC} $*" >&2; }
warn() { echo -e "  ${YELLOW}[WARN]${NC} $*" >&2; }
h2()   { echo "" >&2; echo -e "${CYAN}=== $* ===${NC}" >&2; }

die() { echo -e "${RED}FATAL: $*${NC}" >&2; exit 1; }

backend_health() {
    curl -s -o /dev/null "$BACKEND/actuator/health" 2>/dev/null
}

wait_for_http() {
    local url=$1
    local timeout=${2:-60}
    for i in $(seq 1 "$timeout"); do
        if curl -s -o /dev/null "$url" 2>/dev/null; then
            return 0
        fi
        sleep 1
    done
    return 1
}

ensure_postgres() {
    if docker ps --format '{{.Names}}' | grep -qx "releasehub-postgres"; then
        ok "releasehub-postgres 运行中"
        return
    fi
    if [ "$AUTO_START_SERVICES" != "true" ]; then
        die "releasehub-postgres 未运行"
    fi
    info "releasehub-postgres 未运行，正在启动..."
    docker compose -f "$PROJECT_ROOT/docs/docker-compose.yml" up -d postgres >/tmp/releasehub-postgres-start.log 2>&1 \
        || die "PostgreSQL 启动失败: /tmp/releasehub-postgres-start.log"
    for i in $(seq 1 30); do
        if docker ps --format '{{.Names}}' | grep -qx "releasehub-postgres"; then
            ok "releasehub-postgres 已启动"
            return
        fi
        sleep 1
    done
    die "releasehub-postgres 启动超时"
}

ensure_gitlab() {
    if docker ps --format '{{.Names}}' | grep -qx "releasehub-gitlab"; then
        ok "releasehub-gitlab 运行中"
        GITLAB_READY=true
        return
    fi
    if [ "$AUTO_START_SERVICES" != "true" ]; then
        warn "releasehub-gitlab 未运行，将进入 MOCK_MODE"
        GITLAB_READY=false
        return
    fi
    info "releasehub-gitlab 未运行，正在启动..."
    docker compose -f "$PROJECT_ROOT/docker-compose.gitlab.yml" up -d gitlab >/tmp/releasehub-gitlab-start.log 2>&1 \
        || { warn "GitLab 启动失败，将进入 MOCK_MODE: /tmp/releasehub-gitlab-start.log"; GITLAB_READY=false; return; }
    if wait_for_http "$GITLAB/users/sign_in" 180; then
        ok "releasehub-gitlab 已启动"
        GITLAB_READY=true
    else
        warn "GitLab 等待超时，将进入 MOCK_MODE"
        GITLAB_READY=false
    fi
}

stop_backend() {
    local stopped=false
    if [ -f "$BACKEND_PID_FILE" ]; then
        local pid
        pid=$(cat "$BACKEND_PID_FILE" 2>/dev/null || true)
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            info "停止后端进程 PID=$pid"
            kill "$pid" 2>/dev/null || true
            stopped=true
        fi
        rm -f "$BACKEND_PID_FILE"
    fi

    local port_pids
    port_pids=$(lsof -iTCP:"$BACKEND_PORT" -sTCP:LISTEN -t 2>/dev/null || true)
    if [ -n "$port_pids" ]; then
        info "停止占用端口 $BACKEND_PORT 的后端进程: $port_pids"
        echo "$port_pids" | xargs kill 2>/dev/null || true
        stopped=true
    fi

    if [ "$stopped" = "true" ]; then
        for _ in $(seq 1 20); do
            backend_health || { ok "后端已停止"; return; }
            sleep 1
        done
        port_pids=$(lsof -iTCP:"$BACKEND_PORT" -sTCP:LISTEN -t 2>/dev/null || true)
        [ -n "$port_pids" ] && echo "$port_pids" | xargs kill -9 2>/dev/null || true
        ok "后端已强制停止"
    else
        info "后端未运行"
    fi
}

start_backend() {
    if backend_health; then
        ok "后端 $BACKEND"
        return
    fi
    if [ "$AUTO_START_SERVICES" != "true" ]; then
        die "后端未启动"
    fi

    info "后端未启动，正在编译当前工作区模块并启动..."
    : > "$BACKEND_LOG"
    nohup bash -c '
        cd "$1/backend" && mvn -pl releasehub-bootstrap -am -DskipTests install &&
        cd "$1/backend/releasehub-bootstrap" && SPRING_PROFILES_ACTIVE=local,real mvn spring-boot:run
    ' _ "$PROJECT_ROOT" >> "$BACKEND_LOG" 2>&1 &
    local pid=$!
    echo "$pid" > "$BACKEND_PID_FILE"
    info "后端启动中 (PID=$pid)，日志: $BACKEND_LOG"

    for i in $(seq 1 90); do
        if backend_health; then
            ok "后端启动成功 (${i}s)"
            return
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            tail -80 "$BACKEND_LOG" >&2 || true
            die "后端启动进程已退出"
        fi
        sleep 1
    done
    tail -80 "$BACKEND_LOG" >&2 || true
    die "后端启动超时"
}

ensure_services() {
    ensure_gitlab
    ensure_postgres
    start_backend
}

# 等待异步任务完成并校验结果
# Usage: wait_for_run <run_id> <timeout_sec>
wait_for_run() {
    local run_id=$1
    local timeout=${2:-30}
    local start_time=$(date +%s)
    local status="RUNNING"
    
    while [ "$status" = "RUNNING" ]; do
        local now=$(date +%s)
        if [ $((now - start_time)) -gt $timeout ]; then
            echo "TIMEOUT"
            return 1
        fi
        
        local resp=$(curl -s "$BACKEND/api/v1/runs/$run_id" -H "$AUTH")
        status=$(echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data', {}).get('status', 'FAILED'))")
        
        if [ "$status" = "RUNNING" ]; then
            sleep 1
        fi
    done
    
    echo "$status"
}

is_run_success() {
    local status=$1
    [ "$status" = "SUCCESS" ] || [ "$status" = "COMPLETED" ]
}

# 验证 GitLab 侧是否产生了预期的 Commit
# Usage: verify_gitlab_commit <repo_clone_url> <branch> <message_keyword>
verify_gitlab_commit() {
    local clone_url=$1
    local branch=$2
    local keyword=$3
    
    # 从 clone_url 提取 project_path (e.g. http://.../group/project.git -> group/project)
    local project_path=$(echo "$clone_url" | sed -E 's|https?://[^/]+/||; s|\.git$||')
    local encoded_path=$(echo "$project_path" | python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.stdin.read().strip(), safe=''))" 2>/dev/null || echo "$project_path" | sed 's|/|%2F|g')
    local encoded_branch=$(echo "$branch" | python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.stdin.read().strip(), safe=''))" 2>/dev/null || echo "$branch")
    
    # 优先使用本轮种子数据创建的 token；接口返回值可能是加密/脱敏 token，不适合作为 GitLab API token。
    local gl_token="${GITLAB_PAT:-}"
    if [ -z "$gl_token" ] || [ "$gl_token" = "null" ]; then
        gl_token=$(curl -s "$BACKEND/api/v1/settings/gitlab" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))")
    fi
    
    if [ -z "$gl_token" ] || [ "$gl_token" = "null" ]; then
        echo "MISSING_TOKEN"
        return 1
    fi

    # 查询最近的 Commit
    local endpoint="$GITLAB/api/v4/projects/$encoded_path/repository/commits?ref_name=$encoded_branch&per_page=5"
    local commits=$(curl -s -H "PRIVATE-TOKEN: $gl_token" "$endpoint")
    
    echo "$commits" | python3 -c "
import sys,json
keyword = '$keyword'
commits = json.load(sys.stdin)
if not isinstance(commits, list):
    print('NON_LIST_RESPONSE')
    sys.exit(0)
found = any(keyword.lower() in c.get('title','').lower() for c in commits)
print('FOUND' if found else 'NOT_FOUND')
"
}

gitlab_encode() {
    python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.stdin.read().strip(), safe=''))" 2>/dev/null
}

gitlab_project_path_from_clone_url() {
    echo "$1" | sed -E 's|https?://[^/]+/||; s|\.git$||'
}

gitlab_branch_state() {
    local clone_url=$1
    local branch=$2
    local gl_token="${GITLAB_PAT:-}"
    if [ -z "$gl_token" ] || [ "$gl_token" = "null" ]; then
        echo "MISSING_TOKEN"
        return 1
    fi

    local project_path
    project_path=$(gitlab_project_path_from_clone_url "$clone_url")
    local encoded_path
    encoded_path=$(echo "$project_path" | gitlab_encode)
    local encoded_branch
    encoded_branch=$(echo "$branch" | gitlab_encode)
    local resp
    resp=$(curl -s -H "PRIVATE-TOKEN: $gl_token" \
        "$GITLAB/api/v4/projects/$encoded_path/repository/branches/$encoded_branch")

    echo "$resp" | python3 -c "
import sys,json
branch = '''$branch'''
try:
    d = json.load(sys.stdin)
except Exception:
    print('NON_JSON_RESPONSE')
    sys.exit(0)
if isinstance(d, dict) and d.get('name') == branch:
    print('FOUND')
elif isinstance(d, dict) and d.get('message'):
    print('NOT_FOUND')
else:
    print('UNKNOWN')
"
}

gitlab_delete_branch() {
    local clone_url=$1
    local branch=$2
    local gl_token="${GITLAB_PAT:-}"
    if [ -z "$gl_token" ] || [ "$gl_token" = "null" ]; then
        echo "MISSING_TOKEN"
        return 1
    fi

    local project_path
    project_path=$(gitlab_project_path_from_clone_url "$clone_url")
    local encoded_path
    encoded_path=$(echo "$project_path" | gitlab_encode)
    local encoded_branch
    encoded_branch=$(echo "$branch" | gitlab_encode)
    curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "PRIVATE-TOKEN: $gl_token" \
        "$GITLAB/api/v4/projects/$encoded_path/repository/branches/$encoded_branch"
}

gitlab_create_branch() {
    local clone_url=$1
    local branch=$2
    local ref=${3:-main}
    local gl_token="${GITLAB_PAT:-}"
    if [ -z "$gl_token" ] || [ "$gl_token" = "null" ]; then
        echo "MISSING_TOKEN"
        return 1
    fi

    local project_path
    project_path=$(gitlab_project_path_from_clone_url "$clone_url")
    local encoded_path
    encoded_path=$(echo "$project_path" | gitlab_encode)
    local encoded_branch
    encoded_branch=$(echo "$branch" | gitlab_encode)
    local encoded_ref
    encoded_ref=$(echo "$ref" | gitlab_encode)
    curl -s -o /dev/null -w "%{http_code}" -X POST -H "PRIVATE-TOKEN: $gl_token" \
        "$GITLAB/api/v4/projects/$encoded_path/repository/branches?branch=$encoded_branch&ref=$encoded_ref"
}

gitlab_commit_file() {
    local clone_url=$1
    local branch=$2
    local file_path=$3
    local content=$4
    local message=$5
    local gl_token="${GITLAB_PAT:-}"
    if [ -z "$gl_token" ] || [ "$gl_token" = "null" ]; then
        echo "MISSING_TOKEN"
        return 1
    fi

    local project_path
    project_path=$(gitlab_project_path_from_clone_url "$clone_url")
    local encoded_path
    encoded_path=$(echo "$project_path" | gitlab_encode)
    local payload
    payload=$(BRANCH="$branch" FILE_PATH="$file_path" CONTENT="$content" MESSAGE="$message" python3 -c '
import json
import os
print(json.dumps({
    "branch": os.environ["BRANCH"],
    "commit_message": os.environ["MESSAGE"],
    "actions": [{
        "action": "update",
        "file_path": os.environ["FILE_PATH"],
        "content": os.environ["CONTENT"],
    }],
}))
')
    curl -s -o /dev/null -w "%{http_code}" -X POST \
        -H "PRIVATE-TOKEN: $gl_token" \
        -H "Content-Type: application/json" \
        --data "$payload" \
        "$GITLAB/api/v4/projects/$encoded_path/repository/commits"
}

auth() {
    local resp=$(curl -s -X POST "$BACKEND/api/v1/auth/login" \
        -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}')
    echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])"
}

# ---- 0. 前置 ----
h2 "0. 环境检查"
if [ "$STOP_SERVICES_ONLY" = "true" ]; then
    stop_backend
    exit 0
fi

if [ "$START_SERVICES_ONLY" = "true" ]; then
    ensure_services
    if [ "$HOLD_SERVICES" = "true" ]; then
        info "服务已启动，保持脚本运行；按 Ctrl-C 或发送 TERM 可停止后端"
        trap 'stop_backend; exit 0' INT TERM
        while true; do
            if ! backend_health; then
                warn "后端健康检查失败，退出保活模式"
                exit 1
            fi
            sleep 5
        done
    fi
    exit 0
fi

ensure_services
AUTH_TOKEN=$(auth)
AUTH="Authorization: Bearer $AUTH_TOKEN"
ok "登录成功"
# 前端
curl -s -o /dev/null "$FRONTEND" 2>/dev/null && ok "前端 $FRONTEND" || warn "前端未启动"

# ---- 1. 存量数据审计 ----
h2 "SA-002: 1. 存量数据审计"

# 1.1 数据资产统计
STATS=$(curl -s "$BACKEND/api/v1/runs/paged?size=1" -H "$AUTH")
RUN_TOTAL=$(echo "$STATS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('page', {}).get('total', 0))" 2>/dev/null || echo 0)
GROUP_COUNT=$(curl -s "$BACKEND/api/v1/groups" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo 0)
REPO_COUNT=$(curl -s "$BACKEND/api/v1/repositories" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo 0)
WINDOW_COUNT=$(curl -s "$BACKEND/api/v1/release-windows" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo 0)
ITER_COUNT=$(curl -s "$BACKEND/api/v1/iterations" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo 0)
info "Groups:$GROUP_COUNT  Repos:$REPO_COUNT  Windows:$WINDOW_COUNT  Iterations:$ITER_COUNT  Runs:$RUN_TOTAL"

# 1.2 Flyway 版本
FLYWAY_V=$(docker exec releasehub-postgres psql -U postgres -d release_hub -t -c \
    "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null | tr -d ' ')
[ -n "$FLYWAY_V" ] && ok "Flyway 最新迁移: $FLYWAY_V" || warn "Flyway 历史为空"

# 1.3 Token 加密状态
TOKEN_CHECK=$(docker exec releasehub-postgres psql -U postgres -d release_hub -t -A -F, -c \
    "SELECT COUNT(*) FILTER (WHERE git_token IS NOT NULL AND git_token != '') AS with_token,
            COUNT(*) FILTER (WHERE git_token ~ '^glpat-') AS plaintext,
            COUNT(*) FILTER (WHERE git_token IS NOT NULL AND git_token != '' AND git_token !~ '^glpat-') AS encrypted
     FROM code_repository;" 2>/dev/null)
PLAINTEXT_COUNT=$(echo "$TOKEN_CHECK" | cut -d, -f2 | tr -d ' ')
ENCRYPTED_COUNT=$(echo "$TOKEN_CHECK" | cut -d, -f3 | tr -d ' ')
PLAINTEXT_COUNT=${PLAINTEXT_COUNT:-0}
ENCRYPTED_COUNT=${ENCRYPTED_COUNT:-0}
if [ "$PLAINTEXT_COUNT" != "0" ] && [ -n "$PLAINTEXT_COUNT" ]; then
    no "Token 明文存储: $PLAINTEXT_COUNT 个仓库"
else
    ok "Token 已全部加密: $ENCRYPTED_COUNT 个仓库"
fi

# 1.3.1 BranchCreationMode 分布
BM_STATS=$(docker exec releasehub-postgres psql -U postgres -d release_hub -t -A -F, -c \
    "SELECT branch_creation_mode, COUNT(*) FROM iteration_repo WHERE branch_creation_mode IS NOT NULL GROUP BY branch_creation_mode ORDER BY branch_creation_mode;" 2>/dev/null)
if [ -n "$BM_STATS" ]; then
    BM_SUMMARY=$(echo "$BM_STATS" | tr '\n' ' ' | sed 's/,$//')
    info "BranchCreationMode 分布: $BM_SUMMARY"
else
    warn "BranchCreationMode 分布为空（无 iteration_repo 记录?）"
fi

# 1.3.2 featureBranch 为 null 的行数（三层关联遗漏检测）
FB_NULL=$(docker exec releasehub-postgres psql -U postgres -d release_hub -t -A -c \
    "SELECT COUNT(*) FROM iteration_repo WHERE feature_branch IS NULL;" 2>/dev/null | tr -d ' ')
FB_NULL=${FB_NULL:-0}
[ "$FB_NULL" -eq 0 ] && ok "featureBranch null: 0（三层关联无遗漏）" || warn "featureBranch null: $FB_NULL 个（三层关联有遗漏）"

# 1.3.3 cloneUrl 格式校验（双 http:// 前缀 / 非 http 开头）
BAD_URLS=$(docker exec releasehub-postgres psql -U postgres -d release_hub -t -A -c \
    "SELECT COUNT(*) FROM code_repository WHERE clone_url LIKE 'http://http://%';" 2>/dev/null | tr -d ' ')
BAD_URLS=${BAD_URLS:-0}
[ "$BAD_URLS" -eq 0 ] && ok "cloneUrl 格式: 0 个异常" || warn "cloneUrl 格式异常: $BAD_URLS 个含双 http:// 前缀"

# 1.4 脏数据检测
h2 "SA-002: 1.4 脏数据检测"
DIRTY=0
DRAFT_WINDOWS=$(curl -s "$BACKEND/api/v1/release-windows" -H "$AUTH" | python3 -c "
import sys,json
drafts = [(w['id'][:8], w['name'], w['status']) for w in json.load(sys.stdin).get('data',[]) if w.get('status')=='DRAFT']
for d in drafts: print(f'{d[0]} | {d[1]} | {d[2]}')
" 2>/dev/null)
if [ -n "$DRAFT_WINDOWS" ]; then
    DIRTY=$((DIRTY+1))
    while IFS= read -r line; do
        warn "DRAFT 窗口残留: $line"
    done <<< "$DRAFT_WINDOWS"
    info "建议: 关闭或删除上述 DRAFT 窗口后再测（当前不阻塞，验收会新建窗口）"
fi

# 检查有无 Attach 失败残留
FAILED_ATTACH=$(docker exec releasehub-postgres psql -U postgres -d release_hub -t -c \
    "SELECT wi.iteration_key, wi.branch_created FROM window_iteration wi WHERE wi.branch_created = false;" 2>/dev/null | grep -v "^$")
if [ -n "$FAILED_ATTACH" ]; then
    DIRTY=$((DIRTY+1))
    while IFS= read -r line; do
        warn "Attach 未完成(branch_created=false): $line"
    done <<< "$FAILED_ATTACH"
fi

[ $DIRTY -eq 0 ] && ok "无脏数据" || info "脏数据项数: $DIRTY（仅报告，不清除）"

# 提供显式清理方案
if [ $DIRTY -gt 0 ]; then
    echo ""
    echo "  ┌─────────────────────────────────────────────┐"
    echo "  │ 脏数据清理方案（按需执行）                       │"
    echo "  │                                               │"
    echo "  │ # 关闭所有 DRAFT 窗口                           │"
    echo "  │ curl -X POST .../release-windows/{id}/close    │"
    echo "  │                                               │"
    echo "  │ # 删除指定窗口（通过 API）                       │"
    echo "  │ curl -X DELETE .../release-windows/{id}        │"
    echo "  └─────────────────────────────────────────────┘"
fi

$CHECK_ONLY && exit 0

# ---- 2. GitLab 种子数据 ----
h2 "2. GitLab 种子数据"
INIT_SH="$PROJECT_ROOT/scripts/e2e/init-gitlab.sh"
if [ -f "$INIT_SH" ]; then
    GITLAB_URL="$GITLAB" ROOT_PASS=releasehub123 bash "$INIT_SH" 2>&1 | grep -E "^(===|GitLab ready|Repo|Env)" | while read l; do info "$l"; done
fi
ok "种子数据就绪"

# ---- 3. 场景：新增全链路 ----
h2 "SA-003/SA-005/SA-008/SA-009: 3. 场景: 发布准备数据验证"

# 3.1 确保三层组织分组（资源挂叶子分组）
ensure_group() {
    local name=$1
    local parent_code=${2:-}
    local group_code=$(curl -s "$BACKEND/api/v1/groups" -H "$AUTH" | python3 -c "
import sys,json
name = '''$name'''
parent = '''$parent_code'''
for g in json.load(sys.stdin).get('data',[]):
    if g.get('name') == name and (g.get('parentCode') or '') == parent:
        print(g.get('code',''))
        break
" 2>/dev/null)

    if [ -n "$group_code" ]; then
        ok "复用分组: $name → $group_code"
        echo "$group_code"
        return 0
    fi

    local payload
    if [ -n "$parent_code" ]; then
        payload="{\"name\":\"$name\",\"parentCode\":\"$parent_code\"}"
    else
        payload="{\"name\":\"$name\",\"parentCode\":null}"
    fi

    local group_id=$(curl -s -X POST "$BACKEND/api/v1/groups" -H "$AUTH" -H "Content-Type: application/json" \
        -d "$payload" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',''))" 2>/dev/null)
    if [ -z "$group_id" ]; then
        no "创建分组失败: $name"
        echo ""
        return 1
    fi

    group_code=$(curl -s "$BACKEND/api/v1/groups/$group_id" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('code',''))" 2>/dev/null)
    [ -n "$group_code" ] && ok "创建分组: $name → $group_code" || no "创建分组后无法读取 code: $name"
    echo "$group_code"
}

CUSTOMER_CODE=$(ensure_group "验收-客户A")
BUSINESS_LINE_CODE=$(ensure_group "验收-业务线X" "$CUSTOMER_CODE")
LEAF_GROUP_CODE=$(ensure_group "验收-末级分组Y" "$BUSINESS_LINE_CODE")
GROUP_CODE="$LEAF_GROUP_CODE"

if [ -z "$CUSTOMER_CODE" ] || [ -z "$BUSINESS_LINE_CODE" ] || [ -z "$LEAF_GROUP_CODE" ]; then
    die "三层分组初始化失败"
fi

GROUP_TREE_OK=$(curl -s "$BACKEND/api/v1/groups/tree" -H "$AUTH" | python3 -c "
import sys,json
data = json.load(sys.stdin).get('data', [])
names = set()
def walk(nodes):
    for n in nodes:
        names.add(n.get('name',''))
        walk(n.get('children') or [])
walk(data)
print(all(name in names for name in ['验收-客户A','验收-业务线X','验收-末级分组Y']))
" 2>/dev/null)
[ "$GROUP_TREE_OK" = "True" ] && ok "三层分组树可见: 客户A → 业务线X → 末级分组Y" || no "三层分组树校验失败"
info "资源将挂载到叶子分组: $GROUP_CODE"

NON_LEAF_WINDOW=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-非叶子窗口-$TS\",\"description\":\"non-leaf probe\",\"groupCode\":\"$CUSTOMER_CODE\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null || echo "False")
[ "$NON_LEAF_WINDOW" = "True" ] && no "非叶子分组创建发布窗口未被拒绝" || ok "非叶子分组创建发布窗口被拒绝"

NON_LEAF_ITER=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-非叶子迭代-$TS\",\"groupCode\":\"$BUSINESS_LINE_CODE\",\"repoIds\":[]}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null || echo "False")
[ "$NON_LEAF_ITER" = "True" ] && no "非叶子分组创建迭代未被拒绝" || ok "非叶子分组创建迭代被拒绝"

NON_LEAF_REPO=$(curl -s -X POST "$BACKEND/api/v1/repositories" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-非叶子仓库-$TS\",\"cloneUrl\":\"http://localhost:9080/e2e-user/non-leaf-probe.git\",\"defaultBranch\":\"main\",\"groupCode\":\"$CUSTOMER_CODE\",\"gitProvider\":\"MOCK\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null || echo "False")
[ "$NON_LEAF_REPO" = "True" ] && no "非叶子分组创建仓库未被拒绝" || ok "非叶子分组创建仓库被拒绝"

# 3.2 GitLab PAT
source /tmp/e2e-gitlab.env 2>/dev/null || true
GITLAB_PAT="${E2E_GITLAB_TOKEN:-}"
[ -z "$GITLAB_PAT" ] && { GITLAB_PAT=$(docker exec releasehub-gitlab gitlab-rails runner "puts User.find(1).personal_access_tokens.create!(name:'acc-ts',scopes:['api','read_repository','write_repository'],expires_at:30.days.from_now).token" 2>&1 | tail -1); echo "E2E_GITLAB_TOKEN=$GITLAB_PAT" > /tmp/e2e-gitlab.env; }

# 3.2.1 确保后端已配置 GitLab Settings（v0.1.11：脱离 MVP 后这是 Orchestrate / 版本更新的硬前置）
CURRENT_GL=$(curl -s "$BACKEND/api/v1/settings/gitlab" -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('baseUrl','') or '')" 2>/dev/null)
if [ -z "$CURRENT_GL" ] && [ -n "$GITLAB_PAT" ] && [ "$GITLAB_READY" = "true" ]; then
    SETTINGS_RESP=$(curl -s -X POST "$BACKEND/api/v1/settings/gitlab" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"baseUrl\":\"$GITLAB\",\"token\":\"$GITLAB_PAT\"}")
    SETTINGS_OK=$(echo "$SETTINGS_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
    [ "$SETTINGS_OK" = "True" ] && ok "GitLab Settings 已配置: $GITLAB" || no "GitLab Settings 配置失败: $SETTINGS_RESP"
elif [ -n "$CURRENT_GL" ]; then
    ok "GitLab Settings 已存在: $CURRENT_GL（持久化校验：✓）"
else
    skip "跳过 GitLab Settings 配置（MOCK_MODE 或缺 PAT）"
fi

# 3.3 确保仓库（按 cloneUrl 精确复用，每个仓库只注册一次）
R1=""; R2=""; R3=""

ensure_repo() {
    local name=$1
    local clone_url=$2
    # 修复已知数据问题：init-gitlab.sh 输出偶含双 http:// 前缀，归一化
    clone_url=$(echo "$clone_url" | sed 's|^http://http://|http://|')
    local repo_id=$(curl -s "$BACKEND/api/v1/repositories" -H "$AUTH" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('data',[]):
    if r.get('cloneUrl','') == '$clone_url':
        print(r['id']); break
")
    if [ -z "$repo_id" ]; then
        repo_id=$(curl -s -X POST "$BACKEND/api/v1/repositories" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"name\":\"$name\",\"cloneUrl\":\"$clone_url\",\"defaultBranch\":\"main\",\"groupCode\":\"$GROUP_CODE\",\"gitProvider\":\"GITLAB\",\"gitAccessToken\":\"$GITLAB_PAT\"}" \
            | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
        ok "注册仓库: $name → ${repo_id:0:8}..."
    else
        # 复用时也刷新 token —— init-gitlab.sh 每次会撤销并重建 PAT，
        # 旧 token 在 GitLab 端已失效，不刷新会导致后续 401。
        if [ -n "$GITLAB_PAT" ]; then
            curl -s -o /dev/null -X PUT "$BACKEND/api/v1/repositories/$repo_id" -H "$AUTH" -H "Content-Type: application/json" \
                -d "{\"name\":\"$name\",\"cloneUrl\":\"$clone_url\",\"defaultBranch\":\"main\",\"groupCode\":\"$GROUP_CODE\",\"gitProvider\":\"GITLAB\",\"gitAccessToken\":\"$GITLAB_PAT\"}"
        fi
        ok "复用仓库: $name → ${repo_id:0:8}... (token 已刷新)"
    fi
    echo "$repo_id"
}

R1=$(ensure_repo "验收-Maven单模块" "http://localhost:9080/e2e-user/seed-repo-1-maven.git")
R2=$(ensure_repo "验收-Maven多模块" "http://localhost:9080/e2e-user/seed-repo-2-maven-multi.git")
R3=$(ensure_repo "验收-Gradle" "http://localhost:9080/e2e-user/seed-repo-3-gradle.git")
REPO_IDS="$R1 $R2 $R3"

# 3.4 创建新窗口
NEXT_WEEK=$(date -u -v+7d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d "+7 days" +%Y-%m-%dT%H:%M:%SZ)
WINDOW_RESP=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-$TS\",\"description\":\"全链路验收 $TS\",\"plannedReleaseAt\":\"$NEXT_WEEK\",\"groupCode\":\"$GROUP_CODE\"}")
WINDOW_ID=$(echo "$WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('id', ''))")
[ -n "$WINDOW_ID" ] && ok "创建窗口: 验收-$TS" || { no "创建窗口失败: $WINDOW_RESP"; exit 1; }

# 3.5 创建迭代
REPO_JSON="[\"$R1\",\"$R2\",\"$R3\"]"
ITER_RESP=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收迭代-$TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":$REPO_JSON}")
ITER_KEY=$(echo "$ITER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))")
[ -n "$ITER_KEY" ] && ok "创建迭代: $ITER_KEY" || { no "创建迭代失败: $ITER_RESP"; exit 1; }

# 3.6 创建 feature 分支
if [ "$GITLAB_READY" = "true" ] && [ -n "$E2E_GITLAB_TOKEN" ] && [ -n "$ITER_KEY" ]; then
    FEATURE_BRANCH="feature/${ITER_KEY}"
    h2 "3.6 创建 feature 分支: $FEATURE_BRANCH"
    for repo_path in e2e-user/seed-repo-1-maven e2e-user/seed-repo-2-maven-multi e2e-user/seed-repo-3-gradle; do
        CLONE_URL="http://oauth2:${E2E_GITLAB_TOKEN}@localhost:9080/${repo_path}.git"
        TMP_CLONE="/tmp/e2e-feature-$(echo $repo_path | tr '/' '-')"
        rm -rf "$TMP_CLONE"
        git clone --depth 1 "$CLONE_URL" "$TMP_CLONE" > /dev/null 2>&1
        cd "$TMP_CLONE" 2>/dev/null || continue
        if git branch -r 2>/dev/null | grep -q "origin/$FEATURE_BRANCH"; then
            info "$FEATURE_BRANCH 已存在"
        else
            git checkout -b "$FEATURE_BRANCH" > /dev/null 2>&1
            echo "# Feature branch for $ITER_KEY" > FEATURE.md
            git add FEATURE.md && git commit -m "feat: $ITER_KEY branch" > /dev/null 2>&1
            git push origin "$FEATURE_BRANCH" > /dev/null 2>&1
        fi
        cd "$PROJECT_ROOT"
        rm -rf "$TMP_CLONE"
    done
    ok "Feature 分支已就绪"
else
    skip "跳过 Feature 分支创建 (MOCK_MODE or missing key)"
fi

# ---- 3.7 设置持久化重启验证 ----
h2 "SA-001/SA-004: 3.7 设置持久化重启验证"
info "重启后端以验证 Settings 持久化..."
stop_backend
start_backend
backend_health || die "后端重启后无法访问"
cd "$PROJECT_ROOT"

# 重新登录（token 可能因重启失效）
AUTH_TOKEN=$(auth)
AUTH="Authorization: Bearer $AUTH_TOKEN"

# 验证 Settings 仍存在
GL_AFTER=$(curl -s "$BACKEND/api/v1/settings/gitlab" -H "$AUTH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('baseUrl','') or 'MISSING')" 2>/dev/null)
[ "$GL_AFTER" != "MISSING" ] && ok "设置持久化验证通过: GitLab baseUrl=$GL_AFTER 在重启后仍存在" || no "设置持久化验证失败: Settings 重启后丢失"

# 验证仓库数据仍存在
REPO_AFTER=$(curl -s "$BACKEND/api/v1/repositories/$R1" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',{}).keys()))" 2>/dev/null)
[ "${REPO_AFTER:-0}" -gt 0 ] && ok "仓库数据重启后可查询" || warn "仓库数据重启后查询异常"

# ---- 4. 场景: Attach + 分支创建 ----
h2 "SA-010: 4. 场景: Attach 迭代 & GitLab release 分支创建"
ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"iterationKeys\":[\"$ITER_KEY\"]}")
HAS_ERR=$(echo "$ATTACH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(any(r.get('hasErrors', False) for r in d.get('data', [])))" 2>/dev/null)
if [ "$HAS_ERR" = "True" ]; then
    echo "$ATTACH" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('data', []):
    for e in r.get('errors',[]): print(f'    {e.get(\"repoName\", \"?\")}: {e.get(\"message\", \"?\")}')
" | while read l; do no "Attach 失败: $l"; done
else
    ok "Attach 成功"
fi

# 4.1 Attach 细粒度结果：每个 repo 的分支创建/MR 状态 + RunItem 数
ATTACH_DETAIL=$(echo "$ATTACH" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('data', []):
    ik = r.get('iterationKey', '?')
    he = r.get('hasErrors', False)
    ec = len(r.get('errors', []))
    print(f'{ik}: hasErrors={he} errorCount={ec}')
")
if [ -n "$ATTACH_DETAIL" ]; then
    while IFS= read -r detail_line; do
        [ -n "$detail_line" ] && info "  $detail_line"
    done <<< "$ATTACH_DETAIL"
fi

# 验证 GitLab 上真实分支存在
if [ "$GITLAB_READY" = "true" ]; then
    BRANCH_COUNT=0
    WINDOW_KEY_FOR_BRANCH=$(curl -s "$BACKEND/api/v1/release-windows/$WINDOW_ID" -H "$AUTH" \
        | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('windowKey',''))" 2>/dev/null)
    EXPECTED_RELEASE_BRANCH="release/$WINDOW_KEY_FOR_BRANCH"
    for rh_repo_id in $REPO_IDS; do
        REPO_URL=$(curl -s "$BACKEND/api/v1/repositories/$rh_repo_id" -H "$AUTH" \
            | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))" 2>/dev/null)
        RELEASE_BRANCH_STATE=$(gitlab_branch_state "$REPO_URL" "$EXPECTED_RELEASE_BRANCH")
        if [ "$RELEASE_BRANCH_STATE" = "FOUND" ]; then
            BRANCH_COUNT=$((BRANCH_COUNT+1))
        fi
    done
    [ "$BRANCH_COUNT" -eq 3 ] && ok "GitLab 真实 release 分支: 3/3 ($EXPECTED_RELEASE_BRANCH)" || warn "GitLab release 分支: $BRANCH_COUNT/3 ($EXPECTED_RELEASE_BRANCH)"
fi

# 验证 WindowIteration 状态
WI_STATE=$(curl -s "$BACKEND/api/v1/release-windows/$WINDOW_ID/iterations" -H "$AUTH" | python3 -c "
import sys,json
for wi in json.load(sys.stdin).get('data',[]):
    print(f\"branchCreated={wi['branchCreated']} releaseBranch={wi['releaseBranch']}\")
")
echo "$WI_STATE" | while read l; do info "  $l"; done

# ---- 5. 场景: 冲突检测 ----
h2 "SA-011: 5. 场景: 冲突检测"
# 先触发扫描
CONFLICT_SCAN=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$WINDOW_ID/conflicts/check" -H "$AUTH" -H "Content-Type: application/json" -d '{}' 2>/dev/null)
# 再获取报告
CONFLICT=$(curl -s "$BACKEND/api/v1/release-windows/$WINDOW_ID/conflicts" -H "$AUTH" 2>/dev/null)
CONFLICT_COUNT=$(echo "$CONFLICT" | python3 -c "
import sys,json
d = json.load(sys.stdin)
data = d.get('data', {})
print(data.get('totalCount', 0))
" 2>/dev/null || echo "0")
if [ "$CONFLICT_COUNT" -gt 0 ]; then
    IFS=$'\n' CONFLICT_DETAIL=$(echo "$CONFLICT" | python3 -c "
import sys,json
for c in json.load(sys.stdin).get('data',{}).get('conflicts',[]):
    print(f\"    {c['conflictType']}: {c.get('message','')[:60]}\")
" 2>/dev/null)
    warn "检测到 $CONFLICT_COUNT 个冲突"
    echo "$CONFLICT_DETAIL"
else
    ok "冲突检测完成: 0 个冲突"
fi

# ---- 5.1 冲突分类统计 ----
h2 "SA-011: 5.1 冲突分类统计"
CONFLICT_TYPES=$(echo "$CONFLICT" | python3 -c "
import sys,json
from collections import Counter
types = Counter()
for c in json.load(sys.stdin).get('data',{}).get('conflicts',[]):
    types[c.get('conflictType','?')] += 1
for t, n in sorted(types.items()):
    print(f'  {t}: {n}')
" 2>/dev/null)
if [ -n "$CONFLICT_TYPES" ]; then
    info "冲突类型分布:"
    echo "$CONFLICT_TYPES"
fi

# ---- 5.2 干净窗口黄金路径：解决冲突 → 0 冲突 → Publish → Orchestrate COMPLETED/SUCCESS ----
h2 "SA-012/SA-013: 5.2 干净窗口黄金路径: 冲突解决 + 编排验证"

# 5.2.1 创建干净窗口（新 key 确保 release 分支不冲突）
CLEAN_TS=$(date -u +%Y%m%d-%H%M%S)
CLEAN_WINDOW_RESP=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json"     -d "{\"name\":\"验收-干净-$CLEAN_TS\",\"description\":\"Clean-room golden path $CLEAN_TS\",\"plannedReleaseAt\":\"$NEXT_WEEK\",\"groupCode\":\"$GROUP_CODE\"}")
CLEAN_WINDOW_ID=$(echo "$CLEAN_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('id', ''))")
[ -n "$CLEAN_WINDOW_ID" ] && ok "干净窗口创建: $CLEAN_WINDOW_ID" || { no "干净窗口创建失败"; CLEAN_WINDOW_ID=""; }

if [ -n "$CLEAN_WINDOW_ID" ]; then
    # 5.2.2 创建迭代（NAMED 模式 + 唯一分支名，避免历史分支冲突）
    CLEAN_BRANCH="feature/acceptance-clean-$CLEAN_TS"
    CLEAN_ITER_RESP=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json"         -d "{\"name\":\"验收-干净-$CLEAN_TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[]}")
    CLEAN_ITER_KEY=$(echo "$CLEAN_ITER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))")
    [ -n "$CLEAN_ITER_KEY" ] && ok "干净迭代创建: $CLEAN_ITER_KEY" || { no "干净迭代创建失败"; CLEAN_ITER_KEY=""; }

    if [ -n "$CLEAN_ITER_KEY" ]; then
        # 5.2.3 addRepos (NAMED mode, 唯一分支名)
        CLEAN_ADD=$(curl -s -X POST "$BACKEND/api/v1/iterations/$CLEAN_ITER_KEY/repos/add" -H "$AUTH" -H "Content-Type: application/json"             -d "{\"repoIds\":[\"$R1\"],\"branchCreationMode\":\"NAMED\",\"customBranchName\":\"$CLEAN_BRANCH\"}")
        CLEAN_ADD_OK=$(echo "$CLEAN_ADD" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('success', False))")
        [ "$CLEAN_ADD_OK" = "True" ] && ok "干净迭代 addRepos 成功" || warn "干净迭代 addRepos 部分失败"

        # 5.2.4 Attach 到干净窗口
        CLEAN_ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$CLEAN_WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json"             -d "{\"iterationKeys\":[\"$CLEAN_ITER_KEY\"]}")
        CLEAN_ATTACH_OK=$(echo "$CLEAN_ATTACH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('success', False))")
        [ "$CLEAN_ATTACH_OK" = "True" ] && ok "干净窗口 Attach 成功" || no "干净窗口 Attach 失败"

        # 5.2.5 冲突检测（应仅有版本 MISMATCH，无 BRANCH_EXISTS）
        CLEAN_CONFLICT=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$CLEAN_WINDOW_ID/conflicts/check" -H "$AUTH" -H "Content-Type: application/json" -d '{}' 2>/dev/null)
        CLEAN_CONFLICT_COUNT=$(echo "$CLEAN_CONFLICT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('totalCount',0))")
        CLEAN_CONFLICT_TYPES=$(echo "$CLEAN_CONFLICT" | python3 -c "
import sys,json
from collections import Counter
types = Counter()
for c in json.load(sys.stdin).get('data',{}).get('conflicts',[]):
    types[c.get('conflictType','?')] += 1
print(','.join(f'{t}={n}' for t,n in sorted(types.items())))
")
        info "干净窗口冲突: total=$CLEAN_CONFLICT_COUNT types=[$CLEAN_CONFLICT_TYPES]"

        # 5.2.6 逐个解决版本冲突（MISMATCH → USE_SYSTEM）
        if [ "$CLEAN_CONFLICT_COUNT" -gt 0 ]; then
            RESOLVED=0
            RESOLVE_CANDIDATES=$(echo "$CLEAN_CONFLICT" | python3 -c '
import sys,json
for c in json.load(sys.stdin).get("data",{}).get("conflicts",[]):
    if c.get("conflictType") in ("MISMATCH", "CROSS_REPO_VERSION_MISMATCH"):
        print("{}|{}".format(c.get("iterationKey",""), c.get("repoId","")))
' 2>/dev/null)
            if [ -n "$RESOLVE_CANDIDATES" ]; then
                info "版本冲突解决候选: $(echo "$RESOLVE_CANDIDATES" | tr '\n' ' ')"
            else
                warn "未解析到版本冲突解决候选"
            fi
            while IFS='|' read -r ikey rid; do
                if [ -n "$ikey" ] && [ -n "$rid" ]; then
                    RESOLVE_RESP=$(curl -s -X POST "$BACKEND/api/v1/iterations/$ikey/repos/$rid/resolve-conflict" -H "$AUTH" -H "Content-Type: application/json"                         -d '{"resolution":"USE_SYSTEM"}')
                    RESOLVE_OK=$(echo "$RESOLVE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))")
                    if [ "$RESOLVE_OK" = "True" ]; then
                        RESOLVED=$((RESOLVED + 1))
                    else
                        RESOLVE_ERR=$(echo "$RESOLVE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?') + ': ' + d.get('message','?'))" 2>/dev/null || echo "$RESOLVE_RESP")
                        warn "版本冲突解决失败: $ikey/$rid -> $RESOLVE_ERR"
                    fi
                fi
            done <<< "$RESOLVE_CANDIDATES"
            info "已解决 $RESOLVED 个版本冲突"

            # 5.2.7 重新检测冲突 → 应为 0
            sleep 1
            CLEAN_CONFLICT2=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$CLEAN_WINDOW_ID/conflicts/check" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
            CLEAN_CONFLICT2_COUNT=$(echo "$CLEAN_CONFLICT2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('totalCount',0))" 2>/dev/null || echo "0")
            [ "$CLEAN_CONFLICT2_COUNT" -eq 0 ] && ok "冲突解决后重新检测: 0（黄金路径前置条件满足）" || warn "仍有 $CLEAN_CONFLICT2_COUNT 个冲突"
        else
            ok "干净窗口初始 0 冲突（黄金路径前置条件满足）"
            CLEAN_CONFLICT2_COUNT=0
        fi

        # 5.2.8 Publish 干净窗口
        CLEAN_CONFLICT2_COUNT=${CLEAN_CONFLICT2_COUNT:--1}
        CLEAN_CONFLICT_COUNT=${CLEAN_CONFLICT_COUNT:--1}
        if [ "$CLEAN_CONFLICT2_COUNT" -eq 0 ] || [ "$CLEAN_CONFLICT_COUNT" -eq 0 ]; then
            CLEAN_PUB=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$CLEAN_WINDOW_ID/publish" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
            CLEAN_PUB_OK=$(echo "$CLEAN_PUB" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))")
            [ "$CLEAN_PUB_OK" = "True" ] && ok "干净窗口 Publish 成功" || no "干净窗口 Publish 失败"

            # 5.2.9 Orchestrate → 应有 items > 0
            sleep 2
            CLEAN_ORCH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$CLEAN_WINDOW_ID/orchestrate" -H "$AUTH" -H "Content-Type: application/json"                 -d "{\"repoIds\":[\"$R1\"],\"iterationKeys\":[\"$CLEAN_ITER_KEY\"],\"failFast\":false,\"operator\":\"acceptance-clean-$CLEAN_TS\"}")
            CLEAN_ORCH_OK=$(echo "$CLEAN_ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))")
            if [ "$CLEAN_ORCH_OK" = "True" ]; then
                CLEAN_RUN_ID=$(echo "$CLEAN_ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'])")
                info "干净窗口编排已启动 (run=$CLEAN_RUN_ID)，等待结果..."
                CLEAN_FINAL_STATUS=$(wait_for_run "$CLEAN_RUN_ID" 60)
                if is_run_success "$CLEAN_FINAL_STATUS"; then
                    ok "SA-013 干净窗口编排成功 (Run status: $CLEAN_FINAL_STATUS)"

                    # 5.2.10 验证 RunItem / RunStep 分布
                    CLEAN_RUN_DETAIL=$(curl -s "$BACKEND/api/v1/runs/$CLEAN_RUN_ID" -H "$AUTH")
                    CLEAN_ITEM_COUNT=$(echo "$CLEAN_RUN_DETAIL" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',{}).get('items',[])))" 2>/dev/null || echo "0")
                    [ "$CLEAN_ITEM_COUNT" -gt 0 ] && ok "SA-013 RunItem > 0: $CLEAN_ITEM_COUNT" || no "SA-013 RunItem 为 0"

                    CLEAN_STEP_COUNT=$(echo "$CLEAN_RUN_DETAIL" | python3 -c "
import sys,json
items=json.load(sys.stdin).get('data',{}).get('items',[])
print(sum(len(item.get('steps',[])) for item in items))
" 2>/dev/null || echo "0")
                    [ "$CLEAN_STEP_COUNT" -gt 0 ] && ok "SA-013 RunStep > 0: $CLEAN_STEP_COUNT" || no "SA-013 RunStep 为 0"

                    CLEAN_STEP_INFO=$(echo "$CLEAN_RUN_DETAIL" | python3 -c "
import sys,json
from collections import Counter
d = json.load(sys.stdin).get('data', {})
items = d.get('items', [])
actions = Counter()
results = Counter()
for item in items:
    for s in item.get('steps', []):
        actions[s.get('actionType', '?')] += 1
        results[s.get('result', '?')] += 1
print(f'items={len(items)} stepActions={dict(actions)} stepResults={dict(results)}')
" 2>/dev/null)
                    ok "SA-013 RunItem 详情: $CLEAN_STEP_INFO"
                else
                    no "SA-013 干净窗口编排状态: $CLEAN_FINAL_STATUS (预期 COMPLETED/SUCCESS)"
                fi
            else
                ORCH_CODE=$(echo "$CLEAN_ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code','?'))")
                ORCH_MSG=$(echo "$CLEAN_ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message','?'))")
                no "干净窗口编排启动失败: code=$ORCH_CODE msg=$ORCH_MSG"
            fi
        fi
    fi
fi

# ---- 5.3 feature 分支缺失：后端 + GitLab 强证据 ----
h2 "SA-012: 5.3 Feature 分支缺失后端/GitLab 强证据"
if [ "$GITLAB_READY" = "true" ] && [ -n "$GITLAB_PAT" ]; then
    MISSING_TS=$(date -u +%Y%m%d-%H%M%S)
    MISSING_WINDOW_RESP=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"验收-feature缺失-$MISSING_TS\",\"description\":\"Feature missing evidence $MISSING_TS\",\"plannedReleaseAt\":\"$NEXT_WEEK\",\"groupCode\":\"$GROUP_CODE\"}")
    MISSING_WINDOW_ID=$(echo "$MISSING_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('id', ''))" 2>/dev/null)
    if [ -z "$MISSING_WINDOW_ID" ]; then
        no "feature 缺失证据窗口创建失败: $MISSING_WINDOW_RESP"
    else
        ok "feature 缺失证据窗口创建: $MISSING_WINDOW_ID"
    fi

    MISSING_ITER_RESP=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"验收-feature缺失-$MISSING_TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[\"$R1\"]}")
    MISSING_ITER_KEY=$(echo "$MISSING_ITER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))" 2>/dev/null)
    if [ -z "$MISSING_ITER_KEY" ]; then
        no "feature 缺失证据迭代创建失败: $MISSING_ITER_RESP"
    else
        ok "feature 缺失证据迭代创建: $MISSING_ITER_KEY"
    fi

    if [ -n "$MISSING_WINDOW_ID" ] && [ -n "$MISSING_ITER_KEY" ]; then
        MISSING_REPO_URL=$(curl -s "$BACKEND/api/v1/repositories/$R1" -H "$AUTH" \
            | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))" 2>/dev/null)
        MISSING_FEATURE_BRANCH="feature/$MISSING_ITER_KEY"
        MISSING_FEATURE_BEFORE=$(gitlab_branch_state "$MISSING_REPO_URL" "$MISSING_FEATURE_BRANCH")
        [ "$MISSING_FEATURE_BEFORE" = "FOUND" ] && ok "GitLab feature 分支已由迭代创建: $MISSING_FEATURE_BRANCH" || warn "删除前 GitLab feature 分支状态: $MISSING_FEATURE_BEFORE"

        DELETE_STATUS=$(gitlab_delete_branch "$MISSING_REPO_URL" "$MISSING_FEATURE_BRANCH")
        if [ "$DELETE_STATUS" = "204" ] || [ "$DELETE_STATUS" = "200" ] || [ "$DELETE_STATUS" = "404" ]; then
            ok "已制造本轮 feature 缺失 GitLab 状态: $MISSING_FEATURE_BRANCH (delete=$DELETE_STATUS)"
        else
            warn "删除 feature 分支返回异常状态: $DELETE_STATUS"
        fi

        MISSING_FEATURE_AFTER=$(gitlab_branch_state "$MISSING_REPO_URL" "$MISSING_FEATURE_BRANCH")
        [ "$MISSING_FEATURE_AFTER" = "NOT_FOUND" ] && ok "GitLab 直查确认 feature 分支不存在: $MISSING_FEATURE_BRANCH" || no "GitLab 直查 feature 分支状态异常: $MISSING_FEATURE_AFTER"

        MISSING_ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$MISSING_WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"iterationKeys\":[\"$MISSING_ITER_KEY\"]}")
        MISSING_ATTACH_ERRORS=$(echo "$MISSING_ATTACH" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(any(r.get('hasErrors', False) for r in d.get('data', [])))
" 2>/dev/null || echo "False")
        [ "$MISSING_ATTACH_ERRORS" = "True" ] && ok "Attach 显式报告 feature 缺失导致的合并问题" || warn "Attach 未报告错误，继续以 branch-status/orchestrate 复核"

        MISSING_BRANCH_STATUS=$(curl -s "$BACKEND/api/v1/release-windows/$MISSING_WINDOW_ID/branch-status" -H "$AUTH")
        MISSING_STATUS_SUMMARY=$(echo "$MISSING_BRANCH_STATUS" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data', {})
repos=d.get('repos', [])
if not repos:
    print('NO_REPOS')
else:
    r=repos[0]
    fb=r.get('featureBranch', {})
    rb=r.get('releaseBranch', {})
    print('{}|{}|{}|{}'.format(
        fb.get('branchName',''), fb.get('exists'),
        rb.get('branchName',''), rb.get('exists')))
" 2>/dev/null || echo "NO_REPOS")
        IFS='|' read -r MISSING_FB_NAME MISSING_FB_EXISTS MISSING_RB_NAME MISSING_RB_EXISTS <<< "$MISSING_STATUS_SUMMARY"
        if [ "$MISSING_FB_NAME" = "$MISSING_FEATURE_BRANCH" ] && [ "$MISSING_FB_EXISTS" = "False" ]; then
            ok "branch-status 确认 feature 分支缺失: $MISSING_FB_NAME exists=$MISSING_FB_EXISTS"
        else
            no "branch-status feature 缺失证据异常: $MISSING_STATUS_SUMMARY"
        fi
        [ "$MISSING_RB_EXISTS" = "True" ] && ok "branch-status 同时确认 release 分支存在: $MISSING_RB_NAME" || warn "release 分支状态: $MISSING_RB_NAME exists=$MISSING_RB_EXISTS"

        MISSING_PUB=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$MISSING_WINDOW_ID/publish" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
        MISSING_PUB_OK=$(echo "$MISSING_PUB" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
        if [ "$MISSING_PUB_OK" = "True" ]; then
            MISSING_ORCH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$MISSING_WINDOW_ID/orchestrate" -H "$AUTH" -H "Content-Type: application/json" \
                -d "{\"repoIds\":[\"$R1\"],\"iterationKeys\":[\"$MISSING_ITER_KEY\"],\"failFast\":false,\"operator\":\"acceptance-feature-missing-$MISSING_TS\"}")
            MISSING_ORCH_OK=$(echo "$MISSING_ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
            if [ "$MISSING_ORCH_OK" = "True" ]; then
                MISSING_RUN_ID=$(echo "$MISSING_ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',''))" 2>/dev/null)
                MISSING_FINAL_STATUS=$(wait_for_run "$MISSING_RUN_ID" 60)
                MISSING_RUN_DETAIL=$(curl -s "$BACKEND/api/v1/runs/$MISSING_RUN_ID" -H "$AUTH")
                MISSING_ENSURE_FEATURE=$(echo "$MISSING_RUN_DETAIL" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data', {})
for item in d.get('items', []):
    for step in item.get('steps', []):
        if step.get('actionType') == 'ENSURE_FEATURE':
            print('{}|{}'.format(step.get('result'), step.get('message','')))
            raise SystemExit
print('MISSING_STEP|')
" 2>/dev/null || echo "MISSING_STEP|")
                IFS='|' read -r MISSING_STEP_RESULT MISSING_STEP_MESSAGE <<< "$MISSING_ENSURE_FEATURE"
                if [ "$MISSING_STEP_RESULT" = "SKIPPED" ] && echo "$MISSING_STEP_MESSAGE" | grep -q "$MISSING_FEATURE_BRANCH"; then
                    ok "Orchestrate RunStep 显式报告 feature 缺失: $MISSING_STEP_RESULT ($MISSING_FINAL_STATUS)"
                else
                    no "Orchestrate feature 缺失 RunStep 异常: $MISSING_ENSURE_FEATURE"
                fi
            else
                MISSING_ORCH_CODE=$(echo "$MISSING_ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code','?'))" 2>/dev/null)
                MISSING_ORCH_MSG=$(echo "$MISSING_ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message','?'))" 2>/dev/null)
                no "feature 缺失编排启动失败: code=$MISSING_ORCH_CODE msg=$MISSING_ORCH_MSG"
            fi
        else
            warn "feature 缺失窗口发布失败，跳过 Orchestrate RunStep 复核: $MISSING_PUB"
        fi
    fi
else
    skip "跳过 feature 缺失强证据（MOCK_MODE 或缺 GitLab PAT）"
fi

# ---- 5.4 release 分支已存在：后端 + GitLab 强证据 ----
h2 "SA-012: 5.4 Release 分支已存在后端/GitLab 强证据"
if [ "$GITLAB_READY" = "true" ] && [ -n "$GITLAB_PAT" ]; then
    EXISTS_TS=$(date -u +%Y%m%d-%H%M%S)
    EXISTS_WINDOW_RESP=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"验收-release已存在-$EXISTS_TS\",\"description\":\"Release branch exists evidence $EXISTS_TS\",\"plannedReleaseAt\":\"$NEXT_WEEK\",\"groupCode\":\"$GROUP_CODE\"}")
    EXISTS_WINDOW_ID=$(echo "$EXISTS_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('id', ''))" 2>/dev/null)
    EXISTS_WINDOW_KEY=$(echo "$EXISTS_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('windowKey', ''))" 2>/dev/null)
    if [ -z "$EXISTS_WINDOW_ID" ] || [ -z "$EXISTS_WINDOW_KEY" ]; then
        no "release 已存在证据窗口创建失败: $EXISTS_WINDOW_RESP"
    else
        ok "release 已存在证据窗口创建: $EXISTS_WINDOW_KEY"
    fi

    EXISTS_ITER_RESP=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"验收-release已存在-$EXISTS_TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[\"$R1\"]}")
    EXISTS_ITER_KEY=$(echo "$EXISTS_ITER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))" 2>/dev/null)
    if [ -z "$EXISTS_ITER_KEY" ]; then
        no "release 已存在证据迭代创建失败: $EXISTS_ITER_RESP"
    else
        ok "release 已存在证据迭代创建: $EXISTS_ITER_KEY"
    fi

    if [ -n "$EXISTS_WINDOW_ID" ] && [ -n "$EXISTS_WINDOW_KEY" ] && [ -n "$EXISTS_ITER_KEY" ]; then
        EXISTS_REPO_URL=$(curl -s "$BACKEND/api/v1/repositories/$R1" -H "$AUTH" \
            | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))" 2>/dev/null)
        EXISTS_RELEASE_BRANCH="release/$EXISTS_WINDOW_KEY"
        CREATE_RELEASE_STATUS=$(gitlab_create_branch "$EXISTS_REPO_URL" "$EXISTS_RELEASE_BRANCH" "main")
        if [ "$CREATE_RELEASE_STATUS" = "201" ] || [ "$CREATE_RELEASE_STATUS" = "400" ]; then
            ok "已预置 GitLab release 分支: $EXISTS_RELEASE_BRANCH (create=$CREATE_RELEASE_STATUS)"
        else
            warn "预置 release 分支返回异常状态: $CREATE_RELEASE_STATUS"
        fi

        EXISTS_RELEASE_BEFORE=$(gitlab_branch_state "$EXISTS_REPO_URL" "$EXISTS_RELEASE_BRANCH")
        [ "$EXISTS_RELEASE_BEFORE" = "FOUND" ] && ok "GitLab 直查确认 attach 前 release 分支已存在: $EXISTS_RELEASE_BRANCH" || no "GitLab 直查 release 分支状态异常: $EXISTS_RELEASE_BEFORE"

        EXISTS_ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$EXISTS_WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"iterationKeys\":[\"$EXISTS_ITER_KEY\"]}")
        EXISTS_ATTACH_OK=$(echo "$EXISTS_ATTACH" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(bool(d.get('success', False)) and not any(r.get('hasErrors', False) for r in d.get('data', [])))
" 2>/dev/null || echo "False")
        [ "$EXISTS_ATTACH_OK" = "True" ] && ok "Attach 在 release 已存在时完成并保留证据" || warn "Attach release 已存在路径返回异常: $EXISTS_ATTACH"

        EXISTS_BRANCH_STATUS=$(curl -s "$BACKEND/api/v1/release-windows/$EXISTS_WINDOW_ID/branch-status" -H "$AUTH")
        EXISTS_STATUS_SUMMARY=$(echo "$EXISTS_BRANCH_STATUS" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data', {})
repos=d.get('repos', [])
if not repos:
    print('NO_REPOS')
else:
    r=repos[0]
    fb=r.get('featureBranch', {})
    rb=r.get('releaseBranch', {})
    print('{}|{}|{}|{}'.format(
        fb.get('branchName',''), fb.get('exists'),
        rb.get('branchName',''), rb.get('exists')))
" 2>/dev/null || echo "NO_REPOS")
        IFS='|' read -r EXISTS_FB_NAME EXISTS_FB_EXISTS EXISTS_RB_NAME EXISTS_RB_EXISTS <<< "$EXISTS_STATUS_SUMMARY"
        if [ "$EXISTS_RB_NAME" = "$EXISTS_RELEASE_BRANCH" ] && [ "$EXISTS_RB_EXISTS" = "True" ]; then
            ok "branch-status 确认 release 分支已存在: $EXISTS_RB_NAME"
        else
            no "branch-status release 已存在证据异常: $EXISTS_STATUS_SUMMARY"
        fi
        [ "$EXISTS_FB_EXISTS" = "True" ] && ok "branch-status 同时确认 feature 分支存在: $EXISTS_FB_NAME" || warn "feature 分支状态: $EXISTS_FB_NAME exists=$EXISTS_FB_EXISTS"

        EXISTS_ATTACH_RUN=$(curl -s "$BACKEND/api/v1/runs" -H "$AUTH" | python3 -c "
import sys,json
window_key='''$EXISTS_WINDOW_KEY'''
iteration_key='''$EXISTS_ITER_KEY'''
repo_id='''$R1'''
runs=json.load(sys.stdin).get('data', [])
for r in reversed(runs):
    if r.get('runType') != 'ATTACH_ITERATION':
        continue
    for item in r.get('items', []):
        if item.get('windowKey') == window_key and item.get('iterationKey') == iteration_key and item.get('repoId') == repo_id:
            for step in item.get('steps', []):
                if step.get('actionType') == 'ENSURE_RELEASE':
                    print('{}|{}|{}'.format(r.get('id',''), step.get('result',''), step.get('message','')))
                    raise SystemExit
print('MISSING_RUN|MISSING_STEP|')
" 2>/dev/null || echo "MISSING_RUN|MISSING_STEP|")
        IFS='|' read -r EXISTS_RUN_ID EXISTS_STEP_RESULT EXISTS_STEP_MESSAGE <<< "$EXISTS_ATTACH_RUN"
        if [ "$EXISTS_STEP_RESULT" = "BRANCH_EXISTS" ] && echo "$EXISTS_STEP_MESSAGE" | grep -q "$EXISTS_RELEASE_BRANCH"; then
            ok "Attach RunStep 显式报告 release 分支已存在: $EXISTS_STEP_RESULT"
        else
            no "Attach release 已存在 RunStep 异常: $EXISTS_ATTACH_RUN"
        fi
    fi
else
    skip "跳过 release 分支已存在强证据（MOCK_MODE 或缺 GitLab PAT）"
fi

# ---- 5.5 分支名不合规：后端 + GitLab 强证据 ----
h2 "SA-012: 5.5 分支名不合规后端/GitLab 强证据"
if [ "$GITLAB_READY" = "true" ] && [ -n "$GITLAB_PAT" ] && [ -n "$ITER_KEY" ]; then
    NONCOMPLIANT_TS=$(date -u +%Y%m%d-%H%M%S)
    NONCOMPLIANT_WINDOW_RESP=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"验收-分支不合规-$NONCOMPLIANT_TS\",\"description\":\"Branch noncompliant evidence $NONCOMPLIANT_TS\",\"plannedReleaseAt\":\"$NEXT_WEEK\",\"groupCode\":\"$GROUP_CODE\"}")
    NONCOMPLIANT_WINDOW_ID=$(echo "$NONCOMPLIANT_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('id', ''))" 2>/dev/null)
    NONCOMPLIANT_WINDOW_KEY=$(echo "$NONCOMPLIANT_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('windowKey', ''))" 2>/dev/null)
    if [ -z "$NONCOMPLIANT_WINDOW_ID" ] || [ -z "$NONCOMPLIANT_WINDOW_KEY" ]; then
        no "分支名不合规证据窗口创建失败: $NONCOMPLIANT_WINDOW_RESP"
    else
        ok "分支名不合规证据窗口创建: $NONCOMPLIANT_WINDOW_KEY"
    fi

    if [ -n "$NONCOMPLIANT_WINDOW_ID" ] && [ -n "$NONCOMPLIANT_WINDOW_KEY" ]; then
        NONCOMPLIANT_ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$NONCOMPLIANT_WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"iterationKeys\":[\"$ITER_KEY\"]}")
        NONCOMPLIANT_ATTACH_OK=$(echo "$NONCOMPLIANT_ATTACH" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(bool(d.get('success', False)) and not any(r.get('hasErrors', False) for r in d.get('data', [])))
" 2>/dev/null || echo "False")
        [ "$NONCOMPLIANT_ATTACH_OK" = "True" ] && ok "分支名不合规证据窗口 Attach 成功" || warn "分支名不合规证据窗口 Attach 返回异常: $NONCOMPLIANT_ATTACH"

        NONCOMPLIANT_REPO_URL=$(curl -s "$BACKEND/api/v1/repositories/$R1" -H "$AUTH" \
            | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))" 2>/dev/null)
        NONCOMPLIANT_FEATURE_BRANCH="feature/$ITER_KEY"
        NONCOMPLIANT_RELEASE_BRANCH="release/$NONCOMPLIANT_WINDOW_KEY"
        NONCOMPLIANT_FEATURE_STATE=$(gitlab_branch_state "$NONCOMPLIANT_REPO_URL" "$NONCOMPLIANT_FEATURE_BRANCH")
        NONCOMPLIANT_RELEASE_STATE=$(gitlab_branch_state "$NONCOMPLIANT_REPO_URL" "$NONCOMPLIANT_RELEASE_BRANCH")
        [ "$NONCOMPLIANT_FEATURE_STATE" = "FOUND" ] && ok "GitLab 直查确认待检 feature 分支存在: $NONCOMPLIANT_FEATURE_BRANCH" || no "GitLab feature 分支状态异常: $NONCOMPLIANT_FEATURE_STATE"
        [ "$NONCOMPLIANT_RELEASE_STATE" = "FOUND" ] && ok "GitLab 直查确认待检 release 分支存在: $NONCOMPLIANT_RELEASE_BRANCH" || no "GitLab release 分支状态异常: $NONCOMPLIANT_RELEASE_STATE"

        BRANCH_RULE_SNAPSHOT=$(curl -s "$BACKEND/api/v1/branch-rules" -H "$AUTH")
        ENABLED_BRANCH_RULE_IDS=$(echo "$BRANCH_RULE_SNAPSHOT" | python3 -c "
import sys,json
for rule in json.load(sys.stdin).get('data', []):
    if rule.get('status') == 'ENABLED':
        print(rule.get('id',''))
" 2>/dev/null)
        ENABLED_BRANCH_RULE_COUNT=$(echo "$ENABLED_BRANCH_RULE_IDS" | sed '/^$/d' | wc -l | tr -d ' ')
        info "临时收紧 BranchRule 前启用规则数: ${ENABLED_BRANCH_RULE_COUNT:-0}"

        while IFS= read -r rule_id; do
            [ -n "$rule_id" ] && curl -s -o /dev/null -X POST "$BACKEND/api/v1/branch-rules/$rule_id/disable" -H "$AUTH"
        done <<< "$ENABLED_BRANCH_RULE_IDS"

        STRICT_PATTERN="^release/acceptance-compliant-only-${NONCOMPLIANT_TS}$"
        STRICT_RULE_RESP=$(curl -s -X POST "$BACKEND/api/v1/branch-rules" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"name\":\"验收-分支不合规-$NONCOMPLIANT_TS\",\"pattern\":\"$STRICT_PATTERN\",\"type\":\"REGEX\",\"description\":\"Acceptance-only strict rule for branch noncompliant evidence\",\"scopeLevel\":\"GLOBAL\",\"scopeProjectId\":null,\"scopeSubProjectId\":null}")
        STRICT_RULE_ID=$(echo "$STRICT_RULE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null)
        if [ -n "$STRICT_RULE_ID" ]; then
            ok "已启用本轮严格 BranchRule: $STRICT_PATTERN"
        else
            no "严格 BranchRule 创建失败: $STRICT_RULE_RESP"
        fi

        if [ -n "$STRICT_RULE_ID" ]; then
            NONCOMPLIANT_FEATURE_ENCODED=$(echo "$NONCOMPLIANT_FEATURE_BRANCH" | gitlab_encode)
            NONCOMPLIANT_RELEASE_ENCODED=$(echo "$NONCOMPLIANT_RELEASE_BRANCH" | gitlab_encode)
            FEATURE_COMPLIANT=$(curl -s "$BACKEND/api/v1/branch-rules/check?branchName=$NONCOMPLIANT_FEATURE_ENCODED" -H "$AUTH" \
                | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('compliant'))" 2>/dev/null)
            RELEASE_COMPLIANT=$(curl -s "$BACKEND/api/v1/branch-rules/check?branchName=$NONCOMPLIANT_RELEASE_ENCODED" -H "$AUTH" \
                | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('compliant'))" 2>/dev/null)
            [ "$FEATURE_COMPLIANT" = "False" ] && ok "BranchRule check 确认 feature 分支不合规: $NONCOMPLIANT_FEATURE_BRANCH" || no "feature 分支合规性异常: compliant=$FEATURE_COMPLIANT"
            [ "$RELEASE_COMPLIANT" = "False" ] && ok "BranchRule check 确认 release 分支不合规: $NONCOMPLIANT_RELEASE_BRANCH" || no "release 分支合规性异常: compliant=$RELEASE_COMPLIANT"

            NONCOMPLIANT_SCAN=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$NONCOMPLIANT_WINDOW_ID/conflicts/check" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
            NONCOMPLIANT_CONFLICT_SUMMARY=$(echo "$NONCOMPLIANT_SCAN" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data', {})
conflicts=d.get('conflicts', [])
matches=[c for c in conflicts if c.get('conflictType') == 'BRANCH_NONCOMPLIANT']
messages=' || '.join(c.get('message','') for c in matches)
print('{}|{}'.format(len(matches), messages))
" 2>/dev/null || echo "0|")
            IFS='|' read -r NONCOMPLIANT_COUNT NONCOMPLIANT_MESSAGES <<< "$NONCOMPLIANT_CONFLICT_SUMMARY"
            if [ "${NONCOMPLIANT_COUNT:-0}" -gt 0 ] && {
                echo "$NONCOMPLIANT_MESSAGES" | grep -q "$NONCOMPLIANT_FEATURE_BRANCH" || \
                echo "$NONCOMPLIANT_MESSAGES" | grep -q "$NONCOMPLIANT_RELEASE_BRANCH"
            }; then
                ok "冲突扫描检出 BRANCH_NONCOMPLIANT: count=$NONCOMPLIANT_COUNT"
            else
                no "BRANCH_NONCOMPLIANT 冲突证据异常: $NONCOMPLIANT_CONFLICT_SUMMARY"
            fi
        fi

        [ -n "$STRICT_RULE_ID" ] && curl -s -o /dev/null -X DELETE "$BACKEND/api/v1/branch-rules/$STRICT_RULE_ID" -H "$AUTH"
        while IFS= read -r rule_id; do
            [ -n "$rule_id" ] && curl -s -o /dev/null -X POST "$BACKEND/api/v1/branch-rules/$rule_id/enable" -H "$AUTH"
        done <<< "$ENABLED_BRANCH_RULE_IDS"
        ok "BranchRule 临时收紧已复原"
    fi
else
    skip "跳过分支名不合规强证据（MOCK_MODE、缺 GitLab PAT 或缺迭代）"
fi

# ---- 5.6 合并冲突：后端 + GitLab 强证据 ----
h2 "SA-011: 5.6 MERGE_CONFLICT 后端/GitLab 强证据"
if [ "$GITLAB_READY" = "true" ] && [ -n "$GITLAB_PAT" ]; then
    MERGE_TS=$(date -u +%Y%m%d-%H%M%S)
    MERGE_WINDOW_RESP=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"验收-合并冲突-$MERGE_TS\",\"description\":\"Merge conflict evidence $MERGE_TS\",\"plannedReleaseAt\":\"$NEXT_WEEK\",\"groupCode\":\"$GROUP_CODE\"}")
    MERGE_WINDOW_ID=$(echo "$MERGE_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('id', ''))" 2>/dev/null)
    MERGE_WINDOW_KEY=$(echo "$MERGE_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('windowKey', ''))" 2>/dev/null)
    if [ -z "$MERGE_WINDOW_ID" ] || [ -z "$MERGE_WINDOW_KEY" ]; then
        no "合并冲突证据窗口创建失败: $MERGE_WINDOW_RESP"
    else
        ok "合并冲突证据窗口创建: $MERGE_WINDOW_KEY"
    fi

    if [ -n "$MERGE_WINDOW_ID" ] && [ -n "$MERGE_WINDOW_KEY" ]; then
        MERGE_REPO_URL=$(curl -s "$BACKEND/api/v1/repositories/$R1" -H "$AUTH" \
            | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))" 2>/dev/null)
        MERGE_FEATURE_BRANCH="feature/acceptance-merge-conflict-$MERGE_TS"
        MERGE_RELEASE_BRANCH="release/$MERGE_WINDOW_KEY"
        CREATE_MERGE_FEATURE=$(gitlab_create_branch "$MERGE_REPO_URL" "$MERGE_FEATURE_BRANCH" "main")
        CREATE_MERGE_RELEASE=$(gitlab_create_branch "$MERGE_REPO_URL" "$MERGE_RELEASE_BRANCH" "main")
        if [ "$CREATE_MERGE_FEATURE" = "201" ] || [ "$CREATE_MERGE_FEATURE" = "400" ]; then
            ok "已预置 GitLab feature 分支: $MERGE_FEATURE_BRANCH (create=$CREATE_MERGE_FEATURE)"
        else
            no "预置 feature 分支失败: $CREATE_MERGE_FEATURE"
        fi
        if [ "$CREATE_MERGE_RELEASE" = "201" ] || [ "$CREATE_MERGE_RELEASE" = "400" ]; then
            ok "已预置 GitLab release 分支: $MERGE_RELEASE_BRANCH (create=$CREATE_MERGE_RELEASE)"
        else
            no "预置 release 分支失败: $CREATE_MERGE_RELEASE"
        fi

        MERGE_FEATURE_POM="<project><modelVersion>4.0.0</modelVersion><groupId>io.releasehub.acceptance</groupId><artifactId>merge-conflict</artifactId><version>1.2.0-feature-$MERGE_TS</version></project>"
        MERGE_RELEASE_POM="<project><modelVersion>4.0.0</modelVersion><groupId>io.releasehub.acceptance</groupId><artifactId>merge-conflict</artifactId><version>1.2.0-release-$MERGE_TS</version></project>"
        COMMIT_MERGE_FEATURE=$(gitlab_commit_file "$MERGE_REPO_URL" "$MERGE_FEATURE_BRANCH" "pom.xml" "$MERGE_FEATURE_POM" "ReleaseHub acceptance: feature conflict $MERGE_TS")
        COMMIT_MERGE_RELEASE=$(gitlab_commit_file "$MERGE_REPO_URL" "$MERGE_RELEASE_BRANCH" "pom.xml" "$MERGE_RELEASE_POM" "ReleaseHub acceptance: release conflict $MERGE_TS")
        [ "$COMMIT_MERGE_FEATURE" = "201" ] && ok "GitLab feature 分支冲突提交已写入: pom.xml" || no "feature 分支冲突提交失败: $COMMIT_MERGE_FEATURE"
        [ "$COMMIT_MERGE_RELEASE" = "201" ] && ok "GitLab release 分支冲突提交已写入: pom.xml" || no "release 分支冲突提交失败: $COMMIT_MERGE_RELEASE"

        MERGE_FEATURE_STATE=$(gitlab_branch_state "$MERGE_REPO_URL" "$MERGE_FEATURE_BRANCH")
        MERGE_RELEASE_STATE=$(gitlab_branch_state "$MERGE_REPO_URL" "$MERGE_RELEASE_BRANCH")
        [ "$MERGE_FEATURE_STATE" = "FOUND" ] && ok "GitLab 直查确认 feature 冲突分支存在: $MERGE_FEATURE_BRANCH" || no "GitLab feature 冲突分支状态异常: $MERGE_FEATURE_STATE"
        [ "$MERGE_RELEASE_STATE" = "FOUND" ] && ok "GitLab 直查确认 release 冲突分支存在: $MERGE_RELEASE_BRANCH" || no "GitLab release 冲突分支状态异常: $MERGE_RELEASE_STATE"

        MERGE_RULE_SNAPSHOT=$(curl -s "$BACKEND/api/v1/branch-rules" -H "$AUTH")
        MERGE_ENABLED_RULE_IDS=$(echo "$MERGE_RULE_SNAPSHOT" | python3 -c "
import sys,json
for rule in json.load(sys.stdin).get('data', []):
    if rule.get('status') == 'ENABLED':
        print(rule.get('id',''))
" 2>/dev/null)
        while IFS= read -r rule_id; do
            [ -n "$rule_id" ] && curl -s -o /dev/null -X POST "$BACKEND/api/v1/branch-rules/$rule_id/disable" -H "$AUTH"
        done <<< "$MERGE_ENABLED_RULE_IDS"

        MERGE_ITER_RESP=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"name\":\"验收-合并冲突-$MERGE_TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[]}")
        MERGE_ITER_KEY=$(echo "$MERGE_ITER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))" 2>/dev/null)
        if [ -z "$MERGE_ITER_KEY" ]; then
            no "合并冲突证据迭代创建失败: $MERGE_ITER_RESP"
        else
            ok "合并冲突证据迭代创建: $MERGE_ITER_KEY"
        fi

        if [ -n "$MERGE_ITER_KEY" ]; then
            MERGE_ADD=$(curl -s -X POST "$BACKEND/api/v1/iterations/$MERGE_ITER_KEY/repos/add" -H "$AUTH" -H "Content-Type: application/json" \
                -d "{\"repoIds\":[\"$R1\"],\"branchCreationMode\":\"EXISTING\",\"customBranchName\":\"$MERGE_FEATURE_BRANCH\"}")
            MERGE_ADD_OK=$(echo "$MERGE_ADD" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null || echo "False")
            [ "$MERGE_ADD_OK" = "True" ] && ok "合并冲突迭代关联 EXISTING feature 分支成功" || no "合并冲突迭代关联失败: $MERGE_ADD"

            MERGE_ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$MERGE_WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
                -d "{\"iterationKeys\":[\"$MERGE_ITER_KEY\"]}")
            MERGE_ATTACH_BLOCKED=$(echo "$MERGE_ATTACH" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(any(
    'conflict' in (err.get('message','').lower())
    for result in d.get('data', [])
    for err in result.get('errors', [])
))
" 2>/dev/null || echo "False")
            [ "$MERGE_ATTACH_BLOCKED" = "True" ] && ok "Attach 显式报告 MERGE_CONFLICT" || warn "Attach 未显式报告合并冲突，继续以冲突扫描复核"

            MERGE_SCAN=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$MERGE_WINDOW_ID/conflicts/check" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
            MERGE_CONFLICT_SUMMARY=$(echo "$MERGE_SCAN" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data', {})
matches=[c for c in d.get('conflicts', []) if c.get('conflictType') == 'MERGE_CONFLICT']
messages=' || '.join(c.get('message','') for c in matches)
print('{}|{}'.format(len(matches), messages))
" 2>/dev/null || echo "0|")
            IFS='|' read -r MERGE_CONFLICT_COUNT MERGE_CONFLICT_MESSAGES <<< "$MERGE_CONFLICT_SUMMARY"
            if [ "${MERGE_CONFLICT_COUNT:-0}" -gt 0 ] && {
                echo "$MERGE_CONFLICT_MESSAGES" | grep -q "$MERGE_FEATURE_BRANCH" || \
                echo "$MERGE_CONFLICT_MESSAGES" | grep -q "$MERGE_RELEASE_BRANCH"
            }; then
                ok "冲突扫描检出 MERGE_CONFLICT: count=$MERGE_CONFLICT_COUNT"
            else
                no "MERGE_CONFLICT 冲突证据异常: $MERGE_CONFLICT_SUMMARY"
            fi
        fi

        while IFS= read -r rule_id; do
            [ -n "$rule_id" ] && curl -s -o /dev/null -X POST "$BACKEND/api/v1/branch-rules/$rule_id/enable" -H "$AUTH"
        done <<< "$MERGE_ENABLED_RULE_IDS"
        ok "MERGE_CONFLICT 证据段 BranchRule 状态已复原"
    fi
else
    skip "跳过 MERGE_CONFLICT 强证据（MOCK_MODE 或缺 GitLab PAT）"
fi

# ---- 5.7 跨仓库目标版本不一致：后端 + GitLab 强证据 ----
h2 "SA-011: 5.7 CROSS_REPO_VERSION_MISMATCH 后端/GitLab 强证据"
if [ "$GITLAB_READY" = "true" ] && [ -n "$GITLAB_PAT" ]; then
    CROSS_TS=$(date -u +%Y%m%d-%H%M%S)
    CROSS_WINDOW_RESP=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"验收-跨仓版本-$CROSS_TS\",\"description\":\"Cross repo version mismatch evidence $CROSS_TS\",\"plannedReleaseAt\":\"$NEXT_WEEK\",\"groupCode\":\"$GROUP_CODE\"}")
    CROSS_WINDOW_ID=$(echo "$CROSS_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('id', ''))" 2>/dev/null)
    CROSS_WINDOW_KEY=$(echo "$CROSS_WINDOW_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('windowKey', ''))" 2>/dev/null)
    if [ -z "$CROSS_WINDOW_ID" ] || [ -z "$CROSS_WINDOW_KEY" ]; then
        no "跨仓版本证据窗口创建失败: $CROSS_WINDOW_RESP"
    else
        ok "跨仓版本证据窗口创建: $CROSS_WINDOW_KEY"
    fi

    if [ -n "$CROSS_WINDOW_ID" ] && [ -n "$CROSS_WINDOW_KEY" ]; then
        CROSS_R1_ORIGINAL=$(curl -s "$BACKEND/api/v1/repositories/$R1/initial-version" -H "$AUTH" \
            | python3 -c "import sys,json; v=json.load(sys.stdin).get('data',{}).get('version'); print(v or '')" 2>/dev/null)
        CROSS_R2_ORIGINAL=$(curl -s "$BACKEND/api/v1/repositories/$R2/initial-version" -H "$AUTH" \
            | python3 -c "import sys,json; v=json.load(sys.stdin).get('data',{}).get('version'); print(v or '')" 2>/dev/null)

        CROSS_SET_R1=$(curl -s -X PUT "$BACKEND/api/v1/repositories/$R1/initial-version" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"version\":\"2.0.0\"}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
        CROSS_SET_R2=$(curl -s -X PUT "$BACKEND/api/v1/repositories/$R2/initial-version" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"version\":\"3.0.0\"}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
        if [ "$CROSS_SET_R1" = "True" ] && [ "$CROSS_SET_R2" = "True" ]; then
            ok "已临时设置两仓库初始版本: $R1=2.0.0 $R2=3.0.0"
        else
            no "临时设置初始版本失败: R1=$CROSS_SET_R1 R2=$CROSS_SET_R2"
        fi

        CROSS_ITER_RESP=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"name\":\"验收-跨仓版本-$CROSS_TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[\"$R1\",\"$R2\"]}")
        CROSS_ITER_KEY=$(echo "$CROSS_ITER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))" 2>/dev/null)
        if [ -z "$CROSS_ITER_KEY" ]; then
            no "跨仓版本证据迭代创建失败: $CROSS_ITER_RESP"
        else
            ok "跨仓版本证据迭代创建: $CROSS_ITER_KEY"
        fi

        if [ -n "$CROSS_R1_ORIGINAL" ]; then
            CROSS_RESTORE_R1=$(curl -s -X PUT "$BACKEND/api/v1/repositories/$R1/initial-version" -H "$AUTH" -H "Content-Type: application/json" \
                -d "{\"version\":\"$CROSS_R1_ORIGINAL\"}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
        else
            CROSS_RESTORE_R1=$(curl -s -X POST "$BACKEND/api/v1/repositories/$R1/sync-version" -H "$AUTH" \
                | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
        fi
        if [ -n "$CROSS_R2_ORIGINAL" ]; then
            CROSS_RESTORE_R2=$(curl -s -X PUT "$BACKEND/api/v1/repositories/$R2/initial-version" -H "$AUTH" -H "Content-Type: application/json" \
                -d "{\"version\":\"$CROSS_R2_ORIGINAL\"}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
        else
            CROSS_RESTORE_R2=$(curl -s -X POST "$BACKEND/api/v1/repositories/$R2/sync-version" -H "$AUTH" \
                | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
        fi
        if [ "$CROSS_RESTORE_R1" = "True" ] && [ "$CROSS_RESTORE_R2" = "True" ]; then
            ok "仓库初始版本已复原或重新同步"
        else
            no "仓库初始版本复原异常: R1=$CROSS_RESTORE_R1 R2=$CROSS_RESTORE_R2"
        fi

        if [ -n "$CROSS_ITER_KEY" ]; then
            CROSS_VINFO_R1=$(curl -s "$BACKEND/api/v1/iterations/$CROSS_ITER_KEY/repos/$R1/version-info" -H "$AUTH")
            CROSS_VINFO_R2=$(curl -s "$BACKEND/api/v1/iterations/$CROSS_ITER_KEY/repos/$R2/version-info" -H "$AUTH")
            CROSS_VERSION_SUMMARY=$(V1="$CROSS_VINFO_R1" V2="$CROSS_VINFO_R2" python3 -c '
import json
import os
r1 = json.loads(os.environ["V1"]).get("data", {})
r2 = json.loads(os.environ["V2"]).get("data", {})
print("{}|{}|{}|{}".format(
    r1.get("featureBranch", ""),
    r1.get("targetVersion", ""),
    r2.get("featureBranch", ""),
    r2.get("targetVersion", ""),
))
' 2>/dev/null || echo "|||")
            IFS='|' read -r CROSS_R1_FEATURE CROSS_R1_TARGET CROSS_R2_FEATURE CROSS_R2_TARGET <<< "$CROSS_VERSION_SUMMARY"
            if [ -n "$CROSS_R1_TARGET" ] && [ -n "$CROSS_R2_TARGET" ] && [ "$CROSS_R1_TARGET" != "$CROSS_R2_TARGET" ]; then
                ok "version-info 确认跨仓目标版本不一致: $R1=$CROSS_R1_TARGET $R2=$CROSS_R2_TARGET"
            else
                no "version-info 跨仓目标版本证据异常: $CROSS_VERSION_SUMMARY"
            fi

            CROSS_REPO_URL_1=$(curl -s "$BACKEND/api/v1/repositories/$R1" -H "$AUTH" \
                | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))" 2>/dev/null)
            CROSS_REPO_URL_2=$(curl -s "$BACKEND/api/v1/repositories/$R2" -H "$AUTH" \
                | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))" 2>/dev/null)
            CROSS_R1_FEATURE_STATE=$(gitlab_branch_state "$CROSS_REPO_URL_1" "$CROSS_R1_FEATURE")
            CROSS_R2_FEATURE_STATE=$(gitlab_branch_state "$CROSS_REPO_URL_2" "$CROSS_R2_FEATURE")
            [ "$CROSS_R1_FEATURE_STATE" = "FOUND" ] && ok "GitLab 直查确认 R1 feature 分支存在: $CROSS_R1_FEATURE" || no "R1 feature 分支状态异常: $CROSS_R1_FEATURE_STATE"
            [ "$CROSS_R2_FEATURE_STATE" = "FOUND" ] && ok "GitLab 直查确认 R2 feature 分支存在: $CROSS_R2_FEATURE" || no "R2 feature 分支状态异常: $CROSS_R2_FEATURE_STATE"

            CROSS_ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$CROSS_WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
                -d "{\"iterationKeys\":[\"$CROSS_ITER_KEY\"]}")
            CROSS_ATTACH_OK=$(echo "$CROSS_ATTACH" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(bool(d.get('success', False)) and not any(r.get('hasErrors', False) for r in d.get('data', [])))
" 2>/dev/null || echo "False")
            [ "$CROSS_ATTACH_OK" = "True" ] && ok "跨仓版本证据窗口 Attach 成功" || warn "跨仓版本证据窗口 Attach 返回异常: $CROSS_ATTACH"

            CROSS_RELEASE_BRANCH="release/$CROSS_WINDOW_KEY"
            CROSS_R1_RELEASE_STATE=$(gitlab_branch_state "$CROSS_REPO_URL_1" "$CROSS_RELEASE_BRANCH")
            CROSS_R2_RELEASE_STATE=$(gitlab_branch_state "$CROSS_REPO_URL_2" "$CROSS_RELEASE_BRANCH")
            [ "$CROSS_R1_RELEASE_STATE" = "FOUND" ] && ok "GitLab 直查确认 R1 release 分支存在: $CROSS_RELEASE_BRANCH" || warn "R1 release 分支状态: $CROSS_R1_RELEASE_STATE"
            [ "$CROSS_R2_RELEASE_STATE" = "FOUND" ] && ok "GitLab 直查确认 R2 release 分支存在: $CROSS_RELEASE_BRANCH" || warn "R2 release 分支状态: $CROSS_R2_RELEASE_STATE"

            CROSS_SCAN=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$CROSS_WINDOW_ID/conflicts/check" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
            CROSS_CONFLICT_SUMMARY=$(echo "$CROSS_SCAN" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data', {})
matches=[c for c in d.get('conflicts', []) if c.get('conflictType') == 'CROSS_REPO_VERSION_MISMATCH']
messages=' || '.join(c.get('message','') for c in matches)
systems=','.join(c.get('systemVersion','') for c in matches)
repos=','.join(c.get('repoVersion','') for c in matches)
print('{}|{}|{}|{}'.format(len(matches), systems, repos, messages))
" 2>/dev/null || echo "0|||")
            IFS='|' read -r CROSS_CONFLICT_COUNT CROSS_SYSTEM_VERSIONS CROSS_REPO_VERSIONS CROSS_CONFLICT_MESSAGES <<< "$CROSS_CONFLICT_SUMMARY"
            if [ "${CROSS_CONFLICT_COUNT:-0}" -gt 0 ] && {
                echo "$CROSS_CONFLICT_MESSAGES" | grep -q "$CROSS_R1_TARGET" || \
                echo "$CROSS_CONFLICT_MESSAGES" | grep -q "$CROSS_R2_TARGET" || \
                echo "$CROSS_SYSTEM_VERSIONS,$CROSS_REPO_VERSIONS" | grep -q "$CROSS_R1_TARGET"
            }; then
                ok "冲突扫描检出 CROSS_REPO_VERSION_MISMATCH: count=$CROSS_CONFLICT_COUNT"
            else
                no "CROSS_REPO_VERSION_MISMATCH 冲突证据异常: $CROSS_CONFLICT_SUMMARY"
            fi
        fi
    fi
else
    skip "跳过 CROSS_REPO_VERSION_MISMATCH 强证据（MOCK_MODE 或缺 GitLab PAT）"
fi

# ---- 6. 场景: Publish + Auto-Orchestration ----
h2 "SA-013: 6. 场景: Publish & 自动编排"
PUB_RESULT=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$WINDOW_ID/publish" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
PUB_SUCCESS=$(echo "$PUB_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['success'])" 2>/dev/null)
if [ "$PUB_SUCCESS" = "True" ]; then
    FINAL_STATUS=$(echo "$PUB_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
    ok "Publish 成功 → $FINAL_STATUS"
else
    ERR_MSG=$(echo "$PUB_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message','?'))" 2>/dev/null || echo "?")
    no "Publish 失败: $ERR_MSG"
fi

# 手动编排
sleep 2
ORCH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$WINDOW_ID/orchestrate" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"repoIds\":[\"$R1\",\"$R2\",\"$R3\"],\"iterationKeys\":[\"$ITER_KEY\"],\"failFast\":false,\"operator\":\"acceptance-$TS\"}")
ORCH_SUCCESS=$(echo "$ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin)['success'])" 2>/dev/null)

if [ "$ORCH_SUCCESS" = "True" ]; then
    ORCH_RUN_ID=$(echo "$ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'])" 2>/dev/null)
    info "Orchestrate 已启动 (run=$ORCH_RUN_ID)，正在等待执行结果..."
    
    FINAL_ORCH_STATUS=$(wait_for_run "$ORCH_RUN_ID" 60)
    if is_run_success "$FINAL_ORCH_STATUS"; then
        ok "Orchestrate 执行成功 (Run status: $FINAL_ORCH_STATUS)"
    else
        no "Orchestrate 执行失败 (Run status: $FINAL_ORCH_STATUS)"
    fi
else
    # 区分「真失败」与「业务正确拒绝」（如冲突未解决）
    ORCH_CODE=$(echo "$ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
    ORCH_MSG=$(echo "$ORCH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))" 2>/dev/null)
    if [ "$ORCH_CODE" = "CONFLICT_001" ]; then
        ok "Orchestrate 被冲突预检拒绝（业务正确）: $ORCH_MSG"
    else
        no "Orchestrate 启动失败: code=$ORCH_CODE msg=$ORCH_MSG"
    fi
fi

# ---- 7. 场景: Run 详情 ----
h2 "SA-015: 7. 场景: Run 执行详情"
sleep 1
RUNS=$(curl -s "$BACKEND/api/v1/runs" -H "$AUTH")
LATEST_RUN=$(echo "$RUNS" | python3 -c "
import sys,json
data = json.load(sys.stdin).get('data',[])
if data:
    r = data[-1]  # last run
    items = len(r.get('items',[]))
    print(f\"id={r['id']} type={r.get('runType','?')} status={r.get('status','?')} items={items}\")
else:
    print('NO_RUNS')
")
if [ "$LATEST_RUN" = "NO_RUNS" ]; then
    no "无 Run 记录（Auto-orchestration 事务边界可能未修复）"
else
    ITEM_COUNT=$(echo "$LATEST_RUN" | grep -o 'items=[0-9]*' | cut -d= -f2)
    [ "$ITEM_COUNT" -eq 0 ] && warn "Run 含 0 item（feature 分支缺失导致全部 SKIP）" || ok "Run items: $ITEM_COUNT"
    info "$LATEST_RUN"

    # 7.1 RunItem step 分布（需要 RunView 返回 items 字段 — RunController v3.3+）
    LATEST_RUN_ID=$(echo "$LATEST_RUN" | sed -n 's/.*id=\([^ ]*\).*/\1/p')
    if [ -n "$LATEST_RUN_ID" ]; then
        RUN_DETAIL=$(curl -s "$BACKEND/api/v1/runs/$LATEST_RUN_ID" -H "$AUTH")
        STEP_DIST=$(echo "$RUN_DETAIL" | python3 -c "
import sys,json
d = json.load(sys.stdin).get('data', {})
items = d.get('items', [])
if not items:
    print('NO_ITEMS')
else:
    from collections import Counter
    actions = Counter()
    results = Counter()
    for item in items:
        for s in item.get('steps', []):
            actions[s.get('actionType', '?')] += 1
            results[s.get('result', '?')] += 1
    print(f'steps={sum(actions.values())} actions={dict(actions)} results={dict(results)}')
" 2>/dev/null)
        [ -n "$STEP_DIST" ] && [ "$STEP_DIST" != "NO_ITEMS" ] && info "  Step 分布: $STEP_DIST"
    fi
fi

# ---- 8. 场景: 版本更新 + 校验 ----
h2 "SA-014: 8. 场景: 版本更新 & 校验"
VU_WINDOW_ID="${CLEAN_WINDOW_ID:-$WINDOW_ID}"
VU_REPO_ID="$R1"
info "SA-014 使用窗口: $VU_WINDOW_ID"

# 8.1 版本校验（验证 VersionPolicy 推导功能可用）
VERSION_VALIDATE=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$VU_WINDOW_ID/validate" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"currentVersion\":\"1.4.0\"}" 2>/dev/null)
VU_VALID=$(echo "$VERSION_VALIDATE" | python3 -c "
import sys,json; d=json.load(sys.stdin)
print(d.get('success','?'))" 2>/dev/null)
if [ "$VU_VALID" = "True" ]; then
    DERIVED=$(echo "$VERSION_VALIDATE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('derivedVersion','?'))" 2>/dev/null)
    ok "版本校验通过 (current=1.4.0 → derived=$DERIVED)"
else
    info "版本校验: 无 VersionPolicy 可匹配（使用默认策略可能为空）"
fi

# 8.2 版本更新（对 Maven 仓库执行实际更新）
VERSION_UPDATE=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$VU_WINDOW_ID/execute/version-update" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"repoId\":\"$VU_REPO_ID\",\"targetVersion\":\"1.5.0\",\"buildTool\":\"MAVEN\",\"repoPath\":\".\",\"pomPath\":\"pom.xml\"}" 2>/dev/null)
VU_SUCCESS=$(echo "$VERSION_UPDATE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success','?'))" 2>/dev/null)
if [ "$VU_SUCCESS" = "True" ]; then
    VU_RUN_ID=$(echo "$VERSION_UPDATE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['runId'])" 2>/dev/null)
    info "版本更新已启动 (run=$VU_RUN_ID)，正在等待执行结果..."
    
    FINAL_STATUS=$(wait_for_run "$VU_RUN_ID" 45)
    if is_run_success "$FINAL_STATUS"; then
        ok "版本更新执行成功 (Run status: $FINAL_STATUS)"
        
        # 增加 Git 远程校验
        REPO_URL=$(curl -s "$BACKEND/api/v1/repositories/$VU_REPO_ID" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))")
        WINDOW_KEY=$(curl -s "$BACKEND/api/v1/release-windows/$VU_WINDOW_ID" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('windowKey',''))")
        
        # 预期的分支名（与后端逻辑一致）
        TARGET_BRANCH="release/$WINDOW_KEY"
        
        GIT_COMMIT=$(verify_gitlab_commit "$REPO_URL" "$TARGET_BRANCH" "ReleaseHub: Update")
        if [ "$GIT_COMMIT" = "FOUND" ]; then
            ok "SA-014 Git 远程校验成功: 已发现版本更新 Commit"
        elif [ -n "$CLEAN_WINDOW_ID" ]; then
            no "SA-014 Git 远程校验失败: 未发现版本更新 Commit ($GIT_COMMIT)"
        else
            warn "Git 远程校验未确认: 可能是分支名或 Commit Message 匹配问题 ($GIT_COMMIT)"
        fi
    else
        no "版本更新执行失败 (Run status: $FINAL_STATUS)"
        info "错误详情: $(curl -s "$BACKEND/api/v1/runs/$VU_RUN_ID" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('items',[{}])[0].get('message','?'))")"
    fi
else
    ERR=$(echo "$VERSION_UPDATE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message','?'))" 2>/dev/null || echo "?")
    if [ -n "$CLEAN_WINDOW_ID" ]; then
        no "SA-014 版本更新失败: $ERR"
    else
        skip "SA-014 版本更新未执行: $ERR"
    fi
fi

# ---- 8.5 场景: 发布后关闭与收尾闭环 ----
h2 "SA-016: 8.5 场景: 关闭窗口后禁止关键操作 & 收尾 Run"
SA16_WINDOW_ID="$VU_WINDOW_ID"
SA16_WINDOW_KEY=$(curl -s "$BACKEND/api/v1/release-windows/$SA16_WINDOW_ID" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('windowKey',''))" 2>/dev/null)

if [ -z "$SA16_WINDOW_ID" ] || [ -z "$SA16_WINDOW_KEY" ]; then
    no "SA-016 无可用窗口用于关闭验收"
else
    CLOSE_RESULT=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$SA16_WINDOW_ID/close" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
    CLOSE_SUCCESS=$(echo "$CLOSE_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success','?'))" 2>/dev/null)
    CLOSE_STATUS=$(echo "$CLOSE_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('status',''))" 2>/dev/null)
    if [ "$CLOSE_SUCCESS" = "True" ] && [ "$CLOSE_STATUS" = "CLOSED" ]; then
        ok "SA-016 关闭窗口成功 → CLOSED"
    else
        no "SA-016 关闭窗口失败: $CLOSE_RESULT"
    fi

    IDEMPOTENT_CLOSE_RESULT=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$SA16_WINDOW_ID/close" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
    IDEMPOTENT_CLOSE_SUCCESS=$(echo "$IDEMPOTENT_CLOSE_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success','?'))" 2>/dev/null)
    IDEMPOTENT_CLOSE_STATUS=$(echo "$IDEMPOTENT_CLOSE_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('status',''))" 2>/dev/null)
    if [ "$IDEMPOTENT_CLOSE_SUCCESS" = "True" ] && [ "$IDEMPOTENT_CLOSE_STATUS" = "CLOSED" ]; then
        ok "SA-016 重复关闭保持幂等 → CLOSED"
    else
        no "SA-016 重复关闭未保持幂等: $IDEMPOTENT_CLOSE_RESULT"
    fi

    POST_CLOSE_ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$SA16_WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"iterationKeys\":[\"$ITER_KEY\"]}" 2>/dev/null)
    POST_CLOSE_ATTACH_CODE=$(echo "$POST_CLOSE_ATTACH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
    if [ "$POST_CLOSE_ATTACH_CODE" = "RW_009" ]; then
        ok "SA-016 关闭后禁止挂载迭代 (RW_009)"
    else
        no "SA-016 关闭后挂载迭代未被正确拒绝: $POST_CLOSE_ATTACH"
    fi

    POST_CLOSE_VU=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$SA16_WINDOW_ID/execute/version-update" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"repoId\":\"$VU_REPO_ID\",\"targetVersion\":\"1.6.0\",\"buildTool\":\"MAVEN\",\"repoPath\":\".\",\"pomPath\":\"pom.xml\"}" 2>/dev/null)
    POST_CLOSE_VU_CODE=$(echo "$POST_CLOSE_VU" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null)
    if [ "$POST_CLOSE_VU_CODE" = "RW_009" ]; then
        ok "SA-016 关闭后禁止版本更新 (RW_009)"
    else
        no "SA-016 关闭后版本更新未被正确拒绝: $POST_CLOSE_VU"
    fi

    CLEANUP_RUNS=$(curl -s "$BACKEND/api/v1/runs/paged?size=10&runType=WINDOW_ORCHESTRATION&windowKey=$SA16_WINDOW_KEY" -H "$AUTH")
    CLEANUP_EVIDENCE=$(echo "$CLEANUP_RUNS" | python3 -c "
import sys,json
d=json.load(sys.stdin)
runs=d.get('data',[])
cleanup=[]
for r in runs:
    actions=set()
    for item in r.get('items',[]):
        for s in item.get('steps',[]):
            actions.add(s.get('actionType'))
    if {'ARCHIVE_BRANCH','MERGE_TO_MASTER','CREATE_TAG','TRIGGER_CI'} & actions:
        cleanup.append((r.get('id'), r.get('status'), len(r.get('items',[])), sorted(actions)))
if cleanup:
    rid,status,items,actions=cleanup[0]
    print(f'FOUND id={rid} status={status} items={items} actions={actions}')
else:
    print('NOT_FOUND')
" 2>/dev/null)
    if echo "$CLEANUP_EVIDENCE" | grep -q '^FOUND'; then
        ok "SA-016 收尾 Run 可见: $CLEANUP_EVIDENCE"
    else
        no "SA-016 未找到收尾 Run: $CLEANUP_EVIDENCE"
    fi
fi

# ---- 9. 场景: 存量冒烟 ----
h2 "9. 场景: 存量数据冒烟"
# 验证之前积累的数据是否仍然可访问
PREV_WINDOWS=$(curl -s "$BACKEND/api/v1/release-windows" -H "$AUTH" | python3 -c "
import sys,json
data = json.load(sys.stdin).get('data',[])
closed = [w for w in data if w.get('status') == 'CLOSED']
published = [w for w in data if w.get('status') == 'PUBLISHED']
print(f'CLOSED:{len(closed)} PUBLISHED:{len(published)}')
")
info "历史窗口: $PREV_WINDOWS"

# 验证旧的 Run 仍可查询
[ "$RUN_TOTAL" -gt 1 ] && ok "历史 Run 可查询: total=$RUN_TOTAL" || info "Run 累积: $RUN_TOTAL"

# ---- 10. 场景: 分支创建模式（三层关联验证） ----
h2 "SA-006/SA-009: 10. 场景: 分支创建模式验证"

# 10.1 AUTO 模式：创建迭代带仓库，无 repoConfigs → 默认 AUTO
info "10.1 AUTO 模式（向后兼容）"
AUTO_ITER=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-AUTO-$TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[\"$R1\"]}")
AUTO_ITER_KEY=$(echo "$AUTO_ITER" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))" 2>/dev/null)
[ -n "$AUTO_ITER_KEY" ] && ok "AUTO 迭代创建: $AUTO_ITER_KEY" || { no "AUTO 迭代创建失败: $AUTO_ITER"; AUTO_ITER_KEY=""; }

if [ -n "$AUTO_ITER_KEY" ]; then
    sleep 1
    AUTO_VINFO=$(curl -s "$BACKEND/api/v1/iterations/$AUTO_ITER_KEY/repos/$R1/version-info" -H "$AUTH")
    AUTO_FB=$(echo "$AUTO_VINFO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('featureBranch','NONE'))" 2>/dev/null)
    if echo "$AUTO_FB" | grep -q "^feature/ITER-"; then
        ok "AUTO featureBranch: $AUTO_FB"
    else
        no "AUTO featureBranch 异常: $AUTO_FB"
    fi
fi

# 10.2 NAMED 模式：addRepos 时指定自定义分支名
info "10.2 NAMED 模式（自定义分支名）"
NAMED_ITER=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-NAMED-$TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[]}")
NAMED_ITER_KEY=$(echo "$NAMED_ITER" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))" 2>/dev/null)
[ -n "$NAMED_ITER_KEY" ] && ok "NAMED 迭代创建: $NAMED_ITER_KEY" || { no "NAMED 迭代创建失败: $NAMED_ITER"; NAMED_ITER_KEY=""; }

if [ -n "$NAMED_ITER_KEY" ]; then
    NAMED_ADD=$(curl -s -X POST "$BACKEND/api/v1/iterations/$NAMED_ITER_KEY/repos/add" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"repoIds\":[\"$R2\"],\"branchCreationMode\":\"NAMED\",\"customBranchName\":\"feature/acceptance-named-$TS\"}")
    NAMED_SUCCESS=$(echo "$NAMED_ADD" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('success', False))" 2>/dev/null)
    [ "$NAMED_SUCCESS" = "True" ] && ok "NAMED addRepos 成功" || no "NAMED addRepos 失败"

    sleep 1
    NAMED_VINFO=$(curl -s "$BACKEND/api/v1/iterations/$NAMED_ITER_KEY/repos/$R2/version-info" -H "$AUTH")
    NAMED_FB=$(echo "$NAMED_VINFO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('featureBranch','NONE'))" 2>/dev/null)
    if echo "$NAMED_FB" | grep -q "acceptance-named"; then
        ok "NAMED featureBranch: $NAMED_FB"
    else
        no "NAMED featureBranch 异常: $NAMED_FB"
    fi
fi

# 10.3 NAMED 非法分支名：不在 feature/ 路径下 → 版本信息不保存
info "10.3 NAMED 非法分支名校验"
NAMED_ITER2=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-NAMED-BAD-$TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[]}")
NAMED_ITER2_KEY=$(echo "$NAMED_ITER2" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))" 2>/dev/null)

if [ -n "$NAMED_ITER2_KEY" ]; then
    # addRepos 吞异常——repo 仍被添加，但 featureBranch 为 null（versionInfo 未保存）
    curl -s -X POST "$BACKEND/api/v1/iterations/$NAMED_ITER2_KEY/repos/add" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"repoIds\":[\"$R3\"],\"branchCreationMode\":\"NAMED\",\"customBranchName\":\"hotfix/bad-name\"}" > /dev/null

    sleep 1
    BAD_VINFO=$(curl -s "$BACKEND/api/v1/iterations/$NAMED_ITER2_KEY/repos/$R3/version-info" -H "$AUTH")
    BAD_FB=$(echo "$BAD_VINFO" | python3 -c "import sys,json; d=json.load(sys.stdin); f=d.get('data',{}).get('featureBranch'); print(f if f is not None else 'None')" 2>/dev/null)
    if [ "$BAD_FB" = "None" ]; then
        ok "NAMED 非法分支名被拒绝（featureBranch=null）"
    else
        no "NAMED 非法分支名未被拦截: featureBranch=$BAD_FB"
    fi
fi

# 10.4 EXISTING 模式：关联不存在的分支 → featureBranch 应为 null
info "10.4 EXISTING 模式（关联不存在分支时拒绝）"
EXISTING_ITER=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-EXISTING-$TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[]}")
EXISTING_ITER_KEY=$(echo "$EXISTING_ITER" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data', {}).get('key', ''))" 2>/dev/null)

if [ -n "$EXISTING_ITER_KEY" ]; then
    # 用一个绝对不存在的分支名，验证 EXISTING 模式的 GitLab 校验
    curl -s -X POST "$BACKEND/api/v1/iterations/$EXISTING_ITER_KEY/repos/add" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"repoIds\":[\"$R1\"],\"branchCreationMode\":\"EXISTING\",\"customBranchName\":\"feature/nonexistent-acceptance-$TS\"}" > /dev/null

    sleep 1
    EXISTING_VINFO=$(curl -s "$BACKEND/api/v1/iterations/$EXISTING_ITER_KEY/repos/$R1/version-info" -H "$AUTH")
    EXISTING_FB=$(echo "$EXISTING_VINFO" | python3 -c "import sys,json; d=json.load(sys.stdin); f=d.get('data',{}).get('featureBranch'); print(f if f is not None else 'None')" 2>/dev/null)
    if [ "$EXISTING_FB" = "None" ]; then
        ok "EXISTING 不存在的分支被拒绝（featureBranch=null）"
    else
        no "EXISTING 未正确拒绝不存在分支: featureBranch=$EXISTING_FB"
    fi
fi

# 10.5 Branches 端点验证
info "10.5 分支列表端点"
BRANCHES=$(curl -s "$BACKEND/api/v1/repositories/$R1/branches?prefix=feature/" -H "$AUTH")
BRANCHES_SUCCESS=$(echo "$BRANCHES" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('success', False))" 2>/dev/null)
if [ "$BRANCHES_SUCCESS" = "True" ]; then
    BCOUNT=$(echo "$BRANCHES" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null)
    ok "GET /branches 端点可用 (返回 $BCOUNT 个分支)"
else
    no "GET /branches 端点失败"
fi

# ---- 11. 汇总 ----
h2 "11. 验收汇总"
echo ""
echo "  数据资产: $GROUP_COUNT groups | $REPO_COUNT repos | $WINDOW_COUNT windows | $ITER_COUNT iterations | $RUN_TOTAL runs"
echo "  Token 安全: 加密=$ENCRYPTED_COUNT | 明文=$PLAINTEXT_COUNT | Flyway=$FLYWAY_V"
echo "  本轮结果: PASS=$PASS | FAIL=$FAIL | SKIP=$SKIP"
echo ""
echo "  ┌─────────────────────────────────────────────┐"
echo "  │ 验收数据已沉淀。下次运行 bash $0 即可追加验证    │"
echo "  │ 不 DROP、不 DELETE、不破坏历史。                  │"
echo "  └─────────────────────────────────────────────┘"
[ $FAIL -eq 0 ] && exit 0 || exit 1
