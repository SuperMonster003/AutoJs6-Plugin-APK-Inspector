# Shared package parser migration

Date: 2026-10-02, Asia/Shanghai. This implements the existing APK Inspector migration item in
3-Setup Installer P8. Inspector remains a read-only plugin with Explorer Action v22, host
file-information v1 and required host build 5277. No installation permission, installer entry,
network access or directory discovery was added.

## Artifact and implementation boundary

The 262,831-byte `libs/package-archive-parser.aar` is the exact release snapshot already audited
by 3-Setup Installer: AutoJs6 `0767971bc9`, build 5299, SHA-256
`1441bbcee8468362b0ee41f7b3d5ab47eb87b4df78a1388bb1134223055f46e7`.
The existing common and Explorer AARs were not replaced. All three files are now checked against
`locks/host-api-aars.lock` during configuration; filenames, key sets and SHA-256 values are fixed.
Producer/consumer provenance is recorded in `libs/README.md` and `THIRD_PARTY_NOTICES.md`.

The local APK binary XML, AAB protobuf XML, bundletool TOC and inspection-limit implementations
were removed. Their callers and existing tests import the public shared classes directly.
`InspectionArchive.kt` replaces the previous archive parser with a report adapter: format,
subtype, base/split selection, common manifest fields and compatibility problems come from the
shared inspector, always with `prepareForInstallation=false`.

Inspector still owns its additional report sections: component/export counts, declared permission
levels, label/icon resource fallback, AAB module delivery metadata, native libraries, DEX and
16 KiB readiness. Additional container-manifest reads cover only the shared selected APK set
and retain a bounded scan budget; they do not repeat the split-selection algorithm. The entry
index and display extraction retain their existing Inspector-specific byte/space/path guards.
The shared installability and staging helpers are not exposed by the report model.

The former AAB decoder exposed a local protobuf tree that the shared API intentionally keeps
private. `AabModuleManifest` now reads the shared decoded XML with namespace-aware SAX, rejects
DOCTYPE/external entities and applies the shared output, depth, node and attribute limits.
Distribution namespace attributes remain distinct from ordinary Android manifest fields.
The existing delivery-condition tests now use the small metadata tree, not a copied decoder.

Cryptographic signature verification and the stricter signing-block rejection reader remain local.
The identical V1 scheme-presence scan delegates to the shared utility. Inspector deliberately
does not call the shared `detectSchemes` convenience method, which checks an implicit `.idsig`
sibling; its existing explicit descriptor authorization and V4 verification continue to apply.
This is a retained Inspector-specific safety policy, not an omitted migration of common code.

## Local validation

| Check | Result | Local log/report |
| --- | --- | --- |
| Temurin platform acceptance, Debug, existing full JVM suite, AndroidTest | Passed, one platform decision block, 5m 16s | `build/shared-parser-debug.log` |
| Final JVM suite including four migration tests | 231 tests, 0 failures/errors/skips | `app/build/test-results/testDebugUnitTest/` |
| Debug/AndroidTest, R8 Release, Debug/Release lint, signed release collection | Passed, 5m 34s | `build/shared-parser-final-build.log` |
| Debug and Release lint | Each 0 errors, 37 warnings | `app/build/reports/lint-results-{debug,release}.txt` |
| Markdown generation/check | 10 languages, 25 generated artifacts consistent | `.python/check_markdown.bat` |
| Python tests | 4/4 passed | `build/shared-parser-python-tests.log` |
| Native alignment and signed APK verification | Passed; no native libraries in the APK | Native alignment reports and release collection task |

The full suite retains signature/tampering/rotation/V4 fixtures, unsafe archives, large/ZIP64
packages, bundletool selection goldens, resource fallback and report-partition isolation tests.
The four added tests compare six format results against the bundled shared parser, verify unchanged
source digests and no staging, exercise XML namespaces/escaped values/tree limits/DOCTYPE refusal,
and ensure an ungranted `.idsig` file is not reported as V4.

## Android and Release checks

Device: Sony XQ-DQ72, serial `QV770340J7`, API 33, arm64-v8a. Before deployment the installed
Inspector was 1.2.1 / build 40, UID 10610. Same-signature `install -r` updates retained that UID and
data directory; no uninstall or clear-data action was used. The device was on its launcher, not
inside a user's inspection report.

On Debug 42, the two existing repository contract tests and the new six-format Android smoke
test passed: **3/3, no failures or skips**, 0.037 seconds as reported by instrumentation. This
checks protected Wake/service discovery, binding and package metadata, and actual Android
execution of shared APK/APKS/XAPK/APKM/APKZ/AAB parsing plus the AAB metadata SAX path.
These are six small synthetic no-code archives in one UUID-named private cache directory.
Each source digest stays unchanged, then only the owned files/directory are removed.
Log: `build/shared-parser-device-QV770340J7/instrumentation.log`.

A direct attempt to reuse the AndroidX Debug runner against the R8 Release failed before any
test, because `MonitoringInstrumentation` referenced `kotlin.jvm.internal.Intrinsics`, which R8
had removed/rewritten from the application. `release-contract.log` and the crash stack preserve
that failure; it is not counted as a Release pass. An initial extra instrumentation element was
also overridden by the Android Gradle test manifest configuration and therefore did not register
the custom runner; `release-platform-contract.log` records this separate driver setup failure.

The final solution is confined to `androidTest`: a plain Java/Android `Instrumentation` selected
with `-PreleaseSmoke=true`, with no AndroidX/JUnit/Kotlin runtime dependency and no production
test keep rule. It verifies the expected installed non-debuggable APK digest and no native code,
Wake protection, actual Explorer service binding/metadata and a PluginInfo Parcelable round trip.
It does not claim cross-UID or cross-process IPC coverage. The installed R8 artifact passed
**2/2 checks, releaseFailures=0** in `release-platform-contract-final.log`.

Reproduction, after building/deploying the signed release and building/deploying this test APK:

```text
gradlew.bat --max-workers=2 -PreleaseSmoke=true -Pautojs.gradle.build.number.auto.increment.enabled=false -Pautojs.gradle.build.time.update.enabled=false :app:assembleDebugAndroidTest
adb -s <serial> shell am instrument -w -r -e expectedSha256 <installed-release-sha256> io.github.supermonster003.autojs6.plugin.apkinspector.test/io.github.supermonster003.autojs6.plugin.apkinspector.release.ReleaseContractInstrumentation
```

Omitting `releaseSmoke` retains the ordinary AndroidJUnitRunner for Debug checks. API 24 and
ColorOS physical-device execution were not repeated in this migration. No real user package
inspection, package installation or OEM file-manager chooser manipulation was needed for these
tests; the prior product UI evidence retains its own scope.

## Release and cleanup

Version: **1.2.2 / build 42**. New local artifact:

- `releases/autojs6-plugin-apk-inspector-v1.2.2-c81f2584.apk`, 4,393,049 bytes.
- CRC32 `c81f2584`; SHA-256 `d0bd86f9c1bc29fdcab95504a2a0ca84f171818437eaca8f52d85dc4fa8d94b1`.
- Signed release collection and SHA-256 sidecar/`SHA256SUMS` generation passed. Existing historical
  release files were preserved. The stale build-output 1.2.1 APK was moved to the ignored
  `build/shared-parser-prior-artifact/` after digest verification, so the current output set is exact.
- Sony retains that same 1.2.2 / 42 R8 Release, debuggable=false, with the installed APK digest verified.

The device's 20 original system session IDs, root Shizuku server PID 6403, UID/data directory and
full preferred XML remain unchanged. The preferred XML SHA-256 is
`b54f15311e4741b35deb26c6c005556f4e7158b02e11ddf7e50187947f5239f5`.
Private `files`/`shared_prefs`/`no_backup` file hashes during the Debug tests are identical, and
no `shared-parser-*` sample cache directory remains. There was no access to a user-owned source,
no Root/Shizuku request, and no default-installer operation.

One system audit difference is preserved explicitly: app-ops originally reported `No operations`,
and instrumentation left an explicit `NO_ISOLATED_STORAGE: deny` row. The Android 33 framework's
default for that op is also `MODE_ERRORED` (deny), so effective access did not become broader.
The agent did not change this permission or clear app-ops to make the text identical. This row
must not be described as byte-identical permission persistence. Final state and checks are in
`build/shared-parser-device-QV770340J7/{before,debug-before,debug-after,release-complete,completion}.json`.

Only local commits and local artifacts are produced. No push, tag, GitHub Release or index
publication was performed. The host's `docs/dev/package-inspection-roadmap.md` is updated as a
separate host-owned documentation change; no host parser, script API or AAR was modified here.
