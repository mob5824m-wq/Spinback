plugins {
    id("com.android.application")
}

// Same tag-derived version as the library (see the root build script).
val soniqVersion: String = rootProject.extra["soniqVersion"] as String
version = soniqVersion

/** `1.2.3` → 1002003; base-1000 minor/patch slots avoid common SemVer collisions. */
fun versionCodeOf(version: String): Int {
    val parts = version.substringBefore('-').split('.').map {
        it.toLongOrNull() ?: error("Invalid SemVer component in $version")
    } + listOf(0L, 0L, 0L)
    require(parts[0] <= 2100L && parts[1] < 1000L && parts[2] < 1000L) {
        "SemVer components exceed Android versionCode encoding limits"
    }
    // Unreleased checkouts use 0.0.0-SNAPSHOT; Android needs versionCode >= 1.
    val code = (parts[0] * 1_000_000L + parts[1] * 1_000L + parts[2]).coerceAtLeast(1L)
    require(code <= 2_100_000_000L) { "SemVer version is outside Android versionCode range" }
    return code.toInt()
}

android {
    namespace = "com.spinback.gplaymusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spinback.gplaymusic"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(soniqVersion)
        versionName = soniqVersion
    }

    buildTypes {
        release {
            // This is a revival client, not a store release: sign with the
            // debug key so `assembleRelease` works out of the box.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        // Print findings to the console as well, so CI annotations can show them.
        textReport = true
    }
}

dependencies {
    // The framework is vendored as :soniq-base (see settings.gradle.kts).
    // To depend on a published build instead, replace this with e.g.
    //   implementation("com.soniq.base:soniq-base:0.1.0")      (GitHub Packages)
    //   implementation("com.github.mob5824m-wq:Soniq-Base:v0.1.0") (JitPack)
    implementation(project(":soniq-base"))
}
