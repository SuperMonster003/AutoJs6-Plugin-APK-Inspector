# Privacy-neutral package fixture matrix

This fixture set covers every package format accepted by APK Inspector across four deliberately
different outcomes. All 24 package files are generated locally from constant strings; none is
copied from a real application or device.

| Format | Normal | Structurally damaged | Over limit | Device incompatible |
| --- | --- | --- | --- | --- |
| APK | `apk-normal.apk` | `apk-structurally-damaged.apk` | `apk-over-limit.apk` | `apk-device-incompatible.apk` |
| APKS | `apks-normal.apks` | `apks-structurally-damaged.apks` | `apks-over-limit.apks` | `apks-device-incompatible.apks` |
| XAPK | `xapk-normal.xapk` | `xapk-structurally-damaged.xapk` | `xapk-over-limit.xapk` | `xapk-device-incompatible.xapk` |
| APKM | `apkm-normal.apkm` | `apkm-structurally-damaged.apkm` | `apkm-over-limit.apkm` | `apkm-device-incompatible.apkm` |
| APKZ | `apkz-normal.apkz` | `apkz-structurally-damaged.apkz` | `apkz-over-limit.apkz` | `apkz-device-incompatible.apkz` |
| AAB | `aab-normal.aab` | `aab-structurally-damaged.aab` | `aab-over-limit.aab` | `aab-device-incompatible.aab` |

The committed files and their machine-readable expectations live in
`app/src/test/resources/privacy-neutral-fixtures/`. `matrix.json` records the construction,
byte count, SHA-256 digest, expected parser result, and baseline device specification for every
case. `SHA256SUMS` is the conventional two-space-delimited checksum list for external tools.

## Generate and verify

Run the generator from the repository root. It uses only the Python standard library and does not
need Android SDK tools, Java, network access, or secrets:

```text
python .python/generate_privacy_neutral_fixtures.py
```

Verify every committed byte, including the matrix and checksum list, without changing the
committed resource directory:

```text
python .python/generate_privacy_neutral_fixtures.py --verify
```

ZIP timestamps are fixed at 1980-01-01, entries use deterministic ordering and stored compression,
and file modes are normalized. This makes regeneration independent of the workstation, current
time, locale, Android SDK, and zlib version.

## Construction and safety properties

- **Normal:** a minimal parser-valid package with no code, no resources, and `minSdkVersion=24`.
  APK uses a text Android manifest; APKS, XAPK, APKM, and APKZ wrap the same minimal nested APK using
  their format marker or metadata. AAB contains an empty valid proto3 `BundleConfig.pb` plus a
  minimal protobuf XML base-module manifest.
- **Structurally damaged:** starts from the corresponding normal ZIP and corrupts every end of
  central directory signature (including the nested APK record in container formats). The payload
  remains attributable to its format while a ZIP reader cannot recover an embedded archive after
  rejecting the outer structure.
- **Over limit:** contains one 1,025-character archive path. This is exactly one character beyond
  APK Inspector's production limit of 1,024 characters and exercises the real early rejection path
  without committing multi-gigabyte data or a decompression bomb.
- **Device incompatible:** keeps valid structure but declares `minSdkVersion=99` against the matrix
  baseline of API 35. APK and split-container inspections therefore report `INCOMPATIBLE`; AAB
  retains the product's `AAB_SOURCE` state because source bundles are not directly installable,
  while its decoded manifest still proves that the baseline device fails the SDK constraint.

Every synthetic package name is below `org.example.apkinspector.fixture`, the visible label is
`Privacy-neutral fixture`, and no fixture contains executable DEX/native code, a real application,
certificate, private key, signing block, host path, device identifier, account, or user content.
These are inspection-path fixtures, not installable APKs and not signature-verification fixtures.
