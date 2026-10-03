import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing secrets never live in git. Sources, in order:
//   CI:    env SIGNING_KEYSTORE_B64 / SIGNING_STORE_PASSWORD / SIGNING_KEY_ALIAS / SIGNING_KEY_PASSWORD
//   Local: app/homeflow.keystore.b64 + signing.* entries in local.properties (both gitignored)
// The SAME key must sign every build so Android updates in place and routines survive.
// Missing secrets fall back to the debug key (build still works, but won't update an installed app).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(prop)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.nahuel.homeflow"
    compileSdk = 35

    defaultConfig {
        // applicationId stays "homeflow": changing it would install a separate app and lose all data.
        applicationId = "com.nahuel.homeflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.0"
    }

    val sharedSigning = runCatching {
        val b64 = System.getenv("SIGNING_KEYSTORE_B64")?.takeIf { it.isNotBlank() }
            ?: rootProject.file("app/homeflow.keystore.b64").takeIf { it.exists() }?.readText()
            ?: return@runCatching null
        val ks = layout.buildDirectory.file("signing/shared.keystore").get().asFile
        ks.parentFile.mkdirs()
        ks.writeBytes(Base64.getDecoder().decode(b64.trim()))
        ks
    }.getOrNull()
    val storePw = secret("SIGNING_STORE_PASSWORD", "signing.storePassword")

    if (sharedSigning != null && storePw != null) {
        signingConfigs {
            create("shared") {
                storeFile = sharedSigning
                storePassword = storePw
                keyAlias = secret("SIGNING_KEY_ALIAS", "signing.keyAlias") ?: "homeflow"
                keyPassword = secret("SIGNING_KEY_PASSWORD", "signing.keyPassword") ?: storePw
            }
        }
    } else {
        logger.warn("SmartFlow: shared signing key not found - using debug key (no in-place update).")
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("shared")
                ?: signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.3")   // QR code generation

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in local unit tests; use the real implementation there.
    testImplementation("org.json:json:20240303")
}
