# Third-party and shared component notices

APK Inspector is distributed under the Mozilla Public License 2.0 in [LICENSE](LICENSE).
This inventory describes the direct runtime components; each dependency retains its own license.

## AutoJs6 shared artifacts

`common-plugin-api.aar`, `explorer-action-api.aar` and `package-archive-parser.aar` are built from
the [AutoJs6 repository](https://github.com/SuperMonster003/AutoJs6), distributed under MPL-2.0.
The parser source is `plugin-api/package-archive-parser` at host commit `0767971bc9`, build 5299.
Its release artifact was assembled 2026-10-01 and staged into Inspector 2026-10-02.

Exact SHA-256 values and the preserved provenance of the older contract snapshots are recorded
in [libs/README.md](libs/README.md) and enforced by [locks/host-api-aars.lock](locks/host-api-aars.lock).
The parser artifact SHA-256 is `1441bbcee8468362b0ee41f7b3d5ab47eb87b4df78a1388bb1134223055f46e7`.
It contains no native libraries. APK Inspector does not copy the shared parser sources back into
its own package; consumers retain access to the corresponding upstream source and MPL license.

## Other direct runtime dependencies

| Component | Version | License / source |
| --- | --- | --- |
| Kotlin stdlib and parcelize runtime | 2.2.21 | Apache-2.0, [JetBrains Kotlin](https://github.com/JetBrains/kotlin) |
| Kotlin coroutines Android | 1.9.0 | Apache-2.0, [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| AndroidX Activity KTX | 1.12.2 | Apache-2.0, [AndroidX](https://android.googlesource.com/platform/frameworks/support/) |
| AndroidX AppCompat | 1.7.1 | Apache-2.0, AndroidX |
| AndroidX Core KTX | 1.15.0 | Apache-2.0, AndroidX |
| ARSCLib | 1.3.8 | [Apache-2.0 license at tag V1.3.8](https://raw.githubusercontent.com/REAndroid/ARSCLib/V1.3.8/LICENSE), REAndroid |
| Gson | 2.13.2 | Apache-2.0, [Google Gson](https://github.com/google/gson) |
| Material Components for Android | 1.13.0 | Apache-2.0, [Material Components](https://github.com/material-components/material-components-android) |

The cryptographic APK signature verifier, native-library/DEX summaries and 16 KiB readiness
analysis remain Inspector source under the repository license. The shared parser's signature
scheme-presence utility is not a replacement for cryptographic verification or authorization to
read sibling files. Test-only synthetic archives and official signing-tool fixtures remain in the
existing test resource directories; they are not bundled as user applications.
