APK Inspector examines APK, APKS, XAPK, APKM, APKZ and AAB packages before you install them, showing the app name, version, permissions, components, signatures and more, and tells whether the package can be installed on this device. It only reads the file: it never installs the package or changes the original file.

### Getting started

1. Make sure this plugin is enabled in AutoJs6 "Plugin Center".
2. Open the AutoJs6 file manager and locate the package file.
3. Tap the file, or choose "Inspect Android package" from its menu (in AutoJs6 6.8.1 and later it is under the top-level menu "APK Inspector").
4. Read the report from top to bottom: "Package details", "Components", "Requested permissions" and "Security and compatibility findings".

### Main features

- **Package details**: shows the package name, version, and minimum and target SDK, compared with this device; long-press a field to copy its value.
- **Signature verification**: verifies the V2, V3 and other signature schemes and shows the fingerprints of the signing certificates.
- **Requested permissions**: lists permissions in three groups (runtime, signature and normal), with runtime permissions on top and a short explanation for each.
- **Components**: summarizes activities, services, receivers and providers and marks whether each one is exported.
- **Security and compatibility findings**: points out problems such as a missing base APK, duplicate splits, or mismatched versions or package names, and separates blocking issues from hints.
- **Device configuration simulation**: for split APK sets such as APKS, switch the language, screen density and ABI, then tap "Apply simulated configuration" to compare the selected splits with this device.
- **View manifest**: reads AndroidManifest as XML with line numbers and syntax highlighting; use "Find in manifest" to search it.
- **Share report**: sends a text report that matches the screen through the system share sheet, without creating a file.

### Requirements

- Android 7.0 or later.
- AutoJs6 6.8.0 or later.
- No storage, network or install permission is needed; the plugin only reads the file you selected in the file manager.

### FAQ

**Why the plugin does not install packages**

The plugin is meant for looking inside a package before installing it, so it requests no install permission and has no install button. Install packages with the system installer or the installation flow of AutoJs6.

**Why some files cannot be inspected**

Common causes are a file larger than 8 GiB, less than 128 MiB of free cache space, a file changed by another app while it is being read, or a damaged file. The error message on the report page names the cause.

**What a valid signature means**

A valid signature only shows that the file has not changed since it was signed; it does not prove that the signer or the app can be trusted. Compare the certificate fingerprint or SHA-256 with the values published by the official source.

**Why AAB files cannot be installed directly**

AAB is a distribution format for app stores, and Android devices cannot install it directly. The plugin shows its manifest and module structure; convert it to APK or APKS with a tool such as bundletool before installing.

See the [project homepage](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector) for more details.
