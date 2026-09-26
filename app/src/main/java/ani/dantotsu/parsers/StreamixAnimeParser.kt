package ani.dantotsu.parsers

import ani.dantotsu.FileUrl
import ani.dantotsu.streamix.StreamixBackend
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import streamix.api.EpisodeRef
import streamix.api.StreamRef
import java.net.URLDecoder
import java.net.URLEncoder

class StreamixAnimeParser : AnimeParser() {

    override val name: String = "Shinigami Runtime"
    override val saveName: String = "shinigami-runtime"
    override val hostUrl: String = "runtime://shinigami"

    override suspend fun search(query: String): List<ShowResponse> =
        StreamixBackend.router.search(query).map { anime ->
            ShowResponse(
                name = anime.title,
                link = anime.url.ifBlank { anime.id },
                coverUrl = "",
                extra = mutableMapOf(
                    PROVIDER_KEY to anime.providerId,
                    ANIME_KEY to anime.id
                )
            )
        }

    override suspend fun loadEpisodes(
        animeLink: String,
        extra: Map<String, String>?,
        sAnime: SAnime
    ): List<Episode> {
        val providerId = extra?.get(PROVIDER_KEY) ?: return emptyList()
        val animeId = extra[ANIME_KEY] ?: animeLink
        val provider = StreamixBackend.provider(providerId) ?: return emptyList()

        return provider.loadEpisodes(animeId).map { episode ->
            val episodeId = episode.providerEpisodeId
            val episodeNumber = episode.number
            val episodeUrl = episode.url.ifBlank { animeLink }
            val title = episode.title ?: "Episode $episodeNumber"
            val sEpisode = SEpisode.create().apply {
                url = episodeUrl
                name = title
                episode_number = episodeNumber.toFloat()
                scanlator = providerId
            }

            Episode(
                number = episodeNumber.toString(),
                link = episodeUrl,
                title = title,
                extra = mutableMapOf(
                    PROVIDER_KEY to providerId,
                    EPISODE_KEY to episodeId
                ),
                sEpisode = sEpisode
            )
        }
    }

    override suspend fun loadVideoServers(
        episodeLink: String,
        extra: Map<String, String>?,
        sEpisode: SEpisode
    ): List<VideoServer> {
        val providerId = extra?.get(PROVIDER_KEY) ?: return emptyList()
        val episodeId = extra[EPISODE_KEY] ?: episodeLink
        val provider = StreamixBackend.provider(providerId) ?: return emptyList()

        val episode = EpisodeRef(
            providerId = providerId,
            number = sEpisode.episode_number.toInt(),
            title = sEpisode.name,
            providerEpisodeId = episodeId,
            url = episodeLink
        )

        return provider.loadStreams(episode)
            .mapIndexed { index, stream ->
                VideoServer(
                    name = streamLabel(stream, index),
                    embed = FileUrl(stream.url, stream.headers),
                    extraData = encodeStreamData(stream)
                )
            }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        if (server.embed.url.isBlank()) return null
        return StreamixVideoExtractor(server)
    }

    private fun streamLabel(stream: StreamRef, index: Int): String {
        val quality = stream.quality?.let { "$it" + "p" }
        val type = stream.type?.takeIf { it.isNotBlank() }
        return listOfNotNull(
            quality,
            type,
            stream.language?.takeIf { it.isNotBlank() }
        ).joinToString(" ").ifBlank { "Stream ${index + 1}" }
    }

    private fun encodeStreamData(stream: StreamRef): Map<String, String> {
        val subtitles = stream.subtitles.joinToString(SUBTITLE_SEPARATOR) { subtitle ->
            listOf(
                encode(subtitle.language.orEmpty()),
                encode(subtitle.url)
            ).joinToString(FIELD_SEPARATOR)
        }

        return buildMap {
            put(TYPE_KEY, stream.type.orEmpty())
            put(QUALITY_KEY, stream.quality?.toString().orEmpty())
            put(LANGUAGE_KEY, stream.language.orEmpty())
            put(REFERER_KEY, stream.referer.orEmpty())
            if (subtitles.isNotBlank()) put(SUBTITLE_KEY, subtitles)
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        const val PROVIDER_KEY = "streamix_provider"
        const val ANIME_KEY = "streamix_anime"
        const val EPISODE_KEY = "streamix_episode"
        const val TYPE_KEY = "streamix_type"
        const val QUALITY_KEY = "streamix_quality"
        const val LANGUAGE_KEY = "streamix_language"
        const val REFERER_KEY = "streamix_referer"
        const val SUBTITLE_KEY = "streamix_subtitles"
        const val FIELD_SEPARATOR = "|"
        const val SUBTITLE_SEPARATOR = ";;"

        fun decode(value: String): String =
            URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}

private class StreamixVideoExtractor(
    override val server: VideoServer
) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        val type = server.extraData?.get(StreamixAnimeParser.TYPE_KEY).orEmpty()
        val quality = server.extraData?.get(StreamixAnimeParser.QUALITY_KEY)?.toIntOrNull()
        val language = server.extraData?.get(StreamixAnimeParser.LANGUAGE_KEY)
        val referer = server.extraData?.get(StreamixAnimeParser.REFERER_KEY)

        val headers = buildMap {
            putAll(server.embed.headers)
            if (!referer.isNullOrBlank()) put("Referer", referer)
        }

        val file = FileUrl(server.embed.url, headers)
        val videoType = when {
            type.contains("m3u8", ignoreCase = true) || type.contains("hls", ignoreCase = true) -> VideoType.M3U8
            type.contains("dash", ignoreCase = true) || type.contains("mpd", ignoreCase = true) -> VideoType.DASH
            server.embed.url.contains(".m3u8", ignoreCase = true) -> VideoType.M3U8
            server.embed.url.contains(".mpd", ignoreCase = true) -> VideoType.DASH
            else -> VideoType.CONTAINER
        }

        return VideoContainer(
            videos = listOf(
                Video(
                    quality = quality,
                    format = videoType,
                    file = file,
                    extraNote = language
                )
            ),
            subtitles = parseSubtitles(server.extraData?.get(StreamixAnimeParser.SUBTITLE_KEY))
        )
    }

    private fun parseSubtitles(raw: String?): List<Subtitle> {
        if (raw.isNullOrBlank()) return emptyList()

        return raw.split(StreamixAnimeParser.SUBTITLE_SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(StreamixAnimeParser.FIELD_SEPARATOR)
                if (parts.size != 2) return@mapNotNull null

                val language = StreamixAnimeParser.decode(parts[0]).ifBlank { "Unknown" }
                val url = StreamixAnimeParser.decode(parts[1])
                if (url.isBlank()) return@mapNotNull null

                Subtitle(
                    language = language,
                    url = url,
                    type = when {
                        url.contains(".ass", true) -> SubtitleType.ASS
                        url.contains(".srt", true) -> SubtitleType.SRT
                        url.contains(".vtt", true) -> SubtitleType.VTT
                        else -> SubtitleType.UNKNOWN
                    }
                )
            }
    }
}
