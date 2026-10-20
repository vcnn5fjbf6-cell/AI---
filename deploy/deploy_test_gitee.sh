#!/usr/bin/env bash
set -euo pipefail

TRUSTED_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="${TEST_SOURCE_ROOT:-$TRUSTED_ROOT}"
STATE_ROOT="${TEST_GITEE_STATE_ROOT:-$HOME/.local/state/procurement-test-gitee}"
STATE_FILE="$STATE_ROOT/current-sha"
TARGET_SHA="${TEST_GITEE_TARGET_SHA:-$(git -C "$PROJECT_ROOT" rev-parse HEAD)}"

if [ "$(hostname)" != "m6-procurement-151" ] || [ "$(id -un)" != "ceshi" ]; then
  echo "Gitee test deployment may run only as ceshi on m6-procurement-151" >&2
  exit 1
fi
if ! printf '%s' "$TARGET_SHA" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "TEST_GITEE_TARGET_SHA must be an exact 40-character commit SHA" >&2
  exit 1
fi
if [ "$TARGET_SHA" != "$(git -C "$PROJECT_ROOT" rev-parse HEAD)" ]; then
  echo "The checked-out commit does not match TEST_GITEE_TARGET_SHA" >&2
  exit 1
fi
if [ -L "$STATE_ROOT" ] || [ -L "$STATE_FILE" ]; then
  echo "Gitee deployment state paths must not be symbolic links" >&2
  exit 1
fi
install -d -m 0700 "$STATE_ROOT"

if [ -s "$STATE_FILE" ]; then
  MIGRATION_BASE_REF="$(tr -d '\r\n' < "$STATE_FILE")"
else
  MIGRATION_BASE_REF="${TEST_GITEE_BOOTSTRAP_BASE_REF:-}"
fi
if ! printf '%s' "$MIGRATION_BASE_REF" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "A valid Gitee migration base SHA is required" >&2
  exit 1
fi
if ! git -C "$PROJECT_ROOT" merge-base --is-ancestor "$MIGRATION_BASE_REF" "$TARGET_SHA"; then
  echo "Gitee migration base must be an ancestor of the target SHA" >&2
  exit 1
fi

BACKEND_IMAGE="procurement-backend:gitee-test-$TARGET_SHA"
FRONTEND_IMAGE="procurement-frontend:gitee-test-$TARGET_SHA"
TEST_IMAGE_RETENTION_COUNT="${TEST_IMAGE_RETENTION_COUNT:-3}"
if ! [[ "$TEST_IMAGE_RETENTION_COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo "TEST_IMAGE_RETENTION_COUNT must be a positive integer" >&2
  exit 1
fi

echo "Building Gitee test backend image for $TARGET_SHA..."
docker build \
  --label "com.company.procurement.source=gitee" \
  --label "com.company.procurement.commit=$TARGET_SHA" \
  --tag "$BACKEND_IMAGE" \
  "$PROJECT_ROOT/backend-springboot"

echo "Building Gitee test frontend image for $TARGET_SHA..."
docker build \
  --build-arg VITE_APP_ENV=test \
  --build-arg VITE_BUILD_REF="$TARGET_SHA" \
  --label "com.company.procurement.source=gitee" \
  --label "com.company.procurement.commit=$TARGET_SHA" \
  --tag "$FRONTEND_IMAGE" \
  "$PROJECT_ROOT/frontend-vue"

test "$(docker image inspect "$BACKEND_IMAGE" --format '{{index .Config.Labels "com.company.procurement.commit"}}')" = "$TARGET_SHA"
test "$(docker image inspect "$FRONTEND_IMAGE" --format '{{index .Config.Labels "com.company.procurement.commit"}}')" = "$TARGET_SHA"

TEST_IMAGE_SOURCE=gitee-local \
TEST_STATUS_CHANNEL=gitee \
TEST_SOURCE_ROOT="$PROJECT_ROOT" \
TEST_BUILD_REF="$TARGET_SHA" \
TEST_MIGRATION_BASE_REF="$MIGRATION_BASE_REF" \
TEST_BACKEND_IMAGE="$BACKEND_IMAGE" \
TEST_FRONTEND_IMAGE="$FRONTEND_IMAGE" \
  bash "$TRUSTED_ROOT/deploy/deploy_test_runner.sh"

cleanup_test_images() {
  local repository="$1"
  local current_tag="$2"
  local keep_count="$TEST_IMAGE_RETENTION_COUNT"
  local retained=0
  local current_present=0
  local non_current_keep
  local created
  local tag
  local tag_list
  local -a tags=()

  if ! tag_list="$(
    docker image ls "$repository" --format '{{.Repository}}:{{.Tag}}' |
      awk -v repository="$repository" '$0 ~ ("^" repository ":gitee-test-[0-9a-f]{40}$")' |
      while IFS= read -r image_tag; do
        created="$(docker image inspect "$image_tag" --format '{{.Created}}')" || exit 1
        printf '%s %s\n' "$created" "$image_tag"
      done |
      sort -r |
      awk '{print $2}'
  )"; then
    return 1
  fi
  if [ -n "$tag_list" ]; then
    mapfile -t tags <<< "$tag_list"
  fi

  non_current_keep="$keep_count"
  for tag in "${tags[@]}"; do
    if [ "$tag" = "$current_tag" ]; then
      current_present=1
      break
    fi
  done
  if [ "$current_present" -eq 1 ]; then
    non_current_keep=$((keep_count - 1))
  fi

  for tag in "${tags[@]}"; do
    if [ "$tag" = "$current_tag" ]; then
      continue
    fi
    if [ "$retained" -lt "$non_current_keep" ]; then
      retained=$((retained + 1))
      continue
    fi
    docker image rm "$tag" >/dev/null || echo "Unable to remove unused test image $tag" >&2
  done
}

if ! cleanup_test_images "procurement-backend" "$BACKEND_IMAGE"; then
  echo "WARNING: unable to finish backend test image retention" >&2
fi
if ! cleanup_test_images "procurement-frontend" "$FRONTEND_IMAGE"; then
  echo "WARNING: unable to finish frontend test image retention" >&2
fi
if ! docker image prune --force \
  --filter 'label=com.company.procurement.source=gitee' \
  --filter "until=${TEST_DANGLING_IMAGE_MAX_AGE:-168h}" >/dev/null; then
  echo "WARNING: unable to prune old dangling Gitee test images" >&2
fi

disk_usage_percent="$(df -P "$PROJECT_ROOT" 2>/dev/null | awk 'NR == 2 {gsub(/%/, "", $5); print $5}')" || true
if [[ "$disk_usage_percent" =~ ^[0-9]+$ ]] && [ "$disk_usage_percent" -ge 80 ]; then
  echo "CRITICAL: test host disk usage is ${disk_usage_percent}%" >&2
elif [[ "$disk_usage_percent" =~ ^[0-9]+$ ]] && [ "$disk_usage_percent" -ge 70 ]; then
  echo "WARNING: test host disk usage is ${disk_usage_percent}%" >&2
elif ! [[ "$disk_usage_percent" =~ ^[0-9]+$ ]]; then
  echo "WARNING: unable to read test host disk usage" >&2
fi

state_temp="$STATE_FILE.$$.tmp"
printf '%s\n' "$TARGET_SHA" > "$state_temp"
chmod 0600 "$state_temp"
mv -f "$state_temp" "$STATE_FILE"

echo "Gitee test deployment state advanced to $TARGET_SHA"
