plugins {
    // ROOT CAUSE SEBENARNYA (ditemukan setelah membaca source KotlinClasspathService.java):
    // versi kotlin-metadata-jvm yang dipakai Loom untuk remap class Kotlin diambil dari versi
    // Kotlin Gradle Plugin yang KITA deklarasikan di sini (bukan dari Gradle atau dari Loom
    // sendiri). Karena itu perbaikannya BUKAN mengejar versi Loom/Gradle terbaru (sempat salah
    // arah ke alpha 1.18.0 yang malah butuh Java 25) -- cukup naikkan versi Kotlin di bawah ini
    // ke >= 2.2.0 (Cobblemon terbaru dikompilasi dengan Kotlin 2.2.0). Kembali ke Loom STABIL.
    id("fabric-loom") version "1.17.14"
    kotlin("jvm") version "2.3.21"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()

    // Repo resmi Modrinth, dipakai untuk menarik jar Cobblemon sebagai compile-time dependency.
    // Ini pola resmi yang direkomendasikan Cobblemon sendiri di halaman Modrinth mereka.
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")

    // Lihat catatan di gradle.properties soal cobblemon_modrinth_version.
    modImplementation("maven.modrinth:MdwFAVRL:${project.property("cobblemon_modrinth_version")}")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}
