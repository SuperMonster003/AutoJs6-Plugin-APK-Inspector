from __future__ import annotations

import hashlib
import sys
import tempfile
import unittest
import zlib
from pathlib import Path


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIRECTORY))

from release_artifacts import (  # noqa: E402
    CHECKSUM_MANIFEST,
    ReleaseArtifactError,
    prepare_release_artifacts,
    verify_release_artifacts,
    write_checksum_files,
)


PROJECT_NAME = "autojs6-plugin-apk-inspector"


def crc32(payload: bytes) -> str:
    return f"{zlib.crc32(payload) & 0xFFFFFFFF:08x}"


def sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


class ReleaseArtifactsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.source = self.root / "source"
        self.destination = self.root / "releases"
        self.source.mkdir()

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def source_apk(self, version: str, payload: bytes, suffix: str = "") -> Path:
        path = self.source / f"{PROJECT_NAME}-v{version}{suffix}.apk"
        path.write_bytes(payload)
        return path

    def test_prepare_writes_crc32_name_sidecar_and_manifest(self) -> None:
        payload = b"signed release payload\x00\x01"
        source = self.source_apk("1.1.0", payload)

        prepared = prepare_release_artifacts(
            self.source,
            self.destination,
            PROJECT_NAME,
            "1.1.0",
        )

        expected_name = f"{PROJECT_NAME}-v1.1.0-{crc32(payload)}.apk"
        self.assertEqual(1, len(prepared))
        self.assertEqual(source, prepared[0].source)
        self.assertEqual(expected_name, prepared[0].release.apk.name)
        self.assertEqual(payload, prepared[0].release.apk.read_bytes())
        expected_line = f"{sha256(payload)}  {expected_name}\n"
        self.assertEqual(expected_line, prepared[0].release.checksum_file.read_text("ascii"))
        self.assertEqual(
            expected_line,
            (self.destination / CHECKSUM_MANIFEST).read_text("ascii"),
        )

    def test_prepare_is_idempotent_and_manifest_is_sorted(self) -> None:
        older_payload = b"older release"
        older_name = f"{PROJECT_NAME}-v1.0.0-{crc32(older_payload)}.apk"
        self.destination.mkdir()
        (self.destination / older_name).write_bytes(older_payload)
        current_payload = b"current release"
        self.source_apk("1.1.0", current_payload)

        first = prepare_release_artifacts(
            self.source,
            self.destination,
            PROJECT_NAME,
            "1.1.0",
        )
        first_snapshot = {
            path.name: path.read_bytes()
            for path in self.destination.iterdir()
            if path.is_file()
        }
        second = prepare_release_artifacts(
            self.source,
            self.destination,
            PROJECT_NAME,
            "1.1.0",
        )
        second_snapshot = {
            path.name: path.read_bytes()
            for path in self.destination.iterdir()
            if path.is_file()
        }

        self.assertEqual(first, second)
        self.assertEqual(first_snapshot, second_snapshot)
        manifest_lines = (self.destination / CHECKSUM_MANIFEST).read_text("ascii").splitlines()
        self.assertEqual(manifest_lines, sorted(manifest_lines, key=lambda line: line.split("  ", 1)[1]))
        self.assertEqual(2, len(verify_release_artifacts(self.destination, PROJECT_NAME)))

    def test_prepare_supports_variant_suffix_after_normalized_version(self) -> None:
        payload = b"arm64 release"
        self.source_apk("1.1.0-beta-1", payload, "-arm64-v8a")

        prepared = prepare_release_artifacts(
            self.source,
            self.destination,
            PROJECT_NAME,
            "1.1.0 Beta-1",
        )

        self.assertEqual(
            f"{PROJECT_NAME}-v1.1.0-beta-1-arm64-v8a-{crc32(payload)}.apk",
            prepared[0].release.apk.name,
        )

    def test_prepare_rejects_wrong_or_already_hashed_source_name(self) -> None:
        for invalid_name in (
            f"different-project-v1.1.0.apk",
            f"{PROJECT_NAME}-v1.1.0-deadbeef.apk",
        ):
            with self.subTest(invalid_name=invalid_name):
                for path in self.source.iterdir():
                    path.unlink()
                (self.source / invalid_name).write_bytes(b"payload")
                with self.assertRaises(ReleaseArtifactError):
                    prepare_release_artifacts(
                        self.source,
                        self.destination,
                        PROJECT_NAME,
                        "1.1.0",
                    )

    def test_verify_rejects_tampered_apk(self) -> None:
        self.source_apk("1.1.0", b"original")
        prepared = prepare_release_artifacts(
            self.source,
            self.destination,
            PROJECT_NAME,
            "1.1.0",
        )
        prepared[0].release.apk.write_bytes(b"tampered")

        with self.assertRaisesRegex(ReleaseArtifactError, "CRC32 suffix mismatch"):
            verify_release_artifacts(self.destination, PROJECT_NAME)

    def test_verify_rejects_tampered_sidecar_and_manifest(self) -> None:
        self.source_apk("1.1.0", b"release")
        prepared = prepare_release_artifacts(
            self.source,
            self.destination,
            PROJECT_NAME,
            "1.1.0",
        )
        artifact = prepared[0].release
        artifact.checksum_file.write_text("0" * 64 + "  wrong.apk\n", encoding="ascii")

        with self.assertRaisesRegex(ReleaseArtifactError, "sidecar does not match"):
            verify_release_artifacts(self.destination, PROJECT_NAME)

        write_checksum_files(self.destination, PROJECT_NAME)
        (self.destination / CHECKSUM_MANIFEST).write_text("not canonical\n", encoding="ascii")
        with self.assertRaisesRegex(ReleaseArtifactError, "not the canonical"):
            verify_release_artifacts(self.destination, PROJECT_NAME)


if __name__ == "__main__":
    unittest.main()
