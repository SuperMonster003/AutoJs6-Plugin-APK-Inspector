#!/usr/bin/env python3
"""Prepare and verify APK files staged for a GitHub release.

Release APK names retain the repository's existing eight-character CRC32 suffix
for compact identity. Cryptographic integrity is provided separately by one
``.sha256`` sidecar per APK and a deterministic ``SHA256SUMS`` manifest.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import sys
import tempfile
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


APK_SUFFIX = ".apk"
CHECKSUM_SUFFIX = ".sha256"
CHECKSUM_MANIFEST = "SHA256SUMS"
CHUNK_SIZE = 1024 * 1024
SAFE_PROJECT_NAME = re.compile(r"[a-z0-9][a-z0-9._-]*")
SAFE_VERSION_NAME = re.compile(r"[a-z0-9][a-z0-9._+-]*")


class ReleaseArtifactError(RuntimeError):
    """Raised when release artifacts do not satisfy the repository policy."""


@dataclass(frozen=True)
class ReleaseArtifact:
    """A verified release APK and its content digests."""

    apk: Path
    crc32: str
    sha256: str

    @property
    def checksum_file(self) -> Path:
        return self.apk.with_name(f"{self.apk.name}{CHECKSUM_SUFFIX}")


@dataclass(frozen=True)
class PreparedArtifact:
    """A source APK copied into the release staging directory."""

    source: Path
    release: ReleaseArtifact


def normalize_project_name(value: str) -> str:
    normalized = value.strip().lower()
    if not SAFE_PROJECT_NAME.fullmatch(normalized):
        raise ReleaseArtifactError(
            "project name must contain only lowercase ASCII letters, digits, '.', '_', or '-'"
        )
    return normalized


def normalize_version_name(value: str) -> str:
    normalized = re.sub(r"\s", "-", value.strip()).lower()
    if not SAFE_VERSION_NAME.fullmatch(normalized):
        raise ReleaseArtifactError(
            "version name must contain only ASCII letters, digits, '.', '_', '+', '-', or whitespace"
        )
    return normalized


def crc32_file(path: Path) -> str:
    checksum = 0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(CHUNK_SIZE), b""):
            checksum = zlib.crc32(chunk, checksum)
    return f"{checksum & 0xFFFFFFFF:08x}"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(CHUNK_SIZE), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _atomic_write(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=path.parent,
        prefix=f".{path.name}.",
        suffix=".tmp",
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _atomic_copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=destination.parent,
        prefix=f".{destination.name}.",
        suffix=".tmp",
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        shutil.copyfile(source, temporary)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)


def _source_apks(
    source_directory: Path,
    project_name: str,
    version_name: str,
) -> tuple[Path, ...]:
    if not source_directory.is_dir():
        raise ReleaseArtifactError(f"release APK source directory does not exist: {source_directory}")

    apks = tuple(
        sorted(
            (
                path
                for path in source_directory.iterdir()
                if path.is_file() and path.suffix.lower() == APK_SUFFIX
            ),
            key=lambda path: path.name,
        )
    )
    if not apks:
        raise ReleaseArtifactError(f"no APK files found in release source: {source_directory}")

    expected_stem = f"{project_name}-v{version_name}"
    allowed_suffix = re.compile(r"-[a-z0-9][a-z0-9._-]*")
    for apk in apks:
        if apk.name != apk.name.lower():
            raise ReleaseArtifactError(f"release APK source name must be lowercase: {apk.name}")
        suffix = apk.stem.removeprefix(expected_stem)
        if apk.stem != expected_stem and (
            not apk.stem.startswith(f"{expected_stem}-")
            or not allowed_suffix.fullmatch(suffix)
        ):
            raise ReleaseArtifactError(
                f"release APK source name does not match {expected_stem}[-variant].apk: {apk.name}"
            )
        if re.search(r"-[0-9a-f]{8}$", suffix):
            raise ReleaseArtifactError(f"release APK source already has a CRC32 suffix: {apk.name}")
    return apks


def _release_name_pattern(project_name: str) -> re.Pattern[str]:
    return re.compile(
        rf"{re.escape(project_name)}-v"
        rf"(?P<version>[a-z0-9][a-z0-9._+-]*)-"
        rf"(?P<crc32>[0-9a-f]{{8}})\.apk"
    )


def _scan_release_apks(
    destination_directory: Path,
    project_name: str,
) -> tuple[ReleaseArtifact, ...]:
    if not destination_directory.is_dir():
        raise ReleaseArtifactError(
            f"release artifact directory does not exist: {destination_directory}"
        )

    candidates = tuple(
        sorted(
            (
                path
                for path in destination_directory.iterdir()
                if path.is_file() and path.suffix.lower() == APK_SUFFIX
            ),
            key=lambda path: path.name,
        )
    )
    if not candidates:
        raise ReleaseArtifactError(f"no release APK files found in: {destination_directory}")

    pattern = _release_name_pattern(project_name)
    artifacts: list[ReleaseArtifact] = []
    for apk in candidates:
        match = pattern.fullmatch(apk.name)
        if match is None:
            raise ReleaseArtifactError(
                "release APK name must match "
                f"{project_name}-v<version>-<8-lowercase-hex-crc32>.apk: {apk.name}"
            )
        expected_crc32 = match.group("crc32")
        actual_crc32 = crc32_file(apk)
        if actual_crc32 != expected_crc32:
            raise ReleaseArtifactError(
                f"CRC32 suffix mismatch for {apk.name}: expected {expected_crc32}, got {actual_crc32}"
            )
        artifacts.append(
            ReleaseArtifact(
                apk=apk,
                crc32=actual_crc32,
                sha256=sha256_file(apk),
            )
        )
    return tuple(artifacts)


def _checksum_line(artifact: ReleaseArtifact) -> str:
    return f"{artifact.sha256}  {artifact.apk.name}\n"


def _expected_sidecars(artifacts: Iterable[ReleaseArtifact]) -> set[Path]:
    return {artifact.checksum_file for artifact in artifacts}


def write_checksum_files(
    destination_directory: Path,
    project_name: str,
) -> tuple[ReleaseArtifact, ...]:
    """Regenerate all per-APK sidecars and the aggregate manifest."""

    project_name = normalize_project_name(project_name)
    artifacts = _scan_release_apks(destination_directory, project_name)
    for artifact in artifacts:
        _atomic_write(artifact.checksum_file, _checksum_line(artifact).encode("ascii"))

    manifest = "".join(_checksum_line(artifact) for artifact in artifacts).encode("ascii")
    _atomic_write(destination_directory / CHECKSUM_MANIFEST, manifest)
    return artifacts


def verify_release_artifacts(
    destination_directory: Path,
    project_name: str,
) -> tuple[ReleaseArtifact, ...]:
    """Verify names, CRC32 suffixes, SHA-256 sidecars, and SHA256SUMS."""

    project_name = normalize_project_name(project_name)
    artifacts = _scan_release_apks(destination_directory, project_name)
    expected_sidecars = _expected_sidecars(artifacts)
    actual_sidecars = {
        path
        for path in destination_directory.iterdir()
        if path.is_file()
        and path.name.startswith(f"{project_name}-v")
        and path.name.endswith(f"{APK_SUFFIX}{CHECKSUM_SUFFIX}")
    }
    stale_sidecars = sorted(actual_sidecars - expected_sidecars, key=lambda path: path.name)
    if stale_sidecars:
        names = ", ".join(path.name for path in stale_sidecars)
        raise ReleaseArtifactError(f"checksum sidecar has no matching APK: {names}")

    for artifact in artifacts:
        expected = _checksum_line(artifact).encode("ascii")
        try:
            actual = artifact.checksum_file.read_bytes()
        except FileNotFoundError as error:
            raise ReleaseArtifactError(
                f"missing SHA-256 sidecar for {artifact.apk.name}: {artifact.checksum_file.name}"
            ) from error
        if actual != expected:
            raise ReleaseArtifactError(
                f"SHA-256 sidecar does not match {artifact.apk.name}: {artifact.checksum_file.name}"
            )

    expected_manifest = "".join(_checksum_line(artifact) for artifact in artifacts).encode("ascii")
    manifest_path = destination_directory / CHECKSUM_MANIFEST
    try:
        actual_manifest = manifest_path.read_bytes()
    except FileNotFoundError as error:
        raise ReleaseArtifactError(f"missing aggregate checksum file: {CHECKSUM_MANIFEST}") from error
    if actual_manifest != expected_manifest:
        raise ReleaseArtifactError(
            f"{CHECKSUM_MANIFEST} is not the canonical sorted checksum list for release APKs"
        )
    return artifacts


def prepare_release_artifacts(
    source_directory: Path,
    destination_directory: Path,
    project_name: str,
    version_name: str,
) -> tuple[PreparedArtifact, ...]:
    """Copy release APKs with CRC32 names, then write and verify SHA-256 files."""

    project_name = normalize_project_name(project_name)
    version_name = normalize_version_name(version_name)
    sources = _source_apks(source_directory, project_name, version_name)
    destination_directory.mkdir(parents=True, exist_ok=True)

    prepared_paths: list[tuple[Path, Path]] = []
    for source in sources:
        source_crc32 = crc32_file(source)
        source_sha256 = sha256_file(source)
        destination = destination_directory / f"{source.stem}-{source_crc32}{APK_SUFFIX}"

        if destination.exists():
            destination_sha256 = sha256_file(destination)
            if destination_sha256 != source_sha256:
                raise ReleaseArtifactError(
                    "refusing to overwrite a different APK with the same CRC32 release name: "
                    f"{destination.name}"
                )
        else:
            _atomic_copy(source, destination)
            destination_sha256 = sha256_file(destination)
            if destination_sha256 != source_sha256:
                destination.unlink(missing_ok=True)
                raise ReleaseArtifactError(f"copied APK failed SHA-256 verification: {destination.name}")
        prepared_paths.append((source, destination))

    write_checksum_files(destination_directory, project_name)
    verified = verify_release_artifacts(destination_directory, project_name)
    verified_by_path = {artifact.apk: artifact for artifact in verified}
    return tuple(
        PreparedArtifact(source=source, release=verified_by_path[destination])
        for source, destination in prepared_paths
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Prepare or verify CRC32-named APK release artifacts and SHA-256 files."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare = subparsers.add_parser("prepare", help="copy APKs and generate checksum files")
    prepare.add_argument("--source-dir", type=Path, required=True)
    prepare.add_argument("--destination-dir", type=Path, required=True)
    prepare.add_argument("--project-name", required=True)
    prepare.add_argument("--version", required=True)

    verify = subparsers.add_parser("verify", help="verify staged APKs and checksum files")
    verify.add_argument("--destination-dir", type=Path, required=True)
    verify.add_argument("--project-name", required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "prepare":
            prepared = prepare_release_artifacts(
                source_directory=args.source_dir.resolve(),
                destination_directory=args.destination_dir.resolve(),
                project_name=args.project_name,
                version_name=args.version,
            )
            for item in prepared:
                print(f"Prepared: {item.release.apk}")
                print(f"SHA-256: {item.release.sha256}")
            print(f"Checksums: {args.destination_dir.resolve() / CHECKSUM_MANIFEST}")
        else:
            verified = verify_release_artifacts(
                destination_directory=args.destination_dir.resolve(),
                project_name=args.project_name,
            )
            print(f"Verified {len(verified)} release APK(s) in {args.destination_dir.resolve()}")
    except ReleaseArtifactError as error:
        print(f"Release artifact error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
