pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.lavalink.dev/releases")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.lavalink.dev/releases")
        maven("https://maven.lavalink.dev/snapshots")
        maven("https://maven.topi.wtf/releases")
        maven("https://jitpack.io")
    }
}

rootProject.name = "spotify-source"
include("common", "plugin")
