#!/usr/bin/env bash
#
# ReleaseHub 本地开发数据库清理脚本。
#
# 该脚本只面向本地 Docker PostgreSQL 容器。默认清理业务数据，同时保留
# schema 元数据、登录账号和本地配置，便于开发阶段反复清理而不重建数据库。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TS="${DEV_DB_CLEANUP_TS:-$(date -u +%Y%m%d-%H%M%S)}"
REPORT_DIR="$PROJECT_ROOT/.ai/reports/dev-db-cleanup/$TS"
DB_CONTAINER="${RELEASEHUB_DB_CONTAINER:-releasehub-postgres}"
DB_NAME="${RELEASEHUB_DB_NAME:-release_hub}"
DB_USER="${RELEASEHUB_DB_USER:-postgres}"
TARGET_SCHEMAS=("release_hub" "public")
PRESERVED_TABLES=("flyway_schema_history" "users" "system_settings")
EXECUTE=false

usage() {
    cat <<EOF
Usage: scripts/dev/cleanup-dev-database.sh [--dry-run] [--execute] [--report-dir DIR]

清理 ReleaseHub 本地开发数据库中的业务数据。

默认模式为 dry-run。只有显式传入 --execute 才会清理候选表。

默认目标：
  container: $DB_CONTAINER
  database:  $DB_NAME
  schemas:   ${TARGET_SCHEMAS[*]}

默认保留表：
  ${PRESERVED_TABLES[*]}

参数：
  --dry-run            只生成执行前计数报告。默认即 dry-run。
  --execute            使用 RESTART IDENTITY CASCADE 清理候选表。
  --report-dir DIR     summary.md 和 TSV 计数文件输出目录。
  --schema NAME        增加或替换目标 schema。第一次传入 --schema 会清空默认 schema。
  --include-settings   同时清理 system_settings。
  --include-users      同时清理 users。
  -h, --help           显示帮助。

环境变量：
  RELEASEHUB_DB_CONTAINER  Docker 容器名，默认 releasehub-postgres。
  RELEASEHUB_DB_NAME       数据库名，默认 release_hub。
  RELEASEHUB_DB_USER       数据库用户，默认 postgres。
  DEV_DB_CLEANUP_TS        报告时间戳覆盖值。
EOF
}

die() {
    echo "FATAL: $*" >&2
    exit 1
}

info() {
    echo "[INFO] $*"
}

remove_preserved_table() {
    local table="$1"
    local next=()
    local preserved
    for preserved in "${PRESERVED_TABLES[@]}"; do
        [ "$preserved" = "$table" ] && continue
        next+=("$preserved")
    done
    PRESERVED_TABLES=("${next[@]}")
}

schema_overridden=false

while [ "$#" -gt 0 ]; do
    case "$1" in
        --dry-run)
            EXECUTE=false
            shift
            ;;
        --execute)
            EXECUTE=true
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
        --schema=*)
            if [ "$schema_overridden" = "false" ]; then
                TARGET_SCHEMAS=()
                schema_overridden=true
            fi
            TARGET_SCHEMAS+=("${1#*=}")
            shift
            ;;
        --schema)
            [ "$#" -ge 2 ] || die "--schema requires a value."
            if [ "$schema_overridden" = "false" ]; then
                TARGET_SCHEMAS=()
                schema_overridden=true
            fi
            TARGET_SCHEMAS+=("$2")
            shift 2
            ;;
        --include-settings)
            remove_preserved_table "system_settings"
            shift
            ;;
        --include-users)
            remove_preserved_table "users"
            shift
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

[ "${#TARGET_SCHEMAS[@]}" -gt 0 ] || die "At least one target schema is required."

if [ "$DB_CONTAINER" != "releasehub-postgres" ]; then
    die "拒绝清理非本地容器 '$DB_CONTAINER'。本地开发清理只允许 releasehub-postgres。"
fi

if [ "$DB_NAME" != "release_hub" ]; then
    die "拒绝清理非本地数据库 '$DB_NAME'。本地开发清理只允许 release_hub。"
fi

if ! command -v docker >/dev/null 2>&1; then
    die "docker is required."
fi

if ! docker ps --format '{{.Names}}' | grep -Fxq "$DB_CONTAINER"; then
    die "$DB_CONTAINER 未运行。请先启动本地开发环境。"
fi

sql_array_literal() {
    local result="ARRAY["
    local value
    for value in "$@"; do
        value="${value//\'/\'\'}"
        result="${result}'${value}',"
    done
    result="${result%,}]"
    printf '%s' "$result"
}

TARGET_SCHEMA_SQL="$(sql_array_literal "${TARGET_SCHEMAS[@]}")"
PRESERVED_TABLE_SQL="$(sql_array_literal "${PRESERVED_TABLES[@]}")"

psql_plain() {
    docker exec "$DB_CONTAINER" psql -q -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" "$@"
}

psql_tsv() {
    psql_plain -t -A -F $'\t' -c "$1"
}

COUNTS_SQL="
CREATE TEMP TABLE dev_cleanup_counts (
    schema_name text,
    table_name text,
    row_count bigint,
    preserved boolean
) ON COMMIT DROP;

DO \$\$
DECLARE
    item record;
    rows_count bigint;
BEGIN
    FOR item IN
        SELECT schemaname, tablename
        FROM pg_tables
        WHERE schemaname = ANY($TARGET_SCHEMA_SQL)
          AND tablename <> 'flyway_schema_history'
        ORDER BY schemaname, tablename
    LOOP
        EXECUTE format('SELECT count(*) FROM %I.%I', item.schemaname, item.tablename)
            INTO rows_count;
        INSERT INTO dev_cleanup_counts(schema_name, table_name, row_count, preserved)
        VALUES (
            item.schemaname,
            item.tablename,
            rows_count,
            item.tablename = ANY($PRESERVED_TABLE_SQL)
        );
    END LOOP;
END
\$\$;

SELECT schema_name, table_name, row_count, preserved
FROM dev_cleanup_counts
ORDER BY schema_name, table_name;
"

TRUNCATE_SQL="
DO \$\$
DECLARE
    table_list text;
BEGIN
    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ' ORDER BY schemaname, tablename)
    INTO table_list
    FROM pg_tables
    WHERE schemaname = ANY($TARGET_SCHEMA_SQL)
      AND tablename <> 'flyway_schema_history'
      AND NOT (tablename = ANY($PRESERVED_TABLE_SQL));

    IF table_list IS NULL THEN
        RAISE NOTICE 'No development tables matched cleanup criteria.';
    ELSE
        EXECUTE 'TRUNCATE TABLE ' || table_list || ' RESTART IDENTITY CASCADE';
    END IF;
END
\$\$;
"

mkdir -p "$REPORT_DIR"
BEFORE_TSV="$REPORT_DIR/before.tsv"
AFTER_TSV="$REPORT_DIR/after.tsv"
SUMMARY_MD="$REPORT_DIR/summary.md"

if ! psql_tsv "SELECT 1;" >/dev/null; then
    die "无法查询 $DB_CONTAINER 中的 $DB_NAME。"
fi

psql_tsv "$COUNTS_SQL" > "$BEFORE_TSV"

if [ "$EXECUTE" = "true" ]; then
    psql_plain -c "$TRUNCATE_SQL" >/dev/null
    psql_tsv "$COUNTS_SQL" > "$AFTER_TSV"
else
    : > "$AFTER_TSV"
fi

python3 - "$SUMMARY_MD" "$BEFORE_TSV" "$AFTER_TSV" "$EXECUTE" "$TS" "$DB_CONTAINER" "$DB_NAME" "${TARGET_SCHEMAS[*]}" "${PRESERVED_TABLES[*]}" <<'PY'
import csv
import sys
from pathlib import Path

summary_path = Path(sys.argv[1])
before_path = Path(sys.argv[2])
after_path = Path(sys.argv[3])
executed = sys.argv[4] == "true"
timestamp = sys.argv[5]
container = sys.argv[6]
database = sys.argv[7]
schemas = sys.argv[8]
preserved_tables = sys.argv[9]


def read_counts(path):
    rows = []
    if not path.exists() or path.stat().st_size == 0:
        return rows
    with path.open(newline="") as handle:
        reader = csv.reader(handle, delimiter="\t")
        for row in reader:
            if len(row) != 4:
                continue
            schema_name, table_name, row_count, preserved = row
            rows.append({
                "schema": schema_name,
                "table": table_name,
                "rows": int(row_count or "0"),
                "preserved": preserved.lower() == "t",
            })
    return rows


before = read_counts(before_path)
after = read_counts(after_path)
after_by_key = {(row["schema"], row["table"]): row for row in after}
candidate_before = [row for row in before if not row["preserved"]]
preserved_before = [row for row in before if row["preserved"]]
candidate_rows = sum(row["rows"] for row in candidate_before)
preserved_rows = sum(row["rows"] for row in preserved_before)

with summary_path.open("w") as out:
    out.write("# 本地开发数据库清理报告\n\n")
    out.write(f"- 生成时间：`{timestamp}`\n")
    out.write(f"- 模式：`{'execute' if executed else 'dry-run'}`\n")
    out.write(f"- 容器：`{container}`\n")
    out.write(f"- 数据库：`{database}`\n")
    out.write(f"- Schema：`{schemas}`\n")
    out.write(f"- 默认保留表：`{preserved_tables}`\n")
    out.write(f"- 清理候选表数：`{len(candidate_before)}`\n")
    out.write(f"- 清理候选行数：`{candidate_rows}`\n")
    out.write(f"- 保留表行数：`{preserved_rows}`\n")
    out.write(f"- 执行前计数：`{before_path}`\n")
    out.write(f"- 执行后计数：`{after_path}`\n\n")
    out.write("## 安全边界\n\n")
    out.write("- 仅允许本地 `releasehub-postgres` 容器和 `release_hub` 数据库。\n")
    out.write("- 不删除 schema，不删除 `flyway_schema_history`，不触碰 GitLab 远端资源。\n")
    out.write("- 默认保留 `users` 和 `system_settings`，避免清理后无法登录或丢失本地 GitLab 配置。\n")
    out.write("- `--include-users` 或 `--include-settings` 只适用于需要完全重置登录/配置数据的开发场景。\n\n")
    out.write("## 表计数\n\n")
    out.write("| Schema | 表 | 执行前 | 执行后 | 处理 |\n")
    out.write("|---|---|---:|---:|---|\n")
    for row in before:
        key = (row["schema"], row["table"])
        after_count = after_by_key.get(key, {}).get("rows")
        action = "保留" if row["preserved"] else ("已清理" if executed else "待清理")
        out.write(
            f"| `{row['schema']}` | `{row['table']}` | {row['rows']} | "
            f"{'' if after_count is None else after_count} | {action} |\n"
        )
PY

info "本地开发数据库清理报告已生成："
info "  $SUMMARY_MD"
info "  $BEFORE_TSV"
if [ "$EXECUTE" = "true" ]; then
    info "  $AFTER_TSV"
    info "清理已执行。"
else
    info "当前仅 dry-run。需要清理候选表时使用 --execute 重新运行。"
fi
