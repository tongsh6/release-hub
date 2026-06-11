#!/bin/bash
set -euo pipefail

GITLAB_URL="${GITLAB_URL:-http://localhost:9080}"
ROOT_PASS="${ROOT_PASS:-releasehub123}"
TEST_USER="${TEST_USER:-e2e-user}"
EXECUTE=false
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
REPORT_DIR="$PROJECT_ROOT/.ai/reports/gitlab-seed-branch-reset/$TS"
TMP_FILES=()

usage() {
  cat <<EOF
Usage: scripts/e2e/reset-gitlab-seed-branches.sh [--execute] [--report-dir DIR]

Reset local GitLab seed repositories back to their canonical seed branches.

Default mode is a dry run. Pass --execute to delete non-seed branches.
Every run writes summary.md, branches.md, and branches.jsonl for audit evidence.

Environment:
  GITLAB_URL   GitLab base URL, default: http://localhost:9080
  ROOT_PASS    root user password, default: releasehub123
  TEST_USER    seed namespace user, default: e2e-user
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --execute) EXECUTE=true ;;
    --dry-run) EXECUTE=false ;;
    --report-dir=*) REPORT_DIR="${1#*=}" ;;
    --report-dir)
      [ "$#" -ge 2 ] || {
        echo "--report-dir requires a value." >&2
        usage >&2
        exit 2
      }
      REPORT_DIR="$2"
      shift
      ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

mkdir -p "$REPORT_DIR"
SUMMARY_MD="$REPORT_DIR/summary.md"
BRANCHES_MD="$REPORT_DIR/branches.md"
BRANCHES_JSONL="$REPORT_DIR/branches.jsonl"
: > "$BRANCHES_JSONL"
cat > "$BRANCHES_MD" <<'EOF'
# GitLab 种子分支清理证据

| 仓库 | Project ID | 分支 | 动作 | HTTP 状态 | 保护原因 |
|---|---|---|---|---|---|
EOF

cleanup() {
  for tmp in "${TMP_FILES[@]:-}"; do
    [ -n "$tmp" ] && [ -f "$tmp" ] && rm -f "$tmp"
  done
}
trap cleanup EXIT

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

die() {
  echo "FATAL: $*" >&2
  exit 1
}

api_get() {
  curl -fsS -H "Authorization: Bearer $ROOT_TOKEN" "$1"
}

api_delete() {
  curl -sS -o /dev/null -w "%{http_code}" --request DELETE \
    -H "Authorization: Bearer $ROOT_TOKEN" "$1"
}

seed_branches_for_repo() {
  case "$1" in
    seed-repo-1-maven)
      printf '%s\n' "main" "feature/upgrade-guava" "feature/add-logging"
      ;;
    seed-repo-2-maven-multi)
      printf '%s\n' "main" "feature/update-lib"
      ;;
    seed-repo-3-gradle)
      printf '%s\n' "main" "feature/kotlin-support"
      ;;
    *)
      return 1
      ;;
  esac
}

is_seed_branch() {
  local repo_name=$1
  local branch=$2
  seed_branches_for_repo "$repo_name" | grep -Fxq -- "$branch"
}

project_id_for_repo() {
  local repo_name=$1
  local search
  search=$(urlencode "$repo_name")
  api_get "$GITLAB_URL/api/v4/users/$USER_ID/projects?search=$search&simple=true&per_page=100" | python3 -c "
import json, sys
target = '$repo_name'
projects = json.load(sys.stdin)
for project in projects:
    if project.get('name') == target or project.get('path') == target:
        print(project['id'])
        break
"
}

list_branches() {
  local project_id=$1
  local page=1
  local payload
  local count

  while true; do
    payload=$(api_get "$GITLAB_URL/api/v4/projects/$project_id/repository/branches?per_page=100&page=$page")
    count=$(printf '%s' "$payload" | python3 -c 'import json, sys; data=json.load(sys.stdin); print(len(data) if isinstance(data, list) else 0)')
    [ "$count" -eq 0 ] && break
    printf '%s' "$payload" | python3 -c 'import json, sys; [print(branch["name"]) for branch in json.load(sys.stdin)]'
    page=$((page + 1))
  done
}

append_event() {
  local repo_name=$1
  local project_id=$2
  local branch=$3
  local action=$4
  local http_status=$5
  local reason=$6

  printf '| `%s` | `%s` | `%s` | `%s` | `%s` | %s |\n' \
    "$repo_name" "$project_id" "$branch" "$action" "$http_status" "$reason" >> "$BRANCHES_MD"
  python3 - "$repo_name" "$project_id" "$branch" "$action" "$http_status" "$reason" >> "$BRANCHES_JSONL" <<'PY'
import json
import sys

repo_name, project_id, branch, action, http_status, reason = sys.argv[1:]
print(json.dumps({
    "repo": repo_name,
    "projectId": project_id,
    "branch": branch,
    "action": action,
    "httpStatus": http_status or None,
    "reason": reason,
}, ensure_ascii=False))
PY
}

if ! curl -fs -o /dev/null "$GITLAB_URL/users/sign_in" 2>/dev/null; then
  die "GitLab is not reachable at $GITLAB_URL. Start it first with scripts/dev/start-local-env.sh hold or scripts/e2e/init-gitlab.sh."
fi

echo "=== Acquiring root OAuth token ==="
TOKEN_RESPONSE=$(curl -fsS -X POST "$GITLAB_URL/oauth/token" \
  -d "grant_type=password&username=root&password=$ROOT_PASS") \
  || die "failed to acquire root OAuth token from $GITLAB_URL"
ROOT_TOKEN=$(printf '%s' "$TOKEN_RESPONSE" | python3 -c '
import json, sys
payload = json.load(sys.stdin)
token = payload.get("access_token")
if not token:
    raise SystemExit(f"root token acquisition failed: {payload}")
print(token)
')

echo "=== Resolving seed namespace user: $TEST_USER ==="
USER_ID=$(api_get "$GITLAB_URL/api/v4/users?username=$(urlencode "$TEST_USER")" | python3 -c '
import json, sys
users = json.load(sys.stdin)
print(users[0]["id"] if users else "")
')
[ -n "$USER_ID" ] || die "GitLab user '$TEST_USER' not found. Run scripts/e2e/init-gitlab.sh first."

if [ "$EXECUTE" = "true" ]; then
  echo "Mode: EXECUTE. Non-seed branches will be deleted."
else
  echo "Mode: DRY RUN. Pass --execute to delete non-seed branches."
fi

TOTAL_DELETE=0
TOTAL_WOULD_DELETE=0
TOTAL_KEEP=0
TOTAL_MISSING=0
TOTAL_POST_MISSING=0
TOTAL_POST_REMOVED=0
TOTAL_POST_UNEXPECTED_PRESENT=0

for repo_name in seed-repo-1-maven seed-repo-2-maven-multi seed-repo-3-gradle; do
  echo ""
  echo "=== $repo_name ==="

  project_id=$(project_id_for_repo "$repo_name")
  if [ -z "$project_id" ]; then
    echo "  [WARN] project not found; run scripts/e2e/init-gitlab.sh first"
    append_event "$repo_name" "" "" "PROJECT_MISSING" "" "run init-gitlab first"
    continue
  fi

  branches=$(list_branches "$project_id")
  echo "  project_id=$project_id"
  delete_candidates_file=$(mktemp)
  seed_file=$(mktemp)
  TMP_FILES+=("$delete_candidates_file" "$seed_file")
  seed_branches_for_repo "$repo_name" > "$seed_file"

  while IFS= read -r expected; do
    [ -z "$expected" ] && continue
    if ! printf '%s\n' "$branches" | grep -Fxq -- "$expected"; then
      echo "  [WARN] missing seed branch: $expected"
      append_event "$repo_name" "$project_id" "$expected" "MISSING_SEED" "" "canonical seed branch must exist"
      TOTAL_MISSING=$((TOTAL_MISSING + 1))
    fi
  done <<EOF
$(cat "$seed_file")
EOF

  while IFS= read -r branch; do
    [ -z "$branch" ] && continue
    if is_seed_branch "$repo_name" "$branch"; then
      echo "  [KEEP] $branch"
      append_event "$repo_name" "$project_id" "$branch" "KEEP" "" "canonical seed branch"
      TOTAL_KEEP=$((TOTAL_KEEP + 1))
      continue
    fi

    printf '%s\n' "$branch" >> "$delete_candidates_file"
    if [ "$EXECUTE" = "true" ]; then
      encoded_branch=$(urlencode "$branch")
      status=$(api_delete "$GITLAB_URL/api/v4/projects/$project_id/repository/branches/$encoded_branch")
      if [ "$status" = "204" ]; then
        echo "  [DELETE] $branch"
        append_event "$repo_name" "$project_id" "$branch" "DELETE" "$status" "non-seed branch"
        TOTAL_DELETE=$((TOTAL_DELETE + 1))
      else
        append_event "$repo_name" "$project_id" "$branch" "DELETE_FAILED" "$status" "non-seed branch"
        die "failed to delete $repo_name:$branch, HTTP $status"
      fi
    else
      echo "  [WOULD DELETE] $branch"
      append_event "$repo_name" "$project_id" "$branch" "WOULD_DELETE" "" "non-seed branch"
      TOTAL_WOULD_DELETE=$((TOTAL_WOULD_DELETE + 1))
    fi
  done <<EOF
$branches
EOF

  if [ "$EXECUTE" = "true" ]; then
    post_branches=$(list_branches "$project_id")
    while IFS= read -r expected; do
      [ -z "$expected" ] && continue
      if printf '%s\n' "$post_branches" | grep -Fxq -- "$expected"; then
        append_event "$repo_name" "$project_id" "$expected" "POST_KEEP" "" "seed branch still present after execute"
      else
        append_event "$repo_name" "$project_id" "$expected" "POST_MISSING_SEED" "" "seed branch missing after execute"
        TOTAL_POST_MISSING=$((TOTAL_POST_MISSING + 1))
      fi
    done < "$seed_file"

    while IFS= read -r deleted; do
      [ -z "$deleted" ] && continue
      if printf '%s\n' "$post_branches" | grep -Fxq -- "$deleted"; then
        append_event "$repo_name" "$project_id" "$deleted" "POST_UNEXPECTED_PRESENT" "" "non-seed branch still present after execute"
        TOTAL_POST_UNEXPECTED_PRESENT=$((TOTAL_POST_UNEXPECTED_PRESENT + 1))
      else
        append_event "$repo_name" "$project_id" "$deleted" "POST_REMOVED" "" "non-seed branch absent after execute"
        TOTAL_POST_REMOVED=$((TOTAL_POST_REMOVED + 1))
      fi
    done < "$delete_candidates_file"
  fi
done

echo ""
if [ "$EXECUTE" = "true" ]; then
  echo "Deleted non-seed branches: $TOTAL_DELETE"
else
  echo "Non-seed branches that would be deleted: $TOTAL_WOULD_DELETE"
fi
echo "Missing seed branches reported: $TOTAL_MISSING"
echo "Report:"
echo "  $SUMMARY_MD"
echo "  $BRANCHES_MD"
echo "  $BRANCHES_JSONL"

if [ "$TOTAL_MISSING" -gt 0 ]; then
  echo "Run scripts/e2e/init-gitlab.sh to recreate missing seed repositories or branches where possible."
fi

{
  echo "# GitLab 种子分支清理报告"
  echo
  echo "- SA：SA-016"
  echo "- 模式：$([ "$EXECUTE" = "true" ] && echo "EXECUTE" || echo "DRY_RUN")"
  echo "- GitLab：$GITLAB_URL"
  echo "- 用户：$TEST_USER"
  echo "- 报告时间：$TS"
  echo
  echo "## 汇总"
  echo
  echo "- 保留种子分支：$TOTAL_KEEP"
  echo "- dry-run 候选删除：$TOTAL_WOULD_DELETE"
  echo "- 实际删除非种子分支：$TOTAL_DELETE"
  echo "- 执行后确认已移除：$TOTAL_POST_REMOVED"
  echo "- 执行后仍存在的非种子分支：$TOTAL_POST_UNEXPECTED_PRESENT"
  echo "- 缺失种子分支：$TOTAL_MISSING"
  echo "- 执行后缺失种子分支：$TOTAL_POST_MISSING"
  echo
  echo "## 保护边界"
  echo
  echo "- 仅处理固定种子仓库：seed-repo-1-maven、seed-repo-2-maven-multi、seed-repo-3-gradle。"
  echo '- `main` 和 `feature/*` 种子分支由脚本内置 allowlist 保护。'
  echo '- 非种子分支仅在显式传入 `--execute` 时删除；默认 dry-run 只产出候选清单。'
  echo "- 脚本不读取、不修改 ReleaseHub 数据库。"
  echo
  echo "## 明细"
  echo
  echo "- Markdown：$BRANCHES_MD"
  echo "- JSONL：$BRANCHES_JSONL"
} > "$SUMMARY_MD"

if [ "$TOTAL_POST_UNEXPECTED_PRESENT" -gt 0 ]; then
  die "non-seed branches remained after execute; see $SUMMARY_MD"
fi
