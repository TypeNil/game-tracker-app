package com.gametracker.backend.application

import io.ktor.server.config.ApplicationConfig

/**
 * Конфигурация для доступа к IGDB API.
 * Значения загружаются из application.conf или переменных окружения.
 */
class IgdbConfig(config: ApplicationConfig) {
    val clientId: String
    val clientSecret: String

    init {
        val props = java.util.Properties()
        val file = java.io.File("local.properties")
        if (file.exists()) {
            props.load(file.inputStream())
        } else if (java.io.File("../local.properties").exists()) {
            props.load(java.io.File("../local.properties").inputStream())
        }

        clientId = config.propertyOrNull("igdb.clientId")?.getString()?.takeIf { it.isNotBlank() }
            ?: props.getProperty("IGDB_CLIENT_ID") ?: ""

        clientSecret = config.propertyOrNull("igdb.clientSecret")?.getString()?.takeIf { it.isNotBlank() }
            ?: props.getProperty("IGDB_CLIENT_SECRET") ?: ""

        if (clientId.isBlank() || clientSecret.isBlank()) {
            println("WARN: IGDB credentials are not set properly. API calls will fail.")
        }
    }
}
