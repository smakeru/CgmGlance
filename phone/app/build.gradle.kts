plugins {
    // AGP 9 compiles Kotlin itself; applying `org.jetbrains.kotlin.android` on top is now
    // a hard error. See https://developer.android.com/build/migrate-to-built-in-kotlin
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ro.smakeru.cgmglance.phone"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Must stay byte-for-byte identical to the wear module. The Wear Data Layer only
        // pairs nodes that share an application id *and* a signing certificate; a mismatch
        // fails silently at runtime rather than at build time.
        applicationId = "ro.smakeru.cgmglance"
        minSdk = libs.versions.minSdkPhone.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    // Kotlin's jvmTarget follows targetCompatibility under built-in Kotlin, so setting it
    // separately is unnecessary and would only create a second place to keep in sync.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:sync-contract"))

    // Brings in the notification listener service and its manifest entry.
    implementation(project(":provider:linx"))

    // The only channel to the watch, and the project's only Play Services dependency.
    implementation(libs.play.services.wearable)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
