# -*- coding: utf-8 -*-
"""Generate or verify the privacy-neutral Android package fixture matrix.

The fixtures exercise the inspector's six supported filename formats without embedding any real
application, signing material, executable code, or user data. ZIP entries are stored rather than
deflated so output remains byte-for-byte reproducible across Python and zlib versions.
"""

import argparse
import hashlib
import io
import json
from pathlib import Path
import shutil
import sys
import zipfile


NORMALIZED_ZIP_TIME = (1980, 1, 1, 0, 0, 0)
ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE = b"PK\x05\x06"
MAX_ENTRY_NAME_CHARS = 4_096
OVER_LIMIT_ENTRY_NAME_CHARS = MAX_ENTRY_NAME_CHARS + 1
NORMAL_MIN_SDK = 24
INCOMPATIBLE_MIN_SDK = 99
TARGET_SDK = 35

FORMATS = ("APK", "APKS", "XAPK", "APKM", "APKZ", "AAB")
CATEGORIES = (
    "NORMAL",
    "STRUCTURALLY_DAMAGED",
    "OVER_LIMIT",
    "DEVICE_INCOMPATIBLE",
)

ROOT = Path(__file__).resolve().parents[1]
BUILD_DIR = ROOT / "build" / "privacy-neutral-fixtures"
GENERATED_DIR = BUILD_DIR / "generated"
RESOURCE_DIR = ROOT / "app" / "src" / "test" / "resources" / "privacy-neutral-fixtures"
MATRIX_NAME = "matrix.json"
CHECKSUM_NAME = "SHA256SUMS"

DEVICE_SPEC = {
    "sdk": TARGET_SDK,
    "abis": ["arm64-v8a", "armeabi-v7a"],
    "densityDpi": 480,
    "locales": ["en-US"],
}


def normalized_zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, NORMALIZED_ZIP_TIME)
    info.compress_type = zipfile.ZIP_STORED
    info.external_attr = 0o100644 << 16
    info.create_system = 3
    return info


def zip_bytes(entries: dict[str, bytes]) -> bytes:
    if len(entries) != len(set(entries)):
        raise RuntimeError("ZIP fixture entries must be unique")
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        for name in sorted(entries):
            archive.writestr(normalized_zip_info(name), entries[name])
    return output.getvalue()


def json_bytes(value: dict) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def package_name(format_name: str, category: str) -> str:
    suffix = category.lower().replace("-", "_")
    return f"org.example.apkinspector.fixture.{format_name.lower()}.{suffix}"


def manifest_bytes(format_name: str, category: str, minimum_sdk: int) -> bytes:
    package = package_name(format_name, category)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    package="{package}"\n'
        '    android:versionCode="1"\n'
        '    android:versionName="1.0">\n'
        f'    <uses-sdk android:minSdkVersion="{minimum_sdk}" '
        f'android:targetSdkVersion="{TARGET_SDK}" />\n'
        '    <application android:label="Privacy-neutral fixture" '
        'android:hasCode="false" />\n'
        '</manifest>\n'
    ).encode("utf-8")


def proto_varint(value: int) -> bytes:
    if value < 0:
        raise ValueError("Fixture protobuf values must be non-negative")
    output = bytearray()
    remaining = value
    while remaining > 0x7F:
        output.append((remaining & 0x7F) | 0x80)
        remaining >>= 7
    output.append(remaining)
    return bytes(output)


def proto_bytes(field_number: int, value: bytes) -> bytes:
    tag = (field_number << 3) | 2
    return proto_varint(tag) + proto_varint(len(value)) + value


def proto_string(field_number: int, value: str) -> bytes:
    return proto_bytes(field_number, value.encode("utf-8"))


def aab_xml_attribute(namespace_uri: str, name: str, value: str) -> bytes:
    fields = []
    if namespace_uri:
        fields.append(proto_string(1, namespace_uri))
    fields.extend((proto_string(2, name), proto_string(3, value)))
    return b"".join(fields)


def aab_xml_element(
    name: str,
    attributes: list[tuple[str, str, str]] | None = None,
    children: list[bytes] | None = None,
    declare_android_namespace: bool = False,
) -> bytes:
    fields = []
    if declare_android_namespace:
        namespace = proto_string(1, "android") + proto_string(
            2,
            "http://schemas.android.com/apk/res/android",
        )
        fields.append(proto_bytes(1, namespace))
    fields.append(proto_string(3, name))
    fields.extend(proto_bytes(4, aab_xml_attribute(*attribute)) for attribute in attributes or ())
    fields.extend(proto_bytes(5, proto_bytes(1, child)) for child in children or ())
    return b"".join(fields)


def aab_manifest_bytes(format_name: str, category: str, minimum_sdk: int) -> bytes:
    android_namespace = "http://schemas.android.com/apk/res/android"
    uses_sdk = aab_xml_element(
        "uses-sdk",
        attributes=[
            (android_namespace, "minSdkVersion", str(minimum_sdk)),
            (android_namespace, "targetSdkVersion", str(TARGET_SDK)),
        ],
    )
    application = aab_xml_element(
        "application",
        attributes=[
            (android_namespace, "label", "Privacy-neutral fixture"),
            (android_namespace, "hasCode", "false"),
        ],
    )
    root = aab_xml_element(
        "manifest",
        attributes=[
            ("", "package", package_name(format_name, category)),
            (android_namespace, "versionCode", "1"),
            (android_namespace, "versionName", "1.0"),
        ],
        children=[uses_sdk, application],
        declare_android_namespace=True,
    )
    return proto_bytes(1, root)


def format_entries(format_name: str, category: str, minimum_sdk: int) -> dict[str, bytes]:
    package = package_name(format_name, category)
    if format_name == "AAB":
        return {
            "BundleConfig.pb": b"",
            "base/manifest/AndroidManifest.xml": aab_manifest_bytes(
                format_name,
                category,
                minimum_sdk,
            ),
        }

    manifest = manifest_bytes(format_name, category, minimum_sdk)
    if format_name == "APK":
        return {"AndroidManifest.xml": manifest}

    nested_apk = zip_bytes({"AndroidManifest.xml": manifest})
    entries = {"base.apk": nested_apk}
    if format_name == "APKS":
        entries["meta.sai_v2.json"] = json_bytes(
            {
                "label": "Privacy-neutral fixture",
                "meta_version": 2,
                "package": package,
                "split_apk": False,
                "version_code": 1,
                "version_name": "1.0",
            }
        )
    elif format_name == "XAPK":
        entries["manifest.json"] = json_bytes(
            {
                "name": "Privacy-neutral fixture",
                "package_name": package,
                "version_code": 1,
                "version_name": "1.0",
                "xapk_version": 2,
            }
        )
    elif format_name == "APKM":
        entries["META-INF/APKMIRROR"] = b"Synthetic APKMirror format marker.\n"
        entries["info.json"] = json_bytes(
            {
                "apkm_version": 1,
                "app_name": "Privacy-neutral fixture",
                "min_api": str(minimum_sdk),
                "pname": package,
                "release_version": "1.0",
                "versioncode": "1",
            }
        )
    elif format_name == "APKZ":
        entries["apkz.json"] = json_bytes(
            {
                "format": "APKZ",
                "package_name": package,
                "schema_version": 1,
            }
        )
    else:
        raise RuntimeError(f"Unsupported fixture format: {format_name}")
    return entries


def long_entry_name() -> str:
    prefix = "limit/"
    name = prefix + "x" * (OVER_LIMIT_ENTRY_NAME_CHARS - len(prefix))
    if len(name) != OVER_LIMIT_ENTRY_NAME_CHARS:
        raise AssertionError("Over-limit entry name has the wrong length")
    return name


def fixture_bytes(format_name: str, category: str) -> bytes:
    minimum_sdk = INCOMPATIBLE_MIN_SDK if category == "DEVICE_INCOMPATIBLE" else NORMAL_MIN_SDK
    entries = format_entries(format_name, category, minimum_sdk)
    if category == "OVER_LIMIT":
        entries[long_entry_name()] = b"limit sentinel\n"
    value = zip_bytes(entries)
    if category == "STRUCTURALLY_DAMAGED":
        if ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE not in value:
            raise AssertionError("Fixture ZIP has no end of central directory signature")
        # Container fixtures embed a ZIP-format base APK. Corrupt every EOCD signature so a ZIP
        # reader cannot accidentally recover the nested APK after rejecting the outer container.
        value = value.replace(ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE, b"PX\x05\x06")
        try:
            with zipfile.ZipFile(io.BytesIO(value), "r"):
                pass
        except zipfile.BadZipFile:
            return value
        raise AssertionError("Structurally damaged fixture is still accepted as a ZIP")
    return value


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def expected_result(format_name: str, category: str) -> dict:
    if category == "STRUCTURALLY_DAMAGED":
        return {
            "result": "REJECTED",
            "rejection": "MALFORMED_ZIP",
        }
    if category == "OVER_LIMIT":
        return {
            "result": "REJECTED",
            "rejection": "ARCHIVE_ENTRY_NAME_LIMIT",
        }

    device_compatible = category == "NORMAL"
    return {
        "result": "ACCEPTED",
        "detectedFormat": format_name,
        "inspectionState": (
            "AAB_SOURCE"
            if format_name == "AAB"
            else "COMPATIBLE" if device_compatible else "INCOMPATIBLE"
        ),
        "deviceCompatible": device_compatible,
        "minimumSdk": NORMAL_MIN_SDK if device_compatible else INCOMPATIBLE_MIN_SDK,
        "packageName": package_name(format_name, category),
    }


def construction_name(category: str) -> str:
    return {
        "NORMAL": "SYNTHETIC_MINIMAL_PACKAGE",
        "STRUCTURALLY_DAMAGED": "ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURES_CORRUPTED",
        "OVER_LIMIT": "ARCHIVE_ENTRY_NAME_4097_CHARS",
        "DEVICE_INCOMPATIBLE": "MIN_SDK_99",
    }[category]


def generate(destination: Path) -> list[Path]:
    destination.mkdir(parents=True, exist_ok=True)
    cases = []
    generated_files = []
    for format_name in FORMATS:
        extension = format_name.lower()
        for category in CATEGORIES:
            category_slug = category.lower().replace("_", "-")
            case_id = f"{extension}-{category_slug}"
            filename = f"{case_id}.{extension}"
            value = fixture_bytes(format_name, category)
            path = destination / filename
            path.write_bytes(value)
            generated_files.append(path)
            cases.append(
                {
                    "id": case_id,
                    "format": format_name,
                    "category": category,
                    "file": filename,
                    "bytes": len(value),
                    "sha256": sha256_bytes(value),
                    "construction": construction_name(category),
                    "expected": expected_result(format_name, category),
                }
            )

    matrix = {
        "schemaVersion": 1,
        "provenance": {
            "kind": "DETERMINISTIC_SYNTHETIC",
            "namespace": "org.example.apkinspector.fixture",
            "containsPersonalData": False,
            "containsRealApplications": False,
            "containsSigningMaterial": False,
            "containsExecutableCode": False,
            "networkRequired": False,
        },
        "generation": {
            "script": ".python/generate_privacy_neutral_fixtures.py",
            "generateCommand": "python .python/generate_privacy_neutral_fixtures.py",
            "verifyCommand": "python .python/generate_privacy_neutral_fixtures.py --verify",
            "zipTimestamp": "1980-01-01T00:00:00",
            "zipCompression": "STORED",
        },
        "limits": {
            "archiveEntryNameCharacters": MAX_ENTRY_NAME_CHARS,
            "overLimitEntryNameCharacters": OVER_LIMIT_ENTRY_NAME_CHARS,
        },
        "deviceSpec": DEVICE_SPEC,
        "formats": list(FORMATS),
        "categories": list(CATEGORIES),
        "cases": cases,
    }
    matrix_path = destination / MATRIX_NAME
    matrix_path.write_text(
        json.dumps(matrix, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    generated_files.append(matrix_path)

    checksum_path = destination / CHECKSUM_NAME
    checksum_path.write_text(
        "".join(f"{case['sha256']}  {case['file']}\n" for case in cases),
        encoding="ascii",
        newline="\n",
    )
    generated_files.append(checksum_path)
    return generated_files


def verify_or_install(generated_files: list[Path], verify: bool) -> None:
    generated_names = {path.name for path in generated_files}
    if verify:
        failures = []
        committed_names = (
            {path.name for path in RESOURCE_DIR.iterdir() if path.is_file()}
            if RESOURCE_DIR.is_dir()
            else set()
        )
        if committed_names != generated_names:
            missing = sorted(generated_names - committed_names)
            unexpected = sorted(committed_names - generated_names)
            if missing:
                failures.append("missing committed files: " + ", ".join(missing))
            if unexpected:
                failures.append("unexpected committed files: " + ", ".join(unexpected))
        for generated in generated_files:
            committed = RESOURCE_DIR / generated.name
            if committed.is_file() and generated.read_bytes() != committed.read_bytes():
                failures.append(f"generated file differs: {committed}")
        if failures:
            raise RuntimeError("Fixture verification failed:\n- " + "\n- ".join(failures))
        print("Privacy-neutral fixture matrix is reproducible and up to date.")
        return

    RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
    for generated in generated_files:
        shutil.copyfile(generated, RESOURCE_DIR / generated.name)
    print(f"Wrote {len(generated_files) - 2} fixtures plus manifests to {RESOURCE_DIR}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--verify",
        action="store_true",
        help="regenerate in build/ and fail unless committed files are byte-identical",
    )
    args = parser.parse_args()
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    generated_files = generate(GENERATED_DIR)
    verify_or_install(generated_files, args.verify)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1) from error
