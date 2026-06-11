# ReleaseHub Docker Compose Deployment

This directory is the canonical open-source single-server deployment entrypoint.

## Start

```bash
cp deploy/compose/.env.example deploy/compose/.env
$EDITOR deploy/compose/.env
docker compose --env-file deploy/compose/.env -f deploy/compose/docker-compose.yml up -d --build
```

Open the frontend at the `APP_BASE_URL` configured in `.env`.

## GitLab

ReleaseHub does not bundle GitLab in this deployment. After login, configure your existing GitLab instance in the Settings page.

Required GitLab token scopes:

- `api`
- `read_repository`
- `write_repository`

## Operations

```bash
deploy/scripts/healthcheck.sh
deploy/scripts/backup-db.sh
deploy/scripts/upgrade.sh v0.1.13
```

See `docs/deploy/docker-compose.md` and `docs/deploy/upgrade.md` for the full guide.
