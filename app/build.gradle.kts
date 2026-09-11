import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec

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

android {
    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = globalApplicationId
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

        resValue("string", "plugin_author", "SuperMonster003")
        resValue("string", "plugin_version_date", utils.getDateString("MMM d, yyyy", "GMT+08:00"))
    }

    lint {
        abortOnError = false
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

    implementation(files("$rootDir/libs/common-plugin-api.aar"))
    implementation(files("$rootDir/libs/explorer-action-api.aar"))

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.arsclib)
    implementation(libs.core.ktx)
    implementation(libs.gson)
    implementation(libs.material)

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
