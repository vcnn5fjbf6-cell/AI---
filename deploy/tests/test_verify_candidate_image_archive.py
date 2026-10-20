from __future__ import annotations

import hashlib
import importlib.util
import io
import json
import tarfile
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = PROJECT_ROOT / "deploy" / "verify_candidate_image_archive.py"
SPEC = importlib.util.spec_from_file_location("verify_candidate_image_archive", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CandidateArchiveVerificationTest(unittest.TestCase):
    candidate_sha = "a" * 40
    backend_image = f"ghcr.io/example/backend@sha256:{'b' * 64}"
    frontend_image = f"ghcr.io/example/frontend@sha256:{'c' * 64}"
    backend_image_id = f"sha256:{'d' * 64}"
    frontend_image_id = f"sha256:{'e' * 64}"

    def create_archive(self, root: Path, *, frontend_tag_matches: bool = True) -> dict[str, str]:
        archive_path = root / "candidate-images.tar.gz"
        backend_tag = f"ghcr.io/example/backend:test-{self.candidate_sha}"
        frontend_tag = f"ghcr.io/example/frontend:test-{self.candidate_sha}"
        if not frontend_tag_matches:
            frontend_tag += "-wrong"
        docker_manifest = [
            {
                "Config": f"{'d' * 64}.json",
                "RepoTags": [backend_tag],
                "Layers": [],
            },
            {
                "Config": f"blobs/sha256/{'e' * 64}",
                "RepoTags": [frontend_tag],
                "Layers": [],
            },
        ]

        with tarfile.open(archive_path, "w:gz") as archive:
            entries = {
                "manifest.json": json.dumps(docker_manifest).encode(),
                f"{'d' * 64}.json": b"{}",
                f"blobs/sha256/{'e' * 64}": b"{}",
            }
            for name, content in entries.items():
                info = tarfile.TarInfo(name)
                info.size = len(content)
                archive.addfile(info, io.BytesIO(content))

        archive_sha256 = hashlib.sha256(archive_path.read_bytes()).hexdigest()
        manifest = {
            "schema_version": "1",
            "candidate_sha": self.candidate_sha,
            "archive_sha256": archive_sha256,
            "backend_image": self.backend_image,
            "backend_image_id": self.backend_image_id,
            "frontend_image": self.frontend_image,
            "frontend_image_id": self.frontend_image_id,
        }
        (root / "candidate-images.tar.gz.sha256").write_text(
            f"{archive_sha256}  candidate-images.tar.gz\n", encoding="utf-8"
        )
        (root / "candidate-images.manifest").write_text(
            "".join(f"{key}={value}\n" for key, value in manifest.items()),
            encoding="utf-8",
        )
        return manifest

    def test_accepts_archive_bound_to_release_and_both_image_ids(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            manifest = self.create_archive(root)
            result = MODULE.verify_archive(
                root,
                self.candidate_sha,
                self.backend_image,
                self.frontend_image,
                manifest["archive_sha256"],
                self.backend_image_id,
                self.frontend_image_id,
            )
            self.assertEqual(manifest, result)

    def test_rejects_release_sha_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self.create_archive(root)
            with self.assertRaisesRegex(MODULE.VerificationError, "SHA does not match"):
                MODULE.verify_archive(
                    root,
                    "f" * 40,
                    self.backend_image,
                    self.frontend_image,
                )

    def test_rejects_docker_tag_not_bound_to_trusted_image_id(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self.create_archive(root, frontend_tag_matches=False)
            with self.assertRaisesRegex(MODULE.VerificationError, "does not bind"):
                MODULE.verify_archive(
                    root,
                    self.candidate_sha,
                    self.backend_image,
                    self.frontend_image,
                )

    def test_rejects_unexpected_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self.create_archive(root)
            (root / "extra.txt").write_text("unexpected", encoding="utf-8")
            with self.assertRaisesRegex(MODULE.VerificationError, "exactly three files"):
                MODULE.verify_archive(
                    root,
                    self.candidate_sha,
                    self.backend_image,
                    self.frontend_image,
                )


if __name__ == "__main__":
    unittest.main()
