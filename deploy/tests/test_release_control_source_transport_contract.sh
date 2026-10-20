#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
workflow="$PROJECT_ROOT/.github/workflows/deploy-release-control.yml"
PYTHON_BIN="${PYTHON_BIN:-python3}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

grep -Fq 'name: Package trusted main for the isolated runner' "$workflow" ||
  fail "release control does not package trusted main on a hosted runner"
grep -Fq 'runs-on: ubuntu-latest' "$workflow" ||
  fail "trusted source package job is not hosted by GitHub"
grep -Fq 'git -C trusted archive --format=tar "$SOURCE_SHA"' "$workflow" ||
  fail "trusted source package is not bound to the exact source SHA"
grep -Fq 'actions/cache/save@0400d5f644dc74513175e3cd8d07132dd4860809' "$workflow" ||
  fail "trusted source cache save action is not pinned"
grep -Fq 'actions/cache/restore@0400d5f644dc74513175e3cd8d07132dd4860809' "$workflow" ||
  fail "trusted source cache restore action is not pinned"
[ "$(grep -Fc 'release-control-source-${{ github.run_id }}-${{ github.sha }}' "$workflow")" = "2" ] ||
  fail "source cache save and restore keys must match the exact run and source SHA"
grep -Fq 'test "$SOURCE_CACHE_HIT" = "true"' "$workflow" ||
  fail "self-hosted runner does not require an exact source cache hit"
grep -Fq 'test "$actual_archive_sha256" = "$EXPECTED_ARCHIVE_SHA256"' "$workflow" ||
  fail "self-hosted runner does not verify the hosted archive checksum"
grep -Fq 'trusted source manifest does not match this workflow run' "$workflow" ||
  fail "self-hosted runner does not bind the manifest to this workflow run"
grep -Fq 'trusted source archive contains an unsafe path' "$workflow" ||
  fail "self-hosted runner does not reject unsafe archive paths"
grep -Fq 'trusted source archive contains a non-regular entry' "$workflow" ||
  fail "self-hosted runner does not reject links and special files"
grep -Fq 'name: Remove trusted source cache from the runner' "$workflow" ||
  fail "self-hosted runner does not clean the source bundle"

deploy_block="$(sed -n '/^  deploy:/,$p' "$workflow")"
if grep -Fq 'uses: actions/checkout@' <<<"$deploy_block"; then
  fail "self-hosted release-control job must not connect to GitHub for checkout"
fi

test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT
sed -n '/python3 - "$manifest" "$archive"/,/^          PY$/p' "$workflow" |
  sed '1d;$d;s/^          //' > "$test_root/verify_source_archive.py"
"$PYTHON_BIN" -m py_compile "$test_root/verify_source_archive.py"

"$PYTHON_BIN" - "$test_root" <<'PY'
import io
import pathlib
import sys
import tarfile

root = pathlib.Path(sys.argv[1])

with tarfile.open(root / "good.tar.gz", "w:gz") as archive:
    content = b"trusted\n"
    member = tarfile.TarInfo("deploy/ok.txt")
    member.size = len(content)
    member.mode = 0o644
    archive.addfile(member, io.BytesIO(content))

with tarfile.open(root / "traversal.tar.gz", "w:gz") as archive:
    content = b"escape\n"
    member = tarfile.TarInfo("../escape.txt")
    member.size = len(content)
    member.mode = 0o644
    archive.addfile(member, io.BytesIO(content))

with tarfile.open(root / "symlink.tar.gz", "w:gz") as archive:
    member = tarfile.TarInfo("deploy/link")
    member.type = tarfile.SYMTYPE
    member.linkname = "/etc/passwd"
    archive.addfile(member)
PY

source_sha="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
for fixture in good traversal symlink; do
  checksum="$(sha256sum "$test_root/$fixture.tar.gz" | awk '{print $1}')"
  printf '%s\n' \
    'schema_version=1' \
    "source_sha=$source_sha" \
    "archive_sha256=$checksum" \
    > "$test_root/$fixture.manifest"
done

good_checksum="$(sha256sum "$test_root/good.tar.gz" | awk '{print $1}')"
"$PYTHON_BIN" "$test_root/verify_source_archive.py" \
  "$test_root/good.manifest" \
  "$test_root/good.tar.gz" \
  "$test_root/good-output" \
  "$source_sha" \
  "$good_checksum"
test "$(cat "$test_root/good-output/deploy/ok.txt")" = "trusted"

for fixture in traversal symlink; do
  checksum="$(sha256sum "$test_root/$fixture.tar.gz" | awk '{print $1}')"
  if "$PYTHON_BIN" "$test_root/verify_source_archive.py" \
    "$test_root/$fixture.manifest" \
    "$test_root/$fixture.tar.gz" \
    "$test_root/$fixture-output" \
    "$source_sha" \
    "$checksum" >/dev/null 2>&1; then
    fail "trusted source verifier accepted the $fixture fixture"
  fi
done

echo "Release control source transport contract passed"
