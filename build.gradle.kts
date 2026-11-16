plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "dev.waris.spotify"
    version = "1.0.1"
}

