package ani.dantotsu.connections.shinigami

import ani.dantotsu.BuildConfig

object ShinigamiBackendConfig {
    val baseUrl: String
        get() = BuildConfig.SHINIGAMI_BACKEND_URL
            .trim()
            .removeSuffix("/")
            .takeIf { it.isNotBlank() }
            ?: error("Shinigami backend URL is not configured")
}
