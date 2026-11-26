import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// ./gradlew assembleRelease            -> regular "blisschat"
// ./gradlew assembleRelease -Pglass=true -> "blisschat glass" (own package, liquid-glass engine from src/glass)
val glass = providers.gradleProperty("glass").orNull == "true"

android {
    namespace = "com.bliss.blisschat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bliss.blisschat"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1.0"
        if (glass) applicationId = "com.bliss.blisschat.glass"
        buildConfigField("boolean", "GLASS", glass.toString())
        manifestPlaceholders["appLabel"] = if (glass) "blisschat glass" else "blisschat"
    }

    signingConfigs {
        create("releaseKey") {
            val keystoreFile = file("release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "123456"
                keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "bliss"
                keyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "123456"
            }
        }
    }

    buildTypes {
        debug {
            val cfg = signingConfigs.findByName("releaseKey")
            if (cfg?.storeFile != null) signingConfig = cfg
        }
        release {
            isMinifyEnabled = false
            val cfg = signingConfigs.findByName("releaseKey")
            if (cfg?.storeFile != null) signingConfig = cfg
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
            // the glass app gets the real liquid-glass engine, the regular app a no-op with the same API
            java.srcDir(if (glass) "src/glass/java" else "src/standard/java")
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
                URI.create(u).toURL().openStream().use { input ->
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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // WebRTC (prebuilt libwebrtc) for audio / video calls
    implementation("io.getstream:stream-webrtc-android:1.1.1")
}
