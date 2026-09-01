# Release history

## v1.1.0

_2026/09/01_

- `Hint` Requires AutoJs6 version code 5277 or later for Explorer Action protocol v22 and bounded V4 sidecar access
- `Feature` Added responsive layouts for 1.5x/2.0x fonts, landscape, and 320 dp screens: the report header stacks when space is tight, compact toolbar titles remain complete, long SHA-256 and permission identifiers wrap without ellipsis, and manifest search avoids full-screen IME extraction
- `Feature` Added TalkBack and RTL accessibility across the report and manifest viewer: compatibility heading semantics, named icon actions, 48 dp custom touch targets, live result announcements, locale-directed layouts, and mirrored Arabic navigation
- `Feature` Added line numbers, Material-aware XML syntax highlighting, and bounded case-insensitive text search with highlighted matches, previous/next wraparound navigation, and state restoration to the read-only manifest viewer
- `Feature` Added system-following Material 3 light/dark themes for the report and manifest viewer, Android 12+ Material You dynamic colors, and contrast-aware system-bar icons
- `Feature` Added bounded container-metadata summaries for SAI APKS `meta.sai_v1/v2.json`, XAPK `manifest.json`, and APKMirror APKM `info.json`, showing the packager/schema version, metadata-declared app version, and an existing icon entry; malformed or over-1 MiB metadata is isolated and labeled
- `Feature` Added in-report device configuration simulation for APKS, XAPK, APKM, and APKZ: switch language, screen density, and ABI to rerun bounded split selection locally against the same private snapshot, with compatible/invalid status and APKs added or removed versus the actual device; the main report remains actual-device based
- `Feature` Added bounded AAB configuration and delivery metadata: decodes BundleConfig.pb bundletool/type/split/compression/optimization settings and annotates base, feature, asset, ML, AI, and SDK modules with install-time, conditional, on-demand, fast-follow, fusing, and removable delivery; malformed, oversized, and omitted metadata stays isolated and labeled
- `Feature` Added bounded resource-table fallback for app labels and raster icons: AAB resources.pb and APK resources.arsc are resolved for the current locale and density without extracting oversized nested APKs; table, icon, and scan-limit failures remain isolated and clearly labeled
- `Feature` Added long-press copying for primary package-detail values and exact plain-text report sharing through the Android Sharesheet; sharing stays memory-only, requests no storage permission, and creates no file
- `Feature` Added a bounded DEX overview that naturally orders standard classes*.dex files from selected APK splits and AAB modules with per-file and total uncompressed sizes, sharing the native-library central-directory pass without extracting, decoding, or decompiling DEX contents
- `Feature` Added a bounded native-library overview that groups .so files from selected APK splits and AAB modules by ABI and uncompressed size, marking preferred, fallback-compatible, and unsupported device ABIs without extracting library contents
- `Feature` Added bounded manifest component statistics for activities/aliases, services, broadcast receivers, and content providers across selected APK splits and scanned AAB modules, grouped by explicit android:exported state with partial-result labels
- `Feature` Added on-device cryptographic verification for APK Signature Scheme V2, V3, V3.1, V4, and V4.1, including content digests, signer proofs, fs-verity roots, embedded Merkle trees, and complementary-scheme matching
- `Feature` Added detailed signing-certificate fields and verified proof-of-rotation lineages with old/current roles, capability flags, and SHA-256 fingerprints
- `Feature` Added bounded `.idsig` staging through an exact host-derived read-only descriptor; directory enumeration and arbitrary sibling access remain unavailable
- `Feature` Grouped requested permissions by `protectionLevel` into runtime/dangerous, signature/protected, and normal sections; runtime permissions are highlighted first with bounded one-line descriptions, while unavailable levels remain visible and labeled
- `Fix` Fixed the manifest action disappearing after a locale, theme, or other activity recreation by safely replacing the previous read-only private manifest snapshot
- `Fix` Prevented the manifest search panel from becoming unreachable when the soft keyboard compressed compact or large-text screens; the keyboard is now deferred until the focused search field is tapped
- `Improvement` Hardened Explorer Action v22 request validation and immutable private snapshots, with a 4 GiB package limit, a 40 MiB idsig limit, identity checks, and prompt host-session closure
- `Improvement` Added official Build Tools 37 `apksigner` fixtures for valid, tampered, multi-signer, V3.1 rotation, V4.1 rotation, missing, and malformed-signature cases
- `Improvement` Added a real-package partition-isolation matrix for production permission, manifest-component, native-library, and DEX limits plus resource-table and malformed nested-directory failures; every sample asserts unaffected sections remain complete and partial notices survive plain-text sharing
- `Improvement` Added a reproducible bundletool 1.18.2 selection golden matrix: `build-apks` runs on a privacy-neutral minimal AAB, then the `ExtractApksCommand` shared by `install-apks` records install splits for three language/density/ABI device specs; unit tests verify each simulated set is duplicate-free and stable across repeated runs
- `Improvement` Added a repeatable 36-case UI screenshot review matrix covering 411/320 dp portrait and compact landscape, 1.0x/1.5x/2.0x fonts, light/dark themes, and LTR/RTL; its state-preserving ADB runner audits control reachability, accessibility reading order, and 48 dp touch targets while recording PNG/XML/contact-sheet evidence
- `Improvement` Added a deterministic 24-case privacy-neutral fixture matrix spanning APK/APKS/XAPK/APKM/APKZ/AAB across normal, structurally damaged, over-limit, and device-incompatible inputs; a standard-library generator, SHA-256 manifest, and JVM contract tests verify byte-for-byte reproduction, parser outcomes, compact size, and the absence of code, signing material, and user data
- `Improvement` Added exhaustive fail-closed unit-test matrices for package request validation, private-cache staging, Android archive guards, and APK signing-block parsing; every enumerated rejection reason is exercised, including malformed metadata, unsafe paths, resource limits, truncated structures, and cancellation
- `Improvement` Added Robolectric security regression tests for both exported entry activities, covering spoofed actions, overprivileged URI grants, declarations above 4 GiB, and concurrent lifecycle cancellation; rejected requests never launch inspection or open oversized content, and Explorer host sessions close exactly once
- `Improvement` Added a least-privilege GitHub Actions Android CI workflow pinned to immutable action SHAs: Ubuntu 24.04 with JDK 21 builds the debug APK, runs the complete JVM suite, and regenerates all 10-language Markdown; any tracked drift or unexpected untracked output fails the check
- `Improvement` Added a reproducible release-artifact preparation and verification flow: it preserves the `autojs6-plugin-apk-inspector-v<version>-<CRC32>.apk` naming rule, writes a `.sha256` sidecar for every APK plus a deterministically sorted `SHA256SUMS`, and rejects any naming, CRC32, SHA-256, or manifest mismatch
- `Improvement` Standardize the README layout and Gradle platform version management
- `Improvement` Refine the plugin description and normalize punctuation in multilingual resources
- `Improvement` Rename the external viewing entry to External Viewer for consistent viewer semantics

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
