plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a plain JVM module: no Android dependency, so the safety critical logic
// (units, trend, freshness) stays testable without an emulator and runs identically on
// phone and watch. Keep it that way — adding an Android dependency here is an architecture
// change, not a convenience.
kotlin {
    jvmToolchain(
        libs.versions.javaToolchain
            .get()
            .toInt(),
    )
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
