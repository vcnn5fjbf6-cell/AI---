#!/usr/bin/env bash
set -Eeuo pipefail

readonly EXPECTED_REMOTE_DIR="/home/caigou/procurement"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE_DIR="${DEPLOY_REMOTE_DIR:-$EXPECTED_REMOTE_DIR}"
RELEASE_SHA="${RELEASE_SHA:-}"

if ! printf '%s' "$RELEASE_SHA" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "RELEASE_SHA must be an exact 40-character commit SHA" >&2
  exit 1
fi
if [ "$REMOTE_DIR" != "$EXPECTED_REMOTE_DIR" ]; then
  echo "DEPLOY_REMOTE_DIR must be exactly $EXPECTED_REMOTE_DIR" >&2
  exit 1
fi

cd "$PROJECT_ROOT"
if [ "$(git rev-parse HEAD)" != "$RELEASE_SHA" ]; then
  echo "Checked out source does not match RELEASE_SHA" >&2
  exit 1
fi

remote_parent="$(dirname "$REMOTE_DIR")"
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
  "$REMOTE_DIR/deploy/releases"; do
  if [ -L "$managed_path" ]; then
    echo "Production managed path must not be a symbolic link: $managed_path" >&2
    exit 1
  fi
done

current_release_record="$REMOTE_DIR/deploy/releases/current.json"
if [ ! -f "$current_release_record" ] || [ -L "$current_release_record" ]; then
  echo "Missing trusted production baseline marker: $current_release_record" >&2
  echo "Verify the currently deployed commit and create current.json before the first automated release." >&2
  exit 1
fi
if ! baseline_output="$(python3 - "$current_release_record" <<'PY'
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    value = json.load(handle)
release_sha = value.get("releaseSha") if isinstance(value, dict) else None
backend_image = value.get("backendImage") if isinstance(value, dict) else None
frontend_image = value.get("frontendImage") if isinstance(value, dict) else None
if not isinstance(release_sha, str) or not re.fullmatch(r"[0-9a-f]{40}", release_sha):
    raise SystemExit("production baseline marker has an invalid releaseSha")
if not isinstance(backend_image, str):
    raise SystemExit("production baseline marker is missing backendImage")
if not isinstance(frontend_image, str):
    raise SystemExit("production baseline marker is missing frontendImage")
print(release_sha)
print(backend_image)
print(frontend_image)
PY
)"; then
  exit 1
fi
mapfile -t baseline_record <<< "$baseline_output"
if [ "${#baseline_record[@]}" -ne 3 ]; then
  echo "Production baseline marker is incomplete" >&2
  exit 1
fi

previous_release_sha="${baseline_record[0]%$'\r'}"
previous_backend_image="${baseline_record[1]%$'\r'}"
previous_frontend_image="${baseline_record[2]%$'\r'}"
if ! printf '%s' "$previous_backend_image" | grep -Eq '^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:[0-9a-f]{64}$' && \
  [ "$previous_backend_image" != "procurement-backend:hotfix-$previous_release_sha" ]; then
  echo "Production baseline backendImage is not a trusted digest or release-bound hotfix tag" >&2
  exit 1
fi
if ! printf '%s' "$previous_frontend_image" | grep -Eq '^ghcr\.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:[0-9a-f]{64}$' && \
  [ "$previous_frontend_image" != "procurement-frontend:hotfix-$previous_release_sha" ]; then
  echo "Production baseline frontendImage is not a trusted digest or release-bound hotfix tag" >&2
  exit 1
fi
if ! git cat-file -e "$previous_release_sha^{commit}" 2>/dev/null; then
  echo "Production baseline commit is not available in the trusted checkout: $previous_release_sha" >&2
  exit 1
fi
if ! git merge-base --is-ancestor "$previous_release_sha" "$RELEASE_SHA"; then
  echo "Release commit must be a descendant of the deployed production baseline" >&2
  exit 1
fi

printf '%s\n%s\n%s\n' \
  "$previous_release_sha" \
  "$previous_backend_image" \
  "$previous_frontend_image"
