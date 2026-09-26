package streamix.provider.control

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ProviderHealth(
    val providerId: String,
    val available: Boolean,
    val consecutiveFailures: Int = 0,
    val lastSuccessAt: Instant? = null,
    val lastFailureAt: Instant? = null,
    val lastError: String? = null
)

data class ProviderVersion(
    val providerId: String,
    val version: String,
    val upstream: String,
    val upstreamCommit: String,
    val activatedAt: Instant
)

data class ProviderStatus(
    val providerId: String,
    val health: ProviderHealth,
    val version: ProviderVersion?
)

class ProviderControlPlane(
    providerIds: Iterable<String>
) {
    private val health = ConcurrentHashMap<String, ProviderHealth>()
    private val versions = ConcurrentHashMap<String, ProviderVersion>()

    init {
        providerIds.forEach { id ->
            require(id.isNotBlank()) { "provider id must not be blank" }
            val key = id.lowercase()
            health.putIfAbsent(key, ProviderHealth(providerId = id, available = true))
        }
    }

    fun status(): List<ProviderStatus> =
        health.values
            .sortedBy { it.providerId.lowercase() }
            .map { current ->
                ProviderStatus(
                    providerId = current.providerId,
                    health = current,
                    version = versions[current.providerId.lowercase()]
                )
            }

    fun recordSuccess(providerId: String, at: Instant = Instant.now()) {
        val key = providerId.lowercase()
        health.compute(key) { _, current ->
            val base = current ?: ProviderHealth(providerId = providerId, available = true)
            base.copy(
                available = true,
                consecutiveFailures = 0,
                lastSuccessAt = at,
                lastError = null
            )
        }
    }

    fun recordFailure(providerId: String, error: String? = null, at: Instant = Instant.now()) {
        val key = providerId.lowercase()
        health.compute(key) { _, current ->
            val base = current ?: ProviderHealth(providerId = providerId, available = false)
            base.copy(
                available = false,
                consecutiveFailures = base.consecutiveFailures + 1,
                lastFailureAt = at,
                lastError = error
            )
        }
    }

    fun activateVersion(
        providerId: String,
        version: String,
        upstream: String,
        upstreamCommit: String,
        activatedAt: Instant = Instant.now()
    ) {
        require(version.isNotBlank()) { "version must not be blank" }
        require(upstream.isNotBlank()) { "upstream must not be blank" }
        require(upstreamCommit.isNotBlank()) { "upstream commit must not be blank" }

        versions[providerId.lowercase()] = ProviderVersion(
            providerId = providerId,
            version = version,
            upstream = upstream,
            upstreamCommit = upstreamCommit,
            activatedAt = activatedAt
        )
    }

    fun activeVersion(providerId: String): ProviderVersion? =
        versions[providerId.lowercase()]
}
