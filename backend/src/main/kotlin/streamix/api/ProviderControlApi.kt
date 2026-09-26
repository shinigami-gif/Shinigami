package streamix.api

import streamix.provider.control.ProviderControlPlane
import streamix.provider.control.ProviderUpdateJob
import streamix.provider.control.ProviderUpdateManager
import streamix.provider.control.ControlPanelSnapshot
import streamix.provider.control.NetworkDiagnosticSnapshot
import streamix.provider.control.ProviderIncident
import streamix.provider.control.ProviderIncidentStore
import streamix.provider.control.ProviderUpdateSummary
import streamix.provider.control.ExtractorCatalog
import streamix.provider.control.ExtractorStatus
import streamix.provider.control.RuntimeComponentStatus
import streamix.core.StreamixExtractorRegistry

/**
 * Application-facing control-plane view and provider update actions.
 *
 * HTTP/authentication remains outside this class. The transport layer must
 * restrict mutating operations to authorized administrators.
 */
class ProviderControlApi(
    private val controlPlane: ProviderControlPlane,
    private val updateManager: ProviderUpdateManager? = null,
    private val incidentStore: ProviderIncidentStore? = null,
    private val extractorRegistry: StreamixExtractorRegistry? = null
) {
    fun status(): List<ProviderStatusResponse> =
        controlPlane.status().map { current ->
            val version = current.version
            ProviderStatusResponse(
                providerId = current.providerId,
                available = current.health.available,
                consecutiveFailures = current.health.consecutiveFailures,
                lastSuccessAt = current.health.lastSuccessAt?.toString(),
                lastFailureAt = current.health.lastFailureAt?.toString(),
                lastError = current.health.lastError,
                version = version?.version,
                upstream = version?.upstream,
                upstreamCommit = version?.upstreamCommit,
                activatedAt = version?.activatedAt?.toString()
            )
        }

    fun extractors(): ExtractorCatalog {
        val entries = requireNotNull(extractorRegistry) {
            "Extractor registry is not configured"
        }.all().map { extractor ->
            ExtractorStatus(
                extractorId = extractor.id,
                domains = extractor.domains.sorted()
            )
        }.sortedBy { it.extractorId.lowercase() }
        return ExtractorCatalog(
            total = entries.size,
            extractors = entries
        )
    }

    fun incidents(): List<ProviderIncident> =
        incidentStore?.all() ?: emptyList()

    fun recentIncidents(limit: Int = 50): List<ProviderIncident> =
        incidentStore?.recent(limit) ?: emptyList()

    fun resolveIncident(id: String): Boolean =
        requireNotNull(incidentStore) { "Provider incident store is not configured" }
            .resolve(id)

    fun runtimeStatus(runtime: List<RuntimeComponentStatus>): RuntimeStatusResponse =
        RuntimeStatusResponse(components = runtime)

    fun networkDiagnostics(network: NetworkDiagnosticSnapshot): NetworkDiagnosticsResponse =
        NetworkDiagnosticsResponse(snapshot = network)

    fun config(entries: Map<String, String>): ConfigResponse =
        ConfigResponse(entries = entries.toSortedMap())

    fun snapshot(
        runtime: List<RuntimeComponentStatus>,
        network: NetworkDiagnosticSnapshot
    ): ControlPanelSnapshot {
        val providerStatuses = status()
        val updateJobs = requireUpdateManager().jobs().map(::toResponse)
        val summary = ProviderUpdateSummary(
            total = providerStatuses.size,
            available = updateJobs.count { it.status == "UPDATE_AVAILABLE" },
            upToDate = updateJobs.count { it.status == "UP_TO_DATE" },
            running = updateJobs.count {
                it.status == "QUEUED" || it.status == "RUNNING" || it.status == "READY_TO_DEPLOY"
            },
            failed = updateJobs.count {
                it.status == "FAILED" || it.status == "ROLLED_BACK"
            }
        )
        return ControlPanelSnapshot(
            providers = providerStatuses,
            updateSummary = summary,
            updates = updateJobs,
            incidents = recentIncidents(),
            runtime = runtime,
            network = network
        )
    }

    fun updates(): List<ProviderUpdateResponse> =
        requireUpdateManager().jobs().map(::toResponse)

    fun update(providerId: String): ProviderUpdateResponse? =
        requireUpdateManager().job(providerId)?.let(::toResponse)

    suspend fun checkUpdate(providerId: String): ProviderUpdateResponse =
        toResponse(requireUpdateManager().check(providerId))

    fun queueUpdate(providerId: String): ProviderUpdateResponse =
        toResponse(requireUpdateManager().enqueue(providerId))

    suspend fun runUpdate(providerId: String): ProviderUpdateResponse =
        toResponse(requireUpdateManager().update(providerId))

    suspend fun activateUpdate(providerId: String): ProviderUpdateResponse =
        toResponse(requireUpdateManager().activate(providerId))

    suspend fun rollback(
        providerId: String,
        targetVersion: String
    ): ProviderUpdateResponse =
        toResponse(requireUpdateManager().rollback(providerId, targetVersion))

    private fun toResponse(job: ProviderUpdateJob) = ProviderUpdateResponse(
        providerId = job.providerId,
        status = job.status.name,
        currentVersion = job.candidate.currentVersion,
        currentCommit = job.candidate.currentCommit,
        targetVersion = job.candidate.targetVersion,
        targetCommit = job.candidate.targetCommit,
        upstream = job.candidate.upstream,
        changelog = job.candidate.changelog,
        detectedAt = job.candidate.detectedAt.toString(),
        queuedAt = job.queuedAt?.toString(),
        startedAt = job.startedAt?.toString(),
        completedAt = job.completedAt?.toString(),
        error = job.error,
        steps = job.steps.map { step ->
            ProviderUpdateStepResponse(
                id = step.id,
                name = step.name,
                status = step.status.name,
                durationMs = step.durationMs,
                message = step.message
            )
        }
    )

    private fun requireUpdateManager(): ProviderUpdateManager =
        requireNotNull(updateManager) {
            "Provider update manager is not configured"
        }
}

data class ProviderUpdateResponse(
    val providerId: String,
    val status: String,
    val currentVersion: String?,
    val currentCommit: String?,
    val targetVersion: String,
    val targetCommit: String,
    val upstream: String,
    val changelog: List<String>,
    val detectedAt: String,
    val queuedAt: String?,
    val startedAt: String?,
    val completedAt: String?,
    val error: String?,
    val steps: List<ProviderUpdateStepResponse>
)

data class ProviderUpdateStepResponse(
    val id: String,
    val name: String,
    val status: String,
    val durationMs: Long?,
    val message: String?
)
