# APK Inspector

APK Inspector supplies the primary read-only inspection action in the file manager for APK, APKS, XAPK, APKM, APKZ, and AAB files.

It reports package details, requested permissions, components, device-matched splits, OBB assets, structural findings, APK V1-V3 signature presence, and a formatted Android Manifest.

The plugin requires host build 5277+.

Safety and privacy limits:

- The source is opened through temporary read-only `content` URI access.
- Input is copied once to a private read-only snapshot with a 4 GiB limit and SHA-256 calculation.
- The plugin requests no storage, network, or package installation permission.
- The plugin never installs or modifies a package. Signature schemes are presence checks only.
