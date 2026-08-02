<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>Deep read-only inspection for APK, split package containers, and Android App Bundles in AutoJs6 Explorer</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Languages

******

The current README.md supports the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Francais [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-fr.md)
- [Espanol [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Russkii [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### Introduction

******

The AutoJs6 APK Inspector plugin supplies the primary inspection action for Android package files in the main Explorer. It analyzes a bounded app-private snapshot and never modifies or installs the source package.

******

### Features

******

- Registers an Explorer Action protocol v2 primary action for APK, APKS, XAPK, APKM, APKZ, and AAB files.
- Decodes text and binary APK manifests, AAB protobuf manifests, and bundletool toc.pb metadata.
- Reports package identity, version, SDK range, requested permissions, components, device-matched splits, OBB assets, and structural problems.
- Detects the presence of APK V1, V2, and V3 signature schemes without claiming cryptographic validity.
- Displays a formatted Android Manifest in a separate read-only viewer.
- Provides a separate Android ACTION_VIEW gateway for exact Android package MIME types.

******

### Supported formats

******

The Explorer primary action matches these extensions exactly:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### Plugin interface

******

AutoJs6 discovers and executes the plugin with the following identities:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5269
supported ABIs: unrestricted (supportedAbis = emptyArray())
```

Version 1 performs inspection only. It has no install button, installation permission, package installer, source editor, or directory enumeration. Host installation flows remain independent. If the plugin is unavailable, AutoJs6 uses its host fallback.

The plugin is implemented entirely on the JVM and contains no native library. It declares supportedAbis = emptyArray() and is released as one ABI-independent APK. AutoJs6 host build 5269 or later is required.

******

### Security

******

The protected Explorer gateway validates protocol v2, the main-files surface, action ID, content URI ancestry, exact ClipData, display name, extension, MIME type, size, and read-only grants. The public ACTION_VIEW gateway accepts only dedicated package MIME types. Input is copied once into a bounded, read-only private snapshot while SHA-256 is calculated. The protocol parent URI is never enumerated.

******

### Safety limits

******

- Maximum input size: 4 GiB.
- One target file per action and at least 128 MiB of cache reserve.
- Archive entry count, entry name, declared size, total size, nested APK scan, metadata, protobuf, and manifest output are bounded.
- External ACTION_VIEW rejects application/zip, application/octet-stream, write, persistable, and prefix grants.
- V1-V3 signature schemes are presence checks only. V4 requires a separate idsig input and is outside this protocol.
- The plugin never installs packages and requests no storage, network, or package installation permission.

******

### Release history

******

# v1.0.0

###### 2026/08/02

* `Feature` APK Inspector plugin with plugin ID `apk-inspector`, action ID `inspect-android-package`, engine `explorer-action`, and variant `default`
* `Feature` Explorer Action protocol v2 primary inspection for APK, APKS, XAPK, APKM, APKZ, and AAB files
* `Feature` Read-only decoding for text and binary APK manifests, AAB protobuf manifests, and bundletool `toc.pb` metadata
* `Feature` Package details, requested permissions, components, device-matched splits, OBB assets, structural findings, formatted manifest viewing, and APK V1-V3 signature presence detection
* `Feature` Separate protected Explorer and exact-MIME Android `ACTION_VIEW` gateways with a 4 GiB input limit and bounded private read-only snapshots calculated with SHA-256
* `Feature` Pure JVM implementation with no native library, unrestricted ABIs declared by `supportedAbis = emptyArray()`, one ABI-independent APK, and required AutoJs6 host build 5269
* `Feature` Localized metadata, interface text, usage instructions, README files, and changelogs in Spanish, French, Russian, Arabic, Japanese, Korean, English, Simplified Chinese, Hong Kong Traditional Chinese, and Taiwan Traditional Chinese
* `Dependency` Added Gson version 2.13.2

##### For more releases

* [CHANGELOG-en.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-en.md)

******

### Build

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease
```

Build parameters come from version.properties. The current minimum SDK is 24 and the target SDK is 36.

******

### Resource layout

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xml localizes plugin metadata and UI text. plugin_instruction.md provides instructions shown by the host. .python/generate_markdown.py generates localized README and changelog files from JSON sources.

******

### Links

******

- AutoJs6 documentation: https://docs.autojs6.com
- Android secure file sharing: https://developer.android.com/training/secure-file-sharing
