#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "requirements-gate: $1" >&2
  exit 1
}

extract_requirement_path() {
  local proposal_file="$1"
  rg -o -m 1 'requirements/(in-progress|completed)/[^ )]+' "$proposal_file" || true
}

requirement_backlink_expected() {
  local change_id="$1"
  echo "openspec/changes/${change_id}/proposal.md"
}

validate_change() {
  local change_dir="$1"
  local change_id
  change_id="$(basename "$change_dir")"

  local proposal="$change_dir/proposal.md"
  [[ -f "$proposal" ]] || fail "missing proposal.md for change '${change_id}'"

  local req_path
  req_path="$(extract_requirement_path "$proposal")"
  [[ -n "$req_path" ]] || fail "missing requirement link in '${proposal}' (expected 'requirements/in-progress/..' or 'requirements/completed/..')"

  local req_abs="$ROOT_DIR/$req_path"
  [[ -f "$req_abs" ]] || fail "requirement doc not found: ${req_path} (referenced by '${proposal}')"

  if [[ "$req_path" == requirements/in-progress/* ]]; then
    local req_rel_in_index="${req_path#requirements/}"
    rg -qF "$req_path" "$ROOT_DIR/requirements/INDEX.md" || rg -qF "$req_rel_in_index" "$ROOT_DIR/requirements/INDEX.md" || fail "requirement doc not registered in requirements/INDEX.md: ${req_path}"
  fi

  local expected_backlink
  expected_backlink="$(requirement_backlink_expected "$change_id")"
  rg -qF "$expected_backlink" "$req_abs" || fail "requirement doc missing backlink to change proposal: ${expected_backlink}"
}

main() {
  local changes_dir="$ROOT_DIR/openspec/changes"
  [[ -d "$changes_dir" ]] || fail "openspec/changes not found"

  local change_dir
  for change_dir in "$changes_dir"/*; do
    [[ -d "$change_dir" ]] || continue
    [[ "$(basename "$change_dir")" == "archive" ]] && continue
    validate_change "$change_dir"
  done

  echo "requirements-gate: OK"
}

main "$@"
