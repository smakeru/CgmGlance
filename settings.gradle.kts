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

rootProject.name = "cgm-glance"

// Pure JVM. Holds the safety-critical logic (units, trend, freshness) so it can be
// unit tested in milliseconds and reused byte-for-byte on both phone and watch.
include(":core:domain")

// The wire format between the two APKs. Plain JVM so version skew rules can be tested
// without a device. Treat its contents as published: users update phone and watch apart.
include(":core:sync-contract")

// Reads glucose out of the official LinX app. Its parsing logic is plain Kotlin so it can be
// tested against captured layouts without a device; only the RemoteViews extraction is Android.
include(":provider:linx")
include(":provider:dexcom")

// Acquires glucose from the official CGM app, validates it, and syncs it to the watch.
include(":phone:app")

// Renders the value: complication, tile and a small app. Shares the application id with
// the phone module, which is a hard requirement of the Wear Data Layer.
include(":wear:app")
