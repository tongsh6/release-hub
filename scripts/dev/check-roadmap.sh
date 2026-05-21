#!/usr/bin/env bash
#
# check-roadmap.sh — validates docs/execution-roadmap.md as a single task pointer.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && git rev-parse --show-toplevel 2>/dev/null || pwd)"
ROADMAP="$REPO_ROOT/docs/execution-roadmap.md"
MATRIX="$REPO_ROOT/docs/reports/scenario-acceptance-matrix.md"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

pass() {
    echo "PASS: $*"
}

[ -f "$ROADMAP" ] || fail "missing docs/execution-roadmap.md"
[ -f "$MATRIX" ] || fail "missing docs/reports/scenario-acceptance-matrix.md"

HEAD_COUNT=$(grep -Ec '^[|][[:space:]]*[0-9]+[[:space:]]*[|][[:space:]]*HEAD[[:space:]]*[|]' "$ROADMAP" || true)
[ "$HEAD_COUNT" -eq 1 ] || fail "execution-roadmap.md must contain exactly one HEAD row, found $HEAD_COUNT"

HEAD_ROW=$(grep -E '^[|][[:space:]]*[0-9]+[[:space:]]*[|][[:space:]]*HEAD[[:space:]]*[|]' "$ROADMAP")

if echo "$HEAD_ROW" | grep -Eq '或|任选|待定|二选一'; then
    fail "HEAD row contains ambiguous task-selection wording: $HEAD_ROW"
fi

if echo "$HEAD_ROW" | grep -q '后续保持回归'; then
    fail "HEAD row points to a regression-only item: $HEAD_ROW"
fi

SA_ID=$(echo "$HEAD_ROW" | sed -E 's/.*(SA-[0-9]{3}).*/\1/')
[ -n "$SA_ID" ] || fail "HEAD row must contain an SA-xxx identifier"

if ! grep -q "| $SA_ID |" "$MATRIX"; then
    fail "$SA_ID does not exist in scenario-acceptance-matrix.md"
fi

if ! grep -q 'docs/reports/scenario-acceptance-matrix.md' "$ROADMAP"; then
    fail "execution-roadmap.md must cite scenario-acceptance-matrix.md as an authority"
fi

if ! grep -q 'docs/project-ledger.md' "$ROADMAP"; then
    fail "execution-roadmap.md must cite project-ledger.md as an authority"
fi

pass "roadmap HEAD is unique and points to $SA_ID"
