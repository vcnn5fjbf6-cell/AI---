#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ ! -f "$DEPLOY_DIR/.env" ]; then
  echo "[ERROR] $DEPLOY_DIR/.env 不存在，请先从 .env.example 复制"
  exit 1
fi

cd "$DEPLOY_DIR"
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml ps
