plugins {
    kotlin("jvm") version "2.0.0"
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.boy0000"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jglrxavpok.hephaistos:common:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.github.ajalt.mordant:mordant:2.6.0")
    implementation("com.github.ajalt.mordant:mordant-coroutines:2.6.0")
    implementation("com.google.code.gson:gson:2.12.1")
}

application {
    mainClass.set("com.boy0000.MainKt")
}

tasks {
    shadowJar.get().archiveFileName.set("MinecraftWorldScanner.jar")
    build.get().dependsOn(shadowJar)
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    manifest {
        attributes["Main-Class"] = "com.boy0000.MainKt"
    }
    mergeServiceFiles()
}

kotlin {
    jvmToolchain(21)
}