package streamix.provider

import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.api.StreamRef
import streamix.core.StreamixProvider
import streamix.core.ProviderEpisode
import streamix.core.ProviderStream

/**
 * Transitional adapter from the canonical JVM StreamixProvider contract to the
 * application-facing ProviderRuntime contract.
 *
 * This is intentionally NOT a CloudStream bridge. It lets the existing
 * AniList/router/service layer consume the new JVM provider runtime while the
 * duplicate legacy application models are retired.
 */
class NativeStreamixProviderRuntime(
    private val provider: StreamixProvider
) : ProviderRuntime {
    override val providerId: String
        get() = provider.id

    override suspend fun search(query: String, page: Int): List<ProviderAnime> =
        provider.search(query, page).map(::toProviderAnime)

    override suspend fun loadAnime(providerAnimeId: String): ProviderAnime? {
        val anime = ProviderAnime(
            providerId = providerId,
            id = providerAnimeId,
            title = providerAnimeId,
            url = providerAnimeId
        )
        return provider.detail(anime.toCore())?.let(::toProviderAnime)
    }

    override suspend fun loadEpisodes(providerAnimeId: String): List<EpisodeRef> {
        val anime = ProviderAnime(
            providerId = providerId,
            id = providerAnimeId,
            title = providerAnimeId,
            url = providerAnimeId
        )
        return provider.episodes(anime.toCore()).map(::toEpisodeRef)
    }

    override suspend fun loadStreams(episode: EpisodeRef): List<StreamRef> =
        provider.streams(
            ProviderEpisode(
                providerId = providerId,
                number = episode.number,
                id = episode.providerEpisodeId,
                title = episode.title,
                url = episode.url
            )
        ).map(::toStreamRef)

    private fun ProviderAnime.toCore() = streamix.core.ProviderAnime(
        providerId = providerId,
        id = id,
        title = title,
        url = url
    )

    private fun toProviderAnime(value: streamix.core.ProviderAnime) = ProviderAnime(
        providerId = value.providerId,
        id = value.id,
        title = value.title,
        url = value.url
    )

    private fun toEpisodeRef(value: ProviderEpisode) = EpisodeRef(
        providerId = value.providerId,
        number = value.number,
        title = value.title,
        providerEpisodeId = value.id,
        url = value.url
    )

    private fun toStreamRef(value: ProviderStream) = StreamRef(
        providerId = value.providerId,
        url = value.url,
        quality = value.quality,
        type = value.type,
        language = value.language,
        subtitleLanguage = value.subtitleLanguage,
        headers = value.headers,
        referer = value.referer,
        subtitles = value.subtitles.map {
            streamix.api.SubtitleRef(
                url = it.url,
                language = it.language,
                headers = it.headers,
                referer = it.referer
            )
        }
    )
}
