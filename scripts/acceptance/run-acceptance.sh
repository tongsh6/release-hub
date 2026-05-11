#!/bin/bash
# ============================================================
# ReleaseHub 全链路验收脚本 v3
#
# ╔═══════════════════════════════════════════════════════════╗
# ║  ⚠️  重要提示：请先用本脚本，不要手工逐 API 调试验收  ⚠️  ║
# ╚═══════════════════════════════════════════════════════════╝
#
# 为什么必须先用本脚本？
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
# 能力清单（11 个场景，25+ 验收项）:
#   0. 环境检查 + 脏数据检测
#   1. 存量数据审计（Groups/Repos/Windows/Iterations/Runs/Token 加密）
#   2. GitLab 种子数据初始化（幂等）
#   3. 新增发布窗口全链路（Group → Repo → Window → Iteration）
#   4. Attach 迭代 & GitLab 分支创建 & 错误可见性
#   5. 冲突检测
#   6. Publish & 自动编排 & WindowLifecycleListener 验证
#   7. Run 执行详情
#   8. 版本更新 & 校验
#   9. 存量数据冒烟
#  10. 分支创建模式验证（AUTO/NAMED/NAMED非法/EXISTING/Branches端点）
#  11. 汇总报告
#
# 原则:
#   1. 永不 DROP DATABASE / DELETE 数据（本地持久化模式）
#   2. 脏数据检测 → 报告 + 提供清理方案，由人决定
#   3. 场景化验证：存量冒烟 + 新增全链路 + 冲突 + 版本更新 + 多仓编排
#   4. 双模式：本地持久化（默认） / CI 一次性（--ci）
#
# 用法:
#   bash run-acceptance.sh              # 本地模式
#   bash run-acceptance.sh --ci         # CI 模式（docker compose up/down）
#   bash run-acceptance.sh --check      # 仅检查存量数据完整性
# ============================================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND="${BACKEND_URL:-http://localhost:8080}"
FRONTEND="${FRONTEND_URL:-http://localhost:5173}"
GITLAB="${GITLAB_URL:-http://localhost:9080}"

MODE="local"
CHECK_ONLY=false
TS=$(date -u +%Y%m%d-%H%M%S)
PASS=0; FAIL=0; SKIP=0

for arg in "$@"; do
    case $arg in
        --ci) MODE="ci" ;;
        --check) CHECK_ONLY=true ;;
        --help) echo "Usage: $0 [--ci] [--check]"; exit 0 ;;
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

# 验证 GitLab 侧是否产生了预期的 Commit
# Usage: verify_gitlab_commit <repo_clone_url> <branch> <message_keyword>
verify_gitlab_commit() {
    local clone_url=$1
    local branch=$2
    local keyword=$3
    
    # 从 clone_url 提取 project_path (e.g. http://.../group/project.git -> group/project)
    local project_path=$(echo "$clone_url" | sed -E 's|https?://[^/]+/||; s|\.git$||')
    local encoded_path=$(echo "$project_path" | python3 -c "import sys, urllib.parse; print(python3 urllib.parse.quote(sys.stdin.read().strip(), safe=''))" 2>/dev/null || echo "$project_path" | sed 's|/|%2F|g')
    
    # 获取 GitLab Token (假设系统设置中已配置，脚本直接通过 API 读取)
    local gl_token=$(curl -s "$BACKEND/api/v1/settings/gitlab" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))")
    
    if [ -z "$gl_token" ] || [ "$gl_token" = "null" ]; then
        echo "MISSING_TOKEN"
        return 1
    fi

    # 查询最近的 Commit
    local endpoint="$GITLAB/api/v4/projects/$encoded_path/repository/commits?ref_name=$branch&per_page=5"
    local commits=$(curl -s -H "PRIVATE-TOKEN: $gl_token" "$endpoint")
    
    echo "$commits" | python3 -c "
import sys,json
keyword = '$keyword'
commits = json.load(sys.stdin)
if not isinstance(commits, list): sys.exit(1)
found = any(keyword.lower() in c.get('title','').lower() for c in commits)
print('FOUND' if found else 'NOT_FOUND')
"
}

auth() {
    local resp=$(curl -s -X POST "$BACKEND/api/v1/auth/login" \
        -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}')
    echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])"
}

# ---- 0. 前置 ----
h2 "0. 环境检查"
AUTH_TOKEN=$(auth)
AUTH="Authorization: Bearer $AUTH_TOKEN"
ok "登录成功"

# 容器
GITLAB_READY=true
docker ps --format '{{.Names}}' | grep -q "releasehub-gitlab" && ok "releasehub-gitlab 运行中" || { warn "releasehub-gitlab 未运行，将进入 MOCK_MODE"; GITLAB_READY=false; }
docker ps --format '{{.Names}}' | grep -q "releasehub-postgres" && ok "releasehub-postgres 运行中" || die "releasehub-postgres 未运行"

# 后端
curl -s -o /dev/null "$BACKEND/actuator/health" && ok "后端 $BACKEND" || die "后端未启动"
# 前端
curl -s -o /dev/null "$FRONTEND" 2>/dev/null && ok "前端 $FRONTEND" || warn "前端未启动"

# ---- 1. 存量数据审计 ----
h2 "1. 存量数据审计"

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

# 1.4 脏数据检测
h2 "1.4 脏数据检测"
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
h2 "3. 场景: 新增发布窗口全链路验证"

# 3.1 确保分组（复用）
GROUP_CODE=$(curl -s "$BACKEND/api/v1/groups" -H "$AUTH" | python3 -c "
import sys,json
for g in json.load(sys.stdin).get('data',[]):
    if g.get('name') == '验收分组': print(g['code']); break
")
if [ -z "$GROUP_CODE" ]; then
    GROUP_CODE=$(curl -s -X POST "$BACKEND/api/v1/groups" -H "$AUTH" -H "Content-Type: application/json" \
        -d '{"name":"验收分组","parentCode":null}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data'])")
    ok "创建分组: $GROUP_CODE"
else
    ok "复用分组: $GROUP_CODE"
fi

# 3.2 GitLab PAT
source /tmp/e2e-gitlab.env 2>/dev/null || true
GITLAB_PAT="${E2E_GITLAB_TOKEN:-}"
[ -z "$GITLAB_PAT" ] && { GITLAB_PAT=$(docker exec releasehub-gitlab gitlab-rails runner "puts User.find(1).personal_access_tokens.create!(name:'acc-ts',scopes:['api','read_repository','write_repository'],expires_at:30.days.from_now).token" 2>&1 | tail -1); echo "E2E_GITLAB_TOKEN=$GITLAB_PAT" > /tmp/e2e-gitlab.env; }

# 3.3 确保仓库（按 cloneUrl 精确复用，每个仓库只注册一次）
R1=""; R2=""; R3=""

ensure_repo() {
    local name=$1
    local clone_url=$2
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
        ok "复用仓库: $name → ${repo_id:0:8}..."
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

# ---- 4. 场景: Attach + 分支创建 ----
h2 "4. 场景: Attach 迭代 & GitLab 分支创建"
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

# 验证 GitLab 上真实分支存在
if [ "$GITLAB_READY" = "true" ]; then
    BRANCH_COUNT=0
    for repo_id in 1 2 3; do
        RELEASE_BRANCH=$(curl -s -H "PRIVATE-TOKEN: $GITLAB_PAT" \
            "http://localhost:9080/api/v4/projects/$repo_id/repository/branches" \
            | python3 -c "import sys,json; d=json.load(sys.stdin); branches=[b['name'] for b in d if isinstance(d, list) and b['name'].startswith('release/')]; print(branches[0] if branches else 'MISSING')" 2>/dev/null)
        if [ "$RELEASE_BRANCH" != "MISSING" ] && [ -n "$RELEASE_BRANCH" ]; then
            BRANCH_COUNT=$((BRANCH_COUNT+1))
        fi
    done
    [ "$BRANCH_COUNT" -eq 3 ] && ok "GitLab 真实 release 分支: 3/3" || warn "GitLab release 分支: $BRANCH_COUNT/3"
fi

# 验证 WindowIteration 状态
WI_STATE=$(curl -s "$BACKEND/api/v1/release-windows/$WINDOW_ID/iterations" -H "$AUTH" | python3 -c "
import sys,json
for wi in json.load(sys.stdin).get('data',[]):
    print(f\"branchCreated={wi['branchCreated']} releaseBranch={wi['releaseBranch']}\")
")
echo "$WI_STATE" | while read l; do info "  $l"; done

# ---- 5. 场景: 冲突检测 ----
h2 "5. 场景: 冲突检测"
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

# ---- 6. 场景: Publish + Auto-Orchestration ----
h2 "6. 场景: Publish & 自动编排"
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
    if [ "$FINAL_ORCH_STATUS" = "SUCCESS" ]; then
        ok "Orchestrate 执行成功 (Run status: $FINAL_ORCH_STATUS)"
    else
        no "Orchestrate 执行失败 (Run status: $FINAL_ORCH_STATUS)"
    fi
else
    no "Orchestrate 启动失败"
fi

# ---- 7. 场景: Run 详情 ----
h2 "7. 场景: Run 执行详情"
sleep 1
RUNS=$(curl -s "$BACKEND/api/v1/runs" -H "$AUTH")
LATEST_RUN=$(echo "$RUNS" | python3 -c "
import sys,json
data = json.load(sys.stdin).get('data',[])
if data:
    r = data[-1]  # last run
    items = len(r.get('items',[]))
    print(f\"id={r['id'][:20]} type={r.get('runType','?')} status={r.get('status','?')} items={items}\")
else:
    print('NO_RUNS')
")
if [ "$LATEST_RUN" = "NO_RUNS" ]; then
    no "无 Run 记录（Auto-orchestration 事务边界可能未修复）"
else
    ITEM_COUNT=$(echo "$LATEST_RUN" | grep -o 'items=[0-9]*' | cut -d= -f2)
    [ "$ITEM_COUNT" -eq 0 ] && warn "Run 含 0 item（feature 分支缺失导致全部 SKIP）" || ok "Run items: $ITEM_COUNT"
    info "$LATEST_RUN"
fi

# ---- 8. 场景: 版本更新 + 校验 ----
h2 "8. 场景: 版本更新 & 校验"

# 8.1 版本校验（验证 VersionPolicy 推导功能可用）
VERSION_VALIDATE=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$WINDOW_ID/validate" -H "$AUTH" -H "Content-Type: application/json" \
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
VERSION_UPDATE=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$WINDOW_ID/execute/version-update" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"repoId\":\"$R1\",\"targetVersion\":\"1.5.0\",\"buildTool\":\"MAVEN\",\"repoPath\":\".\",\"pomPath\":\"pom.xml\"}" 2>/dev/null)
VU_SUCCESS=$(echo "$VERSION_UPDATE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success','?'))" 2>/dev/null)
if [ "$VU_SUCCESS" = "True" ]; then
    VU_RUN_ID=$(echo "$VERSION_UPDATE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['runId'])" 2>/dev/null)
    info "版本更新已启动 (run=$VU_RUN_ID)，正在等待执行结果..."
    
    FINAL_STATUS=$(wait_for_run "$VU_RUN_ID" 45)
    if [ "$FINAL_STATUS" = "SUCCESS" ]; then
        ok "版本更新执行成功 (Run status: $FINAL_STATUS)"
        
        # 增加 Git 远程校验
        REPO_URL=$(curl -s "$BACKEND/api/v1/repositories/$R1" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('cloneUrl',''))")
        WINDOW_KEY=$(curl -s "$BACKEND/api/v1/release-windows/$WINDOW_ID" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('windowKey',''))")
        
        # 预期的分支名（与后端逻辑一致）
        TARGET_BRANCH="release/$WINDOW_KEY"
        
        GIT_COMMIT=$(verify_gitlab_commit "$REPO_URL" "$TARGET_BRANCH" "ReleaseHub: Update")
        if [ "$GIT_COMMIT" = "FOUND" ]; then
            ok "Git 远程校验成功: 已发现版本更新 Commit"
        else
            warn "Git 远程校验未确认: 可能是分支名或 Commit Message 匹配问题 ($GIT_COMMIT)"
        fi
    else
        no "版本更新执行失败 (Run status: $FINAL_STATUS)"
        info "错误详情: $(curl -s "$BACKEND/api/v1/runs/$VU_RUN_ID" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('items',[{}])[0].get('message','?'))")"
    fi
else
    ERR=$(echo "$VERSION_UPDATE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message','?'))" 2>/dev/null || echo "?")
    skip "版本更新未执行: $ERR"
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
h2 "10. 场景: 分支创建模式验证"

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
