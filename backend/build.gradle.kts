import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    application
}

application {
    mainClass.set("com.gametracker.backend.ApplicationKt")
}

tasks.withType<JavaExec> {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }
    environment("IGDB_CLIENT_ID", localProperties.getProperty("IGDB_CLIENT_ID") ?: System.getenv("IGDB_CLIENT_ID") ?: "")
    environment("IGDB_CLIENT_SECRET", localProperties.getProperty("IGDB_CLIENT_SECRET") ?: System.getenv("IGDB_CLIENT_SECRET") ?: "")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Ktor Server Engine & Routing
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Ktor Client (для вызовов к IGDB API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)

    // In-memory Cache & Logging
    implementation(libs.caffeine)
    implementation(libs.logback.classic)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Detekt Plugins
    detektPlugins(libs.detekt.formatting)
    detektPlugins(libs.detekt.compose)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
