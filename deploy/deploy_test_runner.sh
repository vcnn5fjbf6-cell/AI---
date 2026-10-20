#!/usr/bin/env bash
set -euo pipefail

TRUSTED_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="${TEST_SOURCE_ROOT:-$TRUSTED_ROOT}"
DEPLOY_ROOT="${TEST_DEPLOY_ROOT:-/opt/procurement-test}"
ENV_FILE="$DEPLOY_ROOT/deploy/.env.test"
BASELINE_FILE="$DEPLOY_ROOT/deploy/db/test-init/runtime/001_baseline.sql"
BUILD_REF="${TEST_BUILD_REF:-local}"
MIGRATION_BASE_REF="${TEST_MIGRATION_BASE_REF:-origin/main}"
TEST_BACKEND_IMAGE="${TEST_BACKEND_IMAGE:-}"
TEST_FRONTEND_IMAGE="${TEST_FRONTEND_IMAGE:-}"
TEST_IMAGE_SOURCE="${TEST_IMAGE_SOURCE:-ghcr}"
TEST_STATUS_CHANNEL="${TEST_STATUS_CHANNEL:-release}"
LOCK_FILE="${TEST_DEPLOY_LOCK_FILE:-$HOME/.local/state/procurement-test/deploy.lock}"

if [ -L "$LOCK_FILE" ]; then
  echo "Test deployment lock must not be a symbolic link: $LOCK_FILE" >&2
  exit 1
fi
install -d -m 0700 "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
if ! flock -w "${TEST_DEPLOY_LOCK_WAIT_SECONDS:-1800}" 9; then
  echo "Timed out waiting for the test deployment lock: $LOCK_FILE" >&2
  exit 1
fi

case "$TEST_STATUS_CHANNEL" in
  release)
    TEST_STATUS_FILE="$DEPLOY_ROOT/deploy/test-status/current-build-ref"
    ;;
  gitee)
    TEST_STATUS_FILE="$DEPLOY_ROOT/deploy/test-status/current-gitee-build-ref"
    ;;
  *)
    echo "TEST_STATUS_CHANNEL must be release or gitee" >&2
    exit 1
    ;;
esac

docker_server_version="$(docker version --format '{{.Server.Version}}')"
if ! [[ "$docker_server_version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+) ]]; then
  echo "Unable to verify Docker Server version: $docker_server_version" >&2
  exit 1
fi
docker_major="${BASH_REMATCH[1]}"
docker_minor="${BASH_REMATCH[2]}"
docker_patch="${BASH_REMATCH[3]}"
if (( docker_major < 25 || (docker_major == 25 && docker_minor == 0 && docker_patch < 5) )); then
  echo "Docker Server $docker_server_version is too old for isolated candidate networking; require 25.0.5 or newer" >&2
  exit 1
fi

for existing_path in \
  "$DEPLOY_ROOT" \
  "$DEPLOY_ROOT/deploy" \
  "$DEPLOY_ROOT/deploy/db" \
  "$DEPLOY_ROOT/deploy/db/test-init" \
  "$DEPLOY_ROOT/deploy/db/test-init/runtime" \
  "$DEPLOY_ROOT/deploy/test-status" \
  "$DEPLOY_ROOT/backend-springboot" \
  "$DEPLOY_ROOT/backend-springboot/sql" \
  "$DEPLOY_ROOT/frontend-vue" \
  "$DEPLOY_ROOT/frontend-vue/deploy" \
  "$DEPLOY_ROOT/frontend-vue/deploy/nginx" \
  "$DEPLOY_ROOT/frontend-vue/deploy/nginx/test-feishu-egress.conf"; do
  if [ -L "$existing_path" ]; then
    echo "Test deployment path must not be a symbolic link: $existing_path" >&2
    exit 1
  fi
done

if ! printf '%s' "$BUILD_REF" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "TEST_BUILD_REF must be an exact 40-character commit SHA" >&2
  exit 1
fi

case "$TEST_IMAGE_SOURCE" in
  ghcr)
    if ! printf '%s' "$TEST_BACKEND_IMAGE" | grep -Eq '^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:[0-9a-f]{64}$'; then
      echo "TEST_BACKEND_IMAGE must be the exact trusted GHCR digest" >&2
      exit 1
    fi
    if ! printf '%s' "$TEST_FRONTEND_IMAGE" | grep -Eq '^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:[0-9a-f]{64}$'; then
      echo "TEST_FRONTEND_IMAGE must be the exact trusted GHCR digest" >&2
      exit 1
    fi
    ;;
  gitee-local)
    if [ "$TEST_STATUS_CHANNEL" != "gitee" ]; then
      echo "Gitee local images must use the isolated gitee status channel" >&2
      exit 1
    fi
    if [ "$TEST_BACKEND_IMAGE" != "procurement-backend:gitee-test-$BUILD_REF" ]; then
      echo "TEST_BACKEND_IMAGE must be tied to the exact Gitee build SHA" >&2
      exit 1
    fi
    if [ "$TEST_FRONTEND_IMAGE" != "procurement-frontend:gitee-test-$BUILD_REF" ]; then
      echo "TEST_FRONTEND_IMAGE must be tied to the exact Gitee build SHA" >&2
      exit 1
    fi
    ;;
  *)
    echo "TEST_IMAGE_SOURCE must be ghcr or gitee-local" >&2
    exit 1
    ;;
esac

if git -C "$PROJECT_ROOT" ls-tree -r --full-tree HEAD | awk '$1 == "120000" { found=1 } END { exit(found ? 0 : 1) }'; then
  echo "Candidate repositories may not contain symbolic links" >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ] || [ -L "$ENV_FILE" ]; then
  echo "Missing test environment file: $ENV_FILE" >&2
  exit 1
fi

if [ ! -s "$BASELINE_FILE" ] || [ -L "$BASELINE_FILE" ]; then
  echo "Missing schema-only test database baseline: $BASELINE_FILE" >&2
  exit 1
fi

if ! git -C "$PROJECT_ROOT" rev-parse --verify "$MIGRATION_BASE_REF^{commit}" >/dev/null 2>&1; then
  echo "Missing test migration base ref: $MIGRATION_BASE_REF" >&2
  exit 1
fi

if [ "$TEST_STATUS_CHANNEL" = "release" ]; then
  bash "$TRUSTED_ROOT/deploy/verify_release_batch_clean_db.sh"
fi

mapfile -d '' incremental_sql_paths < <(
  git -C "$PROJECT_ROOT" diff \
    --no-renames \
    --diff-filter=AM \
    --name-only \
    -z \
    "$MIGRATION_BASE_REF...HEAD" \
    -- 'backend-springboot/sql/zz_*.sql'
)

mkdir -p "$DEPLOY_ROOT"

echo "Synchronizing candidate source into the stable test deployment directory..."
rsync -a --delete \
  --exclude='.git/' \
  --exclude='.github/' \
  --exclude='node_modules/' \
  --exclude='dist/' \
  --exclude='target/' \
  --exclude='deploy/release-agent/' \
  --exclude='deploy/docker-compose.release-control.yml' \
  --exclude='deploy/.env.release-control' \
  --exclude='deploy/.env.test' \
  --exclude='deploy/db/test-init/runtime/' \
  --exclude='deploy/test-status/' \
  "$PROJECT_ROOT/" "$DEPLOY_ROOT/"

for managed_directory in \
  "$DEPLOY_ROOT/deploy" \
  "$DEPLOY_ROOT/backend-springboot/sql" \
  "$DEPLOY_ROOT/frontend-vue/deploy/nginx"; do
  if [ -L "$managed_directory" ]; then
    echo "Managed test path must not be a symbolic link: $managed_directory" >&2
    exit 1
  fi
done

rm -f "$DEPLOY_ROOT/deploy/docker-compose.test.yml"
install -m 0644 "$TRUSTED_ROOT/deploy/docker-compose.test.yml" "$DEPLOY_ROOT/deploy/docker-compose.test.yml"
rm -f "$DEPLOY_ROOT/deploy/db/test-init/010_seed_test.sql"
install -m 0644 "$TRUSTED_ROOT/deploy/db/test-init/010_seed_test.sql" "$DEPLOY_ROOT/deploy/db/test-init/010_seed_test.sql"
rm -f "$DEPLOY_ROOT/deploy/db/test-init/999_safety.sql"
install -m 0644 "$TRUSTED_ROOT/deploy/db/test-init/999_safety.sql" "$DEPLOY_ROOT/deploy/db/test-init/999_safety.sql"
rm -f "$DEPLOY_ROOT/frontend-vue/deploy/nginx/test.conf"
install -m 0644 "$TRUSTED_ROOT/frontend-vue/deploy/nginx/test.conf" "$DEPLOY_ROOT/frontend-vue/deploy/nginx/test.conf"
rm -f "$DEPLOY_ROOT/frontend-vue/deploy/nginx/test-feishu-egress.conf"
install -m 0644 "$TRUSTED_ROOT/frontend-vue/deploy/nginx/test-feishu-egress.conf" "$DEPLOY_ROOT/frontend-vue/deploy/nginx/test-feishu-egress.conf"

cd "$DEPLOY_ROOT"
export TEST_BUILD_REF="${BUILD_REF:0:12}"
export TEST_BACKEND_IMAGE TEST_FRONTEND_IMAGE
COMPOSE=(docker compose --env-file deploy/.env.test -f deploy/docker-compose.test.yml)

"${COMPOSE[@]}" config --quiet
mkdir -p deploy/test-status
if [ -L deploy/test-status ]; then
  echo "Test status directory must not be a symbolic link" >&2
  exit 1
fi
if [ "$TEST_STATUS_CHANNEL" = "gitee" ]; then
  rm -f "$DEPLOY_ROOT/deploy/test-status/current-build-ref"
else
  rm -f "$DEPLOY_ROOT/deploy/test-status/current-gitee-build-ref"
fi
rm -f "$TEST_STATUS_FILE"
"${COMPOSE[@]}" stop frontend backend
"${COMPOSE[@]}" up -d --no-build mysql redis

echo "Applying incremental database scripts to the marked test database..."
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
  echo "Test MySQL did not become ready" >&2
  exit 1
fi

"${COMPOSE[@]}" rm -sf db-configure
"${COMPOSE[@]}" up -d --no-build db-configure
db_configure_container="$("${COMPOSE[@]}" ps -aq db-configure)"
if [ -z "$db_configure_container" ] || [ "$(docker wait "$db_configure_container")" != "0" ]; then
  if [ -n "$db_configure_container" ]; then
    docker logs --tail 80 "$db_configure_container" >&2 || true
  fi
  echo "Test database safety configuration failed" >&2
  exit 1
fi

marker_count="$("${COMPOSE[@]}" exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -Nse "SELECT COUNT(*) FROM procurement_setting WHERE setting_key = '\''test_environment_marker'\'' AND setting_value = '\''procurement-test'\'' AND is_deleted = 0"')"
if [ "$marker_count" != "1" ]; then
  echo "Refusing to apply database scripts without the procurement-test safety marker" >&2
  exit 1
fi

"${COMPOSE[@]}" exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --binary-mode=1 --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"' <<'SQL'
CREATE TABLE IF NOT EXISTS test_schema_migration_history (
  script_path VARCHAR(512) NOT NULL,
  blob_oid CHAR(40) NOT NULL,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  build_ref VARCHAR(40) NOT NULL,
  PRIMARY KEY (script_path, blob_oid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试环境增量脚本执行记录';
SQL

for sql_path in "${incremental_sql_paths[@]}"; do
  if ! [[ "$sql_path" =~ ^backend-springboot/sql/zz_[A-Za-z0-9._-]+\.sql$ ]]; then
    echo "Invalid test database script path: $sql_path" >&2
    exit 1
  fi
  blob_oid="$(git -C "$PROJECT_ROOT" rev-parse "HEAD:$sql_path")"
  if ! printf '%s' "$blob_oid" | grep -Eq '^[0-9a-f]{40}$'; then
    echo "Invalid Git blob OID for test database script: $sql_path" >&2
    exit 1
  fi
  applied_count="$("${COMPOSE[@]}" exec -T mysql sh -ec \
    "MYSQL_PWD=\"\$MYSQL_PASSWORD\" mysql --binary-mode=1 -u\"\$MYSQL_USER\" \"\$MYSQL_DATABASE\" -Nse \"SELECT COUNT(*) FROM test_schema_migration_history WHERE script_path = '$sql_path' AND blob_oid = '$blob_oid'\"")"
  if [ "$applied_count" = "1" ]; then
    echo "Skipping already applied $(basename "$sql_path") ($blob_oid)"
    continue
  fi
  echo "Applying $(basename "$sql_path")"
  git -C "$PROJECT_ROOT" cat-file blob "HEAD:$sql_path" | \
    "${COMPOSE[@]}" exec -T mysql sh -ec \
      'MYSQL_PWD="$MYSQL_PASSWORD" mysql --binary-mode=1 --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"'
  "${COMPOSE[@]}" exec -T mysql sh -ec \
    "MYSQL_PWD=\"\$MYSQL_PASSWORD\" mysql --binary-mode=1 -u\"\$MYSQL_USER\" \"\$MYSQL_DATABASE\" -e \"INSERT INTO test_schema_migration_history (script_path, blob_oid, build_ref) VALUES ('$sql_path', '$blob_oid', '$TEST_BUILD_REF')\""
done
if [ "${#incremental_sql_paths[@]}" -eq 0 ]; then
  echo "No candidate incremental database scripts to apply"
fi

"${COMPOSE[@]}" up -d --no-build --remove-orphans

backend_container="$("${COMPOSE[@]}" ps -q backend)"
frontend_container="$("${COMPOSE[@]}" ps -q frontend)"
egress_container="$("${COMPOSE[@]}" ps -q feishu-egress)"
test -n "$backend_container"
test -n "$frontend_container"
test -n "$egress_container"
test "$(docker inspect "$backend_container" --format '{{.Image}}')" = "$(docker image inspect "$TEST_BACKEND_IMAGE" --format '{{.Id}}')"
test "$(docker inspect "$frontend_container" --format '{{.Image}}')" = "$(docker image inspect "$TEST_FRONTEND_IMAGE" --format '{{.Id}}')"
test "$(docker inspect "$egress_container" --format '{{.Image}}')" = "$(docker image inspect "$TEST_FRONTEND_IMAGE" --format '{{.Id}}')"

echo "Waiting for the test frontend..."
for _ in {1..30}; do
  if curl -fsS http://192.168.132.151:18080/ >/dev/null; then
    break
  fi
  sleep 5
done

curl -fsS http://192.168.132.151:18080/ >/dev/null
curl -fsS http://192.168.132.151:18080/v3/api-docs >/dev/null

echo "Waiting for Docker health checks..."
for _ in {1..12}; do
  backend_health="$(docker inspect "$backend_container" --format '{{.State.Health.Status}}')"
  frontend_health="$(docker inspect "$frontend_container" --format '{{.State.Health.Status}}')"
  egress_health="$(docker inspect "$egress_container" --format '{{.State.Health.Status}}')"
  if [ "$backend_health" = "healthy" ] && [ "$frontend_health" = "healthy" ] && [ "$egress_health" = "healthy" ]; then
    break
  fi
  sleep 5
done

"${COMPOSE[@]}" ps

if [ "$(docker inspect "$egress_container" --format '{{.State.Health.Status}}')" != "healthy" ]; then
  docker logs --tail 80 "$egress_container" >&2 || true
fi

test "$(docker inspect "$backend_container" --format '{{.State.Health.Status}}')" = "healthy"
test "$(docker inspect "$frontend_container" --format '{{.State.Health.Status}}')" = "healthy"
test "$(docker inspect "$egress_container" --format '{{.State.Health.Status}}')" = "healthy"

if docker exec "$backend_container" timeout 5 bash -c '</dev/tcp/1.1.1.1/443' >/dev/null 2>&1; then
  echo "Test backend unexpectedly has an external route" >&2
  exit 1
fi
docker exec "$backend_container" timeout 5 bash -c '</dev/tcp/feishu-egress/8080'
egress_members="$(docker network inspect procurement-test_feishu_egress --format '{{range .Containers}}{{.Name}}{{"\n"}}{{end}}')"
test "$egress_members" = "procurement-test-feishu-egress-1"

marker_temp="$TEST_STATUS_FILE.$$.tmp"
printf '{"buildRef":"%s","backendImage":"%s","frontendImage":"%s"}\n' \
  "$BUILD_REF" "$TEST_BACKEND_IMAGE" "$TEST_FRONTEND_IMAGE" > "$marker_temp"
mv -f "$marker_temp" "$TEST_STATUS_FILE"

echo "Test deployment completed for build $TEST_BUILD_REF"
