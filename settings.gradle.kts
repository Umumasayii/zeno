pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Zeno"

// Production app. Holds NO runtime permissions in M0.
include(":app")
// Pure-Kotlin domain contracts (no Android dependency) and shared fakes for tests.
include(":core:domain")
include(":core:testing")
// Separate debug-only APK for Android 14 device feasibility spikes (SPIKE-01, SPIKE-02).
// Kept apart so its microphone/boot permissions never leak into :app.
include(":spike")
