#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"
bash scripts/dev/validate-requirements-gate.sh

cd "$ROOT_DIR/release-hub"
mvn -q -B test

cd "$ROOT_DIR/release-hub-web"
pnpm -s typecheck
pnpm -s lint
pnpm -s test
pnpm -s build
