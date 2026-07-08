import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nahuel.homeflow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nahuel.homeflow"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Shared signing key decoded from the checked-in base64 so every build has the
    // SAME signature -> Android installs updates IN PLACE and your devices + routines
    // survive. Wrapped in runCatching so a missing/corrupt keystore can never fail
    // the whole build at configuration time (falls back to the debug key).
    val sharedSigning = runCatching {
        val b64 = rootProject.file("app/homeflow.keystore.b64")
        if (!b64.exists()) return@runCatching null
        val ks = layout.buildDirectory.file("homeflow.keystore").get().asFile
        ks.parentFile.mkdirs()
        ks.writeBytes(Base64.getDecoder().decode(b64.readText().trim()))
        ks
    }.getOrNull()

    if (sharedSigning != null) {
        signingConfigs {
            create("shared") {
                storeFile = sharedSigning
                storePassword = "homeflow"
                keyAlias = "homeflow"
                keyPassword = "homeflow"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("shared")
                ?: signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("shared")
                ?: signingConfigs.getByName("debug")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.zxing:core:3.5.3")   // QR code generation
}
