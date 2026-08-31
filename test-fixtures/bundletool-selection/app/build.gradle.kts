plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.supermonster003.apkinspector.fixture"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.supermonster003.apkinspector.fixture"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    bundle {
        language.enableSplit = true
        density.enableSplit = true
        abi.enableSplit = true
    }

    packaging {
        jniLibs.keepDebugSymbols += "**/libselection.so"
    }
}
