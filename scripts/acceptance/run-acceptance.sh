#!/bin/bash
set -e

# ============================================================
# ReleaseHub 验收脚本 — 幂等、可重复执行、数据可沉淀验证
#
# 原则：
#   1. 不 DROP DATABASE（Flyway 负责 schema，数据是持久资产）
#   2. 创建操作前先检查是否存在，存在则复用
#   3. GitLab 种子数据只初始化一次
#   4. 每次运行只做必要变更，保留历史数据用于验证
#
# 用法：
#   bash scripts/acceptance/run-acceptance.sh          # 增量验收
#   bash scripts/acceptance/run-acceptance.sh --clean  # 先清空验收数据再跑
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
GITLAB_URL="${GITLAB_URL:-http://localhost:9080}"
ROOT_PASS="${ROOT_PASS:-releasehub123}"
CLEAN_FIRST=false
FORCE_REFRESH_TOKEN=false

for arg in "$@"; do
    case $arg in
        --clean) CLEAN_FIRST=true ;;
        --refresh-token) FORCE_REFRESH_TOKEN=true ;;
        --help) echo "Usage: $0 [--clean] [--refresh-token]"; exit 0 ;;
    esac
done

# ---- 工具函数 ----
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "${GREEN}[PASS]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; }

# 幂等创建：状态日志输出到 stderr，返回值到 stdout，互不干扰
ensure_group() {
    local name=$1
    local existing=$(curl -s "$BACKEND_URL/api/v1/groups" -H "$AUTH" | python3 -c "
import sys,json
for g in json.load(sys.stdin).get('data', []):
    if g.get('name') == '$name':
        print(g.get('code',''))
        break
" 2>/dev/null)
    if [ -n "$existing" ]; then
        echo "  $(pass "复用已有分组: $name (code=$existing)")" >&2
        echo "$existing"
        return
    fi
    local code=$(curl -s -X POST "$BACKEND_URL/api/v1/groups" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"$name\",\"parentCode\":null}" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'])")
    echo "  $(pass "创建分组: $name → $code")" >&2
    echo "$code"
}

ensure_repo() {
    local name=$1 clone_url=$2 group_code=$3 git_token=$4
    # 用 paged 接口精确匹配名称（keyword 查询不等于精确匹配）
    local existing=$(curl -s "$BACKEND_URL/api/v1/repositories" -H "$AUTH" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('data', []):
    if r.get('name') == '$name':
        print(r.get('id',''))
        break
" 2>/dev/null)
    if [ -n "$existing" ]; then
        echo "  $(pass "复用已有仓库: $name ($existing)")" >&2
        echo "$existing"
        return
    fi
    local repo_id=$(curl -s -X POST "$BACKEND_URL/api/v1/repositories" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"$name\",\"cloneUrl\":\"$clone_url\",\"defaultBranch\":\"main\",\"groupCode\":\"$group_code\",\"gitProvider\":\"GITLAB\",\"gitAccessToken\":\"$git_token\"}" \
        | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
    echo "  $(pass "创建仓库: $name → $repo_id")" >&2
    echo "$repo_id"
}

ensure_window() {
    local name=$1 group_code=$2
    local existing=$(curl -s "$BACKEND_URL/api/v1/release-windows/paged?name=$name&size=50" -H "$AUTH" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('data', []):
    if r.get('name') == '$name':
        print(r.get('id',''))
        break
" 2>/dev/null)
    if [ -n "$existing" ]; then
        echo "  $(pass "复用已有窗口: $name ($existing)")" >&2
        echo "$existing"
        return
    fi
    local next_week=$(date -u -v+7d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d "+7 days" +%Y-%m-%dT%H:%M:%SZ)
    local rw_id=$(curl -s -X POST "$BACKEND_URL/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"$name\",\"description\":\"验收窗口\",\"plannedReleaseAt\":\"$next_week\",\"groupCode\":\"$group_code\"}" \
        | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
    echo "  $(pass "创建窗口: $name → $rw_id")" >&2
    echo "$rw_id"
}

ensure_iteration() {
    local name=$1 group_code=$2
    shift 2; local repo_ids=("$@")
    local existing=$(curl -s "$BACKEND_URL/api/v1/iterations" -H "$AUTH" | python3 -c "
import sys,json
for it in json.load(sys.stdin).get('data', []):
    if it.get('name') == '$name':
        print(it.get('key',''))
        break
" 2>/dev/null)
    if [ -n "$existing" ]; then
        echo "  $(pass "复用已有迭代: $name ($existing)")" >&2
        echo "$existing"
        return
    fi
    local repo_json="["
    for rid in "${repo_ids[@]}"; do
        [ "$repo_json" != "[" ] && repo_json+=","
        repo_json+="\"$rid\""
    done
    repo_json+="]"
    local iter_key=$(curl -s -X POST "$BACKEND_URL/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"name\":\"$name\",\"groupCode\":\"$group_code\",\"repoIds\":$repo_json}" \
        | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['key'])")
    echo "  $(pass "创建迭代: $name → $iter_key")" >&2
    echo "$iter_key"
}

# ---- 入口 ----
echo "============================================"
echo " ReleaseHub 验收脚本 (幂等模式)"
echo " $(date '+%Y-%m-%d %H:%M:%S')"
echo " Backend: $BACKEND_URL"
echo "============================================"

# ---- 0. 前置检查 ----
echo ""
echo "--- 0. 前置检查 ---"

# 检查容器
if ! docker ps --format '{{.Names}}' | grep -q 'releasehub-gitlab'; then
    echo "  GitLab 容器未运行，启动中..."
    docker compose -f "$PROJECT_ROOT/docker-compose.gitlab.yml" up -d
    echo "  $(warn "等待 GitLab 就绪 (最多 3 分钟)...")"
    for i in $(seq 1 60); do
        if curl -s -o /dev/null -w "%{http_code}" "$GITLAB_URL/users/sign_in" | grep -q 200; then
            echo "  $(pass "GitLab 就绪")"
            break
        fi
        sleep 3
    done
else
    echo "  $(pass "GitLab 容器运行中")"
fi

if ! docker ps --format '{{.Names}}' | grep -q 'releasehub-postgres'; then
    echo "  $(fail "PostgreSQL 容器未运行，请先启动: docker compose -f docker-compose.full.yml up -d postgres")"
    exit 1
fi
echo "  $(pass "PostgreSQL 容器运行中")"

# 检查后端
if ! curl -s -o /dev/null "$BACKEND_URL/actuator/health" 2>/dev/null; then
    echo "  $(fail "后端未运行 ($BACKEND_URL)")"
    echo "  启动命令: cd backend && SPRING_PROFILES_ACTIVE=e2e \\"
    echo "    E2E_DATASOURCE_URL=jdbc:postgresql://localhost:5433/release_hub \\"
    echo "    E2E_GITLAB_URL=http://localhost:9080 \\"
    echo "    mvn spring-boot:run -pl releasehub-bootstrap"
    exit 1
fi
echo "  $(pass "后端运行中 ($BACKEND_URL)")"

# ---- 0.5 登录 ----
echo ""
echo "--- 0.5 登录 ---"
LOGIN_RESP=$(curl -s -X POST "$BACKEND_URL/api/v1/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}')
AUTH_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
AUTH="Authorization: Bearer $AUTH_TOKEN"
echo "  $(pass "登录成功")"

# ---- 0.6 清理验收数据（可选） ----
if $CLEAN_FIRST; then
    echo ""
    echo "--- 0.6 清理验收数据 (--clean) ---"

    # 按外键依赖顺序：RunItems/RunSteps → Runs → WindowIteration → IterationRepo → ReleaseWindow → Iteration → CodeRepository
    RUN_IDS=$(curl -s "$BACKEND_URL/api/v1/runs" -H "$AUTH" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('data',[]):
    print(r['id'])
" 2>/dev/null)
    RUN_COUNT=$(echo "$RUN_IDS" | grep -c . || true)
    echo "  清理 $RUN_COUNT 条 Run..."
    # Note: 当前 API 无批量删除 Run 的端点，通过 PG 清理
    if [ "$RUN_COUNT" -gt 0 ]; then
        docker exec releasehub-postgres psql -U postgres -d release_hub -c "DELETE FROM run_step; DELETE FROM run_item; DELETE FROM run;" > /dev/null 2>&1
        echo "  $(pass "Run 记录已清理")"
    fi

    WI_COUNT=$(curl -s "http://localhost:8080/api/v1/release-windows" -H "$AUTH" | python3 -c "
import sys,json; print(len(json.load(sys.stdin).get('data',[])))
")
    echo "  清理 $WI_COUNT 个窗口关联..."
    docker exec releasehub-postgres psql -U postgres -d release_hub -c \
        "DELETE FROM window_iteration; DELETE FROM iteration_repo; DELETE FROM iteration; DELETE FROM release_window; DELETE FROM code_repository; DELETE FROM groups WHERE name LIKE '%验收%';" > /dev/null 2>&1
    echo "  $(pass "验收数据已清理")"
fi

# ---- 1. GitLab 种子数据 ----
echo ""
echo "--- 1. GitLab 种子数据 ---"
INIT_SCRIPT="$PROJECT_ROOT/scripts/e2e/init-gitlab.sh"
if [ -f "$INIT_SCRIPT" ]; then
    GITLAB_URL="$GITLAB_URL" ROOT_PASS="$ROOT_PASS" bash "$INIT_SCRIPT" 2>&1 | grep -E "^(===|Repo|GitLab|Test|Personal|Env)" | while read line; do
        echo "  $line"
    done
fi
echo "  $(pass "种子数据检查完成")"

# ---- 1.5 获取/刷新 GitLab PAT ----
echo ""
echo "--- 1.5 GitLab PAT ---"
TOKEN_FILE="/tmp/e2e-gitlab.env"
if $FORCE_REFRESH_TOKEN || [ ! -f "$TOKEN_FILE" ]; then
    echo "  获取新 PAT..."
    GITLAB_PAT=$(docker exec releasehub-gitlab gitlab-rails runner "
token = User.find(1).personal_access_tokens.create!(
  name: 'acceptance-pat',
  scopes: ['api', 'read_repository', 'write_repository'],
  expires_at: 90.days.from_now
)
puts token.token
" 2>&1 | tail -1)
    echo "E2E_GITLAB_TOKEN=$GITLAB_PAT" > "$TOKEN_FILE"
    echo "  $(pass "PAT 已更新 (${GITLAB_PAT:0:12}...)")"
else
    source "$TOKEN_FILE" 2>/dev/null || true
    GITLAB_PAT="${E2E_GITLAB_TOKEN:-}"
    if [ -z "$GITLAB_PAT" ]; then
        echo "  $(fail "PAT 文件存在但无 token，使用 --refresh-token 重新获取")"
        exit 1
    fi
    # 验证 token 是否还有效
    if curl -s -H "PRIVATE-TOKEN: $GITLAB_PAT" "$GITLAB_URL/api/v4/projects/1" | grep -q '"id":1'; then
        echo "  $(pass "PAT 有效 (${GITLAB_PAT:0:12}...)")"
    else
        echo "  $(warn "PAT 已过期，重新获取...")"
        GITLAB_PAT=$(docker exec releasehub-gitlab gitlab-rails runner "
token = User.find(1).personal_access_tokens.create!(
  name: 'acceptance-pat',
  scopes: ['api', 'read_repository', 'write_repository'],
  expires_at: 90.days.from_now
)
puts token.token
" 2>&1 | tail -1)
        echo "E2E_GITLAB_TOKEN=$GITLAB_PAT" > "$TOKEN_FILE"
        echo "  $(pass "PAT 已更新")"
    fi
fi

# ---- 2. 创建验收数据（幂等） ----
echo ""
echo "--- 2. 验收数据准备 ---"

# 2.1 分组
echo "  2.1 分组:"
GROUP_CODE=$(ensure_group "验收分组")

# 2.2 仓库
echo "  2.2 仓库:"
REPO1=$(ensure_repo "验收-Maven单模块" "http://localhost:9080/e2e-user/seed-repo-1-maven.git" "$GROUP_CODE" "$GITLAB_PAT")
REPO2=$(ensure_repo "验收-Maven多模块" "http://localhost:9080/e2e-user/seed-repo-2-maven-multi.git" "$GROUP_CODE" "$GITLAB_PAT")
REPO3=$(ensure_repo "验收-Gradle" "http://localhost:9080/e2e-user/seed-repo-3-gradle.git" "$GROUP_CODE" "$GITLAB_PAT")

# 2.3 窗口
echo "  2.3 窗口:"
WINDOW_ID=$(ensure_window "验收-全链路窗口" "$GROUP_CODE")

# 2.4 迭代
echo "  2.4 迭代:"
ITER_KEY=$(ensure_iteration "验收-全链路迭代" "$GROUP_CODE" "$REPO1" "$REPO2" "$REPO3")

# ---- 3. 验证操作 ----
echo ""
echo "--- 3. 验证操作 ---"

# 获取窗口当前状态
RW_STATUS=$(curl -s "$BACKEND_URL/api/v1/release-windows/$WINDOW_ID" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "  窗口状态: $RW_STATUS"

# 获取已有关联迭代
ATTACHED_ITERS=$(curl -s "$BACKEND_URL/api/v1/release-windows/$WINDOW_ID/iterations" -H "$AUTH" | python3 -c "
import sys,json
for wi in json.load(sys.stdin).get('data',[]):
    print(f\"{wi['iterationKey']} (branchCreated={wi['branchCreated']})\")" 2>/dev/null)
echo "  已挂载迭代: $ATTACHED_ITERS"

# 如果还没 Attach，执行 Attach
if [ "$(echo "$ATTACHED_ITERS" | grep -c "$ITER_KEY" || true)" -eq 0 ]; then
    echo "  3.1 Attach 迭代..."
    ATTACH_RESULT=$(curl -s -X POST "$BACKEND_URL/api/v1/release-windows/$WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
        -d "{\"iterationKeys\":[\"$ITER_KEY\"]}")
    echo "$ATTACH_RESULT" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('data',[]):
    s = 'OK' if not r['hasErrors'] else 'HAS_ERRORS'
    print(f'    {r[\"iterationKey\"]}: {s}')
    for e in r.get('errors',[]): print(f'      → {e[\"message\"][:100]}')
"
else
    echo "  3.1 $(pass "迭代已挂载")"
fi

# 如果还没发布
if [ "$RW_STATUS" = "DRAFT" ]; then
    echo "  3.2 Publish 窗口..."
    PUB_RESULT=$(curl -s -X POST "$BACKEND_URL/api/v1/release-windows/$WINDOW_ID/publish" -H "$AUTH" -H "Content-Type: application/json" -d '{}')
    echo "    $(echo "$PUB_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'success={d[\"success\"]} status={d[\"data\"][\"status\"]}')")"
else
    echo "  3.2 $(pass "窗口已是 $RW_STATUS")"
fi

# 3.3 编排
echo "  3.3 Orchestrate..."
ORCH_RESULT=$(curl -s -X POST "$BACKEND_URL/api/v1/release-windows/$WINDOW_ID/orchestrate" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"repoIds\":[\"$REPO1\",\"$REPO2\",\"$REPO3\"],\"iterationKeys\":[\"$ITER_KEY\"],\"failFast\":false,\"operator\":\"acceptance\"}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'success={d[\"success\"]} run={d.get(\"data\",\"?\")[:20]}')")
echo "    $ORCH_RESULT"

# ---- 4. 结果汇总 ----
echo ""
echo "============================================"
echo " 验收结果汇总"
echo "============================================"

# 4.1 数据资产
GROUP_COUNT=$(curl -s "$BACKEND_URL/api/v1/groups" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))")
REPO_COUNT=$(curl -s "$BACKEND_URL/api/v1/repositories" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))")
WINDOW_COUNT=$(curl -s "$BACKEND_URL/api/v1/release-windows" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))")
ITER_COUNT=$(curl -s "$BACKEND_URL/api/v1/iterations" -H "$AUTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))")
RUN_TOTAL=$(curl -s "$BACKEND_URL/api/v1/runs/paged?size=1" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin)['page']['total'])")

echo ""
echo "  持久化数据资产:"
echo "    Groups:    $GROUP_COUNT"
echo "    Repos:     $REPO_COUNT"
echo "    Windows:   $WINDOW_COUNT"
echo "    Iterations: $ITER_COUNT"
echo "    Runs:      $RUN_TOTAL"

# 4.2 Token 加密验证
echo ""
echo "  Token 加密验证:"
docker exec releasehub-postgres psql -U postgres -d release_hub -t -c \
    "SELECT '    ' || name || ': ' || CASE WHEN git_token IS NULL THEN 'NULL' WHEN git_token ~ '^glpat-' THEN '❌ PLAINTEXT' WHEN git_token != '' THEN '✅ ENCRYPTED' ELSE 'EMPTY' END FROM code_repository ORDER BY name;" 2>&1 | grep -v "^$"

# 4.3 数据库 Schema 版本
echo ""
echo "  Flyway 迁移记录:"
docker exec releasehub-postgres psql -U postgres -d release_hub -t -c \
    "SELECT '    ' || version || ' ' || description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;" 2>&1 | grep -v "^$"

echo ""
echo "============================================"
echo " 验收完成 — 数据已沉淀，可重复执行验证"
echo " $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"
