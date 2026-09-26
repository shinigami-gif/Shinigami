package streamix.provider

import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.api.StreamRef

interface ProviderRuntime {
    val providerId: String
    suspend fun search(query: String, page: Int = 1): List<ProviderAnime>
    suspend fun loadAnime(providerAnimeId: String): ProviderAnime?
    suspend fun loadEpisodes(providerAnimeId: String): List<EpisodeRef>
    suspend fun loadStreams(episode: EpisodeRef): List<StreamRef>
}
