# APK Inspector repository

Application ID: `io.github.supermonster003.autojs6.plugin.apkinspector`. Preserve existing action/category/PluginInfo identity constants.

## Shared repository standard (2026-09-13)

Read [the complete repository standard](docs/development/repository-standard.md) before changing this repository. It is part of this repository guidance. Existing product-specific constraints above remain in force.

This APK contains ABI-independent managed code; native alignment verification rejects native dependencies. No ABI splits are appropriate. Release collection is `:app:appendDigestToReleasedFiles` and verifies the exact signed APK set. Do not claim physical ColorOS activation, projection consent or host output publication was tested unless it was actually exercised.

Run `.python/check_markdown.bat`, `py -3 -m unittest discover -s .python/tests`, and the Gradle Wrapper with `--max-workers=2`. Platform acceptance: `--no-daemon -Djava.vendor="Eclipse Adoptium" -Djava.vendor.version=Temurin-21.0.12.1+1 :app:assembleDebug :app:testDebugUnitTest`. Disable version auto-increment while checking a prepared commit. Before every commit set VERSION_BUILD to `git rev-list --count HEAD` plus one.
