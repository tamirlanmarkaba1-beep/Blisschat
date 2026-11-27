pluginManagement {
    // The glass app (env BLISS_GLASS=true) is built on a newer toolchain that the Backdrop liquid-glass library needs
    // (Kotlin 2.3 / Compose 1.10). The regular app keeps the old, proven versions.
    val glassBuild = System.getenv("BLISS_GLASS") == "true"
    resolutionStrategy {
        eachPlugin {
            if (glassBuild) {
                when {
                    requested.id.id == "com.android.application" -> useVersion("8.13.0")
                    requested.id.id.startsWith("org.jetbrains.kotlin") -> useVersion("2.3.10")
                }
            }
        }
    }
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

rootProject.name = "blisschat"
include(":app")
