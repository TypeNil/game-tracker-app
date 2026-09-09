package io.github.typenil.gametracker.backend.application

data class ServerConfig(
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT,
    val host: String = System.getenv("HOST") ?: DEFAULT_HOST,
    val isDevelopment: Boolean = System.getenv("KTOR_ENV") != "production"
) {
    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_HOST = "127.0.0.1"
    }
}
