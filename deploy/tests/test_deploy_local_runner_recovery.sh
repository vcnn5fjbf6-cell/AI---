#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_UNDER_TEST="$PROJECT_ROOT/deploy/deploy_local_runner.sh"
BASELINE_HELPER_UNDER_TEST="$PROJECT_ROOT/deploy/verify_production_baseline.sh"
REAL_TAR="$(command -v tar)"
PYTHON_BIN="${PYTHON_BIN:-}"

if [ -z "$PYTHON_BIN" ]; then
  for python_candidate in python3 python; do
    if command -v "$python_candidate" >/dev/null 2>&1 && \
      [ "$("$python_candidate" -c 'print("python-probe-ok")' 2>/dev/null || true)" = "python-probe-ok" ]; then
      PYTHON_BIN="$(command -v "$python_candidate")"
      break
    fi
  done
fi

if [ -z "$PYTHON_BIN" ]; then
  echo "python3 or python is required" >&2
  exit 1
fi

OLD_BACKEND_IMAGE_ID="sha256:$(printf '1%.0s' {1..64})"
OLD_FRONTEND_IMAGE_ID="sha256:$(printf '2%.0s' {1..64})"
NEW_BACKEND_IMAGE_ID="sha256:$(printf '3%.0s' {1..64})"
NEW_FRONTEND_IMAGE_ID="sha256:$(printf '4%.0s' {1..64})"
PREVIOUS_BACKEND_IMAGE="ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:$(printf 'c%.0s' {1..64})"
PREVIOUS_FRONTEND_IMAGE="ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:$(printf 'd%.0s' {1..64})"
TARGET_BACKEND_IMAGE="ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:$(printf 'a%.0s' {1..64})"
TARGET_FRONTEND_IMAGE="ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:$(printf 'b%.0s' {1..64})"
RELEASE_BATCH_ID="11111111-1111-4111-8111-111111111111"

TEST_ROOT="$(mktemp -d)"
ACTIVE_RELEASE_PID=""
cleanup_test_root() {
  if [ -n "$ACTIVE_RELEASE_PID" ]; then
    kill -TERM "$ACTIVE_RELEASE_PID" >/dev/null 2>&1 || true
    wait "$ACTIVE_RELEASE_PID" >/dev/null 2>&1 || true
  fi
  if [ "${KEEP_TEST_ROOT:-0}" = "1" ]; then
    echo "Recovery test fixtures retained at $TEST_ROOT" >&2
  else
    rm -rf -- "$TEST_ROOT"
  fi
}
trap cleanup_test_root EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file_contains() {
  local file="$1"
  local expected="$2"
  grep -Fq -- "$expected" "$file" || fail "$file does not contain: $expected"
}

assert_file_not_contains() {
  local file="$1"
  local unexpected="$2"
  if grep -Fq -- "$unexpected" "$file"; then
    fail "$file unexpectedly contains: $unexpected"
  fi
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  [ "$expected" = "$actual" ] || fail "$label: expected '$expected', got '$actual'"
}

write_fake_commands() {
  local fake_bin="$1"
  mkdir -p "$fake_bin"

  cat > "$fake_bin/python3" <<EOF
#!/usr/bin/env bash
exec "$PYTHON_BIN" "\$@"
EOF

  cat > "$fake_bin/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

  cat > "$fake_bin/watchdog-sleep" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
: > "$FAKE_STATE_DIR/watchdog-armed"
trap ': > "$FAKE_STATE_DIR/watchdog-cancelled"; exit 143' TERM
while [ ! -f "$FAKE_STATE_DIR/watchdog-fire" ]; do
  /usr/bin/sleep 0.05
done
EOF

  cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
mode="$(cat "$FAKE_STATE_DIR/app-mode")"
if [ "${FAIL_PHASE:-}" = "public_health" ] && [ "$mode" = "new" ]; then
  printf '503'
else
  printf '200'
fi
EOF

  cat > "$fake_bin/tar" <<EOF
#!/usr/bin/env bash
set -Eeuo pipefail
if [ "\${FAIL_PHASE:-}" = "backup_invoice" ] && printf '%s\n' "\$*" | grep -Fq 'order-invoices.tar.gz' && printf '%s\n' "\$*" | grep -Eq '(^|[[:space:]])-c'; then
  echo "forced invoice backup failure" >&2
  exit 71
fi
if [ "\${FAIL_PHASE:-}" = "backup_invoice_content" ] && printf '%s\n' "\$*" | grep -Fq 'order-invoices.tar.gz' && printf '%s\n' "\$*" | grep -Eq '(^|[[:space:]])-[^[:space:]]*x'; then
  "$REAL_TAR" "\$@"
  args=("\$@")
  extract_dir=""
  for ((index = 0; index < \${#args[@]}; index++)); do
    if [ "\${args[\$index]}" = "-C" ]; then
      extract_dir="\${args[\$((index + 1))]}"
      break
    fi
  done
  [ -n "\$extract_dir" ]
  printf 'invoice-corrupted-after-extract\n' > "\$extract_dir/order-invoices/invoice.txt"
  exit 0
fi
exec "$REAL_TAR" "\$@"
EOF

  cat > "$fake_bin/rsync" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
args=("$@")
count="${#args[@]}"
source_dir="${args[$((count - 2))]%/}"
target_dir="${args[$((count - 1))]%/}"
if [ "${FAIL_PHASE:-}" = "source_sync" ] && grep -Fq 'target-source' "$source_dir/source-version.txt"; then
  echo "forced source synchronization failure" >&2
  exit 74
fi
mkdir -p "$target_dir"

for existing in "$target_dir"/* "$target_dir"/.[!.]* "$target_dir"/..?*; do
  [ -e "$existing" ] || [ -L "$existing" ] || continue
  name="$(basename "$existing")"
  case "$name" in
    data|backups)
      ;;
    deploy)
      for child in "$existing"/* "$existing"/.[!.]* "$existing"/..?*; do
        [ -e "$child" ] || [ -L "$child" ] || continue
        child_name="$(basename "$child")"
        case "$child_name" in
          .env|releases) ;;
          *) rm -rf -- "$child" ;;
        esac
      done
      ;;
    *)
      rm -rf -- "$existing"
      ;;
  esac
done

for source_path in "$source_dir"/* "$source_dir"/.[!.]* "$source_dir"/..?*; do
  [ -e "$source_path" ] || [ -L "$source_path" ] || continue
  name="$(basename "$source_path")"
  case "$name" in
    data|backups)
      ;;
    deploy)
      mkdir -p "$target_dir/deploy"
      for child in "$source_path"/* "$source_path"/.[!.]* "$source_path"/..?*; do
        [ -e "$child" ] || [ -L "$child" ] || continue
        child_name="$(basename "$child")"
        case "$child_name" in
          .env|releases) ;;
          *) cp -a -- "$child" "$target_dir/deploy/" ;;
        esac
      done
      ;;
    *)
      cp -a -- "$source_path" "$target_dir/"
      ;;
  esac
done
if [ "${FAIL_PHASE:-}" = "source_sync_after_copy" ] && grep -Fq 'target-source' "$source_dir/source-version.txt"; then
  echo "forced source synchronization failure after copying candidate files" >&2
  exit 74
fi
EOF

  cat > "$fake_bin/docker-compose" <<'EOF'
#!/usr/bin/env bash
exec docker compose "$@"
EOF

  cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  printf '%s\n' "$*" >> "$FAKE_STATE_DIR/actions.log"
}

old_backend_id="${OLD_BACKEND_IMAGE_ID:?}"
old_frontend_id="${OLD_FRONTEND_IMAGE_ID:?}"
new_backend_id="${NEW_BACKEND_IMAGE_ID:?}"
new_frontend_id="${NEW_FRONTEND_IMAGE_ID:?}"
previous_backend="${PREVIOUS_BACKEND_IMAGE:?}"
previous_frontend="${PREVIOUS_FRONTEND_IMAGE:?}"
target_backend="${TARGET_BACKEND_IMAGE:?}"
target_frontend="${TARGET_FRONTEND_IMAGE:?}"

if [ "$1" = "compose" ] && [ "${2:-}" = "version" ]; then
  exit 0
fi

if [ "$1" = "image" ] && [ "$2" = "tag" ]; then
  log "IMAGE_TAG $3 $4"
  exit 0
fi

if [ "$1" = "image" ] && [ "$2" = "inspect" ]; then
  reference="$3"
  format="${5:-}"
  case "$reference" in
    "$target_backend") image_id="$new_backend_id" ;;
    "$target_frontend") image_id="$new_frontend_id" ;;
    "$previous_backend") image_id="$old_backend_id" ;;
    "$previous_frontend") image_id="$old_frontend_id" ;;
    procurement-rollback-backend:*) image_id="$old_backend_id" ;;
    procurement-rollback-frontend:*) image_id="$old_frontend_id" ;;
    "$old_backend_id") image_id="$old_backend_id" ;;
    "$old_frontend_id") image_id="$old_frontend_id" ;;
    *) echo "unknown image reference: $reference" >&2; exit 1 ;;
  esac
  if [ "$format" = "{{.Id}}" ]; then
    printf '%s\n' "$image_id"
  fi
  exit 0
fi

if [ "$1" = "inspect" ]; then
  container="$2"
  format="${4:-}"
  mode="$(cat "$FAKE_STATE_DIR/app-mode")"
  case "$format" in
    "{{.State.Running}}")
      if [ "$mode" = "stopped" ]; then printf 'false\n'; else printf 'true\n'; fi
      ;;
    "{{.Image}}")
      case "$container:$mode" in
        procurement-backend:old) printf '%s\n' "$old_backend_id" ;;
        procurement-frontend:old) printf '%s\n' "$old_frontend_id" ;;
        procurement-backend:new) printf '%s\n' "$new_backend_id" ;;
        procurement-frontend:new) printf '%s\n' "$new_frontend_id" ;;
        procurement-frontend-candidate-*:new) printf '%s\n' "$new_frontend_id" ;;
        *) echo "unknown container state: $container:$mode" >&2; exit 1 ;;
      esac
      ;;
    "{{.Config.Image}}")
      case "$container:$mode" in
        procurement-backend:old) printf '%s\n' "$previous_backend" ;;
        procurement-frontend:old) printf '%s\n' "$previous_frontend" ;;
        procurement-backend:new) printf '%s\n' "$target_backend" ;;
        procurement-frontend:new) printf '%s\n' "$target_frontend" ;;
        procurement-frontend-candidate-*:new) printf '%s\n' "$target_frontend" ;;
        *) exit 1 ;;
      esac
      ;;
    "{{range \$name, \$_ := .NetworkSettings.Networks}}{{\$name}}{{\"\\n\"}}{{end}}")
      case "$container" in
        procurement-backend) printf 'deploy_default\n' ;;
        *) exit 1 ;;
      esac
      ;;
    "{{ index .Config.Labels \"com.docker.compose.project\" }}")
      if [ "${PRECHECK_DRIFT:-}" = "project" ]; then
        printf 'wrong-project\n'
      else
        printf 'deploy\n'
      fi
      ;;
    "{{ index .Config.Labels \"com.docker.compose.service\" }}")
      case "$container" in
        procurement-backend) printf 'backend\n' ;;
        procurement-frontend) printf 'frontend\n' ;;
        *) exit 1 ;;
      esac
      ;;
    *) echo "unknown docker inspect format: $format" >&2; exit 1 ;;
  esac
  exit 0
fi

if [ "$1" = "rm" ] && [ "$2" = "-f" ]; then
  if printf '%s\n' "$*" | grep -Fq 'procurement-frontend-candidate-'; then
    log "CANDIDATE_FRONTEND_REMOVE"
    exit 0
  fi
  log "REMOVE_CANDIDATE"
  printf '1\n' > "$FAKE_STATE_DIR/rollback-mode"
  if [ "${ROLLBACK_FAIL_PHASE:-}" != "writers" ]; then
    printf 'stopped\n' > "$FAKE_STATE_DIR/app-mode"
  fi
  exit 0
fi

if [ "$1" = "network" ] && [ "$2" = "inspect" ]; then
  [ "$3" = "deploy_default" ] || exit 1
  if [ "${5:-}" = "{{ index .Labels \"com.docker.compose.project\" }}" ]; then
    if [ "${PRECHECK_DRIFT:-}" = "network" ]; then
      printf 'wrong-project\n'
    else
      printf 'deploy\n'
    fi
  fi
  exit 0
fi

if [ "$1" = "run" ]; then
  printf '%s\n' "$*" | grep -Fq -- '--network deploy_default' || exit 1
  printf '%s\n' "$*" | grep -Fq -- "$target_frontend" || exit 1
  log "CANDIDATE_FRONTEND_START"
  printf 'candidate-frontend-id\n'
  exit 0
fi

if [ "$1" = "exec" ]; then
  container="$2"
  shift 2
  case "$container" in
    procurement-frontend-candidate-*)
      printf '%s\n' "$*" | grep -Fq 'wget' || exit 1
      if [ "${FAIL_PHASE:-}" = "health" ] && printf '%s\n' "$*" | grep -Fq '/api/auth/me'; then
        exit 1
      fi
      exit 0
      ;;
    *)
      echo "unsupported standalone docker exec: $container $*" >&2
      exit 1
      ;;
  esac
fi

if [ "$1" = "ps" ]; then
  if printf '%s\n' "$*" | grep -Fq -- '-aq'; then
    if [ "${ROLLBACK_FAIL_PHASE:-}" = "writer_check" ] && [ "$(cat "$FAKE_STATE_DIR/rollback-mode")" = "1" ]; then
      echo "forced writer check failure" >&2
      exit 76
    fi
    if [ "${ROLLBACK_FAIL_PHASE:-}" = "writers" ] && [ "$(cat "$FAKE_STATE_DIR/rollback-mode")" = "1" ]; then
      printf 'rogue-writer\n'
    fi
  else
    printf 'NAME STATUS PORTS\n'
  fi
  exit 0
fi

if [ "$1" != "compose" ]; then
  echo "unsupported docker command: $*" >&2
  exit 1
fi

shift
project_name=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -f|--env-file|--project-name)
      if [ "$1" = "--project-name" ]; then
        project_name="$2"
      fi
      shift 2
      ;;
    *)
      break
      ;;
  esac
done
if [ "$project_name" != "deploy" ]; then
  echo "compose project was not fixed to deploy" >&2
  exit 75
fi

command="${1:-}"
shift || true
case "$command" in
  config)
    exit 0
    ;;
  ps)
    [ "$1" = "-q" ] || exit 1
    case "$2" in
      backend) printf 'procurement-backend\n' ;;
      frontend) printf 'procurement-frontend\n' ;;
      *) exit 1 ;;
    esac
    ;;
  stop)
    log "STOP_WRITERS"
    printf 'stopped\n' > "$FAKE_STATE_DIR/app-mode"
    if [ "${FAIL_PHASE:-}" = "stop" ]; then
      echo "forced Compose stop failure" >&2
      exit 77
    fi
    ;;
  down)
    log "COMPOSE_DOWN"
    printf 'stopped\n' > "$FAKE_STATE_DIR/app-mode"
    ;;
  up)
    joined="$*"
    if printf '%s' "$joined" | grep -Fq 'backend'; then
      if printf '%s' "${BACKEND_IMAGE:-}" | grep -Fq 'procurement-rollback-backend:'; then
        log "APP_UP_OLD"
        printf 'old\n' > "$FAKE_STATE_DIR/app-mode"
      else
        log "APP_UP_NEW"
        printf 'new\n' > "$FAKE_STATE_DIR/app-mode"
        case "${FAIL_PHASE:-}" in
          health|frontend_cutover|public_health)
            printf 'database-mutated\n' > "$FAKE_STATE_DIR/database-state"
            printf 'invoice-mutated\n' > "$FAKE_REMOTE_DIR/data/order-invoices/invoice.txt"
            ;;
        esac
      fi
    elif printf '%s' "$joined" | grep -Fq 'frontend'; then
      if [ "${FAIL_PHASE:-}" = "frontend_cutover" ]; then
        echo "forced production frontend cutover failure" >&2
        exit 78
      fi
      log "FRONTEND_UP_NEW"
      printf 'new\n' > "$FAKE_STATE_DIR/app-mode"
    else
      log "INFRA_UP"
    fi
    ;;
  exec)
    joined="$*"
    rollback_mode="$(cat "$FAKE_STATE_DIR/rollback-mode")"
    if printf '%s' "$joined" | grep -Fq 'printf "%s" "$MYSQL_DATABASE"'; then
      if [ "$rollback_mode" = "1" ] && [ "${ROLLBACK_FAIL_PHASE:-}" = "database_name" ]; then
        printf 'other_database'
      else
        printf 'procurement'
      fi
    elif printf '%s' "$joined" | grep -Fq 'mysqldump'; then
      log "BACKUP_DB"
      if [ "${FAIL_PHASE:-}" = "backup_db" ]; then
        exit 70
      fi
      printf '%s\n' "-- FAKE_DATABASE_STATE=$(cat "$FAKE_STATE_DIR/database-state")"
      printf '%s\n' \
        'CREATE DATABASE IF NOT EXISTS `procurement`;' \
        'USE `procurement`;' \
        'CREATE TABLE sample(id INT);' \
        'INSERT INTO sample VALUES (1);'
    elif printf '%s' "$joined" | grep -Fq 'DROP DATABASE IF EXISTS'; then
      log "RESTORE_DROP"
    elif printf '%s' "$joined" | grep -Fq 'SELECT COUNT(*) FROM production_schema_migration_history'; then
      printf '0\n'
    elif [ "$rollback_mode" = "1" ] && printf '%s' "$joined" | grep -Fq 'mysql --binary-mode=1 --default-character-set=utf8mb4 -uroot'; then
      if [ "${ROLLBACK_FAIL_PHASE:-}" = "deadline" ]; then
        : > "$FAKE_STATE_DIR/rollback-deadline-started"
        while [ ! -f "$FAKE_STATE_DIR/rollback-deadline-continue" ]; do
          /usr/bin/sleep 0.05
        done
      fi
      if [ "${ROLLBACK_FAIL_PHASE:-}" = "signal" ]; then
        printf 'database-partial\n' > "$FAKE_STATE_DIR/database-state"
        printf '%s\n' "$BASHPID" > "$FAKE_STATE_DIR/rollback-child-pid"
        : > "$FAKE_STATE_DIR/rollback-paused"
        trap 'exit 143' TERM
        while [ ! -f "$FAKE_STATE_DIR/rollback-continue" ]; do
          /usr/bin/sleep 0.05
        done
      fi
      restore_payload="$(cat)"
      log "RESTORE_DB"
      if [ "${ROLLBACK_FAIL_PHASE:-}" = "database" ]; then
        exit 73
      fi
      restored_state="$(printf '%s\n' "$restore_payload" | sed -n 's/^-- FAKE_DATABASE_STATE=//p' | head -n 1)"
      [ -n "$restored_state" ]
      printf '%s\n' "$restored_state" > "$FAKE_STATE_DIR/database-state"
    elif printf '%s' "$joined" | grep -Fq 'SELECT 1'; then
      :
    elif printf '%s' "$joined" | grep -Fq 'mysql'; then
      cat >/dev/null
      log "MIGRATE"
      if [ "${FAIL_PHASE:-}" = "migration" ] && [ "$rollback_mode" = "0" ]; then
        printf 'database-mutated\n' > "$FAKE_STATE_DIR/database-state"
        printf 'invoice-mutated\n' > "$FAKE_REMOTE_DIR/data/order-invoices/invoice.txt"
        exit 72
      fi
      if [ "${FAIL_PHASE:-}" = "watchdog_block" ] && [ "$rollback_mode" = "0" ]; then
        printf 'database-mutated\n' > "$FAKE_STATE_DIR/database-state"
        printf 'invoice-mutated\n' > "$FAKE_REMOTE_DIR/data/order-invoices/invoice.txt"
        : > "$FAKE_STATE_DIR/forward-child-blocked"
        trap 'exit 143' TERM
        while :; do
          /usr/bin/sleep 0.05
        done
      fi
    else
      echo "unsupported compose exec: $joined" >&2
      exit 1
    fi
    ;;
  *)
    echo "unsupported compose command: $command $*" >&2
    exit 1
    ;;
esac
EOF

  chmod +x "$fake_bin"/*
}

setup_case() {
  local case_name="$1"
  CASE_DIR="$TEST_ROOT/$case_name"
  REPO_DIR="$CASE_DIR/repo"
  REMOTE_DIR="$CASE_DIR/production"
  STATE_DIR="$CASE_DIR/state"
  FAKE_BIN="$CASE_DIR/fake-bin"
  mkdir -p "$REPO_DIR" "$REMOTE_DIR" "$STATE_DIR"
  printf 'old\n' > "$STATE_DIR/app-mode"
  printf '0\n' > "$STATE_DIR/rollback-mode"
  : > "$STATE_DIR/actions.log"
  printf 'database-original\n' > "$STATE_DIR/database-state"

  git -C "$REPO_DIR" init -q -b main
  git -C "$REPO_DIR" config user.name "Recovery Test"
  git -C "$REPO_DIR" config user.email "demo.user012@example.invalid"
  git -C "$REPO_DIR" config core.autocrlf false

  mkdir -p \
    "$REPO_DIR/deploy" \
    "$REPO_DIR/backend-springboot/sql" \
    "$REPO_DIR/frontend-vue"
  cat > "$REPO_DIR/deploy/docker-compose.prod.yml" <<'EOF'
services:
  backend:
    image: ${BACKEND_IMAGE}
  frontend:
    image: ${FRONTEND_IMAGE}
  mysql:
    image: mysql:8.0
EOF
  printf 'previous-source\n' > "$REPO_DIR/source-version.txt"
  printf '#!/usr/bin/env bash\n' > "$REPO_DIR/deploy/deploy_local_runner.sh"
  git -C "$REPO_DIR" add .
  git -C "$REPO_DIR" commit -qm "previous"
  PREVIOUS_SHA="$(git -C "$REPO_DIR" rev-parse HEAD)"

  "$REAL_TAR" -xf <(git -C "$REPO_DIR" archive "$PREVIOUS_SHA") -C "$REMOTE_DIR"
  mkdir -p \
    "$REMOTE_DIR/deploy/releases" \
    "$REMOTE_DIR/data/order-invoices" \
    "$REMOTE_DIR/backups"
  printf 'MYSQL_DATABASE=procurement\n' > "$REMOTE_DIR/deploy/.env"
  printf '{"releaseSha":"%s","backendImage":"%s","frontendImage":"%s"}\n' \
    "$PREVIOUS_SHA" \
    "$PREVIOUS_BACKEND_IMAGE" \
    "$PREVIOUS_FRONTEND_IMAGE" > "$REMOTE_DIR/deploy/releases/current.json"
  printf 'invoice-original\n' > "$REMOTE_DIR/data/order-invoices/invoice.txt"

  cp "$SCRIPT_UNDER_TEST" "$REPO_DIR/deploy/deploy_local_runner.sh"
  cp "$BASELINE_HELPER_UNDER_TEST" "$REPO_DIR/deploy/verify_production_baseline.sh"
  sed -i \
    "s|readonly EXPECTED_REMOTE_DIR=\"/home/caigou/procurement\"|readonly EXPECTED_REMOTE_DIR=\"$REMOTE_DIR\"|" \
    "$REPO_DIR/deploy/deploy_local_runner.sh"
  sed -i \
    "s|readonly EXPECTED_REMOTE_DIR=\"/home/caigou/procurement\"|readonly EXPECTED_REMOTE_DIR=\"$REMOTE_DIR\"|" \
    "$REPO_DIR/deploy/verify_production_baseline.sh"
  printf 'target-source\n' > "$REPO_DIR/source-version.txt"
  printf 'CREATE TABLE release_test(id INT);\n' > "$REPO_DIR/backend-springboot/sql/zz_release_test.sql"
  git -C "$REPO_DIR" add .
  git -C "$REPO_DIR" commit -qm "target"
  TARGET_SHA="$(git -C "$REPO_DIR" rev-parse HEAD)"

  write_fake_commands "$FAKE_BIN"
}

run_release() {
  local fail_phase="${1:-}"
  local rollback_fail_phase="${2:-}"
  local precheck_drift="${3:-}"
  local watchdog_sleep_command="${4:-}"
  set +e
  (
    cd "$REPO_DIR"
    export PATH="$FAKE_BIN:$PATH"
    export FAKE_STATE_DIR="$STATE_DIR"
    export FAKE_REMOTE_DIR="$REMOTE_DIR"
    export FAIL_PHASE="$fail_phase"
    export ROLLBACK_FAIL_PHASE="$rollback_fail_phase"
    export PRECHECK_DRIFT="$precheck_drift"
    export OLD_BACKEND_IMAGE_ID OLD_FRONTEND_IMAGE_ID
    export NEW_BACKEND_IMAGE_ID NEW_FRONTEND_IMAGE_ID
    export PREVIOUS_BACKEND_IMAGE PREVIOUS_FRONTEND_IMAGE
    export TARGET_BACKEND_IMAGE TARGET_FRONTEND_IMAGE
    export RELEASE_SHA="$TARGET_SHA"
    export RELEASE_BATCH_ID
    export BACKEND_IMAGE="$TARGET_BACKEND_IMAGE"
    export FRONTEND_IMAGE="$TARGET_FRONTEND_IMAGE"
    export DEPLOY_UPLOAD_ENV=0
    export DEPLOY_ALLOW_NONISOLATED_TEST=1
    if [ -n "$watchdog_sleep_command" ]; then
      export DEPLOY_WATCHDOG_SLEEP_COMMAND="$watchdog_sleep_command"
    fi
    bash deploy/deploy_local_runner.sh >"$CASE_DIR/output.log" 2>&1
  )
  RELEASE_STATUS=$?
  set -e
}

run_release_with_rollback_signal() {
  (
    cd "$REPO_DIR"
    export PATH="$FAKE_BIN:$PATH"
    export FAKE_STATE_DIR="$STATE_DIR"
    export FAKE_REMOTE_DIR="$REMOTE_DIR"
    export FAIL_PHASE=migration
    export ROLLBACK_FAIL_PHASE=signal
    export PRECHECK_DRIFT=""
    export OLD_BACKEND_IMAGE_ID OLD_FRONTEND_IMAGE_ID
    export NEW_BACKEND_IMAGE_ID NEW_FRONTEND_IMAGE_ID
    export PREVIOUS_BACKEND_IMAGE PREVIOUS_FRONTEND_IMAGE
    export TARGET_BACKEND_IMAGE TARGET_FRONTEND_IMAGE
    export RELEASE_SHA="$TARGET_SHA"
    export RELEASE_BATCH_ID
    export BACKEND_IMAGE="$TARGET_BACKEND_IMAGE"
    export FRONTEND_IMAGE="$TARGET_FRONTEND_IMAGE"
    export DEPLOY_UPLOAD_ENV=0
    export DEPLOY_ALLOW_NONISOLATED_TEST=1
    exec bash deploy/deploy_local_runner.sh
  ) >"$CASE_DIR/output.log" 2>&1 &
  release_pid=$!
  ACTIVE_RELEASE_PID="$release_pid"

  for _ in {1..1200}; do
    [ -f "$STATE_DIR/rollback-paused" ] && [ -f "$STATE_DIR/rollback-child-pid" ] && break
    /usr/bin/sleep 0.05
  done
  [ -f "$STATE_DIR/rollback-paused" ] || fail "rollback did not reach the signal checkpoint"
  [ -f "$STATE_DIR/rollback-child-pid" ] || fail "rollback restore child PID was not recorded"
  restore_child_pid="$(cat "$STATE_DIR/rollback-child-pid")"
  kill -TERM "$release_pid"
  kill -TERM "$restore_child_pid"
  : > "$STATE_DIR/rollback-continue"

  set +e
  wait "$release_pid"
  RELEASE_STATUS=$?
  set -e
  ACTIVE_RELEASE_PID=""
}

run_release_across_forward_deadline() {
  (
    cd "$REPO_DIR"
    export PATH="$FAKE_BIN:$PATH"
    export FAKE_STATE_DIR="$STATE_DIR"
    export FAKE_REMOTE_DIR="$REMOTE_DIR"
    export FAIL_PHASE=migration
    export ROLLBACK_FAIL_PHASE=deadline
    export PRECHECK_DRIFT=""
    export OLD_BACKEND_IMAGE_ID OLD_FRONTEND_IMAGE_ID
    export NEW_BACKEND_IMAGE_ID NEW_FRONTEND_IMAGE_ID
    export PREVIOUS_BACKEND_IMAGE PREVIOUS_FRONTEND_IMAGE
    export TARGET_BACKEND_IMAGE TARGET_FRONTEND_IMAGE
    export RELEASE_SHA="$TARGET_SHA"
    export RELEASE_BATCH_ID
    export BACKEND_IMAGE="$TARGET_BACKEND_IMAGE"
    export FRONTEND_IMAGE="$TARGET_FRONTEND_IMAGE"
    export DEPLOY_UPLOAD_ENV=0
    export DEPLOY_ALLOW_NONISOLATED_TEST=1
    export DEPLOY_FORWARD_TIMEOUT_SECONDS=1
    export DEPLOY_WATCHDOG_SLEEP_COMMAND="$FAKE_BIN/watchdog-sleep"
    exec bash deploy/deploy_local_runner.sh
  ) >"$CASE_DIR/output.log" 2>&1 &
  release_pid=$!
  ACTIVE_RELEASE_PID="$release_pid"

  for _ in {1..1200}; do
    [ -f "$STATE_DIR/rollback-deadline-started" ] && break
    /usr/bin/sleep 0.05
  done
  [ -f "$STATE_DIR/rollback-deadline-started" ] || fail "rollback did not reach the controlled deadline checkpoint"
  [ -f "$STATE_DIR/watchdog-cancelled" ] || fail "forward watchdog was not cancelled before rollback"

  : > "$STATE_DIR/watchdog-fire"
  /usr/bin/sleep 0.2
  kill -0 "$release_pid" >/dev/null 2>&1 || fail "release process was interrupted after the forward deadline"
  : > "$STATE_DIR/rollback-deadline-continue"

  set +e
  wait "$release_pid"
  RELEASE_STATUS=$?
  set -e
  ACTIVE_RELEASE_PID=""
}

run_release_until_watchdog_expiry() {
  (
    cd "$REPO_DIR"
    export PATH="$FAKE_BIN:$PATH"
    export FAKE_STATE_DIR="$STATE_DIR"
    export FAKE_REMOTE_DIR="$REMOTE_DIR"
    export FAIL_PHASE=watchdog_block
    export ROLLBACK_FAIL_PHASE=""
    export PRECHECK_DRIFT=""
    export OLD_BACKEND_IMAGE_ID OLD_FRONTEND_IMAGE_ID
    export NEW_BACKEND_IMAGE_ID NEW_FRONTEND_IMAGE_ID
    export PREVIOUS_BACKEND_IMAGE PREVIOUS_FRONTEND_IMAGE
    export TARGET_BACKEND_IMAGE TARGET_FRONTEND_IMAGE
    export RELEASE_SHA="$TARGET_SHA"
    export RELEASE_BATCH_ID
    export BACKEND_IMAGE="$TARGET_BACKEND_IMAGE"
    export FRONTEND_IMAGE="$TARGET_FRONTEND_IMAGE"
    export DEPLOY_UPLOAD_ENV=0
    unset DEPLOY_ALLOW_NONISOLATED_TEST
    export DEPLOY_FORWARD_TIMEOUT_SECONDS=1
    export DEPLOY_WATCHDOG_SLEEP_COMMAND="$FAKE_BIN/watchdog-sleep"
    exec setsid --wait -- bash deploy/deploy_local_runner.sh
  ) >"$CASE_DIR/output.log" 2>&1 &
  release_pid=$!
  ACTIVE_RELEASE_PID="$release_pid"

  for _ in {1..1200}; do
    [ -f "$STATE_DIR/watchdog-armed" ] && [ -f "$STATE_DIR/forward-child-blocked" ] && break
    /usr/bin/sleep 0.05
  done
  [ -f "$STATE_DIR/watchdog-armed" ] || fail "isolated watchdog was not armed"
  [ -f "$STATE_DIR/forward-child-blocked" ] || fail "forward child did not reach the watchdog checkpoint"
  : > "$STATE_DIR/watchdog-fire"

  set +e
  wait "$release_pid"
  RELEASE_STATUS=$?
  set -e
  ACTIVE_RELEASE_PID=""
}

run_release_with_finish_entry_signal() {
  (
    cd "$REPO_DIR"
    export PATH="$FAKE_BIN:$PATH"
    export FAKE_STATE_DIR="$STATE_DIR"
    export FAKE_REMOTE_DIR="$REMOTE_DIR"
    export FAIL_PHASE=health
    export ROLLBACK_FAIL_PHASE=""
    export PRECHECK_DRIFT=""
    export OLD_BACKEND_IMAGE_ID OLD_FRONTEND_IMAGE_ID
    export NEW_BACKEND_IMAGE_ID NEW_FRONTEND_IMAGE_ID
    export PREVIOUS_BACKEND_IMAGE PREVIOUS_FRONTEND_IMAGE
    export TARGET_BACKEND_IMAGE TARGET_FRONTEND_IMAGE
    export RELEASE_SHA="$TARGET_SHA"
    export RELEASE_BATCH_ID
    export BACKEND_IMAGE="$TARGET_BACKEND_IMAGE"
    export FRONTEND_IMAGE="$TARGET_FRONTEND_IMAGE"
    export DEPLOY_UPLOAD_ENV=0
    export DEPLOY_ALLOW_NONISOLATED_TEST=1
    export DEPLOY_TEST_FINISH_PAUSE_DIR="$STATE_DIR"
    exec bash deploy/deploy_local_runner.sh
  ) >"$CASE_DIR/output.log" 2>&1 &
  release_pid=$!
  ACTIVE_RELEASE_PID="$release_pid"

  for _ in {1..1200}; do
    [ -f "$STATE_DIR/finish-entry-paused" ] && break
    /usr/bin/sleep 0.05
  done
  [ -f "$STATE_DIR/finish-entry-paused" ] || fail "release did not pause at finish entry"
  kill -TERM "$release_pid"
  : > "$STATE_DIR/finish-entry-continue"

  set +e
  wait "$release_pid"
  RELEASE_STATUS=$?
  set -e
  ACTIVE_RELEASE_PID=""
}

current_release_sha() {
  "$PYTHON_BIN" - "$REMOTE_DIR/deploy/releases/current.json" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    print(json.load(handle)["releaseSha"])
PY
}

run_success_case() {
  setup_case success
  run_release
  assert_equals "0" "$RELEASE_STATUS" "successful release status"
  assert_equals "$TARGET_SHA" "$(current_release_sha)" "successful current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "target-source"
  assert_file_contains "$REMOTE_DIR/data/order-invoices/invoice.txt" "invoice-original"
  assert_file_contains "$STATE_DIR/actions.log" "BACKUP_DB"
  assert_file_contains "$STATE_DIR/actions.log" "MIGRATE"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_NEW"
  assert_file_contains "$STATE_DIR/actions.log" "CANDIDATE_FRONTEND_START"
  assert_file_contains "$STATE_DIR/actions.log" "FRONTEND_UP_NEW"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  backup_dir="$(find "$REMOTE_DIR/backups/releases" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  [ -n "$backup_dir" ] || fail "successful release did not retain a recovery point"
  (cd "$backup_dir" && sha256sum --check --status manifest.json.sha256) || fail "recovery manifest checksum failed"
  assert_file_contains "$REMOTE_DIR/deploy/releases/current.json" '"recoveryPointId"'
  assert_file_contains "$REMOTE_DIR/deploy/releases/current.json" "\"backendImage\":\"$TARGET_BACKEND_IMAGE\""
  assert_file_contains "$REMOTE_DIR/deploy/releases/current.json" "\"frontendImage\":\"$TARGET_FRONTEND_IMAGE\""
}

run_backup_failure_case() {
  setup_case backup_failure
  run_release backup_db
  [ "$RELEASE_STATUS" -ne 0 ] || fail "database backup failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "backup failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "previous-source"
  assert_file_contains "$STATE_DIR/actions.log" "BACKUP_DB"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_not_contains "$STATE_DIR/actions.log" "MIGRATE"
  assert_equals "old" "$(cat "$STATE_DIR/app-mode")" "backup failure application mode"
}

run_invoice_backup_failure_case() {
  setup_case invoice_backup_failure
  run_release backup_invoice
  [ "$RELEASE_STATUS" -ne 0 ] || fail "invoice backup failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "invoice backup failure current release"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_not_contains "$STATE_DIR/actions.log" "MIGRATE"
  assert_equals "old" "$(cat "$STATE_DIR/app-mode")" "invoice backup failure application mode"
}

run_invoice_content_failure_case() {
  setup_case invoice_content_failure
  run_release backup_invoice_content
  [ "$RELEASE_STATUS" -ne 0 ] || fail "corrupted invoice archive unexpectedly passed verification"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "invoice content failure current release"
  assert_file_contains "$REMOTE_DIR/data/order-invoices/invoice.txt" "invoice-original"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_not_contains "$STATE_DIR/actions.log" "MIGRATE"
  assert_file_contains "$CASE_DIR/output.log" "restored order-invoices content does not match"
}

run_migration_failure_case() {
  setup_case migration_failure
  run_release migration
  [ "$RELEASE_STATUS" -ne 0 ] || fail "migration failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "migration failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "previous-source"
  assert_file_contains "$REMOTE_DIR/data/order-invoices/invoice.txt" "invoice-original"
  assert_file_contains "$STATE_DIR/actions.log" "RESTORE_DROP"
  assert_file_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_equals "database-original" "$(cat "$STATE_DIR/database-state")" "migration failure database state"
  assert_equals "old" "$(cat "$STATE_DIR/app-mode")" "migration failure application mode"
  assert_file_not_contains "$REMOTE_DIR/deploy/releases/current.json" "$TARGET_SHA"
}

run_health_failure_case() {
  setup_case health_failure
  run_release health
  [ "$RELEASE_STATUS" -ne 0 ] || fail "health failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "health failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "target-source"
  assert_file_contains "$REMOTE_DIR/data/order-invoices/invoice.txt" "invoice-mutated"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_NEW"
  assert_file_contains "$STATE_DIR/actions.log" "CANDIDATE_FRONTEND_START"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_not_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_not_contains "$STATE_DIR/actions.log" "FRONTEND_UP_NEW"
  assert_equals "database-mutated" "$(cat "$STATE_DIR/database-state")" "health failure database state"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "health failure application mode"
  assert_file_contains "$CASE_DIR/output.log" "stopped without restoring data"
}

run_frontend_cutover_failure_case() {
  setup_case frontend_cutover_failure
  run_release frontend_cutover
  [ "$RELEASE_STATUS" -ne 0 ] || fail "production frontend cutover failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "frontend cutover failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "target-source"
  assert_file_contains "$REMOTE_DIR/data/order-invoices/invoice.txt" "invoice-mutated"
  assert_file_contains "$STATE_DIR/actions.log" "CANDIDATE_FRONTEND_START"
  assert_file_not_contains "$STATE_DIR/actions.log" "FRONTEND_UP_NEW"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_not_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_equals "database-mutated" "$(cat "$STATE_DIR/database-state")" "frontend cutover failure database state"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "frontend cutover failure application mode"
  assert_file_contains "$CASE_DIR/output.log" "stopped without restoring data"
}

run_public_health_failure_case() {
  setup_case public_health_failure
  run_release public_health
  [ "$RELEASE_STATUS" -ne 0 ] || fail "public frontend health failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "public health failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "target-source"
  assert_file_contains "$REMOTE_DIR/data/order-invoices/invoice.txt" "invoice-mutated"
  assert_file_contains "$STATE_DIR/actions.log" "FRONTEND_UP_NEW"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_not_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_equals "database-mutated" "$(cat "$STATE_DIR/database-state")" "public health failure database state"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "public health failure application mode"
  assert_file_contains "$CASE_DIR/output.log" "frontend check failed"
  assert_file_contains "$CASE_DIR/output.log" "stopped without restoring data"
}

run_stop_failure_case() {
  setup_case stop_failure
  run_release stop
  [ "$RELEASE_STATUS" -ne 0 ] || fail "Compose stop failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "stop failure current release"
  assert_file_contains "$STATE_DIR/actions.log" "STOP_WRITERS"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_not_contains "$STATE_DIR/actions.log" "BACKUP_DB"
  assert_file_not_contains "$STATE_DIR/actions.log" "MIGRATE"
  assert_equals "old" "$(cat "$STATE_DIR/app-mode")" "stop failure application mode"
}

run_source_sync_failure_case() {
  setup_case source_sync_failure
  run_release source_sync
  [ "$RELEASE_STATUS" -ne 0 ] || fail "source synchronization failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "source sync failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "previous-source"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_not_contains "$STATE_DIR/actions.log" "MIGRATE"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DROP"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
}

run_source_sync_after_copy_failure_case() {
  setup_case source_sync_after_copy_failure
  run_release source_sync_after_copy
  [ "$RELEASE_STATUS" -ne 0 ] || fail "post-copy source synchronization failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "post-copy source sync failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "previous-source"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_not_contains "$STATE_DIR/actions.log" "MIGRATE"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DROP"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
}

run_writer_stop_failure_case() {
  setup_case writer_stop_failure
  run_release migration writers
  [ "$RELEASE_STATUS" -ne 0 ] || fail "writer stop failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "writer stop failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "target-source"
  assert_file_contains "$REMOTE_DIR/data/order-invoices/invoice.txt" "invoice-mutated"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DROP"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_contains "$CASE_DIR/output.log" "Refusing source or data rollback while an application writer still exists"
}

run_writer_check_failure_case() {
  setup_case writer_check_failure
  run_release migration writer_check
  [ "$RELEASE_STATUS" -ne 0 ] || fail "writer verification failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "writer verification failure current release"
  assert_file_contains "$REMOTE_DIR/source-version.txt" "target-source"
  assert_file_contains "$REMOTE_DIR/data/order-invoices/invoice.txt" "invoice-mutated"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DROP"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_contains "$CASE_DIR/output.log" "Unable to verify that Compose service backend has no writer containers"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "writer verification failure fail-closed mode"
}

run_database_name_drift_case() {
  setup_case database_name_drift
  run_release migration database_name
  [ "$RELEASE_STATUS" -ne 0 ] || fail "database-name drift unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "database-name drift current release"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DROP"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_not_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "database-name drift fail-closed mode"
  assert_file_contains "$CASE_DIR/output.log" "current MYSQL_DATABASE does not match"
}

run_backup_symlink_case() {
  setup_case backup_symlink
  if ! ln -s "$REMOTE_DIR/data/order-invoices" "$REMOTE_DIR/backups/releases"; then
    echo "SKIP: runtime cannot create symbolic links; Linux PR job will execute this case" >&2
    return
  fi
  if [ ! -L "$REMOTE_DIR/backups/releases" ]; then
    echo "SKIP: runtime cannot create symbolic links; Linux PR job will execute this case" >&2
    return
  fi
  run_release
  [ "$RELEASE_STATUS" -ne 0 ] || fail "backup symlink unexpectedly passed"
  assert_file_not_contains "$STATE_DIR/actions.log" "STOP_WRITERS"
  assert_file_contains "$CASE_DIR/output.log" "must not be a symbolic link"
}

run_project_drift_case() {
  setup_case project_drift
  run_release "" "" project
  [ "$RELEASE_STATUS" -ne 0 ] || fail "Compose project drift unexpectedly passed"
  assert_file_not_contains "$STATE_DIR/actions.log" "STOP_WRITERS"
}

run_precheck_watchdog_case() {
  setup_case precheck_watchdog
  run_release "" "" project "$FAKE_BIN/watchdog-sleep"
  [ "$RELEASE_STATUS" -ne 0 ] || fail "precheck drift unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "precheck watchdog current release"
  [ -f "$STATE_DIR/watchdog-armed" ] || fail "forward watchdog did not cover release prechecks"
  [ -f "$STATE_DIR/watchdog-cancelled" ] || fail "forward watchdog was not cancelled after precheck failure"
  assert_file_not_contains "$STATE_DIR/actions.log" "STOP_WRITERS"
  assert_equals "old" "$(cat "$STATE_DIR/app-mode")" "precheck watchdog application mode"
}

run_network_drift_case() {
  setup_case network_drift
  run_release "" "" network
  [ "$RELEASE_STATUS" -ne 0 ] || fail "candidate backend network drift unexpectedly passed"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "network drift current release"
  assert_file_not_contains "$STATE_DIR/actions.log" "CANDIDATE_FRONTEND_START"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "network drift application mode"
  assert_file_contains "$CASE_DIR/output.log" "network does not belong to the trusted Compose project"
}

run_image_drift_case() {
  setup_case image_drift
  printf '{"releaseSha":"%s","backendImage":"%s","frontendImage":"%s"}\n' \
    "$PREVIOUS_SHA" \
    "$TARGET_BACKEND_IMAGE" \
    "$PREVIOUS_FRONTEND_IMAGE" > "$REMOTE_DIR/deploy/releases/current.json"
  run_release
  [ "$RELEASE_STATUS" -ne 0 ] || fail "baseline image drift unexpectedly passed"
  assert_file_not_contains "$STATE_DIR/actions.log" "STOP_WRITERS"
}

run_hotfix_baseline_case() {
  local original_backend_image="$PREVIOUS_BACKEND_IMAGE"
  local original_frontend_image="$PREVIOUS_FRONTEND_IMAGE"

  setup_case hotfix_baseline
  PREVIOUS_BACKEND_IMAGE="procurement-backend:hotfix-$PREVIOUS_SHA"
  PREVIOUS_FRONTEND_IMAGE="procurement-frontend:hotfix-$PREVIOUS_SHA"
  printf '{"releaseSha":"%s","backendImage":"%s","frontendImage":"%s"}\n' \
    "$PREVIOUS_SHA" \
    "$PREVIOUS_BACKEND_IMAGE" \
    "$PREVIOUS_FRONTEND_IMAGE" > "$REMOTE_DIR/deploy/releases/current.json"

  run_release
  assert_equals "0" "$RELEASE_STATUS" "release-bound hotfix baseline status"
  assert_equals "$TARGET_SHA" "$(current_release_sha)" "hotfix baseline current release"

  PREVIOUS_BACKEND_IMAGE="$original_backend_image"
  PREVIOUS_FRONTEND_IMAGE="$original_frontend_image"
}

run_untrusted_baseline_case() {
  setup_case untrusted_baseline
  printf '{"releaseSha":"%s","backendImage":"procurement-backend:latest","frontendImage":"%s"}\n' \
    "$PREVIOUS_SHA" \
    "$PREVIOUS_FRONTEND_IMAGE" > "$REMOTE_DIR/deploy/releases/current.json"
  run_release
  [ "$RELEASE_STATUS" -ne 0 ] || fail "untrusted mutable baseline tag unexpectedly passed"
  assert_file_not_contains "$STATE_DIR/actions.log" "STOP_WRITERS"
  assert_file_contains "$CASE_DIR/output.log" "not a trusted digest or release-bound hotfix tag"
}

run_legacy_baseline_case() {
  setup_case legacy_baseline
  printf '{"releaseSha":"%s"}\n' "$PREVIOUS_SHA" > "$REMOTE_DIR/deploy/releases/current.json"
  run_release
  [ "$RELEASE_STATUS" -ne 0 ] || fail "legacy SHA-only baseline unexpectedly passed"
  assert_file_not_contains "$STATE_DIR/actions.log" "STOP_WRITERS"
  assert_file_contains "$CASE_DIR/output.log" "production baseline marker is missing backendImage"
}

run_rollback_failure_case() {
  setup_case rollback_failure
  run_release migration database
  [ "$RELEASE_STATUS" -ne 0 ] || fail "rollback failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "rollback failure current release"
  assert_file_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_not_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_equals "database-mutated" "$(cat "$STATE_DIR/database-state")" "failed rollback database state"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "rollback failure fail-closed mode"
  backup_dir="$(find "$REMOTE_DIR/backups/releases" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  [ -n "$backup_dir" ] || fail "rollback failure did not retain the recovery point"
  assert_file_contains "$CASE_DIR/output.log" "Automatic rollback failed"
}

run_rollback_signal_case() {
  setup_case rollback_signal
  run_release_with_rollback_signal
  [ "$RELEASE_STATUS" -ne 0 ] || fail "rollback signal unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "rollback signal current release"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_not_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_equals "database-partial" "$(cat "$STATE_DIR/database-state")" "rollback signal partial database state"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "rollback signal controlled stop mode"
  assert_file_contains "$CASE_DIR/output.log" "Rollback received an interrupt signal"
  backup_dir="$(find "$REMOTE_DIR/backups/releases" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  [ -n "$backup_dir" ] || fail "rollback signal did not retain the recovery point"
}

run_forward_deadline_rollback_case() {
  setup_case forward_deadline_rollback
  run_release_across_forward_deadline
  [ "$RELEASE_STATUS" -ne 0 ] || fail "late forward failure unexpectedly succeeded"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "forward deadline rollback current release"
  assert_equals "database-original" "$(cat "$STATE_DIR/database-state")" "forward deadline rollback database state"
  assert_equals "invoice-original" "$(cat "$REMOTE_DIR/data/order-invoices/invoice.txt")" "forward deadline rollback invoice state"
  assert_equals "old" "$(cat "$STATE_DIR/app-mode")" "forward deadline rollback application mode"
  assert_file_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_contains "$CASE_DIR/output.log" "Automatic rollback completed"
  assert_file_not_contains "$CASE_DIR/output.log" "Rollback received an interrupt signal"
}

run_watchdog_expiry_case() {
  if ! command -v setsid >/dev/null 2>&1 || ! ps -o pgid= -p "$$" >/dev/null 2>&1; then
    echo "SKIP: watchdog_expiry requires Linux setsid and POSIX ps"
    return 0
  fi

  setup_case watchdog_expiry
  run_release_until_watchdog_expiry
  [ "$RELEASE_STATUS" -ne 0 ] || fail "expired watchdog unexpectedly allowed the release to succeed"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "watchdog expiry current release"
  assert_equals "database-original" "$(cat "$STATE_DIR/database-state")" "watchdog expiry database state"
  assert_equals "invoice-original" "$(cat "$REMOTE_DIR/data/order-invoices/invoice.txt")" "watchdog expiry invoice state"
  assert_equals "old" "$(cat "$STATE_DIR/app-mode")" "watchdog expiry application mode"
  assert_file_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_contains "$CASE_DIR/output.log" "Forward release exceeded 1s"
  assert_file_contains "$CASE_DIR/output.log" "Automatic rollback completed"
}

run_finish_entry_signal_case() {
  setup_case finish_entry_signal
  run_release_with_finish_entry_signal
  [ "$RELEASE_STATUS" -ne 0 ] || fail "finish-entry signal unexpectedly allowed the release to succeed"
  assert_equals "$PREVIOUS_SHA" "$(current_release_sha)" "finish-entry signal current release"
  assert_equals "database-mutated" "$(cat "$STATE_DIR/database-state")" "finish-entry signal database state"
  assert_equals "invoice-mutated" "$(cat "$REMOTE_DIR/data/order-invoices/invoice.txt")" "finish-entry signal invoice state"
  assert_equals "stopped" "$(cat "$STATE_DIR/app-mode")" "finish-entry signal application mode"
  assert_file_not_contains "$STATE_DIR/actions.log" "RESTORE_DB"
  assert_file_not_contains "$STATE_DIR/actions.log" "APP_UP_OLD"
  assert_file_contains "$CASE_DIR/output.log" "Rollback received an interrupt signal"
  assert_file_contains "$CASE_DIR/output.log" "stopped without restoring data"
  backup_dir="$(find "$REMOTE_DIR/backups/releases" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  [ -n "$backup_dir" ] || fail "finish-entry signal did not retain the recovery point"
}

run_named_case() {
  local case_name="$1"
  echo "CASE: $case_name"
  "run_${case_name}_case"
}

if [ -n "${RECOVERY_TEST_CASE:-}" ]; then
  run_named_case "$RECOVERY_TEST_CASE"
else
  run_named_case success
  run_named_case backup_failure
  run_named_case invoice_backup_failure
  run_named_case invoice_content_failure
  run_named_case migration_failure
  run_named_case health_failure
  run_named_case frontend_cutover_failure
  run_named_case public_health_failure
  run_named_case stop_failure
  run_named_case source_sync_failure
  run_named_case source_sync_after_copy_failure
  run_named_case writer_stop_failure
  run_named_case writer_check_failure
  run_named_case database_name_drift
  run_named_case backup_symlink
  run_named_case project_drift
  run_named_case precheck_watchdog
  run_named_case network_drift
  run_named_case image_drift
  run_named_case hotfix_baseline
  run_named_case untrusted_baseline
  run_named_case legacy_baseline
  run_named_case rollback_failure
  run_named_case rollback_signal
  run_named_case forward_deadline_rollback
  run_named_case watchdog_expiry
  run_named_case finish_entry_signal
fi

echo "deploy_local_runner recovery tests: PASS"
