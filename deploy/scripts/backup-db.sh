#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
COMPOSE_FILE="$REPO_ROOT/deploy/compose/docker-compose.yml"
ENV_FILE="$REPO_ROOT/deploy/compose/.env"
BACKUP_DIR="${BACKUP_DIR:-$REPO_ROOT/deploy/backups}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE. Copy deploy/compose/.env.example to .env first." >&2
  exit 1
fi

read_env() {
  local key=$1
  local default_value=${2:-}
  local value
  value=$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d '=' -f 2- || true)
  value=${value%\"}
  value=${value#\"}
  value=${value%\'}
  value=${value#\'}
  printf '%s' "${value:-$default_value}"
}

POSTGRES_DB=${POSTGRES_DB:-$(read_env POSTGRES_DB releasehub)}
POSTGRES_USER=${POSTGRES_USER:-$(read_env POSTGRES_USER releasehub)}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-$(read_env POSTGRES_PASSWORD)}

: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in deploy/compose/.env}"

mkdir -p "$BACKUP_DIR"

timestamp=$(date +"%Y%m%d-%H%M%S")
backup_file="$BACKUP_DIR/releasehub-db-$timestamp.sql.gz"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "$backup_file"

echo "$backup_file"
