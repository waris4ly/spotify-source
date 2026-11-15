plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.lavalink)
}

kotlin {
    explicitApi()
    compilerOptions {
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        allWarningsAsErrors = true
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("generatePluginProperties")
}

tasks.named<Jar>("javadocJar") {
    dependsOn("generatePluginProperties")
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.plugin.api)
    compileOnly(libs.lavaplayer)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.lavaplayer)
    testImplementation(libs.plugin.api)
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

base {
    archivesName.set("spotify-plugin")
}

tasks.jar {
    archiveBaseName.set("spotify-plugin")
    archiveVersion.set(project.version.toString())
}

val pluginApiVersion =
    libs.versions.plugin.api
        .get()

lavalinkPlugin {
    name = "spotify-plugin"
    path = "dev.waris.spotify.plugin"
    apiVersion = pluginApiVersion
    serverVersion = "4.2.2"
}

ktlint {
    android = true
}
