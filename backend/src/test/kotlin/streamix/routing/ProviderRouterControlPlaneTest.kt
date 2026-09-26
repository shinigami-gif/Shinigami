package streamix.routing

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.api.StreamRef
import streamix.provider.ProviderRuntime
import streamix.provider.control.ProviderControlPlane

class ProviderRouterControlPlaneTest {
    @Test
    fun records_success_and_failure_without_changing_provider_contract() = runBlocking {
        val controlPlane = ProviderControlPlane(listOf("test"))
        val provider = FakeProvider("test") { emptyList() }
        val router = ProviderRouter(
            providers = listOf(provider),
            controlPlane = controlPlane
        )

        router.streams(episode("test", "episode-1"))

        val status = controlPlane.status().single()
        assertTrue(status.health.available)
        assertEquals(0, status.health.consecutiveFailures)
        assertEquals(null, status.health.lastError)
    }

    @Test
    fun records_exception_as_provider_failure() = runBlocking {
        val controlPlane = ProviderControlPlane(listOf("test"))
        val provider = FakeProvider("test") {
            error("upstream timeout")
        }
        val router = ProviderRouter(
            providers = listOf(provider),
            controlPlane = controlPlane
        )

        assertTrue(router.streams(episode("test", "episode-1")).isEmpty())

        val status = controlPlane.status().single()
        assertEquals(false, status.health.available)
        assertEquals(1, status.health.consecutiveFailures)
        assertEquals("upstream timeout", status.health.lastError)
    }

    private fun episode(providerId: String, id: String) = EpisodeRef(
        providerId = providerId,
        number = 1,
        title = "Episode 1",
        providerEpisodeId = id,
        url = id
    )

    private class FakeProvider(
        override val providerId: String,
        private val streamResult: suspend () -> List<StreamRef>
    ) : ProviderRuntime {
        override suspend fun search(query: String, page: Int): List<ProviderAnime> = emptyList()
        override suspend fun loadAnime(providerAnimeId: String): ProviderAnime? = null
        override suspend fun loadEpisodes(providerAnimeId: String): List<EpisodeRef> = emptyList()
        override suspend fun loadStreams(episode: EpisodeRef): List<StreamRef> = streamResult()
    }
}
