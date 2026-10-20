#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HOST="m6-procurement-151"
EXPECTED_USER="ceshi"
# Configure the repository URL outside this file; keep authentication in Git Credential Manager.
REPO_URL="${GITEE_REPO_URL:?Set GITEE_REPO_URL to the repository URL}"
DEPLOY_KEY="$HOME/.ssh/id_ed25519_gitee_test_deploy"
KNOWN_HOSTS="$HOME/.ssh/known_hosts.gitee"
DATA_ROOT="$HOME/.local/share/procurement-gitee-test"
REPO_DIR="$DATA_ROOT/repository.git"
RUNS_DIR="$DATA_ROOT/runs"
DEPLOY_STATE_FILE="$HOME/.local/state/procurement-test-gitee/current-sha"
GITEE_STATUS_FILE="/opt/procurement-test/deploy/test-status/current-gitee-build-ref"
RELEASE_STATUS_FILE="/opt/procurement-test/deploy/test-status/current-build-ref"
RELEASE_CONTROL_ENV="$HOME/procurement-release-control/.env"
SCHEDULER_LOCK_FILE="$HOME/.local/state/procurement-test-gitee/scheduler.lock"
BOOTSTRAP_BASE_REF="81304d459816adf2d692c7e4e888a94bd1d8434e"
SSH_COMMAND="ssh -i $DEPLOY_KEY -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$KNOWN_HOSTS -o BatchMode=yes"

if [ "$(hostname)" != "$EXPECTED_HOST" ] || [ "$(id -un)" != "$EXPECTED_USER" ]; then
  echo "Gitee bootstrap may run only as $EXPECTED_USER on $EXPECTED_HOST" >&2
  exit 1
fi
for required_file in "$DEPLOY_KEY" "$KNOWN_HOSTS"; do
  if [ ! -f "$required_file" ] || [ -L "$required_file" ]; then
    echo "Missing or unsafe Gitee SSH file: $required_file" >&2
    exit 1
  fi
done
if [ -L "$DATA_ROOT" ] || [ -L "$REPO_DIR" ] || [ -L "$RUNS_DIR" ] || \
   [ -L "$DEPLOY_STATE_FILE" ] || [ -L "$GITEE_STATUS_FILE" ] || \
   [ -L "$RELEASE_STATUS_FILE" ] || [ -L "$RELEASE_CONTROL_ENV" ] || \
   [ -L "$SCHEDULER_LOCK_FILE" ]; then
  echo "Gitee bootstrap paths must not be symbolic links" >&2
  exit 1
fi

install -d -m 0700 "$DATA_ROOT" "$RUNS_DIR" "$(dirname "$DEPLOY_STATE_FILE")"
exec 8>"$SCHEDULER_LOCK_FILE"
if ! flock -n 8; then
  echo "Another Gitee test deployment check is already running"
  exit 0
fi

release_control_state="$(python3 - "$RELEASE_CONTROL_ENV" <<'PY'
import base64
import json
import os
import sys
import urllib.request

path = sys.argv[1]
try:
    if not os.path.isfile(path) or os.path.islink(path):
        raise ValueError("unsafe release control environment")
    values = {}
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.rstrip("\n")
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                values[key] = value
    username = values["RELEASE_ADMIN_USERNAME"]
    password = values["RELEASE_ADMIN_PASSWORD"]
    host = values.get("RELEASE_CONTROL_BIND_ADDRESS", "127.0.0.1")
    port = values.get("RELEASE_CONTROL_HTTP_PORT", "18081")
    if host in {"0.0.0.0", "::"}:
        host = "127.0.0.1"
    if host not in {"127.0.0.1", "192.168.132.151"} or not port.isdigit():
        raise ValueError("unexpected release control endpoint")
    authorization = base64.b64encode(f"{username}:{password}".encode()).decode()
    request = urllib.request.Request(
        f"http://{host}:{port}/api/context",
        headers={"Authorization": f"Basic {authorization}"},
    )
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(request, timeout=5) as response:
        payload = json.load(response)
    if payload.get("configured") is not True:
        raise ValueError("release control is not configured")
    print("active" if payload.get("activeBatch") else "idle")
except Exception:
    print("unavailable")
PY
)"

case "$release_control_state" in
  active)
    echo "A managed release batch is active; skipping Gitee test deployment"
    exit 0
    ;;
  idle)
    ;;
  unavailable)
    if [ -s "$RELEASE_STATUS_FILE" ]; then
      echo "Release control is unavailable while a verified release marker exists; preserving the test environment"
      exit 0
    fi
    ;;
  *)
    echo "Unexpected release control state" >&2
    exit 1
    ;;
esac

if [ ! -d "$REPO_DIR" ]; then
  git init --bare "$REPO_DIR"
fi
if git --git-dir="$REPO_DIR" remote get-url origin >/dev/null 2>&1; then
  git --git-dir="$REPO_DIR" remote set-url origin "$REPO_URL"
else
  git --git-dir="$REPO_DIR" remote add origin "$REPO_URL"
fi

export GIT_SSH_COMMAND="$SSH_COMMAND"
git --git-dir="$REPO_DIR" fetch --no-tags --prune origin \
  '+refs/heads/main:refs/remotes/origin/main'
TARGET_SHA="$(git --git-dir="$REPO_DIR" rev-parse refs/remotes/origin/main)"
if ! printf '%s' "$TARGET_SHA" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "Gitee main did not resolve to an exact commit SHA" >&2
  exit 1
fi

expected_backend_image="procurement-backend:gitee-test-$TARGET_SHA"
expected_frontend_image="procurement-frontend:gitee-test-$TARGET_SHA"
expected_marker="{\"buildRef\":\"$TARGET_SHA\",\"backendImage\":\"$expected_backend_image\",\"frontendImage\":\"$expected_frontend_image\"}"
if [ -s "$DEPLOY_STATE_FILE" ] && \
   [ "$(tr -d '\r\n' < "$DEPLOY_STATE_FILE")" = "$TARGET_SHA" ] && \
   [ -s "$GITEE_STATUS_FILE" ] && \
   grep -Fqx "$expected_marker" "$GITEE_STATUS_FILE" && \
   [ "$(docker inspect procurement-test-backend-1 --format '{{.Config.Image}}' 2>/dev/null)" = "$expected_backend_image" ] && \
   [ "$(docker inspect procurement-test-frontend-1 --format '{{.Config.Image}}' 2>/dev/null)" = "$expected_frontend_image" ] && \
   [ "$(docker inspect procurement-test-feishu-egress-1 --format '{{.Config.Image}}' 2>/dev/null)" = "$expected_frontend_image" ] && \
   [ "$(docker inspect procurement-test-backend-1 --format '{{.State.Health.Status}}' 2>/dev/null)" = "healthy" ] && \
   [ "$(docker inspect procurement-test-frontend-1 --format '{{.State.Health.Status}}' 2>/dev/null)" = "healthy" ] && \
   [ "$(docker inspect procurement-test-feishu-egress-1 --format '{{.State.Health.Status}}' 2>/dev/null)" = "healthy" ] && \
   curl -fsS http://192.168.132.151:18080/ >/dev/null && \
   curl -fsS http://192.168.132.151:18080/v3/api-docs >/dev/null; then
  echo "Gitee main $TARGET_SHA is already healthy in the isolated test environment"
  exit 0
fi

RUN_DIR="$(mktemp -d "$RUNS_DIR/run.XXXXXX")"
WORKTREE="$RUN_DIR/source"
cleanup() {
  if [ -e "$WORKTREE/.git" ]; then
    git --git-dir="$REPO_DIR" worktree remove --force "$WORKTREE" || true
  fi
  rmdir "$RUN_DIR" 2>/dev/null || true
}
trap cleanup EXIT

git --git-dir="$REPO_DIR" worktree add --detach "$WORKTREE" "$TARGET_SHA"
TEST_SOURCE_ROOT="$WORKTREE" \
TEST_GITEE_TARGET_SHA="$TARGET_SHA" \
TEST_GITEE_BOOTSTRAP_BASE_REF="$BOOTSTRAP_BASE_REF" \
  bash "$WORKTREE/deploy/deploy_test_gitee.sh"

echo "Gitee main $TARGET_SHA deployed to the isolated test environment"
