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
        versionCode = 1
        versionName = "1.0"
    }

    // Fixed signing key (decoded from checked-in base64) so every build has the SAME
    // signature -> Android installs updates in place and user data survives.
    // Wrapped so a missing/corrupt keystore can NEVER fail the whole build at
    // configuration time: if the decode fails, we simply fall back to the default
    // debug signing and the build still produces an installable APK.
    val sharedSigning = runCatching {
        val b64 = rootProject.file("app/homeflow.keystore.b64")
        if (!b64.exists()) return@runCatching null
        val ks = layout.buildDirectory.file("homeflow.keystore").get().asFile
        ks.parentFile.mkdirs()
        ks.writeBytes(java.util.Base64.getDecoder().decode(b64.readText().trim()))
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
            // Use the shared key if it decoded cleanly, otherwise the auto-generated
            // debug keystore (which always exists on the build machine).
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    // Local device APIs (Hue REST/SSE, Sonos SOAP, LG webOS WebSocket)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
