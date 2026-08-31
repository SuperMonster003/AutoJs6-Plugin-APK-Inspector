# -*- coding: utf-8 -*-
"""Generate or verify the bundletool selection golden fixture.

The committed APKS is compacted after bundletool builds it: toc.pb is preserved byte-for-byte,
while each APK retains only its binary AndroidManifest.xml. This keeps the selector and the
plugin's complete archive-inspection path testable without committing signatures or large blobs.
"""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import zipfile


BUNDLETOOL_VERSION = "1.18.2"
BUNDLETOOL_SHA256 = "378b5434cd1378bef6b2bc527b8c7f0ff2584b273830335bce54d6d0813c8584"
BUNDLETOOL_URL = (
    "https://github.com/google/bundletool/releases/download/"
    f"{BUNDLETOOL_VERSION}/bundletool-all-{BUNDLETOOL_VERSION}.jar"
)
NORMALIZED_ZIP_TIME = (1980, 1, 1, 0, 0, 0)

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "test-fixtures" / "bundletool-selection"
BUILD_DIR = ROOT / "build" / "bundletool-selection"
GENERATED_DIR = BUILD_DIR / "generated"
RESOURCE_DIR = ROOT / "app" / "src" / "test" / "resources" / "bundletool-selection"
FIXTURE_NAME = "selection-matrix.apks"
MATRIX_NAME = "matrix.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run(command: list[str], cwd: Path = ROOT) -> None:
    printable = " ".join(command)
    print(f"> {printable}")
    subprocess.run(command, cwd=cwd, check=True)


def java_executable() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if candidate.is_file():
            return str(candidate)
    return "java"


def gradle_wrapper() -> str:
    return str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))


def ensure_bundletool() -> Path:
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    jar = BUILD_DIR / f"bundletool-all-{BUNDLETOOL_VERSION}.jar"
    if jar.is_file() and sha256(jar) == BUNDLETOOL_SHA256:
        return jar
    if jar.exists():
        jar.unlink()
    temporary = jar.with_suffix(".download")
    if temporary.exists():
        temporary.unlink()
    print(f"Downloading {BUNDLETOOL_URL}")
    urllib.request.urlretrieve(BUNDLETOOL_URL, temporary)
    actual = sha256(temporary)
    if actual != BUNDLETOOL_SHA256:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(
            f"bundletool SHA-256 mismatch: expected {BUNDLETOOL_SHA256}, got {actual}"
        )
    temporary.replace(jar)
    return jar


def build_apks(bundletool: Path) -> Path:
    run([gradle_wrapper(), "-p", str(SOURCE_DIR), ":app:bundleDebug", "--no-daemon"])
    bundle = SOURCE_DIR / "app" / "build" / "outputs" / "bundle" / "debug" / "app-debug.aab"
    if not bundle.is_file():
        raise FileNotFoundError(f"Fixture bundle was not produced: {bundle}")
    apks = BUILD_DIR / "selection-matrix-full.apks"
    apks.unlink(missing_ok=True)
    run(
        [
            java_executable(),
            "-jar",
            str(bundletool),
            "build-apks",
            f"--bundle={bundle}",
            f"--output={apks}",
            "--overwrite",
        ]
    )
    return apks


def load_device_specs() -> list[tuple[str, Path, dict]]:
    cases = []
    for path in sorted((SOURCE_DIR / "devices").glob("*.json")):
        with path.open("r", encoding="utf-8") as stream:
            cases.append((path.stem, path, json.load(stream)))
    if len(cases) != 3:
        raise RuntimeError(f"Expected exactly three device specs, found {len(cases)}")
    return cases


def extract_oracle(bundletool: Path, apks: Path, case_id: str, spec_path: Path) -> list[str]:
    output = BUILD_DIR / "extracted" / case_id
    if output.exists():
        shutil.rmtree(output)
    run(
        [
            java_executable(),
            "-jar",
            str(bundletool),
            "extract-apks",
            f"--apks={apks}",
            f"--device-spec={spec_path}",
            f"--output-dir={output}",
            "--include-metadata",
        ]
    )
    with (output / "metadata.json").open("r", encoding="utf-8") as stream:
        metadata = json.load(stream)
    return [item["path"] for item in metadata["apks"]]


def normalized_zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, NORMALIZED_ZIP_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    info.create_system = 3
    return info


def compact_apk(apk_bytes: bytes, archive_path: str) -> bytes:
    with zipfile.ZipFile(io.BytesIO(apk_bytes), "r") as source:
        try:
            manifest = source.read("AndroidManifest.xml")
        except KeyError as error:
            raise RuntimeError(f"{archive_path} has no AndroidManifest.xml") from error
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as destination:
        destination.writestr(normalized_zip_info("AndroidManifest.xml"), manifest)
    return output.getvalue()


def compact_apks(source_path: Path, destination_path: Path) -> dict[str, str]:
    destination_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination_path.with_suffix(".tmp")
    temporary.unlink(missing_ok=True)
    basename_to_path: dict[str, str] = {}
    with zipfile.ZipFile(source_path, "r") as source, zipfile.ZipFile(temporary, "w") as output:
        output.writestr(normalized_zip_info("toc.pb"), source.read("toc.pb"))
        for name in sorted(item.filename for item in source.infolist() if item.filename.endswith(".apk")):
            basename = Path(name).name
            if basename in basename_to_path:
                raise RuntimeError(f"Duplicate APK basename cannot be mapped safely: {basename}")
            basename_to_path[basename] = name
            output.writestr(normalized_zip_info(name), compact_apk(source.read(name), name))
    temporary.replace(destination_path)
    return basename_to_path


def generate(bundletool: Path) -> None:
    full_apks = build_apks(bundletool)
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    fixture = GENERATED_DIR / FIXTURE_NAME
    basename_to_path = compact_apks(full_apks, fixture)

    matrix_cases = []
    for case_id, spec_path, device_spec in load_device_specs():
        extracted_basenames = extract_oracle(bundletool, full_apks, case_id, spec_path)
        expected_paths = []
        for basename in extracted_basenames:
            if basename not in basename_to_path:
                raise RuntimeError(f"bundletool selected an unknown APK basename: {basename}")
            expected_paths.append(basename_to_path[basename])
        matrix_cases.append(
            {
                "id": case_id,
                "deviceSpec": device_spec,
                "expectedApks": expected_paths,
            }
        )

    matrix = {
        "schemaVersion": 1,
        "bundletool": {
            "version": BUNDLETOOL_VERSION,
            "sha256": BUNDLETOOL_SHA256,
        },
        "fixture": {
            "file": FIXTURE_NAME,
            "sha256": sha256(fixture),
            "sourceProject": "test-fixtures/bundletool-selection",
        },
        "oracle": (
            "bundletool extract-apks --include-metadata; bundletool 1.18.2 "
            "InstallApksCommand delegates its installation selection to ExtractApksCommand"
        ),
        "cases": matrix_cases,
    }
    with (GENERATED_DIR / MATRIX_NAME).open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(matrix, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def verify_or_install(verify: bool) -> None:
    generated_files = [GENERATED_DIR / FIXTURE_NAME, GENERATED_DIR / MATRIX_NAME]
    if verify:
        failures = []
        for generated in generated_files:
            committed = RESOURCE_DIR / generated.name
            if not committed.is_file():
                failures.append(f"missing committed file: {committed}")
            elif generated.read_bytes() != committed.read_bytes():
                failures.append(f"generated file differs: {committed}")
        if failures:
            raise RuntimeError("Fixture verification failed:\n- " + "\n- ".join(failures))
        print("Bundletool selection fixture is reproducible and up to date.")
        return

    RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
    for generated in generated_files:
        shutil.copyfile(generated, RESOURCE_DIR / generated.name)
    print(f"Wrote bundletool selection fixture to {RESOURCE_DIR}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--verify",
        action="store_true",
        help="regenerate in build/ and fail unless the committed fixture is byte-identical",
    )
    args = parser.parse_args()
    bundletool = ensure_bundletool()
    generate(bundletool)
    verify_or_install(args.verify)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1) from error
