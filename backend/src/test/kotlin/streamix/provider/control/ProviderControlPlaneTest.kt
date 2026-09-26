package streamix.provider.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProviderControlPlaneTest {
    @Test
    fun records_health_and_active_version_without_touching_provider_runtime() {
        val plane = ProviderControlPlane(listOf("Otakudesu", "Samehadaku"))

        plane.recordFailure("Otakudesu", "upstream timeout")
        plane.recordFailure("Otakudesu", "upstream timeout")
        plane.recordSuccess("Otakudesu")
        plane.activateVersion(
            providerId = "Otakudesu",
            version = "43",
            upstream = "Hatsune",
            upstreamCommit = "abc123"
        )

        val status = plane.status()
        val otaku = status.first { it.providerId == "Otakudesu" }

        assertTrue(otaku.health.available)
        assertEquals(0, otaku.health.consecutiveFailures)
        assertEquals(null, otaku.health.lastError)
        assertNotNull(otaku.health.lastSuccessAt)
        assertFalse(status.first { it.providerId == "Samehadaku" }.health.consecutiveFailures > 0)
        assertEquals("43", otaku.version?.version)
        assertEquals("abc123", otaku.version?.upstreamCommit)
    }
}
