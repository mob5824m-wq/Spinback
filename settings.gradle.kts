pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "spinback"

// The reusable framework library, vendored from Soniq-Base-Android (MIT).
// Zero runtime dependencies; see soniq-base/build.gradle.kts.
include(":soniq-base")

// The Google Play Music themed app. Everything catalogue-specific lives
// here, behind the MusicProvider SPI; the framework stays provider-agnostic.
include(":app")
