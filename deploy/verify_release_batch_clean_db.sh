#!/usr/bin/env bash
set -euo pipefail

TRUSTED_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="${TEST_SOURCE_ROOT:?TEST_SOURCE_ROOT is required}"
DEPLOY_ROOT="${TEST_DEPLOY_ROOT:-/opt/procurement-test}"
ENV_FILE="$DEPLOY_ROOT/deploy/.env.test"
BASELINE_FILE="$DEPLOY_ROOT/deploy/db/test-init/runtime/001_baseline.sql"
BASELINE_REF_FILE="$DEPLOY_ROOT/deploy/db/test-init/runtime/001_baseline.sha"
BUILD_REF="${TEST_BUILD_REF:?TEST_BUILD_REF is required}"
RELEASE_BATCH_ID="${RELEASE_BATCH_ID:?RELEASE_BATCH_ID is required}"
TEST_BACKEND_IMAGE="${TEST_BACKEND_IMAGE:?TEST_BACKEND_IMAGE is required}"
TEST_FRONTEND_IMAGE="${TEST_FRONTEND_IMAGE:?TEST_FRONTEND_IMAGE is required}"
TEST_CLEAN_HTTP_PORT="${TEST_CLEAN_HTTP_PORT:-18082}"

if [ "$(hostname)" != "m6-procurement-151" ] || [ "$(id -un)" != "ceshi" ]; then
  echo "Clean release verification must run as ceshi on m6-procurement-151" >&2
  exit 1
fi
if ! printf '%s' "$BUILD_REF" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "TEST_BUILD_REF must be an exact commit SHA" >&2
  exit 1
fi
if ! printf '%s' "$RELEASE_BATCH_ID" | grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'; then
  echo "RELEASE_BATCH_ID must be a UUID v4" >&2
  exit 1
fi
if ! printf '%s' "$TEST_BACKEND_IMAGE" | grep -Eq '^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:[0-9a-f]{64}$'; then
  echo "TEST_BACKEND_IMAGE must be the exact trusted GHCR digest" >&2
  exit 1
fi
if ! printf '%s' "$TEST_FRONTEND_IMAGE" | grep -Eq '^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:[0-9a-f]{64}$'; then
  echo "TEST_FRONTEND_IMAGE must be the exact trusted GHCR digest" >&2
  exit 1
fi
if ! printf '%s' "$TEST_CLEAN_HTTP_PORT" | grep -Eq '^[0-9]{2,5}$' || [ "$TEST_CLEAN_HTTP_PORT" -gt 65535 ]; then
  echo "TEST_CLEAN_HTTP_PORT is invalid" >&2
  exit 1
fi

for required_file in "$ENV_FILE" "$BASELINE_FILE" "$BASELINE_REF_FILE"; do
  if [ ! -s "$required_file" ] || [ -L "$required_file" ]; then
    echo "Missing trusted clean-database input: $required_file" >&2
    exit 1
  fi
done
if grep -Eiq '^[[:space:]]*(INSERT|REPLACE|LOAD[[:space:]]+DATA)[[:space:]]' "$BASELINE_FILE"; then
  echo "The clean database baseline must be schema-only" >&2
  exit 1
fi
if [ "$(git -C "$PROJECT_ROOT" rev-parse HEAD)" != "$BUILD_REF" ]; then
  echo "Candidate checkout does not match TEST_BUILD_REF" >&2
  exit 1
fi

baseline_ref="$(tr -d '\r\n' < "$BASELINE_REF_FILE")"
if ! printf '%s' "$baseline_ref" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "The clean database baseline commit marker is invalid" >&2
  exit 1
fi
if ! git -C "$PROJECT_ROOT" cat-file -e "$baseline_ref^{commit}" 2>/dev/null; then
  echo "The clean database baseline commit is unavailable" >&2
  exit 1
fi
if ! git -C "$PROJECT_ROOT" merge-base --is-ancestor "$baseline_ref" "$BUILD_REF"; then
  echo "The release candidate is not descended from the clean database baseline" >&2
  exit 1
fi

mapfile -d '' production_sql_paths < <(
  git -C "$PROJECT_ROOT" diff \
    --no-renames \
    --diff-filter=AM \
    --name-only \
    -z \
    "$baseline_ref...$BUILD_REF" \
    -- 'backend-springboot/sql/zz_*.sql'
)
for sql_path in "${production_sql_paths[@]}"; do
  if ! [[ "$sql_path" =~ ^backend-springboot/sql/zz_[A-Za-z0-9._-]+\.sql$ ]]; then
    echo "Unexpected clean-database migration path: $sql_path" >&2
    exit 1
  fi
done

project_name="procurement-release-clean-${RELEASE_BATCH_ID//-/}"
export TEST_DB_BASELINE_FILE="$BASELINE_FILE"
export TEST_BIND_ADDRESS="127.0.0.1"
export TEST_HTTP_PORT="$TEST_CLEAN_HTTP_PORT"
export TEST_BACKEND_IMAGE TEST_FRONTEND_IMAGE
export TEST_FEISHU_TEST_CARD_ENABLED="false"
export TEST_FEISHU_APP_ID=""
export TEST_FEISHU_APP_SECRET=""
export TEST_FEISHU_RECIPIENT_EMAIL=""
COMPOSE=(docker compose --project-name "$project_name" --env-file "$ENV_FILE" -f "$TRUSTED_ROOT/deploy/docker-compose.test.yml")

cleanup() {
  "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up -d --no-build mysql redis

mysql_ready=0
for _ in {1..30}; do
  if "${COMPOSE[@]}" exec -T mysql sh -ec \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -Nse "SELECT 1"' >/dev/null 2>&1; then
    mysql_ready=1
    break
  fi
  sleep 2
done
if [ "$mysql_ready" != "1" ]; then
  echo "Clean release MySQL did not become ready" >&2
  exit 1
fi

"${COMPOSE[@]}" up -d --no-build db-configure
db_configure_container="$("${COMPOSE[@]}" ps -aq db-configure)"
if [ -z "$db_configure_container" ] || [ "$(docker wait "$db_configure_container")" != "0" ]; then
  echo "Clean release database safety configuration failed" >&2
  exit 1
fi

marker_count="$("${COMPOSE[@]}" exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -Nse "SELECT COUNT(*) FROM procurement_setting WHERE setting_key = '\''test_environment_marker'\'' AND setting_value = '\''procurement-test'\'' AND is_deleted = 0"')"
if [ "$marker_count" != "1" ]; then
  echo "Clean release database safety marker is missing" >&2
  exit 1
fi

for sql_path in "${production_sql_paths[@]}"; do
  echo "Clean database: applying $(basename "$sql_path")"
  git -C "$PROJECT_ROOT" cat-file blob "$BUILD_REF:$sql_path" | \
    "${COMPOSE[@]}" exec -T mysql sh -ec \
      'MYSQL_PWD="$MYSQL_PASSWORD" mysql --binary-mode=1 --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"'
done

"${COMPOSE[@]}" up -d --no-build backend frontend
clean_ready=0
for _ in {1..30}; do
  frontend_status="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$TEST_CLEAN_HTTP_PORT/" || true)"
  openapi_status="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$TEST_CLEAN_HTTP_PORT/v3/api-docs" || true)"
  if [ "$frontend_status" = "200" ] && [ "$openapi_status" = "200" ]; then
    clean_ready=1
    break
  fi
  sleep 5
done
if [ "$clean_ready" != "1" ]; then
  echo "Release candidate failed clean-database startup verification" >&2
  exit 1
fi

backend_container="$("${COMPOSE[@]}" ps -q backend)"
frontend_container="$("${COMPOSE[@]}" ps -q frontend)"
egress_container="$("${COMPOSE[@]}" ps -q feishu-egress)"
test -n "$backend_container"
test -n "$frontend_container"
test -z "$egress_container"
test "$(docker inspect "$backend_container" --format '{{.Image}}')" = "$(docker image inspect "$TEST_BACKEND_IMAGE" --format '{{.Id}}')"
test "$(docker inspect "$frontend_container" --format '{{.Image}}')" = "$(docker image inspect "$TEST_FRONTEND_IMAGE" --format '{{.Id}}')"

if docker exec "$backend_container" timeout 5 bash -c '</dev/tcp/1.1.1.1/443' >/dev/null 2>&1; then
  echo "Clean release backend unexpectedly has an external route" >&2
  exit 1
fi

echo "Clean release database verification passed from baseline $baseline_ref"
