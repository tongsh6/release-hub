#!/bin/bash
# SA-006 focused acceptance: BranchRule constrains real GitLab branch creation.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND="${BACKEND_URL:-http://localhost:8080}"
GITLAB="${GITLAB_URL:-http://localhost:9080}"
TS=$(date -u +%Y%m%d-%H%M%S)
PASS=0
FAIL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

ok() { echo -e "  ${GREEN}[PASS]${NC} $*"; PASS=$((PASS + 1)); }
no() { echo -e "  ${RED}[FAIL]${NC} $*"; FAIL=$((FAIL + 1)); }
info() { echo -e "  ${CYAN}[INFO]${NC} $*"; }

json_get() {
    local expr=$1
    python3 -c "import sys,json; d=json.load(sys.stdin); print($expr)"
}

api() {
    local method=$1
    local path=$2
    local body=${3:-}
    if [ -n "$body" ]; then
        curl -s -X "$method" "$BACKEND$path" -H "$AUTH" -H "Content-Type: application/json" -d "$body"
    else
        curl -s -X "$method" "$BACKEND$path" -H "$AUTH"
    fi
}

gitlab_encode() {
    python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.stdin.read().strip(), safe=''))"
}

gitlab_project_path_from_clone_url() {
    echo "$1" | sed -E 's|https?://[^/]+/||; s|\.git$||'
}

gitlab_branch_state() {
    local clone_url=$1
    local branch=$2
    local project_path
    project_path=$(gitlab_project_path_from_clone_url "$clone_url")
    local encoded_path
    encoded_path=$(echo "$project_path" | gitlab_encode)
    local encoded_branch
    encoded_branch=$(echo "$branch" | gitlab_encode)
    local resp
    resp=$(curl -s -H "PRIVATE-TOKEN: $GITLAB_PAT" \
        "$GITLAB/api/v4/projects/$encoded_path/repository/branches/$encoded_branch")
    echo "$resp" | python3 -c "
import sys,json
branch = '''$branch'''
try:
    d = json.load(sys.stdin)
except Exception:
    print('NON_JSON_RESPONSE')
    raise SystemExit
if isinstance(d, dict) and d.get('name') == branch:
    print('FOUND')
elif isinstance(d, dict) and d.get('message'):
    print('NOT_FOUND')
else:
    print('UNKNOWN')
"
}

echo -e "${CYAN}=== SA-006 BranchRule GitLab evidence ===${NC}"

if ! curl -s -o /dev/null "$BACKEND/actuator/health"; then
    no "后端未就绪: $BACKEND"
    exit 1
fi

INIT_SH="$PROJECT_ROOT/scripts/e2e/init-gitlab.sh"
GITLAB_URL="$GITLAB" ROOT_PASS=releasehub123 bash "$INIT_SH" >/tmp/sa006-init-gitlab.log 2>&1
source /tmp/e2e-gitlab.env
GITLAB_PAT="${E2E_GITLAB_TOKEN:-}"
if [ -z "$GITLAB_PAT" ]; then
    no "无法取得 GitLab PAT"
    exit 1
fi
ok "GitLab 种子数据就绪"

AUTH_TOKEN=$(curl -s -X POST "$BACKEND/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin"}' | json_get "d['data']['token']")
AUTH="Authorization: Bearer $AUTH_TOKEN"
ok "后端登录成功"

GROUP_CODE="SA006${TS//[-:]/}"
GROUP_RESP=$(api POST "/api/v1/groups" "{\"name\":\"SA-006 验收-$TS\",\"code\":\"$GROUP_CODE\",\"parentCode\":null}")
GROUP_ID=$(echo "$GROUP_RESP" | json_get "d.get('data','')")
if [ -z "$GROUP_ID" ]; then
    no "创建 SA-006 验收分组失败: $GROUP_RESP"
    exit 1
fi
ok "创建独立叶子分组: $GROUP_CODE"

PROJECT_PATH="sa006-evidence-$TS"
PROJECT_RESP=$(curl -s -X POST "$GITLAB/api/v4/projects" \
    -H "PRIVATE-TOKEN: $GITLAB_PAT" \
    --data-urlencode "name=$PROJECT_PATH" \
    --data-urlencode "path=$PROJECT_PATH" \
    --data-urlencode "visibility=private" \
    --data-urlencode "initialize_with_readme=true" \
    --data-urlencode "default_branch=main")
PROJECT_NAMESPACE=$(echo "$PROJECT_RESP" | json_get "d.get('path_with_namespace','')")
if [ -z "$PROJECT_NAMESPACE" ]; then
    no "创建 GitLab 验收项目失败: $PROJECT_RESP"
    exit 1
fi
REPO_CLONE="http://localhost:9080/${PROJECT_NAMESPACE}.git"
ok "创建独立 GitLab 验收项目: $PROJECT_NAMESPACE"

REPO_RESP=$(api POST "/api/v1/repositories" "{\"name\":\"SA-006 Repo $TS\",\"cloneUrl\":\"$REPO_CLONE\",\"defaultBranch\":\"main\",\"groupCode\":\"$GROUP_CODE\",\"gitProvider\":\"GITLAB\",\"gitAccessToken\":\"$GITLAB_PAT\",\"initialVersion\":\"1.4.0\"}")
REPO_ID=$(echo "$REPO_RESP" | json_get "((d.get('data') or {}).get('id') or '')")
if [ -z "$REPO_ID" ]; then
    no "创建 SA-006 仓库失败: $REPO_RESP"
    exit 1
fi
ok "创建真实 GitLab 仓库引用: $REPO_ID"

ALLOWED_BRANCH="feature/sa006-allowed-$TS"
DENIED_BRANCH="feature/sa006-denied-$TS"
RULE_PATTERN="^${ALLOWED_BRANCH}$"

RULE_RESP=$(api POST "/api/v1/branch-rules" "{\"name\":\"SA-006 scoped rule $TS\",\"pattern\":\"$RULE_PATTERN\",\"type\":\"REGEX\",\"description\":\"SA-006 focused GitLab evidence\",\"scopeLevel\":\"PROJECT\",\"scopeProjectId\":\"$GROUP_CODE\",\"scopeSubProjectId\":null}")
RULE_ID=$(echo "$RULE_RESP" | json_get "((d.get('data') or {}).get('id') or '')")
if [ -z "$RULE_ID" ]; then
    no "创建项目级 BranchRule 失败: $RULE_RESP"
    exit 1
fi
ok "创建项目级 BranchRule: $RULE_PATTERN"

ITER_OK_RESP=$(api POST "/api/v1/iterations" "{\"name\":\"SA-006 合规 NAMED $TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[]}")
ITER_OK=$(echo "$ITER_OK_RESP" | json_get "((d.get('data') or {}).get('key') or '')")
ADD_OK_RESP=$(api POST "/api/v1/iterations/$ITER_OK/repos/add" "{\"repoIds\":[\"$REPO_ID\"],\"branchCreationMode\":\"NAMED\",\"customBranchName\":\"$ALLOWED_BRANCH\"}")
ADD_OK_SUCCESS=$(echo "$ADD_OK_RESP" | json_get "d.get('success', False)")
if [ "$ADD_OK_SUCCESS" = "True" ]; then
    ok "合规 NAMED 分支写入成功"
else
    no "合规 NAMED 分支写入失败: $ADD_OK_RESP"
fi

VINFO=$(api GET "/api/v1/iterations/$ITER_OK/repos/$REPO_ID/version-info")
RECORDED_BRANCH=$(echo "$VINFO" | json_get "((d.get('data') or {}).get('featureBranch') or '')")
if [ "$RECORDED_BRANCH" = "$ALLOWED_BRANCH" ]; then
    ok "系统记录合规 featureBranch: $RECORDED_BRANCH"
else
    no "系统 featureBranch 记录异常: $VINFO"
fi

ALLOWED_STATE=$(gitlab_branch_state "$REPO_CLONE" "$ALLOWED_BRANCH")
if [ "$ALLOWED_STATE" = "FOUND" ]; then
    ok "GitLab 直查确认合规 NAMED 分支已创建: $ALLOWED_BRANCH"
else
    no "GitLab 合规 NAMED 分支状态异常: $ALLOWED_STATE"
fi

ITER_BAD_RESP=$(api POST "/api/v1/iterations" "{\"name\":\"SA-006 非法 NAMED $TS\",\"groupCode\":\"$GROUP_CODE\",\"repoIds\":[]}")
ITER_BAD=$(echo "$ITER_BAD_RESP" | json_get "((d.get('data') or {}).get('key') or '')")
ADD_BAD_RESP=$(api POST "/api/v1/iterations/$ITER_BAD/repos/add" "{\"repoIds\":[\"$REPO_ID\"],\"branchCreationMode\":\"NAMED\",\"customBranchName\":\"$DENIED_BRANCH\"}")
ADD_BAD_SUCCESS=$(echo "$ADD_BAD_RESP" | json_get "d.get('success', False)")
if [ "$ADD_BAD_SUCCESS" = "False" ]; then
    ok "不合规 NAMED 分支被写入前拒绝"
else
    no "不合规 NAMED 分支未被拒绝: $ADD_BAD_RESP"
fi

BAD_REPOS=$(api GET "/api/v1/iterations/$ITER_BAD/repos")
BAD_REPO_PRESENT=$(echo "$BAD_REPOS" | json_get "'FOUND' if '$REPO_ID' in (d.get('data') or []) else 'NOT_FOUND'")
if [ "$BAD_REPO_PRESENT" = "NOT_FOUND" ]; then
    ok "不合规 NAMED 未写入迭代仓库集合"
else
    no "不合规 NAMED 仍写入迭代仓库集合: $BAD_REPOS"
fi

DENIED_STATE=$(gitlab_branch_state "$REPO_CLONE" "$DENIED_BRANCH")
if [ "$DENIED_STATE" = "NOT_FOUND" ]; then
    ok "GitLab 直查确认不合规 NAMED 分支未创建: $DENIED_BRANCH"
else
    no "GitLab 不合规 NAMED 分支状态异常: $DENIED_STATE"
fi

WINDOW_RESP=$(api POST "/api/v1/release-windows" "{\"name\":\"SA-006 release 拒绝 $TS\",\"description\":\"SA-006 release branch rule evidence\",\"groupCode\":\"$GROUP_CODE\"}")
WINDOW_ID=$(echo "$WINDOW_RESP" | json_get "((d.get('data') or {}).get('id') or '')")
RELEASE_WINDOW_KEY=$(echo "$WINDOW_RESP" | json_get "((d.get('data') or {}).get('windowKey') or '')")
RELEASE_BRANCH="release/$RELEASE_WINDOW_KEY"
RELEASE_RESP=$(api POST "/api/v1/release-windows/$WINDOW_ID/iterations/$ITER_OK/create-release-branch" "")
RELEASE_SUCCESS=$(echo "$RELEASE_RESP" | json_get "d.get('success', False)")
if [ "$RELEASE_SUCCESS" = "False" ]; then
    ok "不合规 release 分支被创建前拒绝"
else
    no "不合规 release 分支未被拒绝: $RELEASE_RESP"
fi

RELEASE_STATE=$(gitlab_branch_state "$REPO_CLONE" "$RELEASE_BRANCH")
if [ "$RELEASE_STATE" = "NOT_FOUND" ]; then
    ok "GitLab 直查确认不合规 release 分支未创建: $RELEASE_BRANCH"
else
    no "GitLab release 分支状态异常: $RELEASE_STATE"
fi

api DELETE "/api/v1/branch-rules/$RULE_ID" >/dev/null || true

echo ""
echo "SA-006 focused result: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
