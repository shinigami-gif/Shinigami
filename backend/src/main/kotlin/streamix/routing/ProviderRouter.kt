package streamix.routing

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import streamix.api.CanonicalAnimeIdentity
import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.api.StreamRef
import streamix.provider.ProviderRuntime
import streamix.provider.control.ProviderControlPlane
import streamix.runtime.ProviderRegistry

/**
 * Streamix's only provider-selection layer.
 *
 * The router does not implement provider/extractor behavior. It selects the
 * native Streamix provider runtime and delegates the complete provider lifecycle.
 *
 * When backed by ProviderRegistry, lookups are registry-backed so an activated
 * provider release is visible to subsequent requests without rebuilding the router.
 */
class ProviderRouter(
    private val providerSource: () -> List<ProviderRuntime>,
    private val searchTimeoutMs: Long = 20_000L,
    private val controlPlane: ProviderControlPlane? = null
) {
    constructor(
        providers: List<ProviderRuntime>,
        searchTimeoutMs: Long = 20_000L,
        controlPlane: ProviderControlPlane? = null
    ) : this({ providers }, searchTimeoutMs, controlPlane)

    constructor(
        registry: ProviderRegistry,
        searchTimeoutMs: Long = 20_000L,
        controlPlane: ProviderControlPlane? = null
    ) : this({ registry.all() }, searchTimeoutMs, controlPlane)

    fun provider(providerId: String): ProviderRuntime? =
        providerSource().firstOrNull { it.providerId.equals(providerId, ignoreCase = true) }

    suspend fun search(query: String, page: Int = 1): List<ProviderAnime> = coroutineScope {
        providerSource().map { provider ->
            async {
                val result = withTimeoutOrNull(searchTimeoutMs) {
                    runCatching { provider.search(query, page) }
                }
                when {
                    result == null -> {
                        controlPlane?.recordFailure(provider.providerId, "search timeout")
                        emptyList()
                    }
                    result.isSuccess -> {
                        controlPlane?.recordSuccess(provider.providerId)
                        result.getOrThrow()
                    }
                    else -> {
                        controlPlane?.recordFailure(
                            provider.providerId,
                            result.exceptionOrNull()?.message
                        )
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun detail(anime: CanonicalAnimeIdentity): List<ProviderAnime> = coroutineScope {
        anime.providerMappings.mapNotNull { (providerId, providerAnimeId) ->
            provider(providerId)?.let { provider ->
                async {
                    runCatching { provider.loadAnime(providerAnimeId) }.also { result ->
                        if (result.isSuccess) {
                            controlPlane?.recordSuccess(provider.providerId)
                        } else {
                            controlPlane?.recordFailure(
                                provider.providerId,
                                result.exceptionOrNull()?.message
                            )
                        }
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun episodes(anime: CanonicalAnimeIdentity): List<EpisodeRef> = coroutineScope {
        anime.providerMappings.mapNotNull { (providerId, providerAnimeId) ->
            provider(providerId)?.let { provider ->
                async<List<EpisodeRef>> {
                    runCatching { provider.loadEpisodes(providerAnimeId) }.also { result ->
                        if (result.isSuccess) {
                            controlPlane?.recordSuccess(provider.providerId)
                        } else {
                            controlPlane?.recordFailure(
                                provider.providerId,
                                result.exceptionOrNull()?.message
                            )
                        }
                    }.getOrDefault(emptyList<EpisodeRef>())
                }
            }
        }.awaitAll().flatten()
            .distinctBy { it.providerId to it.providerEpisodeId }
    }

    suspend fun streams(episode: EpisodeRef): List<StreamRef> {
        val provider = provider(episode.providerId) ?: return emptyList()
        return runCatching { provider.loadStreams(episode) }
            .also { result ->
                if (result.isSuccess) {
                    controlPlane?.recordSuccess(provider.providerId)
                } else {
                    controlPlane?.recordFailure(
                        provider.providerId,
                        result.exceptionOrNull()?.message
                    )
                }
            }
            .getOrDefault(emptyList())
    }

    suspend fun streamsWithFallback(episodes: List<EpisodeRef>): List<StreamRef> {
        for (episode in episodes.distinctBy { it.providerId to it.providerEpisodeId }) {
            val streams = streams(episode)
            if (streams.isNotEmpty()) return streams
        }
        return emptyList()
    }
}
