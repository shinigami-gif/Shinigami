package streamix.e2e

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue
import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.routing.ProviderRouter
import streamix.runtime.NativeProviderHost

class ProviderPoolLiveE2ETest {
    @Test
    fun providerPoolCanReachAStreamWithoutProviderSelection() = runBlocking {
        val runtimes = NativeProviderHost.runtimes()
        val router = ProviderRouter(runtimes, searchTimeoutMs = 15_000L)
        val queries = listOf(
            "Boruto",
            "Demon Slayer",
            "Jujutsu Kaisen",
            "Naruto",
            "Solo Leveling",
            "Attack on Titan",
            "Chainsaw Man",
            "Blue Lock",
            "Frieren",
            "Spy x Family",
            "My Hero Academia",
            "Wind Breaker",
            "Haikyuu",
            "Black Clover",
            "One Punch Man"
        )
        var totalStreams = 0

        for (query in queries) {
            val results = router.search(query)
                .groupBy { it.providerId.lowercase() }
                .mapValues { (_, values) -> values.first() }

            println("SEARCH query=$query providers=${results.keys.sorted()}")

            if (results.isEmpty()) {
                println("POOL query=$query skipped=no search results")
                continue
            }

            val episodeResults = coroutineScope {
                runtimes.map { runtime ->
                    async {
                        val anime = results[runtime.providerId.lowercase()]
                        if (anime == null) {
                            return@async ProviderLifecycle(runtime.providerId, null, null, emptyList(), emptyList())
                        }

                        val detail = withTimeoutOrNull(20_000L) {
                            runCatching { runtime.loadAnime(anime.id) }.getOrNull()
                        }

                        val episodes = detail?.let {
                            withTimeoutOrNull(20_000L) {
                                runCatching { runtime.loadEpisodes(anime.id) }.getOrDefault(emptyList())
                            }.orEmpty()
                        }.orEmpty()

                        val streamEpisodes = episodes.sortedBy { it.number }.take(3)
                        val streams = streamEpisodes.firstNotNullOfOrNull { episode ->
                            withTimeoutOrNull(20_000L) {
                                runCatching { runtime.loadStreams(episode) }
                                    .getOrDefault(emptyList())
                                    .takeIf { it.isNotEmpty() }
                            }
                        }.orEmpty()

                        ProviderLifecycle(runtime.providerId, detail, streamEpisodes.firstOrNull(), streamEpisodes, streams)
                    }
                }.awaitAll()
            }

            episodeResults.forEach { result ->
                println("PROVIDER query=$query ${result.providerId}: detail=${result.detail != null} episodes=${result.episodes.size} streams=${result.streams.size}")
            }

            val fallbackEpisodes = episodeResults
                .mapNotNull { it.episode }
                .distinctBy { it.providerId to it.providerEpisodeId }

            val fallbackStreams = router.streamsWithFallback(fallbackEpisodes)
            totalStreams += fallbackStreams.size

            println(
                "POOL query=$query episodeProviders=${fallbackEpisodes.map { it.providerId }.distinct()} " +
                    "selectedStreamProvider=${fallbackStreams.firstOrNull()?.providerId} streamCount=${fallbackStreams.size}"
            )
        }

        assertTrue(totalStreams > 0, "Provider pool produced no streams across all candidate anime")
    }

    private data class ProviderLifecycle(
        val providerId: String,
        val detail: ProviderAnime?,
        val episode: EpisodeRef?,
        val episodes: List<EpisodeRef>,
        val streams: List<streamix.api.StreamRef>
    )
}