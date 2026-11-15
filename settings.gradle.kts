pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.lavalink.dev/releases")
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
        maven("https://maven.lavalink.dev/releases")
        maven("https://maven.lavalink.dev/snapshots")
        maven("https://jitpack.io")
    }
}

rootProject.name = "spotify-source"
include("common", "plugin")
