#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
COMPOSE_FILE="$REPO_ROOT/deploy/compose/docker-compose.yml"
ENV_FILE="$REPO_ROOT/deploy/compose/.env"

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

FRONTEND_PORT=${FRONTEND_PORT:-$(read_env FRONTEND_PORT 8090)}
BACKEND_PORT=${BACKEND_PORT:-$(read_env BACKEND_PORT 8080)}

frontend_url="http://127.0.0.1:$FRONTEND_PORT"
backend_url="http://127.0.0.1:$BACKEND_PORT/actuator/health"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps

curl -fsS "$backend_url" >/dev/null
curl -fsS "$frontend_url" >/dev/null

echo "ReleaseHub is healthy: frontend=$frontend_url backend=$backend_url"
