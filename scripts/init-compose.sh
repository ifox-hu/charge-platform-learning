#!/usr/bin/env bash
set -euo pipefail
[[ -f .env ]] || cp .env.example .env
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps
if [[ "${1:-}" == "--seed" ]]; then docker compose -f docker-compose.yml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' < docs/demo_seed.sql; fi
