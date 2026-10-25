plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.glasswidgets"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.glasswidgets"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No release keystore yet: sign with the debug key so the APK installs.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    // M3 Expressive components (ButtonGroup, ToggleButton, MaterialShapes, LoadingIndicator, flexible app bars)
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    // Material Color Utilities (scheme generation from a seed color).
    implementation("com.google.android.material:material:1.12.0")
}
