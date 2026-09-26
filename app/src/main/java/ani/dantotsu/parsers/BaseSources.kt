package ani.dantotsu.parsers

import ani.dantotsu.Lazier
import ani.dantotsu.media.Media
import ani.dantotsu.media.anime.Episode
import ani.dantotsu.media.anime.EpisodeStorage
import ani.dantotsu.tryWithSuspend
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime

abstract class WatchSources : BaseSources() {

    override operator fun get(i: Int): AnimeParser {
        return (list.getOrNull(i) ?: list.firstOrNull())?.get?.value as? AnimeParser
            ?: EmptyAnimeParser()
    }

    suspend fun loadEpisodesFromMedia(
        i: Int,
        media: Media,
        invalidate: Boolean = false,
        onCachedLoaded: ((MutableMap<String, Episode>) -> Unit)? = null
    ): MutableMap<String, Episode> {
        return tryWithSuspend(true) {
            val parser = get(i)
            val sourceKey = parser.saveName.ifBlank { parser.name }

            var cached: MutableMap<String, Episode>? = null
            if (!invalidate) {
                val savedResponse = parser.loadSavedShowResponse(media.id)
                if (savedResponse != null && savedResponse.link.isNotBlank()) {
                    cached = EpisodeStorage.loadEpisodes(sourceKey, savedResponse.link)
                    if (!cached.isNullOrEmpty()) {
                        onCachedLoaded?.invoke(cached)
                    }
                }
            }

            val res = parser.autoSearch(media) ?: return@tryWithSuspend (cached ?: mutableMapOf())
            if (cached.isNullOrEmpty() && !invalidate) {
                cached = EpisodeStorage.loadEpisodes(sourceKey, res.link)
                if (!cached.isNullOrEmpty()) {
                    onCachedLoaded?.invoke(cached)
                }
            }

            val loaded = tryWithSuspend(true) {
                loadEpisodes(i, res.link, res.extra, res.sAnime)
            } ?: mutableMapOf()

            if (loaded.isNotEmpty()) {
                EpisodeStorage.saveEpisodes(sourceKey, res.link, loaded)
                loaded
            } else {
                cached ?: loaded
            }
        } ?: mutableMapOf()
    }

    suspend fun loadEpisodes(
        i: Int,
        showLink: String,
        extra: Map<String, String>?,
        sAnime: SAnime?
    ): MutableMap<String, Episode> {
        val map = mutableMapOf<String, Episode>()
        val parser = get(i)
        val actualAnime = sAnime ?: SAnime.create().apply {
            url = showLink
            title = ""
        }
        tryWithSuspend(true) {
            parser.loadEpisodes(showLink, extra, actualAnime).forEach {
                val key = if (it.sEpisode?.scanlator.isNullOrBlank()) it.number else "${it.number}-${it.sEpisode?.scanlator}"
                map[key] = Episode(
                    it.number,
                    it.link,
                    it.title,
                    it.description,
                    it.thumbnail,
                    it.isFiller,
                    extra = it.extra,
                    sEpisode = it.sEpisode
                )
            }
        }
        if (map.isNotEmpty()) {
            val sourceKey = parser.saveName.ifBlank { parser.name }
            EpisodeStorage.saveEpisodes(sourceKey, showLink, map)
        }
        return map
    }
}

abstract class BaseSources {
    abstract val list: List<Lazier<BaseParser>>

    val names: List<String> get() = list.map { it.name }

    fun flushText() {
        list.forEach {
            if (it.get.isInitialized())
                it.get.value?.showUserText = ""
        }
    }

    open operator fun get(i: Int): BaseParser? {
        return list[i].get.value
    }

    fun saveResponse(i: Int, mediaId: Int, response: ShowResponse) {
        get(i)?.saveShowResponse(mediaId, response, true)
    }
}
