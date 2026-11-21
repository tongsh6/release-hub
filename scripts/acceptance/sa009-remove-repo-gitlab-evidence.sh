#!/bin/bash
# SA-009 focused acceptance: iteration configuration never writes Git; removing a repo retains its existing feature branch.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND="${BACKEND_URL:-http://localhost:8080}"
GITLAB="${GITLAB_URL:-http://localhost:9080}"
TS=$(date -u +%Y%m%d-%H%M%S)
PASS=0
FAIL=0
ENABLED_RULE_IDS=""
RULES_RESTORED=0

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

restore_branch_rules() {
    set +e
    if [ "$RULES_RESTORED" != "1" ] && [ -n "$ENABLED_RULE_IDS" ]; then
        while IFS= read -r rule_id; do
            [ -n "$rule_id" ] && api POST "/api/v1/branch-rules/$rule_id/enable" "{}" >/dev/null
        done <<< "$ENABLED_RULE_IDS"
    fi
}

gitlab_encode() {
    python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.stdin.read().strip(), safe=''))"
}

gitlab_project_path_from_clone_url() {
    echo "$1" | sed -E 's|https?://[^/]+/||; s|\.git$||'
}

gitlab_branch_state() {
    local clone_url=$1 branch=$2 output=${3:-state}
    local encoded_path encoded_branch response
    encoded_path=$(gitlab_project_path_from_clone_url "$clone_url" | gitlab_encode)
    encoded_branch=$(echo "$branch" | gitlab_encode)
    response=$(curl -sS -w '\n%{http_code}' -H "PRIVATE-TOKEN: $GITLAB_PAT" \
        "$GITLAB/api/v4/projects/$encoded_path/repository/branches/$encoded_branch") || { echo "UNAVAILABLE"; return; }
    printf '%s' "$response" | python3 -c '
import sys,json,re
branch,output=sys.argv[1:]
body,_,status=sys.stdin.read().rpartition("\n")
if status=="404":
    print("NOT_FOUND")
elif status!="200":
    print("HTTP_"+status)
else:
    try:
        d=json.loads(body); sha=(d.get("commit") or {}).get("id","")
        valid=d.get("name")==branch and re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}",sha)
        print((sha if output=="sha" else "FOUND") if valid else "INVALID_REF_EVIDENCE")
    except (ValueError,TypeError,AttributeError):
        print("INVALID_REF_EVIDENCE")
' "$branch" "$output"
}

gitlab_branch_sha() {
    gitlab_branch_state "$1" "$2" sha
}

enabled_branch_rule_ids() {
    api GET "/api/v1/branch-rules" | python3 -c "
import sys,json
for rule in json.load(sys.stdin).get('data', []):
    if rule.get('status') == 'ENABLED':
        print(rule.get('id',''))
"
}

create_acceptance_group() {
    local code=$1
    local name=$2
    local resp
    resp=$(api POST "/api/v1/groups" "{\"name\":\"$name\",\"code\":\"$code\",\"parentCode\":null}")
    echo "$resp" | json_get "d.get('data','')"
}

create_gitlab_project() {
    local path=$1 response
    response=$(curl -sS -w '\n%{http_code}' -X POST "$GITLAB/api/v4/projects" \
        -H "PRIVATE-TOKEN: $GITLAB_PAT" \
        --data-urlencode "name=$path" --data-urlencode "path=$path" \
        --data-urlencode "visibility=private" --data-urlencode "initialize_with_readme=true" \
        --data-urlencode "default_branch=main")
    printf '%s' "$response" | python3 -c '
import sys,json
body,_,status=sys.stdin.read().rpartition("\n")
d=json.loads(body)
assert status=="201" and isinstance(d.get("id"),int) and d["id"]>0
assert d.get("path")==sys.argv[1] and d.get("path_with_namespace")
print(str(d["id"])+"|"+d["path_with_namespace"])
' "$path"
}

# Explicit external fixture in this script-created project; it is not an iteration action or a UI journey.
prepare_feature_fixture() {
    local project_id=$1 namespace=$2 branch=$3 identity response
    identity=$(curl -sS -H "PRIVATE-TOKEN: $GITLAB_PAT" "$GITLAB/api/v4/projects/$project_id")
    printf '%s' "$identity" | python3 -c '
import sys,json
d=json.load(sys.stdin)
assert str(d.get("id"))==sys.argv[1] and d.get("path_with_namespace")==sys.argv[2]
' "$project_id" "$namespace" || return 1
    response=$(curl -sS -w '\n%{http_code}' -X POST \
        "$GITLAB/api/v4/projects/$project_id/repository/branches" \
        -H "PRIVATE-TOKEN: $GITLAB_PAT" --data-urlencode "branch=$branch" --data-urlencode "ref=main")
    printf '%s' "$response" | python3 -c '
import sys,json,re
body,_,status=sys.stdin.read().rpartition("\n")
d=json.loads(body); sha=(d.get("commit") or {}).get("id","")
assert status=="201" and d.get("name")==sys.argv[1]
assert re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}",sha)
' "$branch"
}

create_repo_ref() {
    local name=$1
    local clone_url=$2
    local group_code=$3
    local resp
    resp=$(api POST "/api/v1/repositories" "{\"name\":\"$name\",\"cloneUrl\":\"$clone_url\",\"defaultBranch\":\"main\",\"groupCode\":\"$group_code\",\"gitProvider\":\"GITLAB\",\"gitAccessToken\":\"$GITLAB_PAT\",\"initialVersion\":\"1.4.0\"}")
    echo "$resp" | json_get "((d.get('data') or {}).get('id') or '')"
}

create_iteration_with_repo() {
    local name=$1
    local group_code=$2
    local repo_id=$3
    local resp
    resp=$(api POST "/api/v1/iterations" "{\"name\":\"$name\",\"groupCode\":\"$group_code\",\"repoIds\":[\"$repo_id\"]}")
    echo "$resp" | json_get "((d.get('data') or {}).get('key') or '')"
}

archive_branch_name() {
    local feature_branch=$1
    echo "archive/unpublished/${feature_branch//\//-}"
}

assert_repo_absent_from_iteration() {
    local iteration_key=$1
    local repo_id=$2
    local repos present
    repos=$(api GET "/api/v1/iterations/$iteration_key/repos")
    present=$(echo "$repos" | json_get "'FOUND' if '$repo_id' in (d.get('data') or []) else 'NOT_FOUND'")
    if [ "$present" = "NOT_FOUND" ]; then
        ok "迭代仓库集合已移除仓库: $repo_id"
    else
        no "迭代仓库集合仍包含仓库: $repos"
    fi
}

assert_repo_present_in_iteration() {
    local iteration_key=$1
    local repo_id=$2
    local repos present
    repos=$(api GET "/api/v1/iterations/$iteration_key/repos")
    present=$(echo "$repos" | json_get "'FOUND' if '$repo_id' in (d.get('data') or []) else 'NOT_FOUND'")
    if [ "$present" = "FOUND" ]; then
        ok "挂窗迭代仓库集合保持不变: $repo_id"
    else
        no "挂窗迭代仓库集合异常丢失仓库: $repos"
    fi
}

echo -e "${CYAN}=== SA-009 Iteration configuration and retained GitLab branch evidence ===${NC}"

if ! curl -s -o /dev/null "$BACKEND/actuator/health"; then
    no "后端未就绪: $BACKEND"
    exit 1
fi

INIT_SH="$PROJECT_ROOT/scripts/e2e/init-gitlab.sh"
GITLAB_URL="$GITLAB" ROOT_PASS=releasehub123 bash "$INIT_SH" >/tmp/sa009-init-gitlab.log 2>&1
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
trap restore_branch_rules EXIT

ENABLED_RULE_IDS=$(enabled_branch_rule_ids)
while IFS= read -r rule_id; do
    [ -n "$rule_id" ] && api POST "/api/v1/branch-rules/$rule_id/disable" "{}" >/dev/null
done <<< "$ENABLED_RULE_IDS"
ok "临时禁用现有 BranchRule，避免验收数据受历史规则干扰"

GROUP_CODE="SA009${TS//[-:]/}"
GROUP_ID=$(create_acceptance_group "$GROUP_CODE" "SA-009 验收-$TS")
if [ -z "$GROUP_ID" ]; then
    no "创建 SA-009 验收分组失败"
    exit 1
fi
ok "创建独立叶子分组: $GROUP_CODE"

IFS='|' read -r REMOVABLE_PROJECT_ID REMOVABLE_PROJECT <<< "$(create_gitlab_project "sa009-removable-$TS")"
[ -n "$REMOVABLE_PROJECT_ID" ] && [ -n "$REMOVABLE_PROJECT" ] || { no "创建独立 GitLab 项目失败"; exit 1; }
REMOVABLE_CLONE="http://localhost:9080/${REMOVABLE_PROJECT}.git"
REMOVABLE_REPO_ID=$(create_repo_ref "SA-009 可移除仓库 $TS" "$REMOVABLE_CLONE" "$GROUP_CODE")
if [ -z "$REMOVABLE_REPO_ID" ]; then
    no "创建可移除仓库引用失败"
    exit 1
fi
ok "创建真实 GitLab 可移除仓库引用: $REMOVABLE_REPO_ID"

REMOVABLE_ITER=$(create_iteration_with_repo "SA-009 未挂窗移除 $TS" "$GROUP_CODE" "$REMOVABLE_REPO_ID")
if [ -z "$REMOVABLE_ITER" ]; then
    no "创建未挂窗迭代失败"
    exit 1
fi
ok "创建未挂窗迭代: $REMOVABLE_ITER"

REMOVABLE_VINFO=$(api GET "/api/v1/iterations/$REMOVABLE_ITER/repos/$REMOVABLE_REPO_ID/version-info")
REMOVABLE_FEATURE=$(echo "$REMOVABLE_VINFO" | json_get "((d.get('data') or {}).get('featureBranch') or '')")
REMOVABLE_ARCHIVE=$(archive_branch_name "$REMOVABLE_FEATURE")
if [ -n "$REMOVABLE_FEATURE" ]; then
    ok "系统记录 featureBranch: $REMOVABLE_FEATURE"
else
    no "未取得 featureBranch: $REMOVABLE_VINFO"
fi

CONFIG_STATE=$(gitlab_branch_state "$REMOVABLE_CLONE" "$REMOVABLE_FEATURE")
if [ "$CONFIG_STATE" = "NOT_FOUND" ]; then
    ok "创建迭代只保存计划，未创建 GitLab feature: $REMOVABLE_FEATURE"
else
    no "迭代配置意外写 Git 或读取证据异常: $CONFIG_STATE"
    exit 1
fi
prepare_feature_fixture "$REMOVABLE_PROJECT_ID" "$REMOVABLE_PROJECT" "$REMOVABLE_FEATURE" \
    || { no "无法在已确认项目准备已有分支 fixture"; exit 1; }
SHA_BEFORE=$(gitlab_branch_sha "$REMOVABLE_CLONE" "$REMOVABLE_FEATURE")
ARCHIVE_BEFORE=$(gitlab_branch_state "$REMOVABLE_CLONE" "$REMOVABLE_ARCHIVE")
[[ "$SHA_BEFORE" =~ ^([0-9a-fA-F]{40}|[0-9a-fA-F]{64})$ ]] \
    && [ "$ARCHIVE_BEFORE" = "NOT_FOUND" ] \
    || { no "移除前分支/SHA/归档证据不完整"; exit 1; }
ok "外部 fixture 已准备；未挂窗原 feature 分支及 SHA 已确认"

REMOVE_RESP=$(api POST "/api/v1/iterations/$REMOVABLE_ITER/repos/remove" "{\"repoIds\":[\"$REMOVABLE_REPO_ID\"]}")
REMOVE_SUCCESS=$(echo "$REMOVE_RESP" | json_get "d.get('success', False)")
if [ "$REMOVE_SUCCESS" = "True" ]; then
    ok "未挂窗迭代移除仓库 API 成功"
else
    no "未挂窗迭代移除仓库 API 失败: $REMOVE_RESP"
fi

assert_repo_absent_from_iteration "$REMOVABLE_ITER" "$REMOVABLE_REPO_ID"

SHA_AFTER=$(gitlab_branch_sha "$REMOVABLE_CLONE" "$REMOVABLE_FEATURE")
ARCHIVE_STATE=$(gitlab_branch_state "$REMOVABLE_CLONE" "$REMOVABLE_ARCHIVE")
if [ "$SHA_AFTER" = "$SHA_BEFORE" ]; then
    ok "移除后原 feature 分支保留且 SHA 不变: $REMOVABLE_FEATURE"
else
    no "移除后 feature 分支被更改或读取证据异常: $SHA_AFTER"
fi
if [ "$ARCHIVE_STATE" = "NOT_FOUND" ]; then
    ok "移除没有产生归档分支: $REMOVABLE_ARCHIVE"
else
    no "移除意外归档或读取证据异常: $ARCHIVE_STATE"
fi

IFS='|' read -r LOCKED_PROJECT_ID LOCKED_PROJECT <<< "$(create_gitlab_project "sa009-locked-$TS")"
[ -n "$LOCKED_PROJECT_ID" ] && [ -n "$LOCKED_PROJECT" ] || { no "创建挂窗 fixture 项目失败"; exit 1; }
LOCKED_CLONE="http://localhost:9080/${LOCKED_PROJECT}.git"
LOCKED_REPO_ID=$(create_repo_ref "SA-009 挂窗仓库 $TS" "$LOCKED_CLONE" "$GROUP_CODE")
if [ -z "$LOCKED_REPO_ID" ]; then
    no "创建挂窗仓库引用失败"
    exit 1
fi
ok "创建真实 GitLab 挂窗仓库引用: $LOCKED_REPO_ID"

LOCKED_ITER=$(create_iteration_with_repo "SA-009 挂窗锁定 $TS" "$GROUP_CODE" "$LOCKED_REPO_ID")
if [ -z "$LOCKED_ITER" ]; then
    no "创建挂窗迭代失败"
    exit 1
fi
ok "创建挂窗迭代: $LOCKED_ITER"

LOCKED_VINFO=$(api GET "/api/v1/iterations/$LOCKED_ITER/repos/$LOCKED_REPO_ID/version-info")
LOCKED_FEATURE=$(echo "$LOCKED_VINFO" | json_get "((d.get('data') or {}).get('featureBranch') or '')")
LOCKED_ARCHIVE=$(archive_branch_name "$LOCKED_FEATURE")
LOCKED_CONFIG_STATE=$(gitlab_branch_state "$LOCKED_CLONE" "$LOCKED_FEATURE")
[ "$LOCKED_CONFIG_STATE" = "NOT_FOUND" ] \
    || { no "挂窗 fixture 的迭代配置意外写 Git: $LOCKED_CONFIG_STATE"; exit 1; }
ok "挂窗前创建迭代仍未写 Git"
prepare_feature_fixture "$LOCKED_PROJECT_ID" "$LOCKED_PROJECT" "$LOCKED_FEATURE" \
    || { no "无法准备挂窗保护的已有分支 fixture"; exit 1; }
LOCKED_SHA_BEFORE=$(gitlab_branch_sha "$LOCKED_CLONE" "$LOCKED_FEATURE")
[[ "$LOCKED_SHA_BEFORE" =~ ^([0-9a-fA-F]{40}|[0-9a-fA-F]{64})$ ]] \
    || { no "挂窗前 feature SHA 证据不完整"; exit 1; }

WINDOW_RESP=$(api POST "/api/v1/release-windows" "{\"name\":\"SA-009 挂窗保护 $TS\",\"description\":\"SA-009 remove repo lock evidence\",\"groupCode\":\"$GROUP_CODE\"}")
WINDOW_ID=$(echo "$WINDOW_RESP" | json_get "((d.get('data') or {}).get('id') or '')")
if [ -z "$WINDOW_ID" ]; then
    no "创建发布窗口失败: $WINDOW_RESP"
    exit 1
fi
ok "创建发布窗口: $WINDOW_ID"

ATTACH_RESP=$(api POST "/api/v1/release-windows/$WINDOW_ID/attach" "{\"iterationKeys\":[\"$LOCKED_ITER\"]}")
ATTACH_SUCCESS=$(echo "$ATTACH_RESP" | json_get "d.get('success', False)")
if [ "$ATTACH_SUCCESS" = "True" ]; then
    ok "迭代挂载发布窗口成功"
else
    no "迭代挂载发布窗口异常: $ATTACH_RESP"
fi

LOCKED_REMOVE_RESP=$(api POST "/api/v1/iterations/$LOCKED_ITER/repos/remove" "{\"repoIds\":[\"$LOCKED_REPO_ID\"]}")
LOCKED_REMOVE_SUCCESS=$(echo "$LOCKED_REMOVE_RESP" | json_get "d.get('success', False)")
if [ "$LOCKED_REMOVE_SUCCESS" = "False" ]; then
    ok "已挂窗迭代拒绝移除仓库"
else
    no "已挂窗迭代未拒绝移除仓库: $LOCKED_REMOVE_RESP"
fi

assert_repo_present_in_iteration "$LOCKED_ITER" "$LOCKED_REPO_ID"

LOCKED_AFTER=$(gitlab_branch_sha "$LOCKED_CLONE" "$LOCKED_FEATURE")
LOCKED_ARCHIVE_STATE=$(gitlab_branch_state "$LOCKED_CLONE" "$LOCKED_ARCHIVE")
if [ "$LOCKED_AFTER" = "$LOCKED_SHA_BEFORE" ]; then
    ok "拒绝移除后 GitLab 原 feature 分支保留且 SHA 不变: $LOCKED_FEATURE"
else
    no "拒绝移除后 GitLab 原 feature 分支状态异常: $LOCKED_AFTER"
fi
if [ "$LOCKED_ARCHIVE_STATE" = "NOT_FOUND" ]; then
    ok "拒绝移除后未产生归档分支: $LOCKED_ARCHIVE"
else
    no "拒绝移除后归档分支不应存在: $LOCKED_ARCHIVE_STATE ($LOCKED_ARCHIVE)"
fi

while IFS= read -r rule_id; do
    [ -n "$rule_id" ] && api POST "/api/v1/branch-rules/$rule_id/enable" "{}" >/dev/null
done <<< "$ENABLED_RULE_IDS"
RULES_RESTORED=1
ok "BranchRule 已恢复"

echo ""
echo "SA-009 focused result: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
