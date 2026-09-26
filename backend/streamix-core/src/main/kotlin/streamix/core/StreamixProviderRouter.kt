package streamix.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class StreamixProviderRouter(
    private val registry: StreamixProviderRegistry,
    private val timeoutMs: Long = 20_000L
) {
    suspend fun search(query: String, page: Int = 1): List<ProviderAnime> = coroutineScope {
        registry.all().map { provider ->
            async {
                withTimeoutOrNull(timeoutMs) {
                    runCatching { provider.search(query, page) }.getOrDefault(emptyList())
                }.orEmpty()
            }
        }.awaitAll().flatten()
    }

    suspend fun streams(episode: ProviderEpisode): List<ProviderStream> =
        registry.get(episode.providerId)?.let { provider ->
            withTimeoutOrNull(timeoutMs) {
                runCatching { provider.streams(episode) }.getOrDefault(emptyList())
            }.orEmpty()
        }.orEmpty()
}
