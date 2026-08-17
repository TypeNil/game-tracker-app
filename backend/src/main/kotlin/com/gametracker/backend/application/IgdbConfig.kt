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
 * Строгий порядок разрешения параметров:
 * 1. Явная конфигурация Ktor (application.conf / sysprops / test config)
 * 2. Переменные окружения процесса (IGDB_CLIENT_ID / IGDB_CLIENT_SECRET)
 * 3. Локальный файл разработчика local.properties (fallback)
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

        clientId = when {
            configClientId != null -> configClientId.trim()
            else -> System.getenv("IGDB_CLIENT_ID")?.takeIf { it.isNotBlank() }
                ?: props.getProperty("IGDB_CLIENT_ID")?.takeIf { it.isNotBlank() }
                ?: ""
        }

        clientSecret = when {
            configClientSecret != null -> configClientSecret.trim()
            else -> System.getenv("IGDB_CLIENT_SECRET")?.takeIf { it.isNotBlank() }
                ?: props.getProperty("IGDB_CLIENT_SECRET")?.takeIf { it.isNotBlank() }
                ?: ""
        }
    }
}
