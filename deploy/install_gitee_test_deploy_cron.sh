#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HOST="m6-procurement-151"
EXPECTED_USER="ceshi"
BOOTSTRAP="$HOME/.local/bin/procurement-gitee-test-bootstrap"
STATE_ROOT="$HOME/.local/state/procurement-test-gitee"
LOG_FILE="$STATE_ROOT/scheduler.log"
CRON_MARKER="# procurement-gitee-test-autodeploy"
CRON_LINE="*/5 * * * * $BOOTSTRAP > $LOG_FILE 2>&1 $CRON_MARKER"

if [ "$(hostname)" != "$EXPECTED_HOST" ] || [ "$(id -un)" != "$EXPECTED_USER" ]; then
  echo "Gitee test deployment cron may run only as $EXPECTED_USER on $EXPECTED_HOST" >&2
  exit 1
fi
if [ ! -x "$BOOTSTRAP" ] || [ -L "$BOOTSTRAP" ]; then
  echo "Missing or unsafe Gitee bootstrap: $BOOTSTRAP" >&2
  exit 1
fi
if [ -L "$STATE_ROOT" ] || [ -L "$LOG_FILE" ]; then
  echo "Gitee scheduler paths must not be symbolic links" >&2
  exit 1
fi
for required_command in crontab flock git docker curl python3; do
  command -v "$required_command" >/dev/null
done

install -d -m 0700 "$STATE_ROOT"
cron_temp="$(mktemp)"
cleanup() {
  rm -f "$cron_temp"
}
trap cleanup EXIT

crontab -l 2>/dev/null | grep -vF "$CRON_MARKER" > "$cron_temp" || true
printf '%s\n' "$CRON_LINE" >> "$cron_temp"
crontab "$cron_temp"

echo "Installed Gitee test deployment polling every 5 minutes"
