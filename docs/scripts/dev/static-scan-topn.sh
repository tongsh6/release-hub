#!/usr/bin/env bash

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TOP_N="${1:-${TOP_N:-10}}"

if ! [[ "$TOP_N" =~ ^[1-9][0-9]*$ ]]; then
  echo "TOP_N must be a positive integer, got: $TOP_N" >&2
  exit 2
fi

REPORT_ROOT="$ROOT_DIR/.ai/reports/static-scan"
STAMP="$(date +%Y%m%d-%H%M%S)"
REPORT_DIR="$REPORT_ROOT/$STAMP"
RAW_DIR="$REPORT_DIR/raw"
SUMMARY="$REPORT_DIR/summary.md"

mkdir -p "$RAW_DIR"

append() {
  printf '%s\n' "$*" >>"$SUMMARY"
}

escape_md_cell() {
  printf '%s' "$1" | tr '\n' ' ' | sed 's/|/\\|/g'
}

run_scan() {
  local name="$1"
  local workdir="$2"
  shift 2

  local log_file="$RAW_DIR/${name}.txt"
  append "- ${name}: \`$*\`"

  if [ ! -d "$workdir" ]; then
    {
      echo "SKIPPED: working directory not found: $workdir"
    } >"$log_file"
    append "  - 状态：SKIPPED，目录不存在"
    return 0
  fi

  (
    cd "$workdir" || exit 1
    "$@"
  ) >"$log_file" 2>&1
  local status=$?

  if [ "$status" -eq 0 ]; then
    append "  - 状态：PASS"
  else
    append "  - 状态：FAIL，退出码 $status"
  fi
}

find_top_findings() {
  local pattern='error|warning|bug|violation|vulnerability|security|failed|failure|exception|严重|错误|警告|漏洞|违规|失败'

  if command -v rg >/dev/null 2>&1; then
    rg -n -i "$pattern" "$RAW_DIR" 2>/dev/null | head -n "$TOP_N"
  else
    grep -RInEi "$pattern" "$RAW_DIR" 2>/dev/null | head -n "$TOP_N"
  fi
}

changed_files() {
  (
    cd "$ROOT_DIR" || exit 1
    {
      git diff --name-only --diff-filter=ACMRTUXB HEAD -- 2>/dev/null
      git ls-files --others --exclude-standard 2>/dev/null
    } | grep -v '^.ai/reports/static-scan/' | sort -u
  )
}

append "# 静态代码扫描与 TopN 处理报告"
append ""
append "- 时间：$(date '+%Y-%m-%d %H:%M:%S %z')"
append "- 仓库：\`$ROOT_DIR\`"
append "- TopN：$TOP_N"
append ""
append "## 变更范围"
append ""
changed_files | sed 's/^/- `/' | sed 's/$/`/' >>"$SUMMARY"
append ""
append "## 扫描命令"
append ""

run_scan "git-diff-check" "$ROOT_DIR" git diff --check

if [ -f "$ROOT_DIR/backend/pom.xml" ]; then
  if [ -x "$ROOT_DIR/backend/mvnw" ]; then
    MVN_CMD="./mvnw"
  else
    MVN_CMD="mvn"
  fi

  if grep -q "<id>quality</id>" "$ROOT_DIR/backend/pom.xml"; then
    run_scan "backend-quality" "$ROOT_DIR/backend" "$MVN_CMD" -q -B -DskipTests verify -Pquality
  else
    run_scan "backend-spotbugs" "$ROOT_DIR/backend" "$MVN_CMD" -q -B -DskipTests com.github.spotbugs:spotbugs-maven-plugin:4.9.8.3:check
  fi
else
  run_scan "backend-static-scan" "$ROOT_DIR/backend" true
fi

if [ -f "$ROOT_DIR/frontend/package.json" ]; then
  run_scan "frontend-lint" "$ROOT_DIR/frontend" pnpm -s lint
  run_scan "frontend-typecheck" "$ROOT_DIR/frontend" pnpm -s typecheck
else
  run_scan "frontend-static-scan" "$ROOT_DIR/frontend" true
fi

append ""
append "## TopN 问题清单"
append ""
append "| # | 扫描来源 | 问题摘要 | 优先级依据 | 处理方式 | 处理结果 | 复扫证据 |"
append "|---|----------|----------|------------|----------|----------|----------|"

mapfile -t FINDINGS < <(find_top_findings)

if [ "${#FINDINGS[@]}" -eq 0 ]; then
  append "| 1 | 全部扫描 | 未提取到错误/警告/安全类问题 | 无 | 无需处理 | PASS | 本报告 raw 日志 |"
else
  index=1
  for finding in "${FINDINGS[@]}"; do
    source_file="${finding%%:*}"
    rest="${finding#*:}"
    line_no="${rest%%:*}"
    message="${rest#*:}"
    append "| $index | \`$(escape_md_cell "${source_file#$RAW_DIR/}:$line_no")\` | $(escape_md_cell "$message") | 靠前且匹配错误/警告/安全关键词 | 待 AI 修复后填写 | 待复扫 | 待复扫 |"
    index=$((index + 1))
  done
fi

append ""
append "## TopN 处理要求"
append ""
append "- AI 必须优先处理上表 TopN；若跳过某项，必须在“处理方式”中写明原因。"
append "- 修复后必须再次运行本脚本或覆盖相关变更范围的静态扫描命令。"
append "- 最终交付必须引用本报告路径，并说明 TopN 中每项的处理结果。"
append "- 原始扫描日志保留在 \`raw/\` 目录。"

echo "$SUMMARY"
