#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
COMPOSE_FILE="$REPO_ROOT/deploy/compose/docker-compose.yml"
ENV_FILE="$REPO_ROOT/deploy/compose/.env"

yes_flag=false
backup_file=""

for arg in "$@"; do
  case "$arg" in
    --yes)
      yes_flag=true
      ;;
    *)
      backup_file="$arg"
      ;;
  esac
done

if [ -z "$backup_file" ] || [ ! -f "$backup_file" ]; then
  echo "Usage: deploy/scripts/restore-db.sh [--yes] <backup.sql.gz>" >&2
  exit 1
fi

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

if [ "$yes_flag" != "true" ]; then
  echo "This will replace database '$POSTGRES_DB' in the running postgres container."
  read -r -p "Type RESTORE to continue: " answer
  if [ "$answer" != "RESTORE" ]; then
    echo "Restore cancelled." >&2
    exit 1
  fi
fi

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" stop backend frontend

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres dropdb --if-exists --maintenance-db=postgres -U "$POSTGRES_USER" "$POSTGRES_DB"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres createdb --maintenance-db=postgres -U "$POSTGRES_USER" "$POSTGRES_DB"

gunzip -c "$backup_file" | docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T \
  -e PGPASSWORD="$POSTGRES_PASSWORD" \
  postgres psql -U "$POSTGRES_USER" "$POSTGRES_DB"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
echo "Database restored from $backup_file"
