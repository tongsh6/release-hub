#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
COMPOSE_FILE="$REPO_ROOT/deploy/compose/docker-compose.yml"
ENV_FILE="$REPO_ROOT/deploy/compose/.env"
TARGET_REF="${1:-}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE. Copy deploy/compose/.env.example to .env first." >&2
  exit 1
fi

cd "$REPO_ROOT"

if [ -n "$TARGET_REF" ]; then
  git fetch --tags
  git checkout "$TARGET_REF"
fi

backup_path=$(deploy/scripts/backup-db.sh)
echo "Database backup created: $backup_path"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d

deploy/scripts/healthcheck.sh
