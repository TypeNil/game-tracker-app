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
 * Загрузка свойств из local.properties в текущем каталоге или родительском каталоге проекта.
 */
fun loadDefaultLocalProperties(): Properties {
    val props = Properties()
    val file = File("local.properties")
    if (file.exists()) {
        props.load(file.inputStream())
    } else if (File("../local.properties").exists()) {
        props.load(File("../local.properties").inputStream())
    }
    return props
}

/**
 * Конфигурация для доступа к IGDB API.
 * Строгий порядок разрешения параметров:
 * 1. Непустая явная конфигурация Ktor (application.conf / sysprops / test config)
 * 2. Переменные окружения процесса (IGDB_CLIENT_ID / IGDB_CLIENT_SECRET)
 * 3. Локальный файл разработчика local.properties (fallback)
 */
class IgdbConfigImpl(
    config: ApplicationConfig,
    envProvider: (String) -> String? = { System.getenv(it) },
    localPropertiesProvider: () -> Properties = { loadDefaultLocalProperties() }
) : IgdbConfig {
    override val clientId: String
    override val clientSecret: String

    init {
        val props by lazy { localPropertiesProvider() }

        clientId = resolveParameter(
            configValue = config.propertyOrNull("igdb.clientId")?.getString(),
            envKey = "IGDB_CLIENT_ID",
            envProvider = envProvider,
            props = props
        )

        clientSecret = resolveParameter(
            configValue = config.propertyOrNull("igdb.clientSecret")?.getString(),
            envKey = "IGDB_CLIENT_SECRET",
            envProvider = envProvider,
            props = props
        )
    }

    private fun resolveParameter(
        configValue: String?,
        envKey: String,
        envProvider: (String) -> String?,
        props: Properties
    ): String {
        return configValue?.trim()?.takeIf { it.isNotEmpty() }
            ?: envProvider(envKey)?.trim()?.takeIf { it.isNotEmpty() }
            ?: props.getProperty(envKey)?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
    }
}
