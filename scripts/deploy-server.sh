#!/usr/bin/env bash
set -euo pipefail
compose_file=${COMPOSE_FILE:-docker-compose.server.yml}
docker compose -f "$compose_file" config --quiet
docker compose -f "$compose_file" up -d --force-recreate backend frontend simulator-001 simulator-002 simulator-003
docker compose -f "$compose_file" ps
