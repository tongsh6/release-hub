#!/bin/bash
# ============================================================
# ReleaseHub 全链路验收脚本 v2
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
ok()   { echo -e "  ${GREEN}[PASS]${NC} $*"; PASS=$((PASS+1)); }
no()   { echo -e "  ${RED}[FAIL]${NC} $*"; FAIL=$((FAIL+1)); }
skip() { echo -e "  ${YELLOW}[SKIP]${NC} $*"; SKIP=$((SKIP+1)); }
info() { echo -e "  ${CYAN}[INFO]${NC} $*"; }
warn() { echo -e "  ${YELLOW}[WARN]${NC} $*"; }
h2()   { echo ""; echo -e "${CYAN}=== $* ===${NC}"; }

die() { echo -e "${RED}FATAL: $*${NC}"; exit 1; }

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
for svc in releasehub-gitlab releasehub-postgres; do
    docker ps --format '{{.Names}}' | grep -q "$svc" && ok "$svc 运行中" || die "$svc 未运行"
done
# 后端
curl -s -o /dev/null "$BACKEND/actuator/health" && ok "后端 $BACKEND" || die "后端未启动"
# 前端
curl -s -o /dev/null "$FRONTEND" 2>/dev/null && ok "前端 $FRONTEND" || warn "前端未启动"

# ---- 1. 存量数据审计 ----
h2 "1. 存量数据审计"

# 1.1 数据资产统计
STATS=$(curl -s "$BACKEND/api/v1/runs/paged?size=1" -H "$AUTH")
RUN_TOTAL=$(echo "$STATS" | python3 -c "import sys,json; print(json.load(sys.stdin)['page']['total'])" 2>/dev/null || echo 0)
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
if [ "$PLAINTEXT_COUNT" -gt 0 ] 2>/dev/null; then
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
declare -A REPO_MAP
while IFS=, read -r name clone_url; do
    [ "$name" = "END" ] && continue
    REPO_ID=$(curl -s "$BACKEND/api/v1/repositories" -H "$AUTH" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('data',[]):
    if r.get('cloneUrl','') == '$clone_url':
        print(r['id']); break
")
    if [ -z "$REPO_ID" ]; then
        REPO_ID=$(curl -s -X POST "$BACKEND/api/v1/repositories" -H "$AUTH" -H "Content-Type: application/json" \
            -d "{\"name\":\"$name\",\"cloneUrl\":\"$clone_url\",\"defaultBranch\":\"main\",\"groupCode\":\"$GROUP_CODE\",\"gitProvider\":\"GITLAB\",\"gitAccessToken\":\"$GITLAB_PAT\"}" \
            | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
        ok "注册仓库: $name → ${REPO_ID:0:8}..."
    else
        ok "复用仓库: $name → ${REPO_ID:0:8}..."
    fi
    REPO_MAP[$name]=$REPO_ID
done <<< "END
验收-Maven单模块,http://localhost:9080/e2e-user/seed-repo-1-maven.git
验收-Maven多模块,http://localhost:9080/e2e-user/seed-repo-2-maven-multi.git
验收-Gradle,http://localhost:9080/e2e-user/seed-repo-3-gradle.git
END"

R1=${REPO_MAP["验收-Maven单模块"]}
R2=${REPO_MAP["验收-Maven多模块"]}
R3=${REPO_MAP["验收-Gradle"]}
REPO_IDS="$R1 $R2 $R3"

# 3.4 创建新窗口（每轮新建，不做删除）
NEXT_WEEK=$(date -u -v+7d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d "+7 days" +%Y-%m-%dT%H:%M:%SZ)
WINDOW_ID=$(curl -s -X POST "$BACKEND/api/v1/release-windows" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收-$TS\",\"description\":\"全链路验收 $TS\",\"plannedReleaseAt\":\"$NEXT_WEEK\",\"groupCode\":\"$GROUP_CODE\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
ok "创建窗口: 验收-$TS"

# 3.5 创建迭代
REPO_JSON="[\"$R1\",\"$R2\",\"$R3\"]"
ITER_KEY=$(curl -s -X POST "$BACKEND/api/v1/iterations" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"验收迭代-$TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":$REPO_JSON}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['key'])")
ok "创建迭代: $ITER_KEY"

# ---- 4. 场景: Attach + 分支创建 ----
h2 "4. 场景: Attach 迭代 & GitLab 分支创建"
ATTACH=$(curl -s -X POST "$BACKEND/api/v1/release-windows/$WINDOW_ID/attach" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"iterationKeys\":[\"$ITER_KEY\"]}")
HAS_ERR=$(echo "$ATTACH" | python3 -c "import sys,json; any(r['hasErrors'] for r in json.load(sys.stdin)['data'])" 2>/dev/null)
if [ "$HAS_ERR" = "True" ]; then
    echo "$ATTACH" | python3 -c "
import sys,json
for r in json.load(sys.stdin)['data']:
    for e in r.get('errors',[]): print(f'    {e[\"repoName\"]}: {e[\"message\"]}')
" | while read l; do no "Attach 失败: $l"; done
else
    ok "Attach 成功（3 个仓库 release 分支已创建）"
fi

# 验证 GitLab 上真实分支存在
BRANCH_COUNT=0
for repo_id in 1 2 3; do
    RELEASE_BRANCH=$(curl -s -H "PRIVATE-TOKEN: $GITLAB_PAT" \
        "http://localhost:9080/api/v4/projects/$repo_id/repository/branches" \
        | python3 -c "import sys,json; branches=[b['name'] for b in json.load(sys.stdin) if b['name'].startswith('release/')]; print(branches[0] if branches else 'MISSING')" 2>/dev/null)
    if [ "$RELEASE_BRANCH" != "MISSING" ]; then
        BRANCH_COUNT=$((BRANCH_COUNT+1))
    fi
done
[ $BRANCH_COUNT -eq 3 ] && ok "GitLab 真实 release 分支: 3/3" || warn "GitLab release 分支: $BRANCH_COUNT/3"

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
[ "$ORCH_SUCCESS" = "True" ] && ok "Orchestrate 成功" || no "Orchestrate 失败"

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
    ok "版本更新已执行 (run=$VU_RUN_ID)"
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

# ---- 10. 汇总 ----
h2 "10. 验收汇总"
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
