package streamix.routing

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.api.StreamRef
import streamix.provider.ProviderRuntime

class ProviderRouterFallbackTest {
    @Test
    fun fallsBackToNextProviderWhenFirstProviderProducesNoStreams() = runBlocking {
        val first = FakeProvider("first") { emptyList() }
        val second = FakeProvider("second") {
            listOf(StreamRef(providerId = "second", url = "https://example.invalid/stream.m3u8"))
        }
        val router = ProviderRouter(listOf(first, second))

        val streams = router.streamsWithFallback(
            listOf(
                episode("first", "episode-1"),
                episode("second", "episode-1")
            )
        )

        assertEquals(listOf("second"), streams.map { it.providerId })
        assertEquals(1, second.streamCalls)
        assertTrue(first.streamCalls == 1)
    }

    @Test
    fun fallbackDoesNotValidateOrRewriteProviderStream() = runBlocking {
        val expected = StreamRef(
            providerId = "second",
            url = "provider-produced://stream",
            quality = 1080,
            type = "hls",
            referer = "https://provider.example"
        )
        val router = ProviderRouter(
            listOf(
                FakeProvider("first") { emptyList() },
                FakeProvider("second") { listOf(expected) }
            )
        )

        assertEquals(
            listOf(expected),
            router.streamsWithFallback(
                listOf(
                    episode("first", "episode-1"),
                    episode("second", "episode-1")
                )
            )
        )
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
        var streamCalls: Int = 0

        override suspend fun search(query: String, page: Int): List<ProviderAnime> = emptyList()

        override suspend fun loadAnime(providerAnimeId: String): ProviderAnime? = null

        override suspend fun loadEpisodes(providerAnimeId: String): List<EpisodeRef> = emptyList()

        override suspend fun loadStreams(episode: EpisodeRef): List<StreamRef> {
            streamCalls++
            return streamResult()
        }
    }
}
