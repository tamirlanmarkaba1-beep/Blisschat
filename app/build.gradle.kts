plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bliss.blisschat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bliss.blisschat"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
    }

    // Fixed key committed to the repo so the SHA-1 stays the same on every machine / CI run
    // (Google Sign-In is bound to the SHA-1). Replace with your own keystore for a public release.
    signingConfigs {
        create("blisschat") {
            storeFile = file("blisschat.keystore")
            storePassword = "blisschat"
            keyAlias = "blisschat"
            keyPassword = "blisschat"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("blisschat") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("blisschat")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint { checkReleaseBuilds = false }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    sourceSets {
        getByName("main") {
            // Nunito is downloaded here at build time (see downloadNunito below)
            assets.srcDir(layout.buildDirectory.dir("generated/nunito"))
        }
    }
}

/**
 * Downloads the Nunito variable font (SIL Open Font License) once, unless you already placed
 * your own copy at app/src/main/assets/fonts/Nunito.ttf. If the download fails the build still
 * succeeds and the app falls back to the system font.
 */
val downloadNunito by tasks.registering {
    val target = layout.buildDirectory.file("generated/nunito/fonts/Nunito.ttf").get().asFile
    val manual = file("src/main/assets/fonts/Nunito.ttf")
    outputs.file(target)
    doLast {
        if (manual.exists() || target.exists()) return@doLast
        target.parentFile.mkdirs()
        val urls = listOf(
            "https://github.com/google/fonts/raw/main/ofl/nunito/Nunito%5Bwght%5D.ttf",
            "https://raw.githubusercontent.com/google/fonts/main/ofl/nunito/Nunito%5Bwght%5D.ttf"
        )
        for (u in urls) {
            try {
                java.net.URL(u).openStream().use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                if (target.length() > 50_000) break else target.delete()
            } catch (e: Exception) {
                target.delete()
                logger.warn("Nunito download failed from $u: ${e.message}")
            }
        }
        if (!target.exists()) logger.warn("Nunito not downloaded - the app will use the system font.")
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(downloadNunito)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Google Sign-In through Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // WebRTC (prebuilt libwebrtc) for audio / video calls
    implementation("io.getstream:stream-webrtc-android:1.1.1")
}
