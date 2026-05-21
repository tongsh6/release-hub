#!/usr/bin/env bash
#
# static-scan-topn.sh — ReleaseHub 静态代码扫描统一入口
#
# 用法:
#   scripts/dev/static-scan-topn.sh [TopN]
#   TOP_N=5 scripts/dev/static-scan-topn.sh
#
# 产出:
#   .ai/reports/static-scan/<timestamp>/summary.md
#   .ai/reports/static-scan/<timestamp>/raw/*.txt

set -euo pipefail

TOP_N="${1:-${TOP_N:-10}}"
REPO_ROOT="$(cd "$(dirname "$0")" && git rev-parse --show-toplevel 2>/dev/null || pwd)"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPORT_DIR="$REPO_ROOT/.ai/reports/static-scan/$TIMESTAMP"
RAW_DIR="$REPORT_DIR/raw"

mkdir -p "$RAW_DIR"

echo "==> ReleaseHub Static Scan Top${TOP_N}"
echo "    报告目录: $REPORT_DIR"
echo ""

# ---- 1. git diff --check (whitespace) ----
echo "--- 1/4 git diff --check ---"
if git -C "$REPO_ROOT" diff --check > "$RAW_DIR/git-diff-check.txt" 2>&1; then
    echo "    PASS"
else
    echo "    FAIL (see raw/git-diff-check.txt)"
fi

# ---- 2. Backend: SpotBugs ----
echo "--- 2/4 backend SpotBugs ---"
BACKEND_DIR="$REPO_ROOT/backend"
if [ -f "$BACKEND_DIR/pom.xml" ]; then
    cd "$BACKEND_DIR"
    if mvn -q -B -DskipTests \
        install \
        com.github.spotbugs:spotbugs-maven-plugin:4.9.8.3:check \
        > "$RAW_DIR/backend-spotbugs.txt" 2>&1; then
        echo "    PASS (0 bugs)"
    else
        echo "    DONE (bugs found, see raw/backend-spotbugs.txt)"
    fi
    cd "$REPO_ROOT"
else
    echo "    SKIP (no backend/pom.xml)"
    echo "SKIP: no backend/pom.xml" > "$RAW_DIR/backend-spotbugs.txt"
fi

# ---- 3. Frontend: ESLint ----
echo "--- 3/4 frontend lint ---"
FRONTEND_DIR="$REPO_ROOT/frontend"
if [ -f "$FRONTEND_DIR/package.json" ]; then
    cd "$FRONTEND_DIR"
    if pnpm -s lint > "$RAW_DIR/frontend-lint.txt" 2>&1; then
        echo "    PASS"
    else
        echo "    DONE (issues found, see raw/frontend-lint.txt)"
    fi
    cd "$REPO_ROOT"
else
    echo "    SKIP (no frontend/package.json)"
    echo "SKIP: no frontend/package.json" > "$RAW_DIR/frontend-lint.txt"
fi

# ---- 4. Frontend: TypeScript typecheck ----
echo "--- 4/4 frontend typecheck ---"
if [ -f "$FRONTEND_DIR/package.json" ]; then
    cd "$FRONTEND_DIR"
    if pnpm -s typecheck > "$RAW_DIR/frontend-typecheck.txt" 2>&1; then
        echo "    PASS"
    else
        echo "    DONE (issues found, see raw/frontend-typecheck.txt)"
    fi
    cd "$REPO_ROOT"
else
    echo "    SKIP (no frontend/package.json)"
    echo "SKIP: no frontend/package.json" > "$RAW_DIR/frontend-typecheck.txt"
fi

echo ""

# ---- Build summary.md ----
SUMMARY="$REPORT_DIR/summary.md"

# Extract real SpotBugs bug lines (not Maven noise)
# SpotBugs bug patterns: https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html
SPOTBUGS_PATTERNS='EI_EXPOSE_REP\|EI_EXPOSE_REP2\|MS_SHOULD_BE_FINAL\|DMI_\|NP_\|SQL_BAD_\|XSS_REQUEST_\|PATH_TRAVERSAL_\|SEC_\|SERVLET_\|REC_CATCH\|RV_RETURN\|URLCONNECTION_\|DM_DEFAULT\|SE_\|UL_\|UG_\|URF_UNREAD\|UW_UNCOND\|UWF_UNWRITTEN\|BC_\|BIT_\|CN_\|Co_\|DB_\|DL_\|DM_\|DP_\|EC_\|EI2_\|Eq_\|ES_\|FE_\|FS_\|GC_\|HE_\|IA_\|IC_\|IM_\|IS_\|IT_\|JCIP_\|JLM_\|LI_\|MC_\|ML_\|MTIA_\|MWN_\|Nm_\|NN_\|No_\|OBL_\|ODR_\|OS_\|PZLA_\|QBA_\|RC_\|RpC_\|RS_\|Ru_\|SA_\|SF_\|SI_\|SIC_\|SS_\|ST_\|SW_\|TL_\|UMAC_\|UP_\|UTA_\|VA_\|VR_\|WL_'

extract_bugs() {
    local f="$1"
    if [ -f "$f" ]; then
        grep '\[ERROR\].*At ' "$f" 2>/dev/null | grep -i "$SPOTBUGS_PATTERNS" || true
    fi
}

# Count real bugs
count_real_bugs() {
    local f="$1"
    extract_bugs "$f" | wc -l | tr -d ' '
}

has_maven_scan_failure() {
    local f="$1"
    [ -f "$f" ] && grep -qE 'BUILD FAILURE|Failed to execute goal|Could not resolve dependencies|No plugin found for prefix|Plugin .* not found' "$f" 2>/dev/null
}

count_eslint_errors() {
    local f="$1"
    if [ -f "$f" ]; then
        { grep -E '^[[:space:]]+[0-9]+:[0-9]+[[:space:]]+error[[:space:]]+' "$f" 2>/dev/null || true; } | wc -l | tr -d ' '
    else
        echo 0
    fi
}

count_eslint_warnings() {
    local f="$1"
    if [ -f "$f" ]; then
        { grep -E '^[[:space:]]+[0-9]+:[0-9]+[[:space:]]+warning[[:space:]]+' "$f" 2>/dev/null || true; } | wc -l | tr -d ' '
    else
        echo 0
    fi
}

cat > "$SUMMARY" << 'HEADER'
# 静态代码扫描与 TopN 处理报告

HEADER

{
    echo "- 时间：$(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "- 仓库：\`$REPO_ROOT\`"
    echo "- TopN：$TOP_N"
    echo "- 处理人：AI"
    echo ""
    echo "## 扫描结果"
    echo ""
    echo "| 扫描工具 | 结果 |"
    echo "|---------|------|"

    # git diff --check: empty output = PASS, non-empty = issues found
    if [ -s "$RAW_DIR/git-diff-check.txt" ]; then
        echo "| git diff --check | ❌ FAIL |"
    else
        echo "| git diff --check | ✅ PASS |"
    fi

    # SpotBugs
    SPOTBUGS_COUNT=$(count_real_bugs "$RAW_DIR/backend-spotbugs.txt")
    if has_maven_scan_failure "$RAW_DIR/backend-spotbugs.txt"; then
        echo "| backend SpotBugs | ❌ FAIL (scan failed) |"
    elif [ "$SPOTBUGS_COUNT" -eq 0 ]; then
        echo "| backend SpotBugs | ✅ PASS (0 bugs) |"
    else
        echo "| backend SpotBugs | ⚠️ $SPOTBUGS_COUNT bugs |"
    fi

    # ESLint: distinguish errors from warnings; warnings still deserve report visibility.
    ESLINT_ERROR_COUNT=$(count_eslint_errors "$RAW_DIR/frontend-lint.txt")
    ESLINT_WARNING_COUNT=$(count_eslint_warnings "$RAW_DIR/frontend-lint.txt")
    if [ "$ESLINT_ERROR_COUNT" -gt 0 ]; then
        echo "| frontend ESLint | ❌ $ESLINT_ERROR_COUNT errors |"
    elif [ "$ESLINT_WARNING_COUNT" -gt 0 ]; then
        echo "| frontend ESLint | ⚠️ $ESLINT_WARNING_COUNT warnings |"
    else
        echo "| frontend ESLint | ✅ PASS |"
    fi

    # Typecheck
    if grep -qi 'error' "$RAW_DIR/frontend-typecheck.txt" 2>/dev/null; then
        echo "| frontend typecheck | ❌ FAIL |"
    else
        echo "| frontend typecheck | ✅ PASS |"
    fi

    echo ""
    echo "## TopN 问题清单与处理结论"
    echo ""
    echo "| # | 扫描来源 | 问题摘要 | 优先级 | 处理方式 | 处理结果 | 复扫证据 |"
    echo "|---|---------|---------|--------|---------|---------|---------|"

    # Extract TopN real issues (prioritize SpotBugs -> typecheck -> ESLint)
    issue_count=0

    # Phase 0: scan execution failures
    if has_maven_scan_failure "$RAW_DIR/backend-spotbugs.txt"; then
        issue_count=$((issue_count + 1))
        first_error=$(grep -E 'Failed to execute goal|Could not resolve dependencies|No plugin found for prefix|BUILD FAILURE' "$RAW_DIR/backend-spotbugs.txt" 2>/dev/null | head -1)
        echo "| $issue_count | backend-spotbugs | $(echo "$first_error" | sed 's/|/\\|/g' | cut -c1-100) | 高 | 修复扫描命令或 Maven 环境 | 待处理 | raw/backend-spotbugs.txt |"
    fi

    # Phase 1: real SpotBugs bugs
    if [ "$issue_count" -lt "$TOP_N" ]; then
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            issue_count=$((issue_count + 1))
            [ "$issue_count" -gt "$TOP_N" ] && break
            echo "| $issue_count | backend-spotbugs | $(echo "$line" | sed 's/|/\\|/g' | cut -c1-100) | 中 | 待修复 | — | — |"
        done < <(extract_bugs "$RAW_DIR/backend-spotbugs.txt")
    fi

    # Phase 2: typecheck errors (if TopN not yet reached)
    if [ "$issue_count" -lt "$TOP_N" ] && [ -f "$RAW_DIR/frontend-typecheck.txt" ]; then
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            issue_count=$((issue_count + 1))
            [ "$issue_count" -gt "$TOP_N" ] && break
            echo "| $issue_count | frontend-typecheck | $(echo "$line" | sed 's/|/\\|/g' | cut -c1-100) | 中 | 待修复 | — | — |"
        done < <(grep -i 'error' "$RAW_DIR/frontend-typecheck.txt" 2>/dev/null | head -"$((TOP_N - issue_count))")
    fi

    # Phase 3: ESLint errors (if TopN not yet reached)
    if [ "$issue_count" -lt "$TOP_N" ] && [ -f "$RAW_DIR/frontend-lint.txt" ]; then
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            issue_count=$((issue_count + 1))
            [ "$issue_count" -gt "$TOP_N" ] && break
            echo "| $issue_count | frontend-lint | $(echo "$line" | sed 's/|/\\|/g' | cut -c1-100) | 低 | 待修复 | — | — |"
        done < <(grep -E '^\s+[0-9]+:[0-9]+\s+error' "$RAW_DIR/frontend-lint.txt" 2>/dev/null | head -"$((TOP_N - issue_count))")
    fi

    # Phase 4: ESLint warnings (if TopN not yet reached)
    if [ "$issue_count" -lt "$TOP_N" ] && [ -f "$RAW_DIR/frontend-lint.txt" ]; then
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            issue_count=$((issue_count + 1))
            [ "$issue_count" -gt "$TOP_N" ] && break
            echo "| $issue_count | frontend-lint | $(echo "$line" | sed 's/|/\\|/g' | cut -c1-100) | 低 | 待修复 | — | — |"
        done < <(grep -E '^\s+[0-9]+:[0-9]+\s+warning' "$RAW_DIR/frontend-lint.txt" 2>/dev/null | head -"$((TOP_N - issue_count))")
    fi

    # If no issues found
    if [ "$issue_count" -eq 0 ]; then
        echo "| — | — | 未发现代码问题 | — | — | — | — |"
    fi

    echo ""
    echo "## 质量基线"
    echo ""
    echo "| 指标 | 状态 |"
    echo "|------|:----:|"
    if [ -s "$RAW_DIR/git-diff-check.txt" ]; then
        echo "| git diff --check | ❌ |"
    else
        echo "| git diff --check | ✅ |"
    fi
    if has_maven_scan_failure "$RAW_DIR/backend-spotbugs.txt"; then
        echo "| backend SpotBugs (新引入) | ❌ |"
    elif [ "$(count_real_bugs "$RAW_DIR/backend-spotbugs.txt")" -eq 0 ]; then
        echo "| backend SpotBugs (新引入) | ✅ |"
    else
        echo "| backend SpotBugs (新引入) | ⚠️ |"
    fi
    if [ "$(count_eslint_errors "$RAW_DIR/frontend-lint.txt")" -gt 0 ]; then
        echo "| frontend ESLint | ❌ |"
    elif [ "$(count_eslint_warnings "$RAW_DIR/frontend-lint.txt")" -gt 0 ]; then
        echo "| frontend ESLint | ⚠️ |"
    else
        echo "| frontend ESLint | ✅ |"
    fi
    if grep -qi 'error' "$RAW_DIR/frontend-typecheck.txt" 2>/dev/null; then
        echo "| frontend typecheck | ❌ |"
    else
        echo "| frontend typecheck | ✅ |"
    fi

    echo ""
    echo "原始扫描日志保留在 \`raw/\` 目录。"
} >> "$SUMMARY"

echo "==> 报告已生成: $SUMMARY"
echo "==> 原始日志: $RAW_DIR/"
echo ""
echo "TopN 问题摘要:"
extract_bugs "$RAW_DIR/backend-spotbugs.txt" | head -"$TOP_N"
