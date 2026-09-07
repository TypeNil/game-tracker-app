package io.github.typenil.gametracker.backend.application

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
 * Loads properties from local.properties in the current directory or the parent project directory.
 */
fun loadDefaultLocalProperties(): Properties {
    val props = Properties()
    val file = File("local.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    } else if (File("../local.properties").exists()) {
        File("../local.properties").inputStream().use { props.load(it) }
    }
    return props
}

/**
 * IGDB API access configuration with strict parameter resolution order:
 * 1. Non-blank explicit Ktor config (application.conf / sysprops / test config)
 * 2. Process environment variables (IGDB_CLIENT_ID / IGDB_CLIENT_SECRET)
 * 3. Local developer file local.properties (fallback)
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
