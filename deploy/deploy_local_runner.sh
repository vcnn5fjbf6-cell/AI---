#!/usr/bin/env bash
set -Eeuo pipefail

readonly EXPECTED_REMOTE_DIR="/home/caigou/procurement"
readonly PRODUCTION_REMOTE_DIR="/home/caigou/procurement"
readonly COMPOSE_PROJECT_NAME="deploy"
REMOTE_DIR="${DEPLOY_REMOTE_DIR:-$EXPECTED_REMOTE_DIR}"
REMOTE_TAR=""
STAGING_DIR=""
BACKUP_TEMP_DIR=""
CANDIDATE_FRONTEND_CONTAINER=""
RELEASE_RECORD_TEMP=""
CURRENT_RECORD_TEMP=""
FORWARD_WATCHDOG_PID=""
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_SHA="${RELEASE_SHA:-}"
RELEASE_BATCH_ID="${RELEASE_BATCH_ID:-}"
BACKEND_IMAGE="${BACKEND_IMAGE:-}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-}"
TARGET_BACKEND_IMAGE="$BACKEND_IMAGE"
TARGET_FRONTEND_IMAGE="$FRONTEND_IMAGE"

APPLICATION_STOPPED=0
RECOVERY_POINT_READY=0
SOURCE_MUTATION_STARTED=0
DATA_MUTATION_STARTED=0
CANDIDATE_APPLICATION_STARTED=0
RELEASE_SUCCEEDED=0
ROLLBACK_RUNNING=0
ROLLBACK_SIGNALLED=0

PREVIOUS_RELEASE_SHA=""
PREVIOUS_BACKEND_IMAGE=""
PREVIOUS_FRONTEND_IMAGE=""
PREVIOUS_BACKEND_IMAGE_ID=""
PREVIOUS_FRONTEND_IMAGE_ID=""
ROLLBACK_BACKEND_IMAGE=""
ROLLBACK_FRONTEND_IMAGE=""
BACKUP_ID=""
BACKUP_DIR=""
DATABASE_NAME=""
DB_DUMP_SHA256=""
INVOICE_ARCHIVE_SHA256=""
INVOICE_MANIFEST_SHA256=""
CURRENT_RELEASE_RECORD_SHA256=""
FORWARD_TIMEOUT_SECONDS="${DEPLOY_FORWARD_TIMEOUT_SECONDS:-7200}"
WATCHDOG_SLEEP_COMMAND="${DEPLOY_WATCHDOG_SLEEP_COMMAND:-/bin/sleep}"
SCRIPT_PROCESS_GROUP=""
WATCHDOG_SIGNAL_TARGET=""

cleanup() {
  [ -z "$CANDIDATE_FRONTEND_CONTAINER" ] || docker rm -f "$CANDIDATE_FRONTEND_CONTAINER" >/dev/null 2>&1 || true
  [ -z "$REMOTE_TAR" ] || rm -f -- "$REMOTE_TAR"
  [ -z "$STAGING_DIR" ] || rm -rf -- "$STAGING_DIR"
  [ -z "$BACKUP_TEMP_DIR" ] || rm -rf -- "$BACKUP_TEMP_DIR"
  [ -z "$RELEASE_RECORD_TEMP" ] || rm -f -- "$RELEASE_RECORD_TEMP"
  [ -z "$CURRENT_RECORD_TEMP" ] || rm -f -- "$CURRENT_RECORD_TEMP"
}

cancel_forward_watchdog() {
  if [ -z "$FORWARD_WATCHDOG_PID" ]; then
    return 0
  fi
  kill -TERM "$FORWARD_WATCHDOG_PID" >/dev/null 2>&1 || true
  wait "$FORWARD_WATCHDOG_PID" >/dev/null 2>&1 || true
  FORWARD_WATCHDOG_PID=""
}

start_forward_watchdog() {
  (
    set +e
    watchdog_sleep_pid=""
    stop_watchdog() {
      [ -z "$watchdog_sleep_pid" ] || kill -TERM "$watchdog_sleep_pid" >/dev/null 2>&1 || true
      [ -z "$watchdog_sleep_pid" ] || wait "$watchdog_sleep_pid" >/dev/null 2>&1 || true
      exit 0
    }
    trap stop_watchdog INT TERM
    "$WATCHDOG_SLEEP_COMMAND" "$FORWARD_TIMEOUT_SECONDS" &
    watchdog_sleep_pid=$!
    wait "$watchdog_sleep_pid"
    sleep_status=$?
    trap - INT TERM
    [ "$sleep_status" -eq 0 ] || exit 0
    echo "Forward release exceeded ${FORWARD_TIMEOUT_SECONDS}s; interrupting the isolated release process group." >&2
    kill -TERM -- "$WATCHDOG_SIGNAL_TARGET"
  ) &
  FORWARD_WATCHDOG_PID=$!
}

compose_run() {
  local compose_file="$1"
  shift
  "${COMPOSE[@]}" \
    --project-name "$COMPOSE_PROJECT_NAME" \
    -f "$compose_file" \
    --env-file "$REMOTE_DIR/deploy/.env" \
    "$@"
}

wait_for_mysql() {
  local compose_file="$1"
  local mysql_ready=0
  local attempt
  for attempt in {1..30}; do
    if compose_run "$compose_file" exec -T mysql sh -ec \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -Nse "SELECT 1"' >/dev/null 2>&1; then
      mysql_ready=1
      break
    fi
    sleep 2
  done
  if [ "$mysql_ready" != "1" ]; then
    echo "Production database did not become ready" >&2
    return 1
  fi
}

check_http() {
  local name="$1"
  local url="$2"
  local status
  local attempt
  for attempt in {1..12}; do
    status="$(curl --connect-timeout 3 --max-time 5 -s -o /dev/null -w '%{http_code}' "$url" || true)"
    echo "$name:$status"
    if [ "$status" = "200" ]; then
      return 0
    fi
    sleep 5
  done
  echo "$name check failed" >&2
  return 1
}

assert_no_application_writers() {
  local service
  local container_name
  local matches

  for service in backend frontend; do
    if ! matches="$(docker ps -aq \
      --filter "label=com.docker.compose.project=$COMPOSE_PROJECT_NAME" \
      --filter "label=com.docker.compose.service=$service")"; then
      echo "Unable to verify that Compose service $service has no writer containers" >&2
      return 1
    fi
    if [ -n "$matches" ]; then
      echo "Writer containers still exist for Compose service $service: $matches" >&2
      return 1
    fi
  done

  for container_name in procurement-backend procurement-frontend; do
    if ! matches="$(docker ps -aq --filter "name=^/${container_name}$")"; then
      echo "Unable to verify that writer container $container_name is absent" >&2
      return 1
    fi
    if [ -n "$matches" ]; then
      echo "Writer container still exists: $container_name" >&2
      return 1
    fi
  done
}

invoice_manifest() {
  local invoice_root="$1"
  local manifest_path="$2"
  local mode="$3"
  python3 - "$invoice_root" "$manifest_path" "$mode" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import sys

root = Path(sys.argv[1])
manifest_path = Path(sys.argv[2])
mode = sys.argv[3]

if not root.is_dir() or root.is_symlink():
    raise SystemExit(f"invoice root must be a real directory: {root}")

records = []
for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()):
    if path.is_symlink():
        raise SystemExit(f"invoice backup refuses symbolic link: {path}")
    if path.is_dir():
        continue
    if not path.is_file():
        raise SystemExit(f"invoice backup refuses special file: {path}")
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
            size += len(chunk)
    records.append({
        "path": path.relative_to(root).as_posix(),
        "size": size,
        "sha256": digest.hexdigest(),
    })

payload = {
    "fileCount": len(records),
    "totalBytes": sum(item["size"] for item in records),
    "files": records,
}

if mode == "write":
    temp_path = manifest_path.with_name(f".{manifest_path.name}.{os.getpid()}.tmp")
    with temp_path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(payload, handle, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        handle.write("\n")
    os.replace(temp_path, manifest_path)
elif mode == "verify":
    with manifest_path.open(encoding="utf-8") as handle:
        expected = json.load(handle)
    if payload != expected:
        raise SystemExit("restored order-invoices content does not match the recovery manifest")
else:
    raise SystemExit(f"unsupported invoice manifest mode: {mode}")
PY
}

check_container_http() {
  local container_name="$1"
  local name="$2"
  local url="$3"
  local attempt
  for attempt in {1..12}; do
    if docker exec "$container_name" wget -q -T 5 -O /dev/null "$url"; then
      echo "$name:200"
      return 0
    fi
    echo "$name:unavailable"
    sleep 5
  done
  echo "$name check failed" >&2
  return 1
}

recreate_service() {
  local compose_file="$1"
  local service="$2"
  local container_name="$3"
  local compose_output
  local compose_code

  set +e
  compose_output="$(compose_run "$compose_file" up -d --force-recreate "$service" 2>&1)"
  compose_code=$?
  set -e
  echo "$compose_output"
  if [ "$compose_code" -eq 0 ]; then
    return 0
  fi
  if ! printf '%s\n' "$compose_output" | grep -q "ContainerConfig"; then
    return "$compose_code"
  fi

  echo "Docker Compose hit ContainerConfig for $service; removing only $container_name and retrying."
  docker rm -f "$container_name" >/dev/null 2>&1 || true
  compose_run "$compose_file" up -d --force-recreate "$service"
}

restore_previous_source() (
  set -Eeuo pipefail
  local rollback_staging_dir=""
  local rollback_tar=""
  cleanup_previous_source_restore() {
    [ -z "$rollback_tar" ] || rm -f -- "$rollback_tar"
    [ -z "$rollback_staging_dir" ] || rm -rf -- "$rollback_staging_dir"
  }
  trap cleanup_previous_source_restore EXIT

  if [ "$SOURCE_MUTATION_STARTED" != "1" ]; then
    return 0
  fi

  echo "Restoring production source to $PREVIOUS_RELEASE_SHA..."
  rollback_staging_dir="$(mktemp -d "$remote_parent/.${remote_name}.rollback.XXXXXX")"
  rollback_tar="$(mktemp "$remote_parent/.${remote_name}.rollback.XXXXXX.tar")"
  git -C "$PROJECT_ROOT" archive --format=tar --output="$rollback_tar" "$PREVIOUS_RELEASE_SHA"
  tar -xf "$rollback_tar" -C "$rollback_staging_dir"
  rm -f -- "$rollback_tar"
  rollback_tar=""
  rsync -a --delete \
    --exclude='/data/' \
    --exclude='/backups/' \
    --exclude='/deploy/.env' \
    --exclude='/deploy/releases/' \
    "$rollback_staging_dir/" "$REMOTE_DIR/"
)

restore_database() {
  local compose_file="$REMOTE_DIR/deploy/docker-compose.prod.yml"
  local dump_path="$BACKUP_DIR/mysql.sql.gz"
  local current_database_name

  printf '%s  %s\n' "$DB_DUMP_SHA256" "$dump_path" | sha256sum --check --status
  gzip -t "$dump_path"
  compose_run "$compose_file" up -d mysql redis qdrant
  wait_for_mysql "$compose_file"
  current_database_name="$(compose_run "$compose_file" exec -T mysql sh -ec 'printf "%s" "$MYSQL_DATABASE"')"
  if [ "$current_database_name" != "$DATABASE_NAME" ]; then
    echo "Refusing database restore: current MYSQL_DATABASE does not match the recovery point" >&2
    return 1
  fi

  compose_run "$compose_file" exec -T mysql sh -ec '
    case "$MYSQL_DATABASE" in
      ""|*[!A-Za-z0-9_]*)
        echo "MYSQL_DATABASE contains unsupported characters" >&2
        exit 1
        ;;
    esac
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e \
      "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
  '
  gzip -dc "$dump_path" | compose_run "$compose_file" exec -T mysql sh -ec \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --binary-mode=1 --default-character-set=utf8mb4 -uroot'
  compose_run "$compose_file" exec -T mysql sh -ec \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -Nse "SELECT 1"' >/dev/null
}

restore_order_invoices() (
  set -Eeuo pipefail
  local invoice_dir="$REMOTE_DIR/data/order-invoices"
  local archive_path="$BACKUP_DIR/order-invoices.tar.gz"
  local manifest_path="$BACKUP_DIR/order-invoices-manifest.json"
  local failed_dir="$REMOTE_DIR/data/.order-invoices.failed.$BACKUP_ID"
  local invoice_restore_temp=""
  cleanup_invoice_restore() {
    [ -z "$invoice_restore_temp" ] || rm -rf -- "$invoice_restore_temp"
  }
  trap cleanup_invoice_restore EXIT

  printf '%s  %s\n' "$INVOICE_ARCHIVE_SHA256" "$archive_path" | sha256sum --check --status
  printf '%s  %s\n' "$INVOICE_MANIFEST_SHA256" "$manifest_path" | sha256sum --check --status
  tar -tzf "$archive_path" >/dev/null

  invoice_restore_temp="$(mktemp -d "$REMOTE_DIR/data/.order-invoices.restore.XXXXXX")"
  tar -xzf "$archive_path" -C "$invoice_restore_temp" --no-same-owner --no-same-permissions
  invoice_manifest "$invoice_restore_temp/order-invoices" "$manifest_path" verify

  if [ -e "$failed_dir" ] || [ -L "$failed_dir" ]; then
    echo "Refusing occupied failed-invoice recovery path: $failed_dir" >&2
    return 1
  fi
  mv -- "$invoice_dir" "$failed_dir"
  if ! mv -- "$invoice_restore_temp/order-invoices" "$invoice_dir"; then
    mv -- "$failed_dir" "$invoice_dir" || true
    return 1
  fi
  if ! invoice_manifest "$invoice_dir" "$manifest_path" verify; then
    rm -rf -- "$invoice_dir"
    mv -- "$failed_dir" "$invoice_dir" || true
    return 1
  fi
  rm -rf -- "$failed_dir"
  rm -rf -- "$invoice_restore_temp"
  invoice_restore_temp=""
)

restart_previous_application() {
  local compose_file="$REMOTE_DIR/deploy/docker-compose.prod.yml"
  export BACKEND_IMAGE="$ROLLBACK_BACKEND_IMAGE"
  export FRONTEND_IMAGE="$ROLLBACK_FRONTEND_IMAGE"

  compose_run "$compose_file" config --quiet
  compose_run "$compose_file" up -d --force-recreate backend frontend
  sleep 25

  local backend_container
  local frontend_container
  backend_container="$(compose_run "$compose_file" ps -q backend)"
  frontend_container="$(compose_run "$compose_file" ps -q frontend)"
  test -n "$backend_container"
  test -n "$frontend_container"
  test "$(docker inspect "$backend_container" --format '{{.Image}}')" = "$PREVIOUS_BACKEND_IMAGE_ID"
  test "$(docker inspect "$frontend_container" --format '{{.Image}}')" = "$PREVIOUS_FRONTEND_IMAGE_ID"
  check_http frontend http://localhost/
  check_http auth http://localhost/api/auth/me
}

rollback_release() {
  if [ "$ROLLBACK_RUNNING" = "1" ]; then
    return 1
  fi
  ROLLBACK_RUNNING=1
  set +e

  echo "Production release failed; starting automatic rollback to $PREVIOUS_RELEASE_SHA..." >&2
  docker rm -f procurement-frontend procurement-backend >/dev/null 2>&1 || true
  local rollback_failed=0
  local database_restore_status=0
  if ! assert_no_application_writers; then
    echo "Refusing source or data rollback while an application writer still exists." >&2
    return 1
  fi
  if [ "$ROLLBACK_SIGNALLED" = "1" ]; then
    echo "Rollback was interrupted before recovery began; application remains stopped." >&2
    return 1
  fi

  (
    set -Eeuo pipefail
    restore_previous_source
  )
  [ "$?" -eq 0 ] || rollback_failed=1

  if [ "$RECOVERY_POINT_READY" = "1" ] && [ "$DATA_MUTATION_STARTED" = "1" ]; then
    if [ -f "$BACKUP_DIR/current-release.json" ] && [ ! -L "$BACKUP_DIR/current-release.json" ] && \
      printf '%s  %s\n' "$CURRENT_RELEASE_RECORD_SHA256" "$BACKUP_DIR/current-release.json" | sha256sum --check --status; then
      install -m 0600 "$BACKUP_DIR/current-release.json" "$current_release_record"
      [ "$?" -eq 0 ] || rollback_failed=1
      if [ "$RELEASE_SHA" != "$PREVIOUS_RELEASE_SHA" ]; then
        rm -f -- "$release_record_dir/$RELEASE_SHA.json"
      fi
    else
      echo "Trusted current release marker recovery copy is missing" >&2
      rollback_failed=1
    fi
    (
      set -Eeuo pipefail
      restore_database
    )
    database_restore_status=$?
    if [ "$database_restore_status" -eq 0 ]; then
      (
        set -Eeuo pipefail
        restore_order_invoices
      )
      [ "$?" -eq 0 ] || rollback_failed=1
    else
      rollback_failed=1
    fi
  fi

  if [ "$ROLLBACK_SIGNALLED" = "1" ]; then
    rollback_failed=1
  fi
  if [ "$rollback_failed" = "0" ]; then
    (
      set -Eeuo pipefail
      restart_previous_application
    )
    restart_status=$?
    if [ "$restart_status" -ne 0 ]; then
      docker rm -f procurement-frontend procurement-backend >/dev/null 2>&1 || true
      assert_no_application_writers || true
      rollback_failed=1
    fi
  fi

  if [ "$ROLLBACK_SIGNALLED" = "1" ]; then
    docker rm -f procurement-frontend procurement-backend >/dev/null 2>&1 || true
    assert_no_application_writers || true
    rollback_failed=1
  fi
  if [ "$rollback_failed" = "0" ]; then
    echo "Automatic rollback completed; production remains at $PREVIOUS_RELEASE_SHA." >&2
  else
    echo "Automatic rollback failed; application containers remain stopped and recovery data is retained at $BACKUP_DIR." >&2
  fi
  return "$rollback_failed"
}

mark_rollback_signal() {
  ROLLBACK_SIGNALLED=1
  echo "Rollback received an interrupt signal; recovery will fail closed with application writers stopped." >&2
}

stop_candidate_application() {
  docker rm -f procurement-frontend procurement-backend >/dev/null 2>&1 || true
  if ! assert_no_application_writers; then
    echo "Candidate application could not be proven stopped; immediate operator intervention is required." >&2
    return 1
  fi
  echo "Candidate application was stopped without restoring data. Recovery point retained at $BACKUP_DIR for an operator decision." >&2
}

finish() {
  trap mark_rollback_signal INT TERM
  if [ -n "${DEPLOY_TEST_FINISH_PAUSE_DIR:-}" ] && [ "$REMOTE_DIR" != "$PRODUCTION_REMOTE_DIR" ]; then
    : > "$DEPLOY_TEST_FINISH_PAUSE_DIR/finish-entry-paused"
    while [ ! -f "$DEPLOY_TEST_FINISH_PAUSE_DIR/finish-entry-continue" ]; do
      /usr/bin/sleep 0.05
    done
  fi
  trap - EXIT
  local status="$1"
  local rollback_status=0
  set +e
  cancel_forward_watchdog
  if [ "$status" -ne 0 ] && [ "$RELEASE_SUCCEEDED" != "1" ] && [ "$APPLICATION_STOPPED" = "1" ]; then
    if [ "$CANDIDATE_APPLICATION_STARTED" = "1" ]; then
      stop_candidate_application
    else
      rollback_release
    fi
    rollback_status=$?
  fi
  cleanup
  trap - INT TERM
  if [ "$rollback_status" -ne 0 ]; then
    exit 1
  fi
  exit "$status"
}

trap 'finish "$?"' EXIT
trap 'finish 130' INT
trap 'finish 143' TERM

if ! printf '%s' "$RELEASE_SHA" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "RELEASE_SHA must be an exact 40-character commit SHA" >&2
  exit 1
fi
if ! printf '%s' "$RELEASE_BATCH_ID" | grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'; then
  echo "RELEASE_BATCH_ID must be a UUID v4" >&2
  exit 1
fi
if ! printf '%s' "$BACKEND_IMAGE" | grep -Eq '^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:[0-9a-f]{64}$'; then
  echo "BACKEND_IMAGE must be the exact tested GHCR digest" >&2
  exit 1
fi
if ! printf '%s' "$FRONTEND_IMAGE" | grep -Eq '^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:[0-9a-f]{64}$'; then
  echo "FRONTEND_IMAGE must be the exact tested GHCR digest" >&2
  exit 1
fi
if [ "$REMOTE_DIR" != "$EXPECTED_REMOTE_DIR" ]; then
  echo "DEPLOY_REMOTE_DIR must be exactly $EXPECTED_REMOTE_DIR" >&2
  exit 1
fi
if [ "${DEPLOY_UPLOAD_ENV:-0}" != "0" ]; then
  echo "Automated production releases may not replace deploy/.env" >&2
  exit 1
fi
if ! printf '%s' "$FORWARD_TIMEOUT_SECONDS" | grep -Eq '^[1-9][0-9]{0,5}$'; then
  echo "DEPLOY_FORWARD_TIMEOUT_SECONDS must be a positive integer no greater than six digits" >&2
  exit 1
fi
if [ ! -x "$WATCHDOG_SLEEP_COMMAND" ]; then
  echo "Production forward watchdog sleep command is unavailable" >&2
  exit 1
fi
if [ "$REMOTE_DIR" = "$PRODUCTION_REMOTE_DIR" ] && \
  { [ "${DEPLOY_ALLOW_NONISOLATED_TEST:-0}" != "0" ] || \
    [ "$WATCHDOG_SLEEP_COMMAND" != "/bin/sleep" ] || \
    [ -n "${DEPLOY_TEST_FINISH_PAUSE_DIR:-}" ]; }; then
  echo "Production release test-only overrides are forbidden" >&2
  exit 1
fi
if [ "${DEPLOY_ALLOW_NONISOLATED_TEST:-0}" = "1" ] && [ "$REMOTE_DIR" != "$PRODUCTION_REMOTE_DIR" ]; then
  WATCHDOG_SIGNAL_TARGET="$$"
else
  SCRIPT_PROCESS_GROUP="$(ps -o pgid= -p "$$" | tr -d '[:space:]')"
  if ! printf '%s' "$SCRIPT_PROCESS_GROUP" | grep -Eq '^[1-9][0-9]*$'; then
    echo "Unable to determine the production release process group" >&2
    exit 1
  fi
  if [ "$SCRIPT_PROCESS_GROUP" != "$$" ]; then
    echo "Production release must run as an isolated process-group leader" >&2
    exit 1
  fi
  WATCHDOG_SIGNAL_TARGET="-$SCRIPT_PROCESS_GROUP"
fi
start_forward_watchdog

export BACKEND_IMAGE FRONTEND_IMAGE RELEASE_SHA RELEASE_BATCH_ID

cd "$PROJECT_ROOT"
if [ "$(git rev-parse HEAD)" != "$RELEASE_SHA" ]; then
  echo "Checked out source does not match RELEASE_SHA" >&2
  exit 1
fi
if git ls-tree -r --full-tree "$RELEASE_SHA" | awk '$1 == "120000" { found=1 } END { exit(found ? 0 : 1) }'; then
  echo "Production releases may not contain symbolic links" >&2
  exit 1
fi

remote_parent="$(dirname "$REMOTE_DIR")"
remote_name="$(basename "$REMOTE_DIR")"
if [ ! -d "$remote_parent" ] || [ -L "$remote_parent" ]; then
  echo "Production parent directory must exist and must not be a symbolic link" >&2
  exit 1
fi
if [ -L "$REMOTE_DIR" ]; then
  echo "Production application directory must not be a symbolic link" >&2
  exit 1
fi
if [ ! -d "$REMOTE_DIR" ]; then
  echo "Production application directory is missing; bootstrap it manually before using automated release" >&2
  exit 1
fi

for managed_path in \
  "$REMOTE_DIR/deploy" \
  "$REMOTE_DIR/deploy/releases" \
  "$REMOTE_DIR/data" \
  "$REMOTE_DIR/data/order-invoices" \
  "$REMOTE_DIR/backups" \
  "$REMOTE_DIR/backups/releases" \
  "$REMOTE_DIR/backend-springboot" \
  "$REMOTE_DIR/frontend-vue"; do
  if [ -L "$managed_path" ]; then
    echo "Production managed path must not be a symbolic link: $managed_path" >&2
    exit 1
  fi
done

backup_root="$REMOTE_DIR/backups/releases"
if [ -L "$REMOTE_DIR/backups" ] || [ -L "$backup_root" ]; then
  echo "Production backup path must not be a symbolic link" >&2
  exit 1
fi
mkdir -p -- "$backup_root"
if [ -L "$REMOTE_DIR/backups" ] || [ -L "$backup_root" ]; then
  echo "Production backup path changed to a symbolic link" >&2
  exit 1
fi
chmod 0700 "$REMOTE_DIR/backups" "$backup_root"

if [ ! -d "$REMOTE_DIR/data/order-invoices" ]; then
  echo "Production order-invoices directory is missing; create the persistent mount before release" >&2
  exit 1
fi
if [ ! -f "$REMOTE_DIR/deploy/.env" ] || [ -L "$REMOTE_DIR/deploy/.env" ]; then
  echo "Production deploy/.env is missing or untrusted" >&2
  exit 1
fi
chmod 0600 "$REMOTE_DIR/deploy/.env"

release_record_dir="$REMOTE_DIR/deploy/releases"
current_release_record="$release_record_dir/current.json"
if ! baseline_output="$(
  DEPLOY_REMOTE_DIR="$REMOTE_DIR" \
    RELEASE_SHA="$RELEASE_SHA" \
    bash "$PROJECT_ROOT/deploy/verify_production_baseline.sh"
)"; then
  exit 1
fi
mapfile -t baseline_record <<< "$baseline_output"
if [ "${#baseline_record[@]}" -ne 3 ]; then
  echo "Production baseline marker is incomplete" >&2
  exit 1
fi
PREVIOUS_RELEASE_SHA="${baseline_record[0]%$'\r'}"
PREVIOUS_BACKEND_IMAGE="${baseline_record[1]%$'\r'}"
PREVIOUS_FRONTEND_IMAGE="${baseline_record[2]%$'\r'}"

mapfile -d '' production_sql_paths < <(
  git diff \
    --no-renames \
    --diff-filter=AM \
    --name-only \
    -z \
    "$PREVIOUS_RELEASE_SHA...$RELEASE_SHA" \
    -- 'backend-springboot/sql/zz_*.sql'
)

STAGING_DIR="$(mktemp -d "$remote_parent/.${remote_name}.release.XXXXXX")"
REMOTE_TAR="$(mktemp "$remote_parent/.${remote_name}.archive.XXXXXX.tar")"
echo "Creating an archive from the exact release commit..."
git archive --format=tar --output="$REMOTE_TAR" "$RELEASE_SHA"
tar -xf "$REMOTE_TAR" -C "$STAGING_DIR"
rm -f -- "$REMOTE_TAR"
REMOTE_TAR=""

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
else
  COMPOSE=(docker-compose)
fi

docker image inspect "$TARGET_BACKEND_IMAGE" >/dev/null
docker image inspect "$TARGET_FRONTEND_IMAGE" >/dev/null
"${COMPOSE[@]}" \
  --project-name "$COMPOSE_PROJECT_NAME" \
  -f "$STAGING_DIR/deploy/docker-compose.prod.yml" \
  --env-file "$REMOTE_DIR/deploy/.env" \
  config --quiet

current_compose_file="$REMOTE_DIR/deploy/docker-compose.prod.yml"
if [ ! -f "$current_compose_file" ] || [ -L "$current_compose_file" ]; then
  echo "Production Docker Compose file is missing or untrusted" >&2
  exit 1
fi
backend_container="$(compose_run "$current_compose_file" ps -q backend)"
frontend_container="$(compose_run "$current_compose_file" ps -q frontend)"
test -n "$backend_container"
test -n "$frontend_container"
test "$(docker inspect "$backend_container" --format '{{.State.Running}}')" = "true"
test "$(docker inspect "$frontend_container" --format '{{.State.Running}}')" = "true"
test "$(docker inspect "$backend_container" --format '{{ index .Config.Labels "com.docker.compose.project" }}')" = "$COMPOSE_PROJECT_NAME"
test "$(docker inspect "$frontend_container" --format '{{ index .Config.Labels "com.docker.compose.project" }}')" = "$COMPOSE_PROJECT_NAME"
test "$(docker inspect "$backend_container" --format '{{ index .Config.Labels "com.docker.compose.service" }}')" = "backend"
test "$(docker inspect "$frontend_container" --format '{{ index .Config.Labels "com.docker.compose.service" }}')" = "frontend"
test "$(docker inspect "$backend_container" --format '{{.Config.Image}}')" = "$PREVIOUS_BACKEND_IMAGE"
test "$(docker inspect "$frontend_container" --format '{{.Config.Image}}')" = "$PREVIOUS_FRONTEND_IMAGE"
PREVIOUS_BACKEND_IMAGE_ID="$(docker inspect "$backend_container" --format '{{.Image}}')"
PREVIOUS_FRONTEND_IMAGE_ID="$(docker inspect "$frontend_container" --format '{{.Image}}')"
printf '%s' "$PREVIOUS_BACKEND_IMAGE_ID" | grep -Eq '^sha256:[0-9a-f]{64}$'
printf '%s' "$PREVIOUS_FRONTEND_IMAGE_ID" | grep -Eq '^sha256:[0-9a-f]{64}$'
docker image inspect "$PREVIOUS_BACKEND_IMAGE_ID" >/dev/null
docker image inspect "$PREVIOUS_FRONTEND_IMAGE_ID" >/dev/null
test "$(docker image inspect "$PREVIOUS_BACKEND_IMAGE" --format '{{.Id}}')" = "$PREVIOUS_BACKEND_IMAGE_ID"
test "$(docker image inspect "$PREVIOUS_FRONTEND_IMAGE" --format '{{.Id}}')" = "$PREVIOUS_FRONTEND_IMAGE_ID"

rollback_tag_suffix="$(printf '%s' "$RELEASE_BATCH_ID" | tr '[:upper:]' '[:lower:]')"
ROLLBACK_BACKEND_IMAGE="procurement-rollback-backend:$rollback_tag_suffix"
ROLLBACK_FRONTEND_IMAGE="procurement-rollback-frontend:$rollback_tag_suffix"
docker image tag "$PREVIOUS_BACKEND_IMAGE_ID" "$ROLLBACK_BACKEND_IMAGE"
docker image tag "$PREVIOUS_FRONTEND_IMAGE_ID" "$ROLLBACK_FRONTEND_IMAGE"
test "$(docker image inspect "$ROLLBACK_BACKEND_IMAGE" --format '{{.Id}}')" = "$PREVIOUS_BACKEND_IMAGE_ID"
test "$(docker image inspect "$ROLLBACK_FRONTEND_IMAGE" --format '{{.Id}}')" = "$PREVIOUS_FRONTEND_IMAGE_ID"
BACKEND_IMAGE="$ROLLBACK_BACKEND_IMAGE" FRONTEND_IMAGE="$ROLLBACK_FRONTEND_IMAGE" \
  "${COMPOSE[@]}" \
    --project-name "$COMPOSE_PROJECT_NAME" \
    -f "$current_compose_file" \
    --env-file "$REMOTE_DIR/deploy/.env" \
    config --quiet
export BACKEND_IMAGE="$TARGET_BACKEND_IMAGE"
export FRONTEND_IMAGE="$TARGET_FRONTEND_IMAGE"

DATABASE_NAME="$(compose_run "$current_compose_file" exec -T mysql sh -ec 'printf "%s" "$MYSQL_DATABASE"')"
if ! printf '%s' "$DATABASE_NAME" | grep -Eq '^[A-Za-z0-9_]+$'; then
  echo "MYSQL_DATABASE contains unsupported characters" >&2
  exit 1
fi

echo "Stopping application writers before creating the production recovery point..."
APPLICATION_STOPPED=1
compose_run "$current_compose_file" stop frontend backend
compose_run "$current_compose_file" up -d mysql redis qdrant
wait_for_mysql "$current_compose_file"

umask 077
BACKUP_ID="$RELEASE_BATCH_ID-$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="$backup_root/$BACKUP_ID"
if [ -e "$BACKUP_DIR" ] || [ -L "$BACKUP_DIR" ]; then
  echo "Recovery point already exists: $BACKUP_DIR" >&2
  exit 1
fi
BACKUP_TEMP_DIR="$(mktemp -d "$backup_root/.${BACKUP_ID}.XXXXXX")"
chmod 0700 "$BACKUP_TEMP_DIR"
install -m 0600 "$current_release_record" "$BACKUP_TEMP_DIR/current-release.json"

echo "Creating consistent MySQL and order-invoices recovery point..."
compose_run "$current_compose_file" exec -T mysql sh -ec '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump \
    --single-transaction \
    --quick \
    --routines \
    --events \
    --triggers \
    --hex-blob \
    --default-character-set=utf8mb4 \
    --set-gtid-purged=OFF \
    --databases "$MYSQL_DATABASE"
' | gzip -9 > "$BACKUP_TEMP_DIR/mysql.sql.gz"
test -s "$BACKUP_TEMP_DIR/mysql.sql.gz"
gzip -t "$BACKUP_TEMP_DIR/mysql.sql.gz"

invoice_manifest \
  "$REMOTE_DIR/data/order-invoices" \
  "$BACKUP_TEMP_DIR/order-invoices-manifest.json" \
  write
tar -czf "$BACKUP_TEMP_DIR/order-invoices.tar.gz" \
  -C "$REMOTE_DIR/data" \
  order-invoices
tar -tzf "$BACKUP_TEMP_DIR/order-invoices.tar.gz" >/dev/null
INVOICE_RESTORE_TEMP="$(mktemp -d "$BACKUP_TEMP_DIR/.invoice-verify.XXXXXX")"
tar -xzf "$BACKUP_TEMP_DIR/order-invoices.tar.gz" \
  -C "$INVOICE_RESTORE_TEMP" \
  --no-same-owner \
  --no-same-permissions
invoice_manifest \
  "$INVOICE_RESTORE_TEMP/order-invoices" \
  "$BACKUP_TEMP_DIR/order-invoices-manifest.json" \
  verify
rm -rf -- "$INVOICE_RESTORE_TEMP"
INVOICE_RESTORE_TEMP=""

DB_DUMP_SHA256="$(sha256sum "$BACKUP_TEMP_DIR/mysql.sql.gz" | awk '{print $1}')"
INVOICE_ARCHIVE_SHA256="$(sha256sum "$BACKUP_TEMP_DIR/order-invoices.tar.gz" | awk '{print $1}')"
INVOICE_MANIFEST_SHA256="$(sha256sum "$BACKUP_TEMP_DIR/order-invoices-manifest.json" | awk '{print $1}')"
CURRENT_RELEASE_RECORD_SHA256="$(sha256sum "$BACKUP_TEMP_DIR/current-release.json" | awk '{print $1}')"
db_dump_size="$(stat -c '%s' "$BACKUP_TEMP_DIR/mysql.sql.gz")"
invoice_archive_size="$(stat -c '%s' "$BACKUP_TEMP_DIR/order-invoices.tar.gz")"
created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

printf '{"schemaVersion":1,"releaseBatchId":"%s","targetReleaseSha":"%s","previousReleaseSha":"%s","createdAt":"%s","writerStopped":true,"database":{"name":"%s","file":"mysql.sql.gz","size":%s,"sha256":"%s"},"orderInvoices":{"archive":"order-invoices.tar.gz","size":%s,"sha256":"%s","manifest":"order-invoices-manifest.json","manifestSha256":"%s"},"previousImages":{"backendImage":"%s","backendImageId":"%s","frontendImage":"%s","frontendImageId":"%s"},"releaseFiles":{"currentReleaseRecord":"current-release.json","currentReleaseRecordSha256":"%s"}}\n' \
  "$RELEASE_BATCH_ID" \
  "$RELEASE_SHA" \
  "$PREVIOUS_RELEASE_SHA" \
  "$created_at" \
  "$DATABASE_NAME" \
  "$db_dump_size" \
  "$DB_DUMP_SHA256" \
  "$invoice_archive_size" \
  "$INVOICE_ARCHIVE_SHA256" \
  "$INVOICE_MANIFEST_SHA256" \
  "$PREVIOUS_BACKEND_IMAGE" \
  "$PREVIOUS_BACKEND_IMAGE_ID" \
  "$PREVIOUS_FRONTEND_IMAGE" \
  "$PREVIOUS_FRONTEND_IMAGE_ID" \
  "$CURRENT_RELEASE_RECORD_SHA256" > "$BACKUP_TEMP_DIR/manifest.json"

(cd "$BACKUP_TEMP_DIR" && sha256sum manifest.json > manifest.json.sha256)
(cd "$BACKUP_TEMP_DIR" && sha256sum --check --status manifest.json.sha256)
mv -- "$BACKUP_TEMP_DIR" "$BACKUP_DIR"
BACKUP_TEMP_DIR=""
RECOVERY_POINT_READY=1
(cd "$BACKUP_DIR" && sha256sum --check --status manifest.json.sha256)
echo "Recovery point ready: $BACKUP_ID"

echo "Synchronizing exact release source into $REMOTE_DIR..."
SOURCE_MUTATION_STARTED=1
rsync -a --delete \
  --exclude='/data/' \
  --exclude='/backups/' \
  --exclude='/deploy/.env' \
  --exclude='/deploy/releases/' \
  "$STAGING_DIR/" "$REMOTE_DIR/"

chmod 0600 "$REMOTE_DIR/deploy/.env"

current_compose_file="$REMOTE_DIR/deploy/docker-compose.prod.yml"
compose_run "$current_compose_file" config --quiet

echo "Applying versioned production database scripts..."
DATA_MUTATION_STARTED=1
compose_run "$current_compose_file" exec -T mysql sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --binary-mode=1 --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"' <<'SQL'
CREATE TABLE IF NOT EXISTS production_schema_migration_history (
  script_path VARCHAR(512) NOT NULL,
  blob_oid CHAR(40) NOT NULL,
  release_sha CHAR(40) NOT NULL,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (script_path, blob_oid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SQL

for sql_path in "${production_sql_paths[@]}"; do
  if ! [[ "$sql_path" =~ ^backend-springboot/sql/zz_[A-Za-z0-9._-]+\.sql$ ]]; then
    echo "Refusing unexpected production database script path: $sql_path" >&2
    exit 1
  fi
  blob_oid="$(git -C "$PROJECT_ROOT" rev-parse "$RELEASE_SHA:$sql_path")"
  printf '%s' "$blob_oid" | grep -Eq '^[0-9a-f]{40}$'
  applied_count="$(compose_run "$current_compose_file" exec -T mysql sh -ec \
    "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql --binary-mode=1 -uroot \"\$MYSQL_DATABASE\" -Nse \"SELECT COUNT(*) FROM production_schema_migration_history WHERE script_path = '$sql_path' AND blob_oid = '$blob_oid'\"")"
  if [ "$applied_count" = "1" ]; then
    echo "Skipping already applied $(basename "$sql_path") ($blob_oid)"
    continue
  fi

  echo "Applying $(basename "$sql_path") ($blob_oid)"
  git -C "$PROJECT_ROOT" cat-file blob "$RELEASE_SHA:$sql_path" | \
    compose_run "$current_compose_file" exec -T mysql sh -ec \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --binary-mode=1 --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"'
  compose_run "$current_compose_file" exec -T mysql sh -ec \
    "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql --binary-mode=1 -uroot \"\$MYSQL_DATABASE\" -e \"INSERT INTO production_schema_migration_history (script_path, blob_oid, release_sha) VALUES ('$sql_path', '$blob_oid', '$RELEASE_SHA')\""
done

if [ "${#production_sql_paths[@]}" -eq 0 ]; then
  echo "No production database scripts changed since $PREVIOUS_RELEASE_SHA"
fi

mkdir -p "$release_record_dir"
RELEASE_RECORD_TEMP="$(mktemp "$release_record_dir/.${RELEASE_SHA}.XXXXXX")"
printf '{"releaseSha":"%s","previousReleaseSha":"%s","releaseBatchId":"%s","recoveryPointId":"%s","deployedAt":"%s","backendImage":"%s","frontendImage":"%s"}\n' \
  "$RELEASE_SHA" \
  "$PREVIOUS_RELEASE_SHA" \
  "$RELEASE_BATCH_ID" \
  "$BACKUP_ID" \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  "$TARGET_BACKEND_IMAGE" \
  "$TARGET_FRONTEND_IMAGE" > "$RELEASE_RECORD_TEMP"
chmod 0600 "$RELEASE_RECORD_TEMP"
mv -f -- "$RELEASE_RECORD_TEMP" "$release_record_dir/$RELEASE_SHA.json"
RELEASE_RECORD_TEMP=""
CURRENT_RECORD_TEMP="$(mktemp "$release_record_dir/.current.XXXXXX")"
cp "$release_record_dir/$RELEASE_SHA.json" "$CURRENT_RECORD_TEMP"
chmod 0600 "$CURRENT_RECORD_TEMP"

echo "Starting the tested backend. From this point failures stop the candidate and retain data for operator review; they do not restore the snapshot."
CANDIDATE_APPLICATION_STARTED=1
recreate_service "$current_compose_file" backend procurement-backend
sleep 25

backend_container="$(compose_run "$current_compose_file" ps -q backend)"
test -n "$backend_container"
test "$(docker inspect "$backend_container" --format '{{.Image}}')" = "$(docker image inspect "$TARGET_BACKEND_IMAGE" --format '{{.Id}}')"
mapfile -t backend_networks < <(
  docker inspect "$backend_container" \
    --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}' |
    sed '/^$/d'
)
if [ "${#backend_networks[@]}" -ne 1 ]; then
  echo "Candidate backend must be attached to exactly one trusted Compose network" >&2
  exit 1
fi
if [ "$(docker network inspect "${backend_networks[0]}" --format '{{ index .Labels "com.docker.compose.project" }}')" != "$COMPOSE_PROJECT_NAME" ]; then
  echo "Candidate backend network does not belong to the trusted Compose project" >&2
  exit 1
fi

candidate_suffix="$(printf '%s' "$RELEASE_BATCH_ID" | tr -d '-' | cut -c1-12)"
CANDIDATE_FRONTEND_CONTAINER="procurement-frontend-candidate-$candidate_suffix"
docker run -d \
  --name "$CANDIDATE_FRONTEND_CONTAINER" \
  --network "${backend_networks[0]}" \
  "$TARGET_FRONTEND_IMAGE" >/dev/null
test "$(docker inspect "$CANDIDATE_FRONTEND_CONTAINER" --format '{{.Image}}')" = "$(docker image inspect "$TARGET_FRONTEND_IMAGE" --format '{{.Id}}')"
check_container_http "$CANDIDATE_FRONTEND_CONTAINER" frontend-candidate http://127.0.0.1/
check_container_http "$CANDIDATE_FRONTEND_CONTAINER" auth-candidate http://127.0.0.1/api/auth/me
docker rm -f "$CANDIDATE_FRONTEND_CONTAINER" >/dev/null
CANDIDATE_FRONTEND_CONTAINER=""

echo "Candidate checks passed; opening production traffic with the tested frontend..."
recreate_service "$current_compose_file" frontend procurement-frontend
frontend_container="$(compose_run "$current_compose_file" ps -q frontend)"
test -n "$frontend_container"
test "$(docker inspect "$frontend_container" --format '{{.State.Running}}')" = "true"
test "$(docker inspect "$frontend_container" --format '{{ index .Config.Labels "com.docker.compose.project" }}')" = "$COMPOSE_PROJECT_NAME"
test "$(docker inspect "$frontend_container" --format '{{ index .Config.Labels "com.docker.compose.service" }}')" = "frontend"
test "$(docker inspect "$frontend_container" --format '{{.Config.Image}}')" = "$TARGET_FRONTEND_IMAGE"
test "$(docker inspect "$frontend_container" --format '{{.Image}}')" = "$(docker image inspect "$TARGET_FRONTEND_IMAGE" --format '{{.Id}}')"
check_http frontend http://localhost/
check_http auth http://localhost/api/auth/me

mv -f -- "$CURRENT_RECORD_TEMP" "$current_release_record"
CURRENT_RECORD_TEMP=""

RELEASE_SUCCEEDED=1
echo "Production release completed with recovery point $BACKUP_ID."
