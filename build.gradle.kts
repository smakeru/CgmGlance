import com.diffplug.spotless.LineEnding

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
}

// Formatting is configured once at the root and targets every module. When the module count
// grows past a handful this moves into a convention plugin under `build-logic`; until then a
// single declaration is simpler than the indirection.
spotless {
    // Pinned explicitly rather than left on the default GIT_ATTRIBUTES policy: that policy
    // falls back to the platform ending when no git repository is present, so the same
    // sources would format differently before and after `git init` on Windows.
    lineEndings = LineEnding.UNIX

    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}
