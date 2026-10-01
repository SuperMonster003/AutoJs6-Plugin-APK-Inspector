import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("io.github.supermonster003.autojs6-native-alignment")
    id("org.autojs.build.utils")
    id("org.autojs.build.versions")
    id("org.autojs.build.signs")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

val globalApplicationId = "io.github.supermonster003.autojs6.plugin.apkinspector"

val buildTypeDebug = "debug"
val buildTypeRelease = "release"

// These release snapshots keep clean clones self-contained and detect unreviewed replacement.
val hostApiNames = listOf("common-plugin-api", "explorer-action-api", "package-archive-parser")
val hostApiLock = Properties().apply {
    rootProject.file("locks/host-api-aars.lock").useLines { lines ->
        lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.forEach { line ->
            val parts = line.split('=', limit = 2)
            require(parts.size == 2 && put(parts[0], parts[1]) == null) { "Invalid or duplicate AAR lock entry" }
        }
    }
}
require(hostApiLock.getProperty("format") == "1" && hostApiLock.stringPropertyNames() ==
    setOf("format") + hostApiNames.flatMap { listOf("$it.file", "$it.sha256") }) { "Unexpected AAR lock fields" }
val hostApiAars = hostApiNames.map { name ->
    require(hostApiLock.getProperty("$name.file") == "$name.aar") { "Only the named release AAR is allowed" }
    val expected = hostApiLock.getProperty("$name.sha256")
    require(expected.matches(Regex("[0-9a-f]{64}"))) { "Invalid AAR SHA-256: $name" }
    rootProject.file("libs/$name.aar").also { file ->
        require(file.isFile) { "Missing bundled release AAR: $name" }
        val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        require(actual == expected) { "Bundled AAR SHA-256 mismatch: $name" }
    }
}

android {
    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        testInstrumentationRunner = if (providers.gradleProperty("releaseSmoke").isPresent) {
            "$globalApplicationId.release.ReleaseContractInstrumentation"
        } else "androidx.test.runner.AndroidJUnitRunner"
        applicationId = globalApplicationId
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

        resValue("string", "plugin_author", "SuperMonster003")
        resValue("string", "plugin_version_date", utils.getDateString("MMM d, yyyy", "GMT+08:00"))
    }

    lint {
        abortOnError = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        if (signs.isValid) {
            create(buildTypeRelease) {
                storeFile = signs.properties["storeFile"]?.let { file(it as String) }
                keyPassword = signs.properties["keyPassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                storePassword = signs.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val proguardFiles = arrayOf<Any>(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
        val niceSigningConfig = takeIf { signs.isValid }?.let {
            signingConfigs.getByName(buildTypeRelease)
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
    }

    buildFeatures {
        aidl = true
        resValues = true
        viewBinding = true
    }

    sourceSets.named("main") {
        kotlin.directories += "src/main/java"
    }
    sourceSets.named("androidTest") {
        assets.srcDir("src/test/resources/privacy-neutral-fixtures")
    }

    packaging {
        resources.pickFirsts.addAll(
            listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.*",
                "META-INF/NOTICE",
                "META-INF/NOTICE.*",
                "META-INF/*.kotlin_module",
            ),
        )
    }

    bundle {
        language.enableSplit = false
        density.enableSplit = false
        abi.enableSplit = false
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val architecture = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier
            val outputFileNameProperty = output.javaClass.methods.firstOrNull {
                it.name == "getOutputFileName" && it.parameterTypes.isEmpty()
            }?.invoke(output) as? Property<*>

            @Suppress("UNCHECKED_CAST")
            (outputFileNameProperty as? Property<String>)?.set(
                output.versionName.map { versionName ->
                    val version = versionName.replace("\\s".toRegex(), "-")
                    val abiSuffix = architecture?.let { "-$it" }.orEmpty()
                    "${rootProject.name}-v$version$abiSuffix.${utils.FILE_EXTENSION_APK}".lowercase()
                },
            )
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:2.2.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(files(hostApiAars))

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.arsclib)
    implementation(libs.core.ktx)
    implementation(libs.gson)
    implementation(libs.material)

    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"
    }

    val releaseArtifactScript = rootProject.layout.projectDirectory.file(".python/release_artifacts.py")
    val releaseSourceDirectory = layout.buildDirectory.dir("outputs/apk/$buildTypeRelease")
    val releaseDestinationDirectory = rootProject.layout.projectDirectory.dir("${buildTypeRelease}s")
    val releasePythonExecutable = providers.gradleProperty("releasePythonExecutable").orElse("python")

    val prepareReleaseArtifacts = register<Exec>("prepareReleaseArtifacts") {
        group = "distribution"
        description = "Stages CRC32-named release APKs with SHA-256 checksum files"
        dependsOn("assembleRelease")

        inputs.file(releaseArtifactScript)
        inputs.dir(releaseSourceDirectory)
        inputs.property("releaseProjectName", rootProject.name)
        inputs.property("releaseVersionName", versions.appVersionName)
        outputs.dir(releaseDestinationDirectory)

        doFirst {
            commandLine(
                releasePythonExecutable.get(),
                releaseArtifactScript.asFile.absolutePath,
                "prepare",
                "--source-dir",
                releaseSourceDirectory.get().asFile.absolutePath,
                "--destination-dir",
                releaseDestinationDirectory.asFile.absolutePath,
                "--project-name",
                rootProject.name,
                "--version",
                versions.appVersionName,
            )
        }
    }

    register<Exec>("verifyReleaseArtifacts") {
        group = "verification"
        description = "Verifies release APK CRC32 names and SHA-256 checksum files"

        inputs.file(releaseArtifactScript)
        inputs.dir(releaseDestinationDirectory)

        doFirst {
            commandLine(
                releasePythonExecutable.get(),
                releaseArtifactScript.asFile.absolutePath,
                "verify",
                "--destination-dir",
                releaseDestinationDirectory.asFile.absolutePath,
                "--project-name",
                rootProject.name,
            )
        }
    }

    register("appendDigestToReleasedFiles") {
        group = "distribution"
        description = "Compatibility alias for prepareReleaseArtifacts"
        dependsOn(prepareReleaseArtifacts)
    }
}

extra {
    versions.handleIfNeeded(project, "", listOf(buildTypeDebug, buildTypeRelease))
}

// Reject accidental native dependencies on every ABI.
nativeAlignment { expectNoNativeLibraries.set(true) }


// Fail before collection when credentials, keystore or the actual APK set are incomplete.
val verifySignedReleaseArtifacts = tasks.register("verifySignedReleaseArtifacts") {
    group = "verification"
    dependsOn("assembleRelease")
    doLast {
        val signing = android.buildTypes.getByName("release").signingConfig
        check(signing != null && signing.storeFile?.isFile == true &&
            !signing.storePassword.isNullOrBlank() && !signing.keyAlias.isNullOrBlank() &&
            !signing.keyPassword.isNullOrBlank()) { "Release signing configuration is missing or incomplete" }
        val directory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = directory.listFiles { file -> file.isFile && file.extension == "apk" }.orEmpty()
        check(apks.map { it.name }.toSet() == setOf("${rootProject.name}-v${versions.appVersionName}.apk")) {
            "Unexpected release APK set: ${apks.map { it.name }.sorted()}"
        }
        val buildTools = androidComponents.sdkComponents.sdkDirectory.get().asFile
            .resolve("build-tools/${android.buildToolsVersion}")
        val signerJar = buildTools.resolve("lib/apksigner.jar")
        check(signerJar.isFile) { "Android SDK apksigner is unavailable" }
        val result = providers.exec {
            commandLine("java", "-jar", signerJar.absolutePath, "verify", apks.single().absolutePath)
            isIgnoreExitValue = true
        }.result.get()
        check(result.exitValue == 0) { "Release APK signature verification failed" }
    }
}
tasks.named("appendDigestToReleasedFiles") { dependsOn(verifySignedReleaseArtifacts) }
tasks.matching { it.name == "prepareReleaseArtifacts" }.configureEach {
    dependsOn(verifySignedReleaseArtifacts)
}
