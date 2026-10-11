plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// KoveLink version. Bump VERSION_NAME on every release; VERSION_CODE must only ever increase.
val kovelinkVersionCode = 100
val kovelinkVersionName = "1.0.0"

android {
    namespace = "com.kove.mirror"
    compileSdk = 34

    defaultConfig {
        // Different applicationId than upstream KoveMirror (com.kove.mirror) so both apps
        // can be installed side by side on the same phone.
        applicationId = "si.kovelink.app"
        minSdk = 26
        targetSdk = 34
        versionCode = kovelinkVersionCode
        versionName = kovelinkVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug-signed release so the APK can be sideloaded straight from CI.
            // Replace with a real keystore before distributing to other people.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        // Other locales only fall back to English for the newest strings; never fail the build on it.
        abortOnError = false
        disable += setOf("MissingTranslation")
    }

    applicationVariants.all {
        val variantName = name
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                "KoveLink-v${kovelinkVersionName}-${variantName}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // OSM Map - osmdroid & Mapsforge (OpenAndroMaps .map support)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("org.osmdroid:osmdroid-mapsforge:6.1.18")
    implementation("org.mapsforge:mapsforge-map-android:0.20.0")
    implementation("org.mapsforge:mapsforge-themes:0.20.0")

    // 3D Map - MapLibre GL Native (3D buildings, tilt/rotate camera).
    // v11.0.0 uses kotlin-stdlib 1.9.x, compatible with project's Kotlin 1.9.22.
    implementation("org.maplibre.gl:android-sdk:11.0.0")
}
