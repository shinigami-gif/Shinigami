package streamix.api

data class ProviderAnime(
    val providerId: String,
    val id: String,
    val title: String,
    val url: String = id
)

data class EpisodeRef(
    val providerId: String,
    val number: Int,
    val title: String? = null,
    val providerEpisodeId: String,
    val url: String
)

data class SubtitleRef(
    val url: String,
    val language: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null
)

data class StreamRef(
    val providerId: String,
    val url: String,
    val quality: Int? = null,
    val type: String? = null,
    val language: String? = null,
    val subtitleLanguage: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null,
    val subtitles: List<SubtitleRef> = emptyList()
)
