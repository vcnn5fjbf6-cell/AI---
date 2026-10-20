#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
STATE_DIR="$TEST_ROOT/state"

cleanup() {
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

mkdir -p "$FAKE_BIN" "$STATE_DIR"

cat > "$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}:${2:-}" in
  image:inspect)
    grep -Fxq -- "${3:-}" "$FAKE_DOCKER_STATE/pulled" 2>/dev/null
    ;;
  pull:*)
    image="${2:-}"
    printf 'pull %s\n' "$image" >> "$FAKE_DOCKER_STATE/calls"
    if [ "${FAKE_DOCKER_ALWAYS_FAIL:-0}" = "1" ]; then
      exit 42
    fi
    if [[ "$image" == *-backend@* ]] && [ ! -f "$FAKE_DOCKER_STATE/backend-interrupted" ]; then
      : > "$FAKE_DOCKER_STATE/backend-interrupted"
      exit 143
    fi
    printf '%s\n' "$image" >> "$FAKE_DOCKER_STATE/pulled"
    ;;
  *)
    echo "unexpected fake docker command: $*" >&2
    exit 2
    ;;
esac
EOF
chmod +x "$FAKE_BIN/docker"

backend_image="ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:$(printf 'b%.0s' {1..64})"
frontend_image="ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:$(printf 'f%.0s' {1..64})"

run_helper() {
  PATH="$FAKE_BIN:$PATH" \
    FAKE_DOCKER_STATE="$STATE_DIR" \
    PULL_RETRY_DELAY_SECONDS=0 \
    BACKEND_IMAGE="$backend_image" \
    FRONTEND_IMAGE="$frontend_image" \
    bash "$PROJECT_ROOT/deploy/pull_immutable_images.sh"
}

run_helper

[ "$(grep -Fxc -- "pull $backend_image" "$STATE_DIR/calls")" -eq 2 ] ||
  fail "backend pull was not retried after exit 143"
[ "$(grep -Fxc -- "pull $frontend_image" "$STATE_DIR/calls")" -eq 1 ] ||
  fail "frontend pull count is unexpected"
grep -Fxq -- "$backend_image" "$STATE_DIR/pulled" ||
  fail "backend digest was not registered"
grep -Fxq -- "$frontend_image" "$STATE_DIR/pulled" ||
  fail "frontend digest was not registered"

rm -f -- "$STATE_DIR/pulled" "$STATE_DIR/calls" "$STATE_DIR/backend-interrupted"
if FAKE_DOCKER_ALWAYS_FAIL=1 run_helper; then
  fail "exhausted image pulls unexpectedly succeeded"
fi
[ "$(grep -Fxc -- "pull $backend_image" "$STATE_DIR/calls")" -eq 3 ] ||
  fail "failed image pull did not stop after three attempts"

echo "immutable image pull retry tests: PASS"
