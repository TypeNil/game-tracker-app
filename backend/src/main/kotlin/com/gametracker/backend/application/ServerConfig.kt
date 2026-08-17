package com.gametracker.backend.application

/**
 * Конфигурация сервера Ktor BFF.
 * Считывает порт и окружение из переменных среды или конфигурационного файла.
 */
data class ServerConfig(
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT,
    val host: String = System.getenv("HOST") ?: DEFAULT_HOST,
    val isDevelopment: Boolean = System.getenv("KTOR_ENV") != "production"
) {
    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_HOST = "0.0.0.0"
    }
}
