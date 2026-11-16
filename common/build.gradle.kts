plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

kotlin {
    explicitApi()
    compilerOptions {
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        allWarningsAsErrors = true
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

tasks.compileTestKotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.named<JavaCompile>("compileTestJava") {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

dependencies {
    compileOnly(libs.lavaplayer)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.lavaplayer)
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.mockwebserver3.junit5)
    testImplementation(libs.lavalink.youtube)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly("org.slf4j:slf4j-simple:${libs.versions.slf4j.get()}")
}

tasks.test {
    useJUnitPlatform {
        if (providers.gradleProperty("includeLive").orNull != "true") {
            excludeTags("live")
        }
    }
}

base {
    archivesName.set("spotify-common")
}

tasks.jar {
    archiveBaseName.set("spotify-common")
    archiveVersion.set(project.version.toString())
}

ktlint {
    android = true
}
