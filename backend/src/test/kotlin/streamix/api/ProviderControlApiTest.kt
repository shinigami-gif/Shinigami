package streamix.api

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import streamix.provider.control.InMemoryProviderIncidentStore
import streamix.provider.control.IncidentCategory
import streamix.provider.control.IncidentSeverity
import streamix.provider.control.ProviderControlPlane
import streamix.provider.control.ProviderIncident
import streamix.provider.control.ProviderUpdateCandidate
import streamix.provider.control.ProviderUpdateExecutor
import streamix.provider.control.ProviderUpdateManager
import streamix.provider.control.ProviderUpdateResult
import streamix.provider.control.ProviderUpdateStep
import streamix.provider.control.ProviderVersion
import streamix.provider.control.RuntimeComponentStatus
import streamix.provider.control.NetworkDiagnosticSnapshot

class ProviderControlApiTest {
    @Test
    fun snapshot_exposes_provider_updates_incidents_runtime_and_network() {
        val controlPlane = ProviderControlPlane(listOf("otakudesu", "samehadaku"))
        val manager = ProviderUpdateManager(controlPlane, NoOpExecutor())
        val incidents = InMemoryProviderIncidentStore()
        incidents.record(
            ProviderIncident(
                id = "inc-1",
                severity = IncidentSeverity.MEDIUM,
                category = IncidentCategory.UPDATE,
                componentId = "otakudesu",
                title = "Update failed",
                message = "verification failed",
                createdAt = Instant.parse("2026-09-25T00:00:00Z")
            )
        )
        val api = ProviderControlApi(controlPlane, manager, incidents)

        val snapshot = api.snapshot(
            runtime = listOf(RuntimeComponentStatus("provider-runtime", true, true)),
            network = NetworkDiagnosticSnapshot(
                connected = true,
                dnsReady = true,
                tlsReady = true,
                responseTimeMs = 120,
                totalRequests = 7
            )
        )

        assertEquals(2, snapshot.providers.size)
        assertEquals(2, snapshot.updateSummary.total)
        assertEquals(1, snapshot.incidents.size)
        assertEquals("provider-runtime", snapshot.runtime.single().componentId)
        assertEquals(120, snapshot.network.responseTimeMs)
    }

    @Test
    fun incident_can_be_resolved_through_control_api() {
        val controlPlane = ProviderControlPlane(listOf("otakudesu"))
        val incidents = InMemoryProviderIncidentStore()
        incidents.record(
            ProviderIncident(
                id = "inc-1",
                severity = IncidentSeverity.LOW,
                category = IncidentCategory.PROVIDER,
                componentId = "otakudesu",
                title = "Transient failure",
                message = "timeout",
                createdAt = Instant.now()
            )
        )
        val api = ProviderControlApi(
            controlPlane,
            ProviderUpdateManager(controlPlane, NoOpExecutor()),
            incidents
        )

        assertTrue(api.resolveIncident("inc-1"))
        assertTrue(api.incidents().single().resolvedAt != null)
    }

    private class NoOpExecutor : ProviderUpdateExecutor {
        override suspend fun detect(
            providerId: String,
            activeVersion: ProviderVersion?
        ): ProviderUpdateCandidate? = null

        override suspend fun execute(
            candidate: ProviderUpdateCandidate,
            onStep: suspend (ProviderUpdateStep) -> Unit
        ) = ProviderUpdateResult(false)

        override suspend fun activate(candidate: ProviderUpdateCandidate) = false

        override suspend fun rollback(
            providerId: String,
            targetVersion: String
        ) = ProviderUpdateResult(false)
    }
}
