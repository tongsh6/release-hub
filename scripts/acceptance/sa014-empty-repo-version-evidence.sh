#!/bin/bash
# SA-014 focused acceptance: a real empty GitLab repository keeps version parsing diagnosable.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND="${BACKEND_URL:-http://localhost:8080}"
GITLAB="${GITLAB_URL:-http://localhost:9080}"
TS=$(date -u +%Y%m%d-%H%M%S)
REPORT_DIR="${REPORT_DIR:-$PROJECT_ROOT/.ai/reports/sa014-empty-repo-version/$TS}"
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

create_acceptance_group() {
    local code=$1
    local name=$2
    local resp
    resp=$(api POST "/api/v1/groups" "{\"name\":\"$name\",\"code\":\"$code\",\"parentCode\":null}")
    echo "$resp" | json_get "d.get('data','')"
}

create_empty_gitlab_project() {
    local path=$1
    local resp
    resp=$(curl -s -X POST "$GITLAB/api/v4/projects" \
        -H "PRIVATE-TOKEN: $GITLAB_PAT" \
        --data-urlencode "name=$path" \
        --data-urlencode "path=$path" \
        --data-urlencode "visibility=private" \
        --data-urlencode "initialize_with_readme=false")
    echo "$resp" | json_get "d.get('path_with_namespace','')"
}

gitlab_branch_count() {
    local project_path=$1
    local encoded_path
    encoded_path=$(echo "$project_path" | gitlab_encode)
    curl -s -H "PRIVATE-TOKEN: $GITLAB_PAT" \
        "$GITLAB/api/v4/projects/$encoded_path/repository/branches?per_page=100" | python3 -c "
import sys,json
try:
    d = json.load(sys.stdin)
except Exception:
    print('NON_JSON_RESPONSE')
    raise SystemExit
print(len(d) if isinstance(d, list) else 'NOT_LIST')
"
}

create_repo_ref_without_initial_version() {
    local name=$1
    local clone_url=$2
    local group_code=$3
    local resp
    resp=$(api POST "/api/v1/repositories" "{\"name\":\"$name\",\"cloneUrl\":\"$clone_url\",\"defaultBranch\":\"main\",\"groupCode\":\"$group_code\",\"gitProvider\":\"GITLAB\",\"gitAccessToken\":\"$GITLAB_PAT\"}")
    echo "$resp" | json_get "((d.get('data') or {}).get('id') or '')"
}

assert_empty_repo_diagnostic() {
    local label=$1
    local payload=$2
    local version source error_type branch paths message success
    success=$(echo "$payload" | json_get "d.get('success', False)")
    version=$(echo "$payload" | json_get "((d.get('data') or {}).get('version') or '')")
    source=$(echo "$payload" | json_get "((d.get('data') or {}).get('versionSource') or '')")
    error_type=$(echo "$payload" | json_get "((d.get('data') or {}).get('errorType') or '')")
    branch=$(echo "$payload" | json_get "((d.get('data') or {}).get('branch') or '')")
    paths=$(echo "$payload" | json_get "','.join((d.get('data') or {}).get('checkedPaths') or [])")
    message=$(echo "$payload" | json_get "((d.get('data') or {}).get('message') or '')")

    [ "$success" = "True" ] && ok "$label API 返回成功" || no "$label API 失败: $payload"
    [ -z "$version" ] && ok "$label 未填充假版本" || no "$label 不应返回版本: $version"
    [ "$source" = "VERSION_FILE_MISSING" ] && ok "$label versionSource=VERSION_FILE_MISSING" || no "$label versionSource 异常: $source"
    [ "$error_type" = "VERSION_FILE_MISSING" ] && ok "$label errorType=VERSION_FILE_MISSING" || no "$label errorType 异常: $error_type"
    [ "$branch" = "main" ] && ok "$label 返回默认分支: $branch" || no "$label 默认分支异常: $branch"
    [[ "$paths" == *"pom.xml"* && "$paths" == *"gradle.properties"* ]] \
        && ok "$label 返回检查路径: $paths" \
        || no "$label 检查路径异常: $paths"
    [[ "$message" == *"未找到"* ]] \
        && ok "$label 返回用户可理解诊断: $message" \
        || no "$label 诊断文案异常: $message"
}

write_report() {
    mkdir -p "$REPORT_DIR"
    cat > "$REPORT_DIR/summary.md" <<EOF
# SA-014 空仓库版本解析真实 GitLab 证据

- 时间：$TS
- 后端：$BACKEND
- GitLab：$GITLAB
- GitLab 项目：${PROJECT_PATH:-}
- ReleaseHub 仓库 ID：${REPO_ID:-}
- GitLab 分支数：${BRANCH_COUNT:-}
- 创建后 versionSource：${INITIAL_SOURCE:-}
- 重扫后 versionSource：${SYNC_SOURCE:-}
- 仓库列表仍可见：${LIST_STATE:-}
- PASS：$PASS
- FAIL：$FAIL
EOF

    cat > "$REPORT_DIR/evidence.json" <<EOF
{
  "timestamp": "$TS",
  "backend": "$BACKEND",
  "gitlab": "$GITLAB",
  "projectPath": "${PROJECT_PATH:-}",
  "repositoryId": "${REPO_ID:-}",
  "gitlabBranchCount": "${BRANCH_COUNT:-}",
  "initialVersionSource": "${INITIAL_SOURCE:-}",
  "syncVersionSource": "${SYNC_SOURCE:-}",
  "listState": "${LIST_STATE:-}",
  "pass": $PASS,
  "fail": $FAIL
}
EOF
}

echo -e "${CYAN}=== SA-014 Empty Repo Version Diagnostic Evidence ===${NC}"

if ! curl -s -o /dev/null "$BACKEND/actuator/health"; then
    no "后端未就绪: $BACKEND"
    write_report
    exit 1
fi

INIT_SH="$PROJECT_ROOT/scripts/e2e/init-gitlab.sh"
GITLAB_URL="$GITLAB" ROOT_PASS=releasehub123 bash "$INIT_SH" >/tmp/sa014-init-gitlab.log 2>&1
source /tmp/e2e-gitlab.env
GITLAB_PAT="${E2E_GITLAB_TOKEN:-}"
if [ -z "$GITLAB_PAT" ]; then
    no "无法取得 GitLab PAT"
    write_report
    exit 1
fi
ok "GitLab 种子数据和 PAT 就绪"

AUTH_TOKEN=$(curl -s -X POST "$BACKEND/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin"}' | json_get "d['data']['token']")
AUTH="Authorization: Bearer $AUTH_TOKEN"
ok "后端登录成功"

SAVE_SETTINGS=$(api POST "/api/v1/settings/gitlab" "{\"baseUrl\":\"$GITLAB\",\"token\":\"$GITLAB_PAT\"}")
SAVE_SETTINGS_OK=$(echo "$SAVE_SETTINGS" | json_get "d.get('success', False) and d.get('data') == True")
if [ "$SAVE_SETTINGS_OK" = "True" ]; then
    ok "GitLab Settings 已刷新为当前 PAT"
else
    no "GitLab Settings 保存失败: $SAVE_SETTINGS"
fi

TEST_SETTINGS=$(api GET "/api/v1/settings/gitlab/test")
TEST_SETTINGS_OK=$(echo "$TEST_SETTINGS" | json_get "d.get('success', False) and d.get('data') == True")
if [ "$TEST_SETTINGS_OK" = "True" ]; then
    ok "GitLab Settings 连接测试通过"
else
    no "GitLab Settings 连接测试失败: $TEST_SETTINGS"
fi

GROUP_CODE="SA014EMPTY${TS//[-:]/}"
GROUP_ID=$(create_acceptance_group "$GROUP_CODE" "SA-014 Empty Repo $TS")
if [ -n "$GROUP_ID" ]; then
    ok "创建独立叶子分组: $GROUP_CODE"
else
    no "创建 SA-014 分组失败"
    write_report
    exit 1
fi

PROJECT_SLUG="sa014-empty-$TS"
PROJECT_PATH=$(create_empty_gitlab_project "$PROJECT_SLUG")
if [ -n "$PROJECT_PATH" ]; then
    ok "创建真实 GitLab 空仓库: $PROJECT_PATH"
else
    no "创建真实 GitLab 空仓库失败"
    write_report
    exit 1
fi

BRANCH_COUNT=$(gitlab_branch_count "$PROJECT_PATH")
if [ "$BRANCH_COUNT" = "0" ]; then
    ok "GitLab 直查确认仓库无分支无提交"
else
    no "GitLab 空仓库状态异常，branch_count=$BRANCH_COUNT"
fi

REPO_NAME="SA014_EMPTY_$TS"
CLONE_URL="http://localhost:9080/${PROJECT_PATH}.git"
REPO_ID=$(create_repo_ref_without_initial_version "$REPO_NAME" "$CLONE_URL" "$GROUP_CODE")
if [ -n "$REPO_ID" ]; then
    ok "通过系统纳管空仓库: $REPO_ID"
else
    no "系统纳管空仓库失败"
    write_report
    exit 1
fi

INITIAL_INFO=$(api GET "/api/v1/repositories/$REPO_ID/initial-version")
INITIAL_SOURCE=$(echo "$INITIAL_INFO" | json_get "((d.get('data') or {}).get('versionSource') or '')")
assert_empty_repo_diagnostic "创建后初始版本" "$INITIAL_INFO"

SYNC_INFO=$(api POST "/api/v1/repositories/$REPO_ID/sync-version" "{}")
SYNC_SOURCE=$(echo "$SYNC_INFO" | json_get "((d.get('data') or {}).get('versionSource') or '')")
assert_empty_repo_diagnostic "重新解析后初始版本" "$SYNC_INFO"

LIST_INFO=$(api GET "/api/v1/repositories?keyword=$REPO_NAME")
LIST_STATE=$(echo "$LIST_INFO" | json_get "'FOUND' if any((repo.get('id') == '$REPO_ID') for repo in (d.get('data') or [])) else 'NOT_FOUND'")
if [ "$LIST_STATE" = "FOUND" ]; then
    ok "空仓库诊断不阻塞既有仓库列表"
else
    no "仓库列表未返回空仓库记录: $LIST_INFO"
fi

write_report
info "证据报告: $REPORT_DIR/summary.md"

if [ "$FAIL" -gt 0 ]; then
    echo -e "${RED}SA-014 empty repo evidence failed: PASS=$PASS FAIL=$FAIL${NC}"
    exit 1
fi

echo -e "${GREEN}SA-014 empty repo evidence passed: PASS=$PASS FAIL=$FAIL${NC}"
