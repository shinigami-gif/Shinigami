package streamix.api

/**
 * Stable backend contract for the Shinigami client.
 *
 * HTTP transport is intentionally kept outside the provider/runtime layers so
 * the Android UI can be wired later without coupling it to provider runtimes.
 */
object BackendApiContract {
    const val SEARCH = "/api/v1/anime/search"
    const val DETAIL = "/api/v1/anime/{id}"
    const val EPISODES = "/api/v1/anime/{id}/episodes"
    const val STREAMS = "/api/v1/anime/{id}/episode/{number}/streams"

    const val PROVIDERS = "/api/v1/providers"
    const val PROVIDER_STATUS = "/api/v1/providers/status"
    const val PROVIDER_VERSION = "/api/v1/providers/{id}/version"

    const val PROVIDER_UPDATES = "/api/v1/providers/updates"
    const val PROVIDER_UPDATE_CHECK = "/api/v1/providers/{id}/updates/check"
    const val PROVIDER_UPDATE_QUEUE = "/api/v1/providers/updates/queue"
    const val PROVIDER_UPDATE = "/api/v1/providers/{id}/update"
    const val PROVIDER_ACTIVATE = "/api/v1/providers/{id}/update/activate"
    const val PROVIDER_ROLLBACK = "/api/v1/providers/{id}/rollback"
    const val PROVIDER_INCIDENTS = "/api/v1/providers/incidents"

    const val EXTRACTORS = "/api/v1/extractors"
    const val PROVIDER_EXTRACTORS = "/api/v1/providers/{id}/extractors"
    const val STREAM_TRACE = "/api/v1/stream/trace/{traceId}"
    const val RUNTIME_STATUS = "/api/v1/runtime/status"
    const val NETWORK_DIAGNOSTICS = "/api/v1/network/diagnostics"
    const val CONFIG = "/api/v1/config"

    const val HEALTH = "/api/v1/health"
}

data class ProviderStatusResponse(
    val providerId: String,
    val available: Boolean,
    val consecutiveFailures: Int,
    val lastSuccessAt: String?,
    val lastFailureAt: String?,
    val lastError: String?,
    val version: String?,
    val upstream: String?,
    val upstreamCommit: String?,
    val activatedAt: String?
)

data class RuntimeStatusResponse(
    val components: List<streamix.provider.control.RuntimeComponentStatus>
)

data class NetworkDiagnosticsResponse(
    val snapshot: streamix.provider.control.NetworkDiagnosticSnapshot
)

data class ConfigResponse(
    val entries: Map<String, String>
)
