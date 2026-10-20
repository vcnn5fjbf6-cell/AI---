#!/usr/bin/env bash
set -Eeuo pipefail

readonly BACKEND_REPOSITORY="ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-backend"
readonly FRONTEND_REPOSITORY="ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-frontend"
readonly MAX_PULL_ATTEMPTS=3
PULL_RETRY_DELAY_SECONDS="${PULL_RETRY_DELAY_SECONDS:-15}"

require_digest() {
  local image="$1"
  local repository="$2"
  printf '%s' "$image" |
    grep -Eq "^${repository//./\\.}@sha256:[0-9a-f]{64}$" || {
      echo "Untrusted immutable image reference: $image" >&2
      return 1
    }
}

pull_exact_image() {
  local image="$1"
  local label="$2"
  local attempt=1
  local status=0

  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "$label image is already available locally"
    return 0
  fi

  while [ "$attempt" -le "$MAX_PULL_ATTEMPTS" ]; do
    echo "Pulling $label image (attempt $attempt/$MAX_PULL_ATTEMPTS)"
    if docker pull "$image"; then
      docker image inspect "$image" >/dev/null
      return 0
    else
      status=$?
    fi

    if [ "$attempt" -eq "$MAX_PULL_ATTEMPTS" ]; then
      echo "Unable to pull $label image after $MAX_PULL_ATTEMPTS attempts (last exit $status)" >&2
      return "$status"
    fi

    echo "$label image pull stopped with exit $status; completed Docker layers remain cached" >&2
    sleep "$PULL_RETRY_DELAY_SECONDS"
    attempt=$((attempt + 1))
  done
}

case "$PULL_RETRY_DELAY_SECONDS" in
  ""|*[!0-9]*)
    echo "PULL_RETRY_DELAY_SECONDS must be a non-negative integer" >&2
    exit 1
    ;;
esac

require_digest "${BACKEND_IMAGE:-}" "$BACKEND_REPOSITORY"
require_digest "${FRONTEND_IMAGE:-}" "$FRONTEND_REPOSITORY"
pull_exact_image "$BACKEND_IMAGE" backend
pull_exact_image "$FRONTEND_IMAGE" frontend
