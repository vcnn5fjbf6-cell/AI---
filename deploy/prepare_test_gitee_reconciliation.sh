#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HOST="m6-procurement-151"
EXPECTED_USER="ceshi"
ENV_FILE="/opt/procurement-test/deploy/.env.test"

if [ "$(hostname)" != "$EXPECTED_HOST" ] || [ "$(id -un)" != "$EXPECTED_USER" ]; then
  echo "Test reconciliation preparation may run only as $EXPECTED_USER on $EXPECTED_HOST" >&2
  exit 1
fi
for managed_path in /opt /opt/procurement-test /opt/procurement-test/deploy "$ENV_FILE"; do
  if [ -L "$managed_path" ]; then
    echo "Test reconciliation path must not be a symbolic link: $managed_path" >&2
    exit 1
  fi
done
test -f "$ENV_FILE"
test "$(stat -c '%a:%U' "$ENV_FILE")" = "600:ceshi"

python3 - "$ENV_FILE" <<'PY'
import os
import re
import sys
import tempfile

path = sys.argv[1]
updates = {
    "TEST_FEISHU_TEST_CARD_ENABLED": "false",
    "TEST_FEISHU_RECIPIENT_EMAIL": "",
}
lines = []
seen = set()
with open(path, encoding="utf-8") as handle:
    for raw in handle:
        line = raw.rstrip("\n")
        if not line or line.startswith("#"):
            lines.append(line)
            continue
        if "=" not in line:
            raise SystemExit("test environment contains an invalid line")
        key, value = line.split("=", 1)
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key) or key in seen:
            raise SystemExit("test environment contains an invalid or duplicate key")
        seen.add(key)
        lines.append(f"{key}={updates.get(key, value)}")
for key, value in updates.items():
    if key not in seen:
        lines.append(f"{key}={value}")

directory = os.path.dirname(path)
descriptor, temporary = tempfile.mkstemp(prefix=".env.test.reconcile.", dir=directory, text=True)
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

echo "Temporarily disabled the test card recipient during Gitee reconciliation"
