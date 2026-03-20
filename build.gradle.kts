import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.0"                // Plugin de Kotlin para JVM
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"  // Soporte para serialización JSON
    application                                  // Plugin para aplicaciones ejecutables
}

group = "com.multimedia"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()  // Repositorio central de Maven para dependencias
}

dependencies {
    // Serialización JSON para Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Corutinas para manejo concurrente de clientes
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Biblioteca para extraer metadatos de archivos de audio (ID3, etc.)
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Dependencias para testing
    testImplementation(kotlin("test"))
}

// Configuración del toolchain de Java
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))  // Requiere Java 21
    }
}

// Configuración del compilador de Kotlin
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)  // Generar bytecode compatible con Java 21
    }
}

// Configuración de la aplicación
application {
    mainClass.set("ServerKt")  // Clase principal generada a partir de Server.kt
}

// Configuración de tests
tasks.test {
    useJUnitPlatform()  // Usar JUnit Platform para tests
}