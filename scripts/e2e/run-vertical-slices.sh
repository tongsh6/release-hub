#!/bin/bash
set -e

echo "╔══════════════════════════════════════════╗"
echo "║  ReleaseHub Full-Link E2E Test Runner   ║"
echo "╚══════════════════════════════════════════╝"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROFILE="${SPRING_PROFILES_ACTIVE:-gitlab-e2e-local}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"

echo "Profile: $PROFILE"
echo "Backend: $BACKEND_URL"

# Wait for backend to be ready
echo "=== Waiting for backend ==="
for i in $(seq 1 30); do
  if curl -s -o /dev/null "$BACKEND_URL/actuator/health" 2>/dev/null; then
    echo "Backend is ready"
    break
  fi
  echo "Waiting... ($i/30)"
  sleep 3
done

PASSED=0
FAILED=0
RESULTS=""

run_slice() {
  local name=$1
  local test_class=$2
  echo ""
  echo "=== [$name] Starting ==="

  cd "$SCRIPT_DIR/../../backend"
  if mvn test -pl releasehub-bootstrap \
       -Dtest="$test_class" \
       -Dspring.profiles.active="$PROFILE" \
       -DfailIfNoTests=false -q 2>&1; then
    echo "=== [$name] ✅ PASSED ==="
    PASSED=$((PASSED + 1))
    RESULTS="$RESULTS\n  ✅ $name"
  else
    echo "=== [$name] ❌ FAILED ==="
    FAILED=$((FAILED + 1))
    RESULTS="$RESULTS\n  ❌ $name"
  fi
  cd "$SCRIPT_DIR"
}

# Run slices in dependency order
run_slice "Slice 1: Group + Window Lifecycle" "Slice1_Group_Window_Lifecycle_E2ETest"
run_slice "Slice 2: Repo + Iter + BranchRule" "Slice2_Repo_Iter_BranchRule_E2ETest"
run_slice "Slice 3: Release Orchestration"    "Slice3_Release_Orchestration_E2ETest"
run_slice "Slice 4: Post-Release Cleanup"     "Slice4_Post_Release_Cleanup_E2ETest"
run_slice "Slice 5: Conflict Detection"       "Slice5_Conflict_Detection_E2ETest"

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║  Results                                ║"
echo "╠══════════════════════════════════════════╣"
echo -e "$RESULTS"
echo "╠══════════════════════════════════════════╣"
printf "║  ✅ Passed: %d  ❌ Failed: %d              ║\n" $PASSED $FAILED
echo "╚══════════════════════════════════════════╝"

exit $FAILED
