package com.gametracker.backend.application

import io.ktor.server.config.ApplicationConfig
import java.io.File
import java.util.Properties

interface IgdbConfig {
    val clientId: String
    val clientSecret: String
    val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()
}

/**
 * Конфигурация для доступа к IGDB API.
 * Значения загружаются из application.conf, local.properties или переменных окружения.
 */
class IgdbConfigImpl(config: ApplicationConfig) : IgdbConfig {
    override val clientId: String
    override val clientSecret: String

    init {
        val props = Properties()
        val file = File("local.properties")
        if (file.exists()) {
            props.load(file.inputStream())
        } else if (File("../local.properties").exists()) {
            props.load(File("../local.properties").inputStream())
        }

        val configClientId = config.propertyOrNull("igdb.clientId")?.getString()
        val configClientSecret = config.propertyOrNull("igdb.clientSecret")?.getString()

        clientId = configClientId
            ?: props.getProperty("IGDB_CLIENT_ID")
            ?: System.getenv("IGDB_CLIENT_ID")
            ?: ""

        clientSecret = configClientSecret
            ?: props.getProperty("IGDB_CLIENT_SECRET")
            ?: System.getenv("IGDB_CLIENT_SECRET")
            ?: ""
    }
}
