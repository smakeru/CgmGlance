import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself; applying `org.jetbrains.kotlin.android` on top is now
    // a hard error. See https://developer.android.com/build/migrate-to-built-in-kotlin
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing, when a key is available.
//
// The keystore and its passwords live outside version control, so a checkout without them —
// CI, or anyone else's machine — still builds; it simply produces an unsigned release APK
// instead of failing. Both apps must use the *same* key: the Wear Data Layer only pairs nodes
// sharing an application id and a signing certificate, and a mismatch fails silently.
val keystoreProperties =
    rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
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
        versionCode = 5
        versionName = "1.2"
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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

        // So the screen can show the version it was actually built from, rather than a string
        // someone has to remember to keep in step with `versionName` above.
        buildConfig = true
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
    implementation(project(":provider:dexcom"))

    // The only channel to the watch, and the project's only Play Services dependency.
    implementation(libs.play.services.wearable)

    // Writes glucose out to Samsung Health, by way of Health Connect. Phone only: the watch
    // module must stay untouched by this.
    implementation(libs.androidx.health.connect)

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
