#!/bin/bash
set -euo pipefail

GITLAB_URL="${GITLAB_URL:-http://localhost:9080}"
ROOT_PASS="${ROOT_PASS:-releasehub123}"
TEST_USER="${TEST_USER:-e2e-user}"
EXECUTE=false

usage() {
  cat <<EOF
Usage: scripts/e2e/reset-gitlab-seed-branches.sh [--execute]

Reset local GitLab seed repositories back to their canonical seed branches.

Default mode is a dry run. Pass --execute to delete non-seed branches.

Environment:
  GITLAB_URL   GitLab base URL, default: http://localhost:9080
  ROOT_PASS    root user password, default: releasehub123
  TEST_USER    seed namespace user, default: e2e-user
EOF
}

for arg in "$@"; do
  case "$arg" in
    --execute) EXECUTE=true ;;
    --dry-run) EXECUTE=false ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $arg" >&2; usage >&2; exit 2 ;;
  esac
done

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
TOTAL_MISSING=0

for repo_name in seed-repo-1-maven seed-repo-2-maven-multi seed-repo-3-gradle; do
  echo ""
  echo "=== $repo_name ==="

  project_id=$(project_id_for_repo "$repo_name")
  if [ -z "$project_id" ]; then
    echo "  [WARN] project not found; run scripts/e2e/init-gitlab.sh first"
    continue
  fi

  branches=$(list_branches "$project_id")
  echo "  project_id=$project_id"

  while IFS= read -r expected; do
    [ -z "$expected" ] && continue
    if ! printf '%s\n' "$branches" | grep -Fxq -- "$expected"; then
      echo "  [WARN] missing seed branch: $expected"
      TOTAL_MISSING=$((TOTAL_MISSING + 1))
    fi
  done <<EOF
$(seed_branches_for_repo "$repo_name")
EOF

  while IFS= read -r branch; do
    [ -z "$branch" ] && continue
    if is_seed_branch "$repo_name" "$branch"; then
      echo "  [KEEP] $branch"
      continue
    fi

    TOTAL_DELETE=$((TOTAL_DELETE + 1))
    if [ "$EXECUTE" = "true" ]; then
      encoded_branch=$(urlencode "$branch")
      status=$(api_delete "$GITLAB_URL/api/v4/projects/$project_id/repository/branches/$encoded_branch")
      if [ "$status" = "204" ]; then
        echo "  [DELETE] $branch"
      else
        die "failed to delete $repo_name:$branch, HTTP $status"
      fi
    else
      echo "  [WOULD DELETE] $branch"
    fi
  done <<EOF
$branches
EOF
done

echo ""
if [ "$EXECUTE" = "true" ]; then
  echo "Deleted non-seed branches: $TOTAL_DELETE"
else
  echo "Non-seed branches that would be deleted: $TOTAL_DELETE"
fi
echo "Missing seed branches reported: $TOTAL_MISSING"

if [ "$TOTAL_MISSING" -gt 0 ]; then
  echo "Run scripts/e2e/init-gitlab.sh to recreate missing seed repositories or branches where possible."
fi
