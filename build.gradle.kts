// Spinback — top-level Gradle build.
//
// :soniq-base is an Android library with zero runtime dependencies: it is built
// against the Android framework only (minSdk 26 / targetSdk 35). :app is the
// Google Play Music themed revival client and depends on it.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
}

/**
 * The version shared by every module — derived from git, never hard-coded:
 *
 *  - the current commit carries a `vX.Y.Z` tag  →  `X.Y.Z`           (a release)
 *  - it is a later commit                       →  `<last tag>-SNAPSHOT`
 *  - there are no tags at all                   →  `0.0.0-SNAPSHOT`
 *
 * Tagging is therefore the whole release step. `-PsoniqVersion=…` overrides
 * this for local experiments only.
 */
fun git(vararg args: String): String? = runCatching {
    val process = ProcessBuilder("git", *args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    output.takeIf { process.exitValue() == 0 && it.isNotEmpty() }
}.getOrNull()

val soniqVersion: String = (findProperty("soniqVersion") as String?)?.takeIf { it.isNotBlank() }
    ?: git("describe", "--tags", "--exact-match", "--match", "v[0-9]*")?.removePrefix("v")
    ?: git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")?.removePrefix("v")?.plus("-SNAPSHOT")
    ?: "0.0.0-SNAPSHOT"

extra["soniqVersion"] = soniqVersion
