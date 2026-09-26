package streamix.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.api.StreamRef
import streamix.provider.ProviderRuntime

class StreamixRuntimeControlPlaneTest {
    @Test
    fun router_and_control_api_use_the_runtime_control_plane() = runBlocking {
        val registry = ProviderRegistry()
        registry.register(FakeProvider("test"))

        val runtime = StreamixRuntime(registry)
        val result = runtime.router().streams(episode("test", "episode-1"))

        assertEquals(listOf(StreamRef("test", "https://example.test/stream.m3u8")), result)

        val status = runtime.controlApi().status().single()
        assertEquals("test", status.providerId)
        assertTrue(status.available)
        assertEquals(0, status.consecutiveFailures)
        assertEquals(null, status.lastError)
    }

    private fun episode(providerId: String, id: String) = EpisodeRef(
        providerId = providerId,
        number = 1,
        title = "Episode 1",
        providerEpisodeId = id,
        url = id
    )

    private class FakeProvider(
        override val providerId: String
    ) : ProviderRuntime {
        override suspend fun search(query: String, page: Int): List<ProviderAnime> = emptyList()
        override suspend fun loadAnime(providerAnimeId: String): ProviderAnime? = null
        override suspend fun loadEpisodes(providerAnimeId: String): List<EpisodeRef> = emptyList()
        override suspend fun loadStreams(episode: EpisodeRef): List<StreamRef> =
            listOf(StreamRef(providerId, "https://example.test/stream.m3u8"))
    }
}
