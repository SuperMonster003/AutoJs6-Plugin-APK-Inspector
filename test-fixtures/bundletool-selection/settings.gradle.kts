pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("io.github.supermonster003.autojs6-platform-versions") version "1.7.5"
    }
}

plugins {
    id("io.github.supermonster003.autojs6-platform-versions")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "bundletool-selection-fixture"
include(":app")
