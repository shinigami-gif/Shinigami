package streamix.core

interface StreamixProvider {
    val id: String
    suspend fun search(query: String, page: Int = 1): List<ProviderAnime>
    suspend fun detail(anime: ProviderAnime): ProviderAnime?
    suspend fun episodes(anime: ProviderAnime): List<ProviderEpisode>
    suspend fun streams(episode: ProviderEpisode): List<ProviderStream>
}

data class ProviderAnime(
    val providerId: String,
    val id: String,
    val title: String,
    val url: String = id,
    val poster: String? = null,
    val banner: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val status: String? = null,
    val tags: List<String> = emptyList(),
    val rating: String? = null,
    val trailer: String? = null
)

data class ProviderEpisode(
    val providerId: String,
    val number: Int,
    val id: String,
    val title: String? = null,
    val url: String = id,
    val description: String? = null,
    val duration: String? = null
)

data class ProviderStream(
    val providerId: String,
    val url: String,
    val quality: Int? = null,
    val type: String? = null,
    val language: String? = null,
    val subtitleLanguage: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null,
    val subtitles: List<ProviderSubtitle> = emptyList()
)


data class ProviderHomeSection(
    val providerId: String,
    val title: String,
    val items: List<ProviderAnime> = emptyList()
)
