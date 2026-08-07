plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plain JVM on purpose. This module is the wire format between two APKs that are updated at
// different times, so it must be readable and testable without an Android device. The Android
// specific DataMap conversion lives on each side; the shape and the rules live here.
kotlin {
    jvmToolchain(libs.versions.javaToolchain.get().toInt())
}

dependencies {
    implementation(project(":core:domain"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
