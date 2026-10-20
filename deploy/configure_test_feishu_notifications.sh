#!/usr/bin/env bash
set -Eeuo pipefail

trap 'status=$?; failed_line=$LINENO; trap - ERR; printf "Test Feishu configuration failed at line %s\n" "$failed_line" >&2; exit "$status"' ERR

TRUSTED_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_ROOT="${TEST_DEPLOY_ROOT:-/opt/procurement-test}"
ENV_FILE="$DEPLOY_ROOT/deploy/.env.test"
CONFIG_B64="${TEST_FEISHU_CONFIG_B64:-}"
LOCK_FILE="${TEST_DEPLOY_LOCK_FILE:-$HOME/.local/state/procurement-test/deploy.lock}"

if [ -z "$CONFIG_B64" ]; then
  echo "One-time test Feishu configuration is missing" >&2
  exit 1
fi

for managed_path in \
  "$DEPLOY_ROOT" \
  "$DEPLOY_ROOT/deploy" \
  "$DEPLOY_ROOT/deploy/.env.test" \
  "$DEPLOY_ROOT/deploy/docker-compose.test.yml" \
  "$DEPLOY_ROOT/deploy/test-status" \
  "$DEPLOY_ROOT/frontend-vue" \
  "$DEPLOY_ROOT/frontend-vue/deploy" \
  "$DEPLOY_ROOT/frontend-vue/deploy/nginx"; do
  if [ -L "$managed_path" ]; then
    echo "Test Feishu configuration path must not be a symbolic link: $managed_path" >&2
    exit 1
  fi
done

if [ -L "$LOCK_FILE" ]; then
  echo "Test deployment lock must not be a symbolic link: $LOCK_FILE" >&2
  exit 1
fi
install -d -m 0700 "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
if ! flock -w "${TEST_DEPLOY_LOCK_WAIT_SECONDS:-600}" 9; then
  echo "Timed out waiting for the test deployment lock" >&2
  exit 1
fi

test -f "$ENV_FILE"
test "$(stat -c '%a:%U' "$ENV_FILE")" = "600:ceshi"

backend_container_before=procurement-test-backend-1
frontend_container_before=procurement-test-frontend-1
egress_container_before=procurement-test-feishu-egress-1
test "$(docker inspect "$backend_container_before" --format '{{index .Config.Labels "com.docker.compose.project"}}:{{index .Config.Labels "com.docker.compose.service"}}')" = "procurement-test:backend"
test "$(docker inspect "$frontend_container_before" --format '{{index .Config.Labels "com.docker.compose.project"}}:{{index .Config.Labels "com.docker.compose.service"}}')" = "procurement-test:frontend"
egress_was_running="$(docker inspect "$egress_container_before" --format '{{.State.Running}}' 2>/dev/null || printf 'false')"
backend_image="$(docker inspect "$backend_container_before" --format '{{.Config.Image}}')"
frontend_image="$(docker inspect "$frontend_container_before" --format '{{.Config.Image}}')"
backend_image_id="$(docker inspect "$backend_container_before" --format '{{.Image}}')"
frontend_image_id="$(docker inspect "$frontend_container_before" --format '{{.Image}}')"
printf '%s' "$backend_image_id" | grep -Eq '^sha256:[0-9a-f]{64}$'
printf '%s' "$frontend_image_id" | grep -Eq '^sha256:[0-9a-f]{64}$'
test "$(docker image inspect "$backend_image" --format '{{.Id}}')" = "$backend_image_id"
test "$(docker image inspect "$frontend_image" --format '{{.Id}}')" = "$frontend_image_id"

if [[ "$backend_image" =~ ^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:[0-9a-f]{64}$ ]] &&
   [[ "$frontend_image" =~ ^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:[0-9a-f]{64}$ ]]; then
  image_source=ghcr
elif [[ "$backend_image" =~ ^procurement-backend:gitee-test-([0-9a-f]{40})$ ]]; then
  image_source=gitee-local
  build_ref="${BASH_REMATCH[1]}"
  test "$frontend_image" = "procurement-frontend:gitee-test-$build_ref"
  gitee_state_file="$HOME/.local/state/procurement-test-gitee/current-sha"
  test -f "$gitee_state_file"
  test ! -L "$gitee_state_file"
  test "$(stat -c '%a:%U' "$gitee_state_file")" = "600:ceshi"
  gitee_state_ref="$(tr -d '\r\n' < "$gitee_state_file")"
  if [ "$gitee_state_ref" != "$build_ref" ]; then
    echo "Gitee test state SHA $gitee_state_ref does not match running image SHA $build_ref" >&2
    exit 1
  fi
  test "$(docker image inspect "$backend_image" --format '{{index .Config.Labels "com.company.procurement.source"}}:{{index .Config.Labels "com.company.procurement.commit"}}')" = "gitee:$build_ref"
  test "$(docker image inspect "$frontend_image" --format '{{index .Config.Labels "com.company.procurement.source"}}:{{index .Config.Labels "com.company.procurement.commit"}}')" = "gitee:$build_ref"
  gitee_status_file="$DEPLOY_ROOT/deploy/test-status/current-gitee-build-ref"
  test ! -L "$gitee_status_file"
  if [ -e "$gitee_status_file" ]; then
    test -f "$gitee_status_file"
  fi
  python3 - "$gitee_status_file" "$build_ref" "$backend_image" "$frontend_image" <<'PY'
import json
import os
import sys
import tempfile

path, build_ref, backend_image, frontend_image = sys.argv[1:]
expected = {
    "buildRef": build_ref,
    "backendImage": backend_image,
    "frontendImage": frontend_image,
}
if os.path.exists(path):
    with open(path, encoding="utf-8") as handle:
        status = json.load(handle)
    if status != expected:
        raise SystemExit("current Gitee test image audit marker does not match the running containers")
else:
    directory = os.path.dirname(path)
    descriptor, temporary = tempfile.mkstemp(prefix=".current-gitee-build-ref.", dir=directory, text=True)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(expected, handle, ensure_ascii=True, separators=(",", ":"))
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.chmod(temporary, 0o644)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print("Restored missing Gitee test image audit marker")
PY
else
  echo "Current test images are not from a trusted GHCR or audited Gitee deployment" >&2
  exit 1
fi

install -m 0644 \
  "$TRUSTED_ROOT/deploy/docker-compose.test.yml" \
  "$DEPLOY_ROOT/deploy/docker-compose.test.yml"
install -m 0644 \
  "$TRUSTED_ROOT/frontend-vue/deploy/nginx/test-feishu-egress.conf" \
  "$DEPLOY_ROOT/frontend-vue/deploy/nginx/test-feishu-egress.conf"

env_backup="$(mktemp "$DEPLOY_ROOT/deploy/.env.test.before-feishu.XXXXXX")"
install -m 0600 "$ENV_FILE" "$env_backup"
env_update_committed=0
restore_previous_test_environment() {
  local status="$?"
  trap - EXIT
  if [ "$env_update_committed" != "1" ]; then
    mv -f "$env_backup" "$ENV_FILE"
    if declare -p COMPOSE >/dev/null 2>&1; then
      if [ "$egress_was_running" = "true" ]; then
        "${COMPOSE[@]}" up -d --no-build --force-recreate feishu-egress >/dev/null 2>&1 || true
      else
        "${COMPOSE[@]}" stop feishu-egress >/dev/null 2>&1 || true
      fi
    fi
    echo "Restored the previous test Feishu environment after configuration failure" >&2
  else
    rm -f "$env_backup"
  fi
  exit "$status"
}
trap restore_previous_test_environment EXIT

TEST_FEISHU_CONFIG_B64="$CONFIG_B64" python3 - "$ENV_FILE" <<'PY'
import base64
import binascii
import json
import os
import re
import sys
import tempfile

path = sys.argv[1]
encoded = os.environ.get("TEST_FEISHU_CONFIG_B64", "")
try:
    raw = base64.b64decode(encoded, validate=True)
    config = json.loads(raw.decode("utf-8"))
except (binascii.Error, UnicodeDecodeError, json.JSONDecodeError) as exc:
    raise SystemExit("one-time test Feishu configuration is invalid") from exc

if not isinstance(config, dict) or set(config) != {"enabled", "appId", "appSecret", "recipientEmail"}:
    raise SystemExit("one-time test Feishu configuration has unexpected fields")

enabled = config["enabled"]
app_id = config["appId"]
app_secret = config["appSecret"]
recipient_email = config["recipientEmail"]
if not isinstance(enabled, bool):
    raise SystemExit("test Feishu enabled flag must be boolean")
if not all(isinstance(value, str) for value in (app_id, app_secret, recipient_email)):
    raise SystemExit("test Feishu configuration values must be strings")

if enabled:
    if not re.fullmatch(r"cli_[A-Za-z0-9]{8,64}", app_id):
        raise SystemExit("test Feishu App ID is invalid")
    if not re.fullmatch(r"[A-Za-z0-9_-]{24,128}", app_secret):
        raise SystemExit("test Feishu App Secret is invalid")
    if not re.fullmatch(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,63}", recipient_email):
        raise SystemExit("test Feishu recipient email is invalid")
else:
    app_id = ""
    app_secret = ""
    recipient_email = ""

updates = {
    "TEST_FEISHU_TEST_CARD_ENABLED": "true" if enabled else "false",
    "TEST_FEISHU_APP_ID": app_id,
    "TEST_FEISHU_APP_SECRET": app_secret,
    "TEST_FEISHU_RECIPIENT_EMAIL": recipient_email,
}

with open(path, encoding="utf-8") as handle:
    original = handle.read().splitlines()

lines = []
seen = set()
for line in original:
    if not line or line.lstrip().startswith("#") or "=" not in line:
        lines.append(line)
        continue
    key, _ = line.split("=", 1)
    if key in updates:
        if key in seen:
            raise SystemExit("test environment contains duplicate Feishu configuration keys")
        seen.add(key)
        lines.append(f"{key}='{updates[key]}'")
    else:
        lines.append(line)
for key, value in updates.items():
    if key not in seen:
        lines.append(f"{key}='{value}'")

directory = os.path.dirname(path)
descriptor, temporary = tempfile.mkstemp(prefix=".env.test.", dir=directory, text=True)
try:
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")
        handle.flush()
        os.fsync(handle.fileno())
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
PY

mapfile -d '' config_values < <(python3 - "$ENV_FILE" <<'PY'
import sys

wanted = (
    "COMPOSE_PROJECT_NAME",
    "TEST_FEISHU_TEST_CARD_ENABLED",
    "TEST_FEISHU_APP_ID",
    "TEST_FEISHU_APP_SECRET",
    "TEST_FEISHU_RECIPIENT_EMAIL",
)
values = {}
with open(sys.argv[1], encoding="utf-8") as handle:
    for raw in handle:
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        values[key] = value
for key in wanted:
    print(values.get(key, ""), end="\0")
PY
)

compose_project="${config_values[0]}"
test_card_enabled="${config_values[1]}"
app_id="${config_values[2]}"
app_secret="${config_values[3]}"
recipient_email="${config_values[4]}"
test "$compose_project" = "procurement-test"

cd "$DEPLOY_ROOT"
export TEST_BACKEND_IMAGE="$backend_image"
export TEST_FRONTEND_IMAGE="$frontend_image"
COMPOSE=(docker compose --env-file deploy/.env.test -f deploy/docker-compose.test.yml)
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up -d --no-build --force-recreate feishu-egress

egress_container="$("${COMPOSE[@]}" ps -q feishu-egress)"
test -n "$egress_container"
test "$(docker inspect "$egress_container" --format '{{.Image}}')" = "$frontend_image_id"
for _ in {1..20}; do
  if [ "$(docker inspect "$egress_container" --format '{{.State.Health.Status}}')" = "healthy" ]; then
    break
  fi
  sleep 2
done
test "$(docker inspect "$egress_container" --format '{{.State.Health.Status}}')" = "healthy"

if [ "$test_card_enabled" = "true" ]; then
  egress_ip="$(docker inspect "$egress_container" --format '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' | awk '{print $1}')"
  printf '%s' "$egress_ip" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'
  token_payload="$(FEISHU_APP_ID="$app_id" FEISHU_APP_SECRET="$app_secret" python3 - <<'PY'
import json
import os

print(json.dumps({
    "app_id": os.environ["FEISHU_APP_ID"],
    "app_secret": os.environ["FEISHU_APP_SECRET"],
}, separators=(",", ":")))
PY
)"
  token_response="$(curl -fsS \
    --connect-timeout 10 \
    --max-time 30 \
    -H 'Content-Type: application/json; charset=utf-8' \
    --data "$token_payload" \
    "http://$egress_ip:8080/open-apis/auth/v3/tenant_access_token/internal")"
  FEISHU_TOKEN_RESPONSE="$token_response" python3 - <<'PY'
import json
import os

response = json.loads(os.environ["FEISHU_TOKEN_RESPONSE"])
if response.get("code") != 0 or not response.get("tenant_access_token"):
    raise SystemExit("test Feishu credentials were rejected")
PY
fi

"${COMPOSE[@]}" rm -sf db-configure
"${COMPOSE[@]}" up -d --no-build db-configure
db_configure_container="$("${COMPOSE[@]}" ps -aq db-configure)"
test -n "$db_configure_container"
if [ "$(docker wait "$db_configure_container")" != "0" ]; then
  docker logs --tail 80 "$db_configure_container" >&2 || true
  echo "Test Feishu recipient database configuration failed" >&2
  exit 1
fi

"${COMPOSE[@]}" up -d --no-build --force-recreate backend
backend_container="$("${COMPOSE[@]}" ps -q backend)"
test -n "$backend_container"
test "$(docker inspect "$backend_container" --format '{{.Image}}')" = "$backend_image_id"
for _ in {1..30}; do
  if [ "$(docker inspect "$backend_container" --format '{{.State.Health.Status}}')" = "healthy" ]; then
    break
  fi
  sleep 3
done
test "$(docker inspect "$backend_container" --format '{{.State.Health.Status}}')" = "healthy"

if docker exec "$backend_container" timeout 5 bash -c '</dev/tcp/1.1.1.1/443' >/dev/null 2>&1; then
  echo "Test backend unexpectedly has a direct external route" >&2
  exit 1
fi
docker exec "$backend_container" timeout 5 bash -c '</dev/tcp/feishu-egress/8080'

egress_members="$(docker network inspect procurement-test_feishu_egress --format '{{range .Containers}}{{.Name}}{{"\n"}}{{end}}')"
test "$egress_members" = "procurement-test-feishu-egress-1"

echo "Test Feishu card configuration applied; enabled=$test_card_enabled; image_source=$image_source"
env_update_committed=1
rm -f "$env_backup"
trap - EXIT
