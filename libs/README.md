# Bundled host release artifacts

The build consumes only the three AAR files in this directory. Each filename and SHA-256 is
checked against `../locks/host-api-aars.lock` during Gradle configuration. It does not resolve
artifacts from sibling repositories or a local Maven repository.

| Artifact | Provenance | SHA-256 |
| --- | --- | --- |
| `common-plugin-api.aar` | Existing Inspector snapshot retained byte-for-byte from repository baseline `b98cd54`; PluginInfo and the common plugin contract | `f9ff9676543f45b2ff2b64bb832d377dbd5b7003acf3bb4239e98556c68b246f` |
| `explorer-action-api.aar` | Existing Inspector snapshot retained byte-for-byte from repository baseline `b98cd54`; Explorer Action v22 and host file-information contract | `057ae2bd927d6f8e0b84f2a30c5d6b80584b4950288c7bb2a06ef489f86ad816` |
| `package-archive-parser.aar` | Release build of AutoJs6 module `plugin-api/package-archive-parser`, host commit `0767971bc9`, host build 5299; assembled 2026-10-01, staged here 2026-10-02 from the same audited artifact used by 3-Setup Installer | `1441bbcee8468362b0ee41f7b3d5ab47eb87b4df78a1388bb1134223055f46e7` |

The older two snapshots do not record a producer build in this repository; this migration
adds their byte locks without inventing that provenance or replacing their published contract.
The parser is bundled implementation code, so consuming it does not raise Inspector's required
host build 5277 or change the Explorer Action/host file-information protocol.

The parser supplies format detection, base/split selection, common manifest summaries, APK
binary XML, AAB protobuf XML, bundletool TOC decoding and inspection ceilings. Gson 2.13.2 is
already an explicit runtime dependency of Inspector and remains required by the raw parser AAR.
Inspector layers its own read-only report details on the shared result. It never requests
installation preparation or exposes the AAR's staging/installability helpers to its entry points.

The signature verifier and its stricter signing-block reader remain local. In particular,
Inspector does not call the shared `ApkSignatureDetector.detectSchemes`, whose implicit `.idsig`
existence check is outside Inspector's explicitly authorized sidecar contract.

License details are in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). Replace an AAR only
with an audited release artifact and update its provenance, hash lock and consumer tests together.
