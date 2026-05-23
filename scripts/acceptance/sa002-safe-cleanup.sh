#!/usr/bin/env bash
#
# SA-002 focused acceptance: produce a dry-run cleanup action plan for legacy data risks.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
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

mkdir -p "$REPORT_DIR"
ACTIONS_JSONL="$REPORT_DIR/actions.jsonl"
ACTIONS_MD="$REPORT_DIR/actions.md"
SUMMARY_MD="$REPORT_DIR/summary.md"
: > "$ACTIONS_JSONL"

cat > "$ACTIONS_MD" <<'EOF'
# SA-002 存量数据安全清理动作清单

| 资源类型 | 资源 ID | 风险类型 | 建议动作 | 已执行 |
|---|---|---|---|---|
EOF

ACTION_COUNT=0

append_action() {
    local resource_type=$1
    local resource_id=$2
    local risk_type=$3
    local suggested_action=$4
    local executed=$5
    local source=$6

    ACTION_COUNT=$((ACTION_COUNT + 1))
    printf '| `%s` | `%s` | `%s` | %s | `%s` |\n' \
        "$resource_type" "$resource_id" "$risk_type" "$suggested_action" "$executed" >> "$ACTIONS_MD"
    python3 - "$resource_type" "$resource_id" "$risk_type" "$suggested_action" "$executed" "$source" >> "$ACTIONS_JSONL" <<'PY'
import json
import sys

resource_type, resource_id, risk_type, suggested_action, executed, source = sys.argv[1:]
print(json.dumps({
    "resourceType": resource_type,
    "resourceId": resource_id,
    "riskType": risk_type,
    "suggestedAction": suggested_action,
    "executed": executed == "true",
    "source": source,
}, ensure_ascii=False))
PY
}

count_or_zero() {
    local table=$1
    psql_scalar "SELECT COUNT(*) FROM $table;" 2>/dev/null || printf '0'
}

GROUP_COUNT=$(count_or_zero groups)
REPO_COUNT=$(count_or_zero code_repository)
WINDOW_COUNT=$(count_or_zero release_window)
ITER_COUNT=$(count_or_zero iteration)
RUN_COUNT=$(count_or_zero run)
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
        "code_repository.git_token:$repo_name"
done < <(psql_tsv "SELECT id, COALESCE(name, '') FROM code_repository WHERE git_token IS NOT NULL AND git_token <> '' AND git_token ~ '^(glpat-|ghp_|github_pat_)' ORDER BY id;" 2>/dev/null || true)

while IFS=$'\t' read -r settings_id; do
    [ -z "${settings_id:-}" ] && continue
    append_action \
        "system_settings" \
        "$settings_id" \
        "SETTINGS_TOKEN_PLAINTEXT" \
        "通过系统设置页重新保存 GitLab token，让应用层透明加密后覆盖旧值；报告不输出 token 明文。" \
        "false" \
        "system_settings.gitlab_token"
done < <(psql_tsv "SELECT id FROM system_settings WHERE gitlab_token IS NOT NULL AND gitlab_token <> '' AND gitlab_token ~ '^(glpat-|ghp_|github_pat_)' ORDER BY id;" 2>/dev/null || true)

while IFS=$'\t' read -r iteration_key repo_id branch_mode; do
    [ -z "${iteration_key:-}" ] && continue
    append_action \
        "iteration_repo" \
        "$iteration_key/$repo_id" \
        "BRANCH_CREATION_MODE_MISSING_OR_INVALID" \
        "复核该迭代仓库的分支创建策略；通过迭代业务入口或后续迁移服务按真实语义补齐，不直接默认覆盖为 AUTO。" \
        "false" \
        "iteration_repo.branch_creation_mode:$branch_mode"
done < <(psql_tsv "SELECT iteration_key, repo_id, COALESCE(branch_creation_mode, '<NULL>') FROM iteration_repo WHERE branch_creation_mode IS NULL OR branch_creation_mode NOT IN ('AUTO', 'MANUAL') ORDER BY iteration_key, repo_id;" 2>/dev/null || true)

while IFS=$'\t' read -r iteration_key repo_id; do
    [ -z "${iteration_key:-}" ] && continue
    append_action \
        "iteration_repo" \
        "$iteration_key/$repo_id" \
        "FEATURE_BRANCH_MISSING" \
        "打开迭代详情复核仓库版本信息；必要时通过迭代仓库同步、重新追加仓库或受控服务生成 featureBranch。" \
        "false" \
        "iteration_repo.feature_branch"
done < <(psql_tsv "SELECT iteration_key, repo_id FROM iteration_repo WHERE feature_branch IS NULL ORDER BY iteration_key, repo_id;" 2>/dev/null || true)

while IFS=$'\t' read -r repo_id clone_url; do
    [ -z "${repo_id:-}" ] && continue
    append_action \
        "code_repository" \
        "$repo_id" \
        "CLONE_URL_INVALID" \
        "通过仓库编辑入口修正 cloneUrl，复用现有重复校验和格式校验；不得直接改库绕过唯一性约束。" \
        "false" \
        "code_repository.clone_url:$clone_url"
done < <(psql_tsv "SELECT id, COALESCE(clone_url, '') FROM code_repository WHERE clone_url LIKE 'http://http://%' OR clone_url !~ '^(https?://[^/]+/.+|git@[^:]+:.+)(\\.git)?$' ORDER BY id;" 2>/dev/null || true)

while IFS=$'\t' read -r window_id window_name; do
    [ -z "${window_id:-}" ] && continue
    append_action \
        "release_window" \
        "$window_id" \
        "DRAFT_WINDOW_REMAINS" \
        "在发布窗口页按业务判断继续发布、关闭或删除；仅空 DRAFT 窗口可通过应用层删除保护删除。" \
        "false" \
        "release_window.status:$window_name"
done < <(psql_tsv "SELECT id, COALESCE(name, '') FROM release_window WHERE status = 'DRAFT' ORDER BY updated_at DESC, id;" 2>/dev/null || true)

while IFS=$'\t' read -r window_id iteration_key; do
    [ -z "${window_id:-}" ] && continue
    append_action \
        "window_iteration" \
        "$window_id/$iteration_key" \
        "ATTACH_BRANCH_NOT_CREATED" \
        "复核发布窗口挂载结果和 Git 分支状态；通过发布窗口或迭代业务流程重新挂载/修正，不直接伪造 branch_created。" \
        "false" \
        "window_iteration.branch_created"
done < <(psql_tsv "SELECT window_id, iteration_key FROM window_iteration WHERE branch_created = false ORDER BY window_id, iteration_key;" 2>/dev/null || true)

if [ "$ACTION_COUNT" -eq 0 ]; then
    echo "| _无_ | _无_ | _无待处理风险_ | 当前审计未发现需要清理的存量数据动作。 | \`false\` |" >> "$ACTIONS_MD"
fi

{
    echo "# SA-002 存量数据安全清理 dry-run 报告"
    echo
    echo "- 模式：dry-run，只读数据库，不修改业务数据。"
    echo "- 生成时间：$TS"
    echo "- Flyway 最新迁移：${FLYWAY_VERSION:-unknown}"
    echo "- 资产统计：Groups=$GROUP_COUNT, Repos=$REPO_COUNT, Windows=$WINDOW_COUNT, Iterations=$ITER_COUNT, Runs=$RUN_COUNT"
    echo "- 待复核动作数：$ACTION_COUNT"
    echo "- 动作 JSONL：$ACTIONS_JSONL"
    echo "- 动作 Markdown：$ACTIONS_MD"
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
} > "$SUMMARY_MD"

info "SA-002 dry-run cleanup report generated:"
info "  $SUMMARY_MD"
info "  $ACTIONS_MD"
info "  $ACTIONS_JSONL"
info "Actions requiring review: $ACTION_COUNT"
