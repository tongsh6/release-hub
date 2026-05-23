#!/usr/bin/env bash
#
# SA-002 focused acceptance: produce a dry-run cleanup action plan for legacy data risks.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND="${BACKEND_URL:-http://localhost:8080}"
TS="${SA002_REPORT_TS:-$(date -u +%Y%m%d-%H%M%S)}"
REPORT_DIR="$PROJECT_ROOT/.ai/reports/sa002-safe-cleanup/$TS"
DRY_RUN=true

usage() {
    cat <<EOF
Usage: scripts/acceptance/sa002-safe-cleanup.sh [--dry-run] [--report-dir DIR]

Generate a dry-run cleanup action plan for SA-002 legacy data risks.

This script is read-only. It does not update database rows, delete release
windows, or touch remote GitLab repositories.

Options:
  --dry-run        Explicitly run in dry-run mode. This is the default.
  --report-dir    Directory for generated summary.md and actions.jsonl.
  -h, --help      Show this help.
EOF
}

die() {
    echo "FATAL: $*" >&2
    exit 1
}

info() {
    echo "[INFO] $*"
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --report-dir=*)
            REPORT_DIR="${1#*=}"
            shift
            ;;
        --report-dir)
            [ "$#" -ge 2 ] || die "--report-dir requires a value."
            REPORT_DIR="$2"
            shift 2
            ;;
        --execute)
            die "--execute is intentionally unavailable for SA-002. Use the generated dry-run action plan and application-level operations for manual cleanup."
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

[ "$DRY_RUN" = "true" ] || die "Only dry-run mode is supported."

if ! command -v docker >/dev/null 2>&1; then
    die "docker is required to read the local releasehub-postgres database."
fi

if ! docker ps --format '{{.Names}}' | grep -Fxq releasehub-postgres; then
    die "releasehub-postgres is not running. Start the local environment before generating the cleanup plan."
fi

psql_tsv() {
    docker exec releasehub-postgres psql -U postgres -d release_hub -t -A -F $'\t' -c "$1"
}

psql_scalar() {
    psql_tsv "$1" | tr -d '[:space:]'
}

if ! psql_scalar "SELECT 1;" >/dev/null 2>&1; then
    die "Cannot query release_hub in releasehub-postgres."
fi

if ! curl -s -o /dev/null "$BACKEND/actuator/health"; then
    die "Backend is not reachable at $BACKEND. Start the local environment before generating the cleanup plan."
fi

AUTH_TOKEN=$(curl -s -X POST "$BACKEND/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin"}' | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))")
[ -n "$AUTH_TOKEN" ] || die "Cannot authenticate against $BACKEND."
AUTH="Authorization: Bearer $AUTH_TOKEN"

mkdir -p "$REPORT_DIR"
ACTIONS_JSONL="$REPORT_DIR/actions.jsonl"
ACTIONS_MD="$REPORT_DIR/actions.md"
SUMMARY_MD="$REPORT_DIR/summary.md"
: > "$ACTIONS_JSONL"

cat > "$ACTIONS_MD" <<'EOF'
# SA-002 存量数据安全清理动作清单

| 资源类型 | 资源 ID | 风险类型 | 应用入口 | 建议动作 | 执行前检查 | 执行后复核 | 复核决策 | 已执行 |
|---|---|---|---|---|---|---|---|---|
EOF

ACTION_COUNT=0

append_action() {
    local resource_type=$1
    local resource_id=$2
    local risk_type=$3
    local suggested_action=$4
    local executed=$5
    local source=$6
    local application_entry=$7
    local pre_execution_check=$8
    local post_execution_verification=$9
    local review_decision="PENDING"

    ACTION_COUNT=$((ACTION_COUNT + 1))
    printf '| `%s` | `%s` | `%s` | `%s` | %s | %s | %s | `%s` | `%s` |\n' \
        "$resource_type" "$resource_id" "$risk_type" "$application_entry" "$suggested_action" \
        "$pre_execution_check" "$post_execution_verification" "$review_decision" "$executed" >> "$ACTIONS_MD"
    python3 - "$resource_type" "$resource_id" "$risk_type" "$suggested_action" "$executed" "$source" \
        "$application_entry" "$pre_execution_check" "$post_execution_verification" "$review_decision" >> "$ACTIONS_JSONL" <<'PY'
import json
import sys

resource_type, resource_id, risk_type, suggested_action, executed, source, application_entry, pre_check, post_verify, review_decision = sys.argv[1:]
print(json.dumps({
    "resourceType": resource_type,
    "resourceId": resource_id,
    "riskType": risk_type,
    "suggestedAction": suggested_action,
    "executed": executed == "true",
    "source": source,
    "applicationEntry": application_entry,
    "preExecutionCheck": pre_check,
    "postExecutionVerification": post_verify,
    "manualReviewRequired": True,
    "reviewDecision": review_decision,
}, ensure_ascii=False))
PY
}

count_or_zero() {
    local table=$1
    psql_scalar "SELECT COUNT(*) FROM $table;" 2>/dev/null || printf '0'
}

api_count() {
    local path=$1
    curl -s "$BACKEND$path" -H "$AUTH" | python3 -c "
import sys,json
try:
    d = json.load(sys.stdin)
except Exception:
    print(0)
    raise SystemExit
if 'page' in d:
    print((d.get('page') or {}).get('total', 0))
else:
    data = d.get('data') or []
    print(len(data) if isinstance(data, list) else 0)
"
}

GROUP_COUNT=$(api_count "/api/v1/groups")
REPO_COUNT=$(api_count "/api/v1/repositories")
WINDOW_COUNT=$(api_count "/api/v1/release-windows")
ITER_COUNT=$(api_count "/api/v1/iterations")
RUN_COUNT=$(api_count "/api/v1/runs/paged?size=1")
DB_GROUP_COUNT=$(count_or_zero groups)
DB_REPO_COUNT=$(count_or_zero code_repository)
DB_WINDOW_COUNT=$(count_or_zero release_window)
DB_ITER_COUNT=$(count_or_zero iteration)
DB_RUN_COUNT=$(count_or_zero run)
FLYWAY_VERSION=$(psql_scalar "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null || true)
BRANCH_MODE_DISTRIBUTION=$(psql_tsv "SELECT COALESCE(branch_creation_mode, '<NULL>'), COUNT(*) FROM iteration_repo GROUP BY branch_creation_mode ORDER BY 1;" 2>/dev/null || true)

while IFS=$'\t' read -r repo_id repo_name; do
    [ -z "${repo_id:-}" ] && continue
    append_action \
        "code_repository" \
        "$repo_id" \
        "REPO_TOKEN_PLAINTEXT" \
        "通过仓库设置入口重新保存 token，让应用层透明加密后覆盖旧值；报告不输出 token 明文。" \
        "false" \
        "code_repository.git_token:$repo_name" \
        "/repositories/{resourceId}" \
        "确认仓库仍存在，且当前 token 仍为明文或需要重新保存。" \
        "仓库保存后重新运行 SA-002 审计，仓库 token 明文数量应为 0。"
done < <(psql_tsv "SELECT id, COALESCE(name, '') FROM code_repository WHERE git_token IS NOT NULL AND git_token <> '' AND git_token ~ '^(glpat-|ghp_|github_pat_)' ORDER BY id;" 2>/dev/null || true)

while IFS=$'\t' read -r settings_id; do
    [ -z "${settings_id:-}" ] && continue
    append_action \
        "system_settings" \
        "$settings_id" \
        "SETTINGS_TOKEN_PLAINTEXT" \
        "通过系统设置页重新保存 GitLab token，让应用层透明加密后覆盖旧值；报告不输出 token 明文。" \
        "false" \
        "system_settings.gitlab_token" \
        "/settings/gitlab" \
        "确认 GitLab Settings 仍存在，且 token 需要通过系统设置重新保存。" \
        "系统设置保存后重新运行 SA-002 审计，Settings token 明文数量应为 0。"
done < <(psql_tsv "SELECT id FROM system_settings WHERE gitlab_token IS NOT NULL AND gitlab_token <> '' AND gitlab_token ~ '^(glpat-|ghp_|github_pat_)' ORDER BY id;" 2>/dev/null || true)

while IFS=$'\t' read -r iteration_key repo_id branch_mode; do
    [ -z "${iteration_key:-}" ] && continue
    append_action \
        "iteration_repo" \
        "$iteration_key/$repo_id" \
        "BRANCH_CREATION_MODE_MISSING_OR_INVALID" \
        "复核该迭代仓库的分支创建策略；通过迭代业务入口或后续迁移服务按真实语义补齐，不直接默认覆盖为 AUTO。" \
        "false" \
        "iteration_repo.branch_creation_mode:$branch_mode" \
        "受控迁移服务: iteration_repo.branch_creation_mode" \
        "确认 iterationKey/repoId 仍存在，且分支模式缺失或不在 AUTO、NAMED、EXISTING 范围内。" \
        "复核 version-info 返回的 branchCreationMode 已按真实业务语义补齐。"
done < <(psql_tsv "SELECT iteration_key, repo_id, COALESCE(branch_creation_mode, '<NULL>') FROM iteration_repo WHERE branch_creation_mode IS NULL OR branch_creation_mode NOT IN ('AUTO', 'NAMED', 'EXISTING') ORDER BY iteration_key, repo_id;" 2>/dev/null || true)

while IFS=$'\t' read -r iteration_key repo_id; do
    [ -z "${iteration_key:-}" ] && continue
    append_action \
        "iteration_repo" \
        "$iteration_key/$repo_id" \
        "FEATURE_BRANCH_MISSING" \
        "打开迭代详情复核仓库版本信息；必要时通过迭代仓库同步、重新追加仓库或受控服务生成 featureBranch。" \
        "false" \
        "iteration_repo.feature_branch" \
        "/iterations/{iterationKey}" \
        "确认迭代仓库关联仍存在，且 featureBranch 仍缺失。" \
        "复核迭代详情 version-info 中 featureBranch 已恢复且符合分支规则。"
done < <(psql_tsv "SELECT iteration_key, repo_id FROM iteration_repo WHERE feature_branch IS NULL ORDER BY iteration_key, repo_id;" 2>/dev/null || true)

while IFS=$'\t' read -r repo_id clone_url; do
    [ -z "${repo_id:-}" ] && continue
    append_action \
        "code_repository" \
        "$repo_id" \
        "CLONE_URL_INVALID" \
        "通过仓库编辑入口修正 cloneUrl，复用现有重复校验和格式校验；不得直接改库绕过唯一性约束。" \
        "false" \
        "code_repository.clone_url:$clone_url" \
        "/repositories/{resourceId}" \
        "确认仓库仍存在，且 cloneUrl 仍无法通过格式和重复纳管校验。" \
        "仓库编辑保存后重新运行 SA-002 审计，cloneUrl 异常数量应减少。"
done < <(psql_tsv "SELECT id, COALESCE(clone_url, '') FROM code_repository WHERE clone_url LIKE 'http://http://%' OR clone_url !~ '^(https?://[^/]+/.+|git@[^:]+:.+)(\\.git)?$' ORDER BY id;" 2>/dev/null || true)

while IFS=$'\t' read -r window_id window_name; do
    [ -z "${window_id:-}" ] && continue
    append_action \
        "release_window" \
        "$window_id" \
        "DRAFT_WINDOW_REMAINS" \
        "在发布窗口页按业务判断继续发布、关闭或删除；仅空 DRAFT 窗口可通过应用层删除保护删除。" \
        "false" \
        "release_window.status:$window_name" \
        "/release-windows/{resourceId}" \
        "确认发布窗口仍为 DRAFT，并由发布经理判断继续发布、关闭或删除。" \
        "复核窗口状态已符合业务决策；如删除，仅通过应用层删除保护完成。"
done < <(curl -s "$BACKEND/api/v1/release-windows" -H "$AUTH" | python3 -c "
import sys,json
try:
    windows = (json.load(sys.stdin).get('data') or [])
except Exception:
    windows = []
for window in windows:
    if window.get('status') == 'DRAFT':
        name = (window.get('name') or '').replace('\t', ' ')
        print(f\"{window.get('id','')}\t{name}\")
")

while IFS=$'\t' read -r window_id iteration_key; do
    [ -z "${window_id:-}" ] && continue
    append_action \
        "window_iteration" \
        "$window_id/$iteration_key" \
        "ATTACH_BRANCH_NOT_CREATED" \
        "复核发布窗口挂载结果和 Git 分支状态；通过发布窗口或迭代业务流程重新挂载/修正，不直接伪造 branch_created。" \
        "false" \
        "window_iteration.branch_created" \
        "/release-windows/{windowId}" \
        "确认窗口挂载关系仍存在，且 branchCreated 仍为 false。" \
        "复核窗口发布计划和 Git 分支状态一致，不伪造 branchCreated。"
done < <(psql_tsv "SELECT window_id, iteration_key FROM window_iteration WHERE branch_created = false ORDER BY window_id, iteration_key;" 2>/dev/null || true)

if [ "$ACTION_COUNT" -eq 0 ]; then
    echo "| _无_ | _无_ | _无待处理风险_ | _无_ | 当前审计未发现需要清理的存量数据动作。 | _无_ | _无_ | \`PENDING\` | \`false\` |" >> "$ACTIONS_MD"
fi

{
    echo "# SA-002 存量数据安全清理 dry-run 报告"
    echo
    echo "- 模式：dry-run，只读数据库，不修改业务数据。"
    echo "- 生成时间：$TS"
    echo "- Flyway 最新迁移：${FLYWAY_VERSION:-unknown}"
    echo "- 后端：$BACKEND"
    echo "- 应用 API 资产统计：Groups=$GROUP_COUNT, Repos=$REPO_COUNT, Windows=$WINDOW_COUNT, Iterations=$ITER_COUNT, Runs=$RUN_COUNT"
    echo "- 数据库直查资产统计：Groups=$DB_GROUP_COUNT, Repos=$DB_REPO_COUNT, Windows=$DB_WINDOW_COUNT, Iterations=$DB_ITER_COUNT, Runs=$DB_RUN_COUNT"
    echo "- 待复核动作数：$ACTION_COUNT"
    echo "- 动作 JSONL：$ACTIONS_JSONL"
    echo "- 动作 Markdown：$ACTIONS_MD"
    echo "- 动作字段：resourceType、resourceId、riskType、suggestedAction、executed、source、applicationEntry、preExecutionCheck、postExecutionVerification、manualReviewRequired、reviewDecision"
    echo
    echo "## BranchCreationMode 分布"
    echo
    if [ -n "$BRANCH_MODE_DISTRIBUTION" ]; then
        echo "| 分支创建模式 | 数量 |"
        echo "|---|---|"
        while IFS=$'\t' read -r mode count; do
            [ -z "${mode:-}" ] && continue
            echo "| \`$mode\` | $count |"
        done <<< "$BRANCH_MODE_DISTRIBUTION"
    else
        echo "无 iteration_repo 记录。"
    fi
    echo
    echo "## 安全边界"
    echo
    echo "- 不输出 token 明文。"
    echo "- 不直接 UPDATE/DELETE 数据库。"
    echo "- 不删除 GitLab 远端分支、仓库或发布窗口。"
    echo "- 需要执行修复时，优先使用应用层入口或人工复核后的受控迁移服务。"
    echo '- `reviewDecision` 默认是 `PENDING`；人工复核后只允许进入应用层入口，不允许脚本直接执行清理。'
} > "$SUMMARY_MD"

info "SA-002 dry-run cleanup report generated:"
info "  $SUMMARY_MD"
info "  $ACTIONS_MD"
info "  $ACTIONS_JSONL"
info "Actions requiring review: $ACTION_COUNT"
