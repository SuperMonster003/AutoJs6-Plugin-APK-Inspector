# Bundletool selection golden fixture

This privacy-neutral Android project exercises language, screen-density, and ABI splits. The
repository fixture is generated with pinned `bundletool` 1.18.2 and checked by the plugin's JVM
tests under three device specifications.

Generate the fixture from the repository root:

```text
python .python/generate_bundletool_selection_fixture.py
```

Verify that the committed fixture can be reproduced byte-for-byte:

```text
python .python/generate_bundletool_selection_fixture.py --verify
```

The generator verifies the downloaded bundletool JAR against its pinned SHA-256, builds this AAB,
runs `build-apks`, then runs `extract-apks --include-metadata` once per device specification. In
bundletool 1.18.2, `install-apks` obtains its installation list by constructing and executing the
same `ExtractApksCommand`; therefore the recorded metadata is the install selection oracle, without
requiring a mutable emulator in the unit-test suite:

- [InstallApksCommand 1.18.2](https://github.com/google/bundletool/blob/1.18.2/src/main/java/com/android/tools/build/bundletool/commands/InstallApksCommand.java#L247-L267)
- [ExtractApksCommand 1.18.2](https://github.com/google/bundletool/blob/1.18.2/src/main/java/com/android/tools/build/bundletool/commands/ExtractApksCommand.java#L183-L215)

To avoid checking in signing material or large generated APKs, the committed APKS preserves
bundletool's `toc.pb` byte-for-byte and replaces each APK with a deterministic ZIP containing only
its generated binary manifest. That is sufficient for both the bounded toc selector and the full
read-only archive inspection path used by device simulation.
