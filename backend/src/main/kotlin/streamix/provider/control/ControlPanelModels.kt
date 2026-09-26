package streamix.provider.control

import java.time.Instant
import streamix.api.ProviderStatusResponse
import streamix.api.ProviderUpdateResponse

enum class IncidentSeverity { LOW, MEDIUM, HIGH }
enum class IncidentCategory { PROVIDER, EXTRACTOR, RUNTIME, NETWORK, UPDATE }

data class ProviderIncident(
    val id: String,
    val severity: IncidentSeverity,
    val category: IncidentCategory,
    val componentId: String,
    val title: String,
    val message: String,
    val createdAt: Instant,
    val resolvedAt: Instant? = null
)

interface ProviderIncidentStore {
    fun all(): List<ProviderIncident>
    fun recent(limit: Int = 50): List<ProviderIncident>
    fun record(incident: ProviderIncident)
    fun resolve(id: String, resolvedAt: Instant = Instant.now()): Boolean
}

class InMemoryProviderIncidentStore : ProviderIncidentStore {
    private val incidents = java.util.concurrent.ConcurrentHashMap<String, ProviderIncident>()

    override fun all(): List<ProviderIncident> =
        incidents.values.sortedByDescending { it.createdAt }

    override fun recent(limit: Int): List<ProviderIncident> =
        all().take(limit.coerceAtLeast(0))

    override fun record(incident: ProviderIncident) {
        require(incident.id.isNotBlank()) { "incident id must not be blank" }
        incidents[incident.id] = incident
    }

    override fun resolve(id: String, resolvedAt: Instant): Boolean {
        val current = incidents[id] ?: return false
        incidents[id] = current.copy(resolvedAt = resolvedAt)
        return true
    }
}

data class ProviderUpdateSummary(
    val total: Int,
    val available: Int,
    val upToDate: Int,
    val running: Int,
    val failed: Int
)

data class RuntimeComponentStatus(
    val componentId: String,
    val running: Boolean,
    val healthy: Boolean,
    val message: String? = null
)

data class NetworkDiagnosticSnapshot(
    val connected: Boolean,
    val dnsReady: Boolean,
    val tlsReady: Boolean,
    val cacheHitRate: Double? = null,
    val responseTimeMs: Long? = null,
    val totalRequests: Long = 0
)

data class ExtractorStatus(
    val extractorId: String,
    val domains: List<String>
)

data class ExtractorCatalog(
    val total: Int,
    val extractors: List<ExtractorStatus>
)

data class ControlPanelSnapshot(
    val providers: List<ProviderStatusResponse>,
    val updateSummary: ProviderUpdateSummary,
    val updates: List<ProviderUpdateResponse>,
    val incidents: List<ProviderIncident>,
    val runtime: List<RuntimeComponentStatus>,
    val network: NetworkDiagnosticSnapshot
)
