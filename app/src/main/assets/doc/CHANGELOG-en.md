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
