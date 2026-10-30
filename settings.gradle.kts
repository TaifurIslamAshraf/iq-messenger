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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiveKit's audio routing (com.github.davidliu:audioswitch) is published to
        // JitPack and nowhere else, and it is a hard dependency of livekit-android -
        // so without this the call SDK cannot resolve at all.
        //
        // Narrowed to that one group on purpose: JitPack builds straight from git
        // tags, so anything resolved through it is whatever that repository held at
        // the time. One known artifact may come from there; the rest of the build
        // stays on Google and Maven Central.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.davidliu") }
        }
    }
}

rootProject.name = "IQ-Message"
include(":app")
