# Release history

## v1.1.0

_2026/08/30_

- `Hint` Requires AutoJs6 version code 5277 or later for Explorer Action protocol v22 and bounded V4 sidecar access
- `Feature` Added on-device cryptographic verification for APK Signature Scheme V2, V3, V3.1, V4, and V4.1, including content digests, signer proofs, fs-verity roots, embedded Merkle trees, and complementary-scheme matching
- `Feature` Added detailed signing-certificate fields and verified proof-of-rotation lineages with old/current roles, capability flags, and SHA-256 fingerprints
- `Feature` Added bounded `.idsig` staging through an exact host-derived read-only descriptor; directory enumeration and arbitrary sibling access remain unavailable
- `Improvement` Hardened Explorer Action v22 request validation and immutable private snapshots, with a 4 GiB package limit, a 40 MiB idsig limit, identity checks, and prompt host-session closure
- `Improvement` Added official Build Tools 37 `apksigner` fixtures for valid, tampered, multi-signer, V3.1 rotation, V4.1 rotation, missing, and malformed-signature cases

## v1.0.1

_2026/08/08_

- `Fix` Fixed the host being unable to bind the plugin service after enabling it in the plugin center; the "Inspect Android package" action now works immediately after enabling
- `Improvement` Streamlined the plugin name and description and made the user documentation read more naturally

## v1.0.0

_2026/08/02_

- `Hint` First public release; requires AutoJs6 version code 5269 or later
- `Feature` Tap an APK, APKS, XAPK, APKM, APKZ, or AAB file in the AutoJs6 file manager to open a read-only inspection report (plugin ID `apk-inspector`, action ID `inspect-android-package`)
- `Feature` The report shows the app name and icon, package name, version, SDK range, requested permissions, splits and OBB assets, structural problems, and V1-V3 signature scheme presence
- `Feature` Text and binary APK manifests, AAB protobuf manifests, and bundletool `toc.pb` metadata are decoded automatically, with a separate viewer for the formatted manifest
- `Feature` Other apps can hand a package over through the system "Open with" dialog (ACTION_VIEW) using dedicated Android package MIME types
- `Feature` Before inspection the file is copied into a read-only private snapshot with SHA-256 calculation (4 GiB limit); the plugin requests no storage, network, or package installation permission
- `Feature` Ships with 10 languages for UI text, instructions, README, and CHANGELOG: Simplified Chinese, Traditional Chinese (Hong Kong and Taiwan), English, French, Spanish, Japanese, Korean, Russian, and Arabic
- `Dependency` Added Gson 2.13.2
