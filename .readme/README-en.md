<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="APK Inspector" width="128" />
  </p>

  <h1>APK Inspector</h1>

  <p>AutoJs6 file manager plugin: tap any APK or AAB file to see its version, permissions, signatures, and device compatibility, without installing it</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

### Languages

This README is available in the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

### Introduction

APK Inspector is a companion plugin for the AutoJs6 file manager. Tap an APK, APKS, XAPK, APKM, APKZ, or AAB file in the file manager and an inspection report opens right away: what the app is called, which version it is, which permissions it wants, and whether it can be installed on this device, all on one screen. The package is never installed, and the source file is never modified.

The report has four sections: "Package details" shows the app name, icon, package name, version, SDK range, signature verification and signing-certificate lineage, file size, and SHA-256 checksum; "Components" lists every APK split and OBB asset inside the package and marks the parts that match this device (for AAB files it lists the modules); "Requested permissions" groups system permissions by protection level and highlights runtime/dangerous permissions first with a short explanation; "Security and compatibility findings" summarizes structural problems and the device compatibility verdict. The "View manifest" button opens the full formatted AndroidManifest.

### Highlights

- One tap to inspect: open a report straight from the AutoJs6 file manager, with no installation, extraction, or network access.
- Six formats: standard APK, multi-split bundle formats (APKS, XAPK, APKM, APKZ), and the store distribution format AAB; APKS covers both bundletool and SAI exports.
- Version and compatibility: shows the package name, version name and code, and min/target/max SDK, compared against this device's Android version before you install.
- Permission transparency: requested permissions are grouped by protection level (runtime/dangerous, signature/protected, and normal), with runtime permissions highlighted first and explained in one line; unavailable levels stay visible and clearly labeled.
- Split analysis: lists every APK entry and OBB asset in a bundle and marks the splits selected for this device (base, language, screen density, ABI).
- Signature and certificate verification: cryptographically verifies APK Signature Scheme V2, V3, and V3.1; reports V1 presence; lists current signing certificates and the verified certificate-rotation lineage with old/new roles and SHA-256 fingerprints.
- V4/V4.1 sidecar verification: AutoJs6 derives only the exact `<APK name>.idsig` sibling and grants a bounded read-only descriptor; the plugin verifies the signed data, certificate and public key, complementary V2/V3 APK digest, fs-verity root, embedded Merkle tree, and any V3.1 rotation signer.
- Readable manifests: binary APK manifests and AAB protobuf manifests are decoded into readable XML, shown in a separate read-only viewer.
- Integrity check: SHA-256 is calculated while the file is read, ready to compare against officially published checksums.
- Structural checkup: detects a missing base APK, duplicate or unresolved splits, version or package mismatches, and marks each finding as blocking [!] or informational [i].

### How to use

1. Download and install APK Inspector, then enable it in the AutoJs6 plugin center (AutoJs6 version code 5277 or later is required).
2. Open the AutoJs6 file manager and locate the package file you want to examine (APK, APKS, XAPK, APKM, APKZ, or AAB).
3. Tap the file, or choose "Inspect Android package" from its menu; the inspection report appears after a moment.
4. Read the report from top to bottom: app icon and name, package details, components, requested permissions, and security and compatibility findings.
5. Tap "View manifest" to read the full AndroidManifest, then press back to return to the file manager.

> Other apps can also hand a package to APK Inspector through the system "Open with" dialog (ACTION_VIEW), as long as they use a content URI with a dedicated Android package MIME type. The plugin is strictly read-only and offers no install entry point.

### Supported formats

The file manager primary action matches these extensions exactly (case-insensitive):

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

APKS, XAPK, APKM, and APKZ are container formats that bundle multiple split APKs; AAB is the App Bundle format submitted to app stores, which can be inspected here but must be converted with a tool such as bundletool before it can be installed.

### FAQ

#### Can this plugin install APKs?

No, and that is deliberate. The plugin requests no install permission and has no install button anywhere; its job is to let you see what is inside a package before you install it. Installation stays with the system installer or the host's own flow.

#### Why can't some files be inspected?

Common reasons: the file exceeds the 4 GiB limit; the device cache is low on space (at least 128 MiB must stay free); the bundle exceeds the parsing bounds on entry count or size; the file was modified by another app while being read; or the file itself is structurally broken. The error message states the specific reason.

#### Does the signature check prove a package is safe?

No. The plugin cryptographically verifies package integrity and signer proofs for V2, V3, V3.1, V4, and V4.1 and shows certificate fingerprints and rotation lineage, but a valid signature only proves that the package has not changed since that signer signed it; it does not establish that the signer or app is trustworthy. Compare the fingerprint and SHA-256 with an official source.

#### Why do AAB files say they "require conversion before installation"?

AAB is a distribution format aimed at app stores; Android devices cannot install it directly. The plugin can decode its protobuf manifest and module structure for viewing, but installation requires converting it to APK(s) first with a tool such as bundletool.

### Permissions and safety

The plugin requests no storage, network, or package installation permission. It reaches the selected package only through the temporary read-only content URI granted by the host; an optional `.idsig` is available only through a bounded host descriptor for the exact host-derived `<APK name>.idsig` sibling, with no directory listing or arbitrary sibling path. Before inspection starts, both inputs are copied into read-only snapshots in the app-private cache (SHA-256 is calculated while copying the package), and all parsing happens on those snapshots; `.idsig` is limited to 40 MiB and stale snapshots are cleaned up within 24 hours. File-manager requests are validated field by field, including protocol version, request and action IDs, target metadata, host version, URI shape, file name, size, read-only grants, and the host-session Binder. "Open with" requests from other apps are accepted only with dedicated package MIME types; application/zip, application/octet-stream, and any write, persistable, or prefix grant are refused.

To keep maliciously crafted files from exhausting device resources, parsing is bounded as follows, and files over a bound are rejected with a message:

- A single file may be at most 4 GiB, at least 128 MiB of cache space must remain free during the copy, and each action handles exactly one target file.
- At most 16384 archive entries are parsed, at most 512 APK entries are scanned per bundle, and entry names may be at most 1024 characters.
- Declared entry size may not exceed 4 GiB, and the declared total may not exceed 8 GiB.
- Nested APK manifest scanning is capped at 256 MiB, bundle metadata at 1 MiB, and the temporary APK used to load the icon and label at 512 MiB.
- Permission classification scans at most 2048 requests, displays at most 512 safe unique names, and limits each loaded explanation to 240 characters; omissions and unavailable protection levels are clearly labeled.

### Plugin interface

The host (AutoJs6) discovers and invokes the plugin through the following identities, provided here for plugin and host developers:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5277
```

The current version performs read-only inspection only: there is no install button, install permission, or package installer, the source file is never modified, and directories are never enumerated. V4 uses only the exact host-derived `.idsig` candidate, copies its bounded read-only descriptor into a private snapshot, and closes the host session immediately after staging. If the plugin is missing or disabled, the host silently falls back to its default action.

### Roadmap

The capabilities above and the checked Roadmap items reflect what is implemented; planned work such as report export and resource/native library analysis is tracked in the Roadmap, and unchecked items are not current capabilities.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### Release history

#### v1.1.0

_2026/08/30_

- `Hint` Requires AutoJs6 version code 5277 or later for Explorer Action protocol v22 and bounded V4 sidecar access
- `Feature` Added on-device cryptographic verification for APK Signature Scheme V2, V3, V3.1, V4, and V4.1, including content digests, signer proofs, fs-verity roots, embedded Merkle trees, and complementary-scheme matching
- `Feature` Added detailed signing-certificate fields and verified proof-of-rotation lineages with old/current roles, capability flags, and SHA-256 fingerprints
- `Feature` Added bounded `.idsig` staging through an exact host-derived read-only descriptor; directory enumeration and arbitrary sibling access remain unavailable
- `Feature` Grouped requested permissions by `protectionLevel` into runtime/dangerous, signature/protected, and normal sections; runtime permissions are highlighted first with bounded one-line descriptions, while unavailable levels remain visible and labeled
- `Improvement` Hardened Explorer Action v22 request validation and immutable private snapshots, with a 4 GiB package limit, a 40 MiB idsig limit, identity checks, and prompt host-session closure
- `Improvement` Added official Build Tools 37 `apksigner` fixtures for valid, tampered, multi-signer, V3.1 rotation, V4.1 rotation, missing, and malformed-signature cases

#### v1.0.1

_2026/08/08_

- `Fix` Fixed the host being unable to bind the plugin service after enabling it in the plugin center; the "Inspect Android package" action now works immediately after enabling
- `Improvement` Streamlined the plugin name and description and made the user documentation read more naturally

#### v1.0.0

_2026/08/02_

- `Hint` First public release; requires AutoJs6 version code 5269 or later
- `Feature` Tap an APK, APKS, XAPK, APKM, APKZ, or AAB file in the AutoJs6 file manager to open a read-only inspection report (plugin ID `apk-inspector`, action ID `inspect-android-package`)
- `Feature` The report shows the app name and icon, package name, version, SDK range, requested permissions, splits and OBB assets, structural problems, and V1-V3 signature scheme presence
- `Feature` Text and binary APK manifests, AAB protobuf manifests, and bundletool `toc.pb` metadata are decoded automatically, with a separate viewer for the formatted manifest
- `Feature` Other apps can hand a package over through the system "Open with" dialog (ACTION_VIEW) using dedicated Android package MIME types
- `Feature` Before inspection the file is copied into a read-only private snapshot with SHA-256 calculation (4 GiB limit); the plugin requests no storage, network, or package installation permission
- `Feature` Ships with 10 languages for UI text, instructions, README, and CHANGELOG: Simplified Chinese, Traditional Chinese (Hong Kong and Taiwan), English, French, Spanish, Japanese, Korean, Russian, and Arabic
- `Dependency` Added Gson 2.13.2

##### Full history

- [CHANGELOG-en.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-en.md)

### Build

```powershell
.\gradlew.bat :app:assembleDebug
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease
```

Build and signing parameters come from version.properties and sign.properties; the current minimum is Android 7.0 (SDK 24) with target SDK 36.

The README and CHANGELOG files are generated by .python/generate_markdown.py from the JSON language sources and templates under .readme/ and .changelog/ (10 languages). To change the documentation, edit the JSON sources and re-run the script instead of editing the generated Markdown.

### Links

- AutoJs6 documentation: https://docs.autojs6.com
- Android secure file sharing: https://developer.android.com/training/secure-file-sharing
