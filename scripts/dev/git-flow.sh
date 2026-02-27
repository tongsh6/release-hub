#!/bin/bash
# =============================================================================
# ReleaseHub GitFlow Helper
# =============================================================================
# 用法: scripts/dev/git-flow.sh <command> [name/version]
#
# GitFlow 流程:
#   1. feature:start <name>   从 main 创建 feature/name 分支
#   2. ... 开发 ...           提交代码
#   3. feature:finish <name>  推送 feature 分支，引导创建 PR 到 release
#   4. release:start <ver>    从 main 创建 release/vX.Y.Z 分支
#   5. 通过 PR 合并各 feature 到 release
#   6. release:finish <ver>   打标签，合并 release → main，删除 release 分支
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log()     { echo -e "${BLUE}▶${NC} $*"; }
success() { echo -e "${GREEN}✅${NC} $*"; }
warn()    { echo -e "${YELLOW}⚠️ ${NC} $*"; }
info()    { echo -e "${CYAN}ℹ️ ${NC} $*"; }
die()     { echo -e "${RED}❌${NC} $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Guards
# ---------------------------------------------------------------------------

ensure_clean() {
  if [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]; then
    die "Working tree is dirty. Commit or stash changes first."
  fi
}

ensure_on_branch() {
  local expected="$1"
  local current
  current="$(git -C "$ROOT_DIR" branch --show-current)"
  if [[ "$current" != "$expected" ]]; then
    die "Expected to be on '$expected', but currently on '$current'. Switch manually."
  fi
}

branch_exists_remote() {
  git -C "$ROOT_DIR" ls-remote --heads origin "$1" | grep -q "$1"
}

# ---------------------------------------------------------------------------
# feature:start <name>
# ---------------------------------------------------------------------------
feature_start() {
  local name="${1:-}"
  [[ -z "$name" ]] && die "Usage: $0 feature:start <name>  # e.g. pagination-fix"
  ensure_clean
  log "Syncing main..."
  git -C "$ROOT_DIR" checkout main
  git -C "$ROOT_DIR" pull origin main
  log "Creating feature/$name..."
  git -C "$ROOT_DIR" checkout -b "feature/$name"
  success "Branch 'feature/$name' ready. Start developing!"
  echo ""
  info "When done: $0 feature:finish $name"
}

# ---------------------------------------------------------------------------
# feature:finish <name>
# ---------------------------------------------------------------------------
feature_finish() {
  local name="${1:-}"
  [[ -z "$name" ]] && die "Usage: $0 feature:finish <name>"
  ensure_on_branch "feature/$name"
  log "Pushing feature/$name..."
  git -C "$ROOT_DIR" push -u origin "feature/$name"
  success "Pushed 'feature/$name'"
  echo ""
  info "Next — Create a PR to merge into the release branch:"
  echo "  gh pr create --title 'feat: $name' --base release/vX.Y.Z --delete-branch"
  echo ""
  info "  Or if going directly to main:"
  echo "  gh pr create --title 'feat: $name' --base main --delete-branch"
}

# ---------------------------------------------------------------------------
# release:start <version>
# ---------------------------------------------------------------------------
release_start() {
  local version="${1:-}"
  [[ -z "$version" ]] && die "Usage: $0 release:start <version>  # e.g. 0.3.0"
  ensure_clean
  log "Syncing main..."
  git -C "$ROOT_DIR" checkout main
  git -C "$ROOT_DIR" pull origin main
  log "Creating release/v$version..."
  git -C "$ROOT_DIR" checkout -b "release/v$version"
  git -C "$ROOT_DIR" push -u origin "release/v$version"
  success "Release branch 'release/v$version' created and pushed."
  echo ""
  info "Next — Merge feature branches into this release via PRs:"
  echo "  gh pr create --title 'feat: <name>' --base release/v$version --delete-branch"
  echo ""
  info "When all features are merged: $0 release:finish $version"
}

# ---------------------------------------------------------------------------
# release:finish <version>
# ---------------------------------------------------------------------------
release_finish() {
  local version="${1:-}"
  [[ -z "$version" ]] && die "Usage: $0 release:finish <version>  # e.g. 0.3.0"
  ensure_clean
  ensure_on_branch "release/v$version"

  log "Pulling latest release/v$version..."
  git -C "$ROOT_DIR" pull origin "release/v$version"

  # --- Tag ---
  if git -C "$ROOT_DIR" tag | grep -q "^v$version$"; then
    warn "Tag v$version already exists, skipping tag creation."
  else
    log "Creating tag v$version..."
    git -C "$ROOT_DIR" tag "v$version" -m "Release v$version"
    git -C "$ROOT_DIR" push origin "v$version"
    success "Tagged v$version"
  fi

  # --- Merge to main ---
  log "Merging release/v$version into main..."
  git -C "$ROOT_DIR" checkout main
  git -C "$ROOT_DIR" pull origin main
  git -C "$ROOT_DIR" merge "release/v$version" --no-ff -m "chore: merge release/v$version into main"
  git -C "$ROOT_DIR" push origin main
  success "Merged into main"

  # --- Delete release branch ---
  log "Deleting release/v$version..."
  git -C "$ROOT_DIR" branch -d "release/v$version"
  git -C "$ROOT_DIR" push origin --delete "release/v$version"
  success "Deleted remote release branch"

  echo ""
  echo -e "${GREEN}🎉 Released v$version!${NC}"
  echo ""
  info "Cleanup: Delete any merged feature branches still on remote:"
  echo "  git branch -r | grep 'origin/feature/' | sed 's/origin\\///' | xargs git push origin --delete"
}

# ---------------------------------------------------------------------------
# status
# ---------------------------------------------------------------------------
show_status() {
  echo -e "${CYAN}=== GitFlow Status ===${NC}"
  echo ""
  echo -e "${BLUE}main (latest 5):${NC}"
  git -C "$ROOT_DIR" log main --oneline -5 | sed 's/^/  /'
  echo ""
  echo -e "${BLUE}Feature branches:${NC}"
  git -C "$ROOT_DIR" branch -a --format='%(refname:short)' | grep 'feature/' | sed 's|remotes/origin/||' | sort -u | sed 's/^/  /' || echo "  (none)"
  echo ""
  echo -e "${BLUE}Release branches:${NC}"
  git -C "$ROOT_DIR" branch -a --format='%(refname:short)' | grep 'release/' | sed 's|remotes/origin/||' | sort -u | sed 's/^/  /' || echo "  (none)"
  echo ""
  echo -e "${BLUE}Tags (latest 5):${NC}"
  git -C "$ROOT_DIR" tag --sort=-version:refname | head -5 | sed 's/^/  v/' || echo "  (none)"
}

# ---------------------------------------------------------------------------
# feature:delete <name>  — 删除已合并的 feature 分支
# ---------------------------------------------------------------------------
feature_delete() {
  local name="${1:-}"
  [[ -z "$name" ]] && die "Usage: $0 feature:delete <name>"
  log "Deleting feature/$name locally and remotely..."
  git -C "$ROOT_DIR" branch -d "feature/$name" 2>/dev/null && success "Deleted local branch" || warn "Local branch not found (already deleted?)"
  git -C "$ROOT_DIR" push origin --delete "feature/$name" 2>/dev/null && success "Deleted remote branch" || warn "Remote branch not found (already deleted?)"
}

# ---------------------------------------------------------------------------
# Main dispatcher
# ---------------------------------------------------------------------------
COMMAND="${1:-help}"
shift || true

case "$COMMAND" in
  "feature:start"|"feat:start")
    feature_start "$@"
    ;;
  "feature:finish"|"feat:finish")
    feature_finish "$@"
    ;;
  "feature:delete"|"feat:delete")
    feature_delete "$@"
    ;;
  "release:start")
    release_start "$@"
    ;;
  "release:finish")
    release_finish "$@"
    ;;
  "status")
    show_status
    ;;
  "help"|"--help"|"-h")
    echo -e "${CYAN}ReleaseHub GitFlow Helper${NC}"
    echo ""
    echo "Usage: scripts/dev/git-flow.sh <command> [arg]"
    echo ""
    echo "Commands:"
    echo "  feature:start <name>      从 main 创建 feature/name 分支"
    echo "  feature:finish <name>     推送 feature 分支（然后手动创建 PR）"
    echo "  feature:delete <name>     删除已合并的 feature 分支（本地 + remote）"
    echo "  release:start <version>   从 main 创建 release/vX.Y.Z 分支"
    echo "  release:finish <version>  打标签，合并 main，删除 release 分支"
    echo "  status                    查看当前 GitFlow 状态"
    echo ""
    echo -e "${YELLOW}完整 GitFlow 流程:${NC}"
    echo "  1.  feature:start <name>          # 创建 feature 分支"
    echo "  2.  ... 开发 + git commit ...     # 提交代码"
    echo "  3.  feature:finish <name>         # 推送 + 创建 PR 到 release"
    echo "  4.  release:start <version>       # 创建 release 分支"
    echo "  5.  合并所有 feature PR 到 release"
    echo "  6.  release:finish <version>      # 打标签 + 合并 main + 清理"
    echo "  7.  feature:delete <name>         # 删除已合并的 feature 分支"
    echo ""
    echo -e "${YELLOW}示例:${NC}"
    echo "  $0 feature:start pagination-fix"
    echo "  $0 feature:finish pagination-fix"
    echo "  $0 release:start 0.3.0"
    echo "  $0 release:finish 0.3.0"
    ;;
  *)
    die "Unknown command: $COMMAND. Run '$0 help' for usage."
    ;;
esac
