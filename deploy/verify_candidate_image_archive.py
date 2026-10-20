#!/usr/bin/env python3
"""Verify a cached release image archive before Docker loads it."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import tarfile
from pathlib import Path


GIT_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
IMAGE_ID_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
IMAGE_RE = re.compile(r"^ghcr\.io/[a-z0-9._/-]+@sha256:[0-9a-f]{64}$")
EXPECTED_FILES = {
    "candidate-images.tar.gz",
    "candidate-images.tar.gz.sha256",
    "candidate-images.manifest",
}
EXPECTED_MANIFEST_KEYS = {
    "schema_version",
    "candidate_sha",
    "archive_sha256",
    "backend_image",
    "backend_image_id",
    "frontend_image",
    "frontend_image_id",
}


class VerificationError(RuntimeError):
    pass


def _read_manifest(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise VerificationError(f"cannot read candidate manifest: {exc}") from exc

    for line in lines:
        if not line or "=" not in line:
            raise VerificationError("candidate manifest contains an invalid line")
        key, value = line.split("=", 1)
        if key in values:
            raise VerificationError(f"candidate manifest repeats key: {key}")
        values[key] = value

    if set(values) != EXPECTED_MANIFEST_KEYS:
        raise VerificationError("candidate manifest fields do not match the trusted schema")
    return values


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise VerificationError(f"cannot read candidate image archive: {exc}") from exc
    return digest.hexdigest()


def _verify_docker_manifest(
    archive_path: Path,
    candidate_sha: str,
    backend_image: str,
    backend_image_id: str,
    frontend_image: str,
    frontend_image_id: str,
) -> None:
    backend_tag = f"{backend_image.split('@', 1)[0]}:test-{candidate_sha}"
    frontend_tag = f"{frontend_image.split('@', 1)[0]}:test-{candidate_sha}"

    try:
        with tarfile.open(archive_path, "r:gz") as archive:
            manifest_members = [
                member for member in archive.getmembers() if member.name == "manifest.json"
            ]
            if len(manifest_members) != 1 or not manifest_members[0].isfile():
                raise VerificationError("candidate Docker manifest is missing or ambiguous")
            if manifest_members[0].size > 4 * 1024 * 1024:
                raise VerificationError("candidate Docker manifest is unexpectedly large")
            manifest_stream = archive.extractfile(manifest_members[0])
            if manifest_stream is None:
                raise VerificationError("candidate Docker manifest cannot be read")
            manifest = json.load(manifest_stream)
            if not isinstance(manifest, list):
                raise VerificationError("candidate Docker manifest is invalid")

            def verify(tag: str, expected_image_id: str) -> None:
                expected_config_id = expected_image_id.removeprefix("sha256:")
                expected_configs = {
                    f"{expected_config_id}.json",
                    f"blobs/sha256/{expected_config_id}",
                }
                matches = [
                    item
                    for item in manifest
                    if isinstance(item, dict) and tag in item.get("RepoTags", [])
                ]
                if len(matches) != 1 or matches[0].get("Config") not in expected_configs:
                    raise VerificationError(
                        f"candidate Docker manifest does not bind {tag} to the trusted config ID"
                    )
                config_name = matches[0]["Config"]
                config_members = [
                    member for member in archive.getmembers() if member.name == config_name
                ]
                if len(config_members) != 1 or not config_members[0].isfile():
                    raise VerificationError(
                        f"candidate Docker config is missing or ambiguous: {config_name}"
                    )

            verify(backend_tag, backend_image_id)
            verify(frontend_tag, frontend_image_id)
    except (OSError, tarfile.TarError, json.JSONDecodeError) as exc:
        raise VerificationError(f"candidate Docker archive is invalid: {exc}") from exc


def verify_archive(
    archive_dir: Path,
    candidate_sha: str,
    backend_image: str,
    frontend_image: str,
    expected_archive_sha256: str | None = None,
    expected_backend_image_id: str | None = None,
    expected_frontend_image_id: str | None = None,
) -> dict[str, str]:
    if not GIT_SHA_RE.fullmatch(candidate_sha):
        raise VerificationError("candidate SHA is invalid")
    if not IMAGE_RE.fullmatch(backend_image) or not IMAGE_RE.fullmatch(frontend_image):
        raise VerificationError("candidate image digest is invalid")
    if archive_dir.is_symlink() or not archive_dir.is_dir():
        raise VerificationError("candidate archive directory is invalid")

    children = list(archive_dir.iterdir())
    if {child.name for child in children} != EXPECTED_FILES:
        raise VerificationError("candidate archive directory must contain exactly three files")
    if any(child.is_symlink() or not child.is_file() for child in children):
        raise VerificationError("candidate archive files must be regular files")

    archive_path = archive_dir / "candidate-images.tar.gz"
    checksum_path = archive_dir / "candidate-images.tar.gz.sha256"
    manifest_path = archive_dir / "candidate-images.manifest"
    if archive_path.stat().st_size <= 0:
        raise VerificationError("candidate image archive is empty")

    manifest = _read_manifest(manifest_path)
    if manifest["schema_version"] != "1":
        raise VerificationError("candidate manifest schema is unsupported")
    if manifest["candidate_sha"] != candidate_sha:
        raise VerificationError("candidate manifest SHA does not match the release")
    if manifest["backend_image"] != backend_image:
        raise VerificationError("candidate backend digest does not match the release")
    if manifest["frontend_image"] != frontend_image:
        raise VerificationError("candidate frontend digest does not match the release")
    if not SHA256_RE.fullmatch(manifest["archive_sha256"]):
        raise VerificationError("candidate archive checksum is invalid")
    if not IMAGE_ID_RE.fullmatch(manifest["backend_image_id"]):
        raise VerificationError("candidate backend image ID is invalid")
    if not IMAGE_ID_RE.fullmatch(manifest["frontend_image_id"]):
        raise VerificationError("candidate frontend image ID is invalid")

    optional_expectations = (
        ("archive checksum", expected_archive_sha256, manifest["archive_sha256"], SHA256_RE),
        ("backend image ID", expected_backend_image_id, manifest["backend_image_id"], IMAGE_ID_RE),
        ("frontend image ID", expected_frontend_image_id, manifest["frontend_image_id"], IMAGE_ID_RE),
    )
    for label, expected, actual, pattern in optional_expectations:
        if expected is not None and (not pattern.fullmatch(expected) or expected != actual):
            raise VerificationError(f"candidate {label} does not match the trusted build output")

    try:
        checksum_line = checksum_path.read_text(encoding="utf-8")
    except OSError as exc:
        raise VerificationError(f"cannot read candidate checksum: {exc}") from exc
    expected_checksum_line = f"{manifest['archive_sha256']}  candidate-images.tar.gz\n"
    if checksum_line != expected_checksum_line:
        raise VerificationError("candidate checksum file does not match the manifest")
    if _sha256(archive_path) != manifest["archive_sha256"]:
        raise VerificationError("candidate image archive checksum verification failed")

    _verify_docker_manifest(
        archive_path,
        candidate_sha,
        backend_image,
        manifest["backend_image_id"],
        frontend_image,
        manifest["frontend_image_id"],
    )
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive-dir", required=True, type=Path)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--backend-image", required=True)
    parser.add_argument("--frontend-image", required=True)
    parser.add_argument("--expected-archive-sha256")
    parser.add_argument("--expected-backend-image-id")
    parser.add_argument("--expected-frontend-image-id")
    args = parser.parse_args()

    try:
        verify_archive(
            args.archive_dir,
            args.candidate_sha,
            args.backend_image,
            args.frontend_image,
            args.expected_archive_sha256,
            args.expected_backend_image_id,
            args.expected_frontend_image_id,
        )
    except VerificationError as exc:
        print(f"candidate image archive rejected: {exc}", file=sys.stderr)
        return 1

    print("candidate image archive verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
