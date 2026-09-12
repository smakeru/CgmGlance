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
    namespace = "ro.smakeru.cgmglance.wear"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Identical to the phone module on purpose. The Wear Data Layer only pairs nodes
        // that share an application id *and* a signing certificate. A mismatch produces no
        // build error and no runtime exception - the watch simply never receives anything,
        // which is the hardest class of bug to diagnose in this project.
        applicationId = "ro.smakeru.cgmglance"
        minSdk = libs.versions.minSdkWear.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        // Play requires a distinct versionCode per APK within one release. The wear APK is
        // offset by a million so both form factors can be versioned from the same logical
        // release number without ever colliding.
        // TODO: confirm the expected ordering against Play's multi-APK rules before the
        //  first upload; the offset direction matters there and cannot be changed later.
        versionCode = 1_000_002
        versionName = "1.1"
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
    }

    testOptions {
        // Robolectric needs the merged resources and the manifest to inflate anything.
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:sync-contract"))

    // Data Layer. The only channel between phone and watch, and the only Play Services
    // dependency in the project.
    implementation(libs.play.services.wearable)

    // Local store read by the complication, the tile and the app. Each of those is a
    // separate system-driven lifecycle, so they share one file rather than one object.
    implementation(libs.androidx.datastore.preferences)

    // Complications and tiles. Verified to build against compileSdk 36.
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.androidx.concurrent.futures)

    // The watch app itself: the tap target for the complication, and where the value is shown
    // in full rather than squeezed into a slot.
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // The surfaces the system drives are only meaningfully testable in a simulated Android.
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
