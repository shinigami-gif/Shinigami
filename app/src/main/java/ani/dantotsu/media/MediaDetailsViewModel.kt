package ani.dantotsu.media

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.dantotsu.R
import ani.dantotsu.client
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.currContext
import ani.dantotsu.media.anime.Episode
import ani.dantotsu.media.anime.SelectorDialogFragment
import ani.dantotsu.media.anime.getEpisode
import ani.dantotsu.others.AniSkip
import ani.dantotsu.others.Anify
import ani.dantotsu.others.AniskipCsvFallback
import ani.dantotsu.others.IdMappers
import ani.dantotsu.others.IntroDbService
import ani.dantotsu.others.Jikan
import ani.dantotsu.others.Kitsu
import ani.dantotsu.others.TmdbService
import ani.dantotsu.parsers.AnimeSources
import ani.dantotsu.parsers.OfflineAnimeParser
import ani.dantotsu.parsers.ShowResponse
import ani.dantotsu.parsers.VideoExtractor
import ani.dantotsu.parsers.WatchSources
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.tryWithSuspend
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

class MediaDetailsViewModel : ViewModel() {
    val scrolledToTop = MutableLiveData(true)

    fun saveSelected(id: Int, data: Selected) {
        PrefManager.setCustomVal("Selected-$id", data)
    }


    fun loadSelected(media: Media): Selected {
        if (media.format == "LOCAL" && media.selected != null) {
            return media.selected!!
        }
        return PrefManager.getNullableCustomVal("Selected-${media.id}", null, Selected::class.java)
            ?: Selected().let {
                it.sourceIndex = 0
                it.preferDub = PrefManager.getVal(PrefName.SettingsPreferDub)
                saveSelected(media.id, it)
                it
            }
    }

    var continueMedia: Boolean? = null
    var loading = false

    private val media: MutableLiveData<Media> = MutableLiveData<Media>(null)
    fun getMedia(): LiveData<Media> = media
    fun loadMedia(m: Media) {
        if (!loading) {
            loading = true
            val rescueMode = PrefManager.getVal(PrefName.RescueMode)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (m.id == 0 && m.format?.startsWith("LOCAL") == true) {
                    m.folderName = m.folderName ?: m.name
                    media.postValue(m)

                    val mapKeyStr = m.folderName ?: m.name
                    var mappedId = PrefManager.getCustomVal<Int>("local_mapping_$mapKeyStr", 0)
                    if (mappedId == 0) {
                        try {
                            var results = Anilist.query.searchAnime(
                                "ANIME",
                                search = m.name
                            )
                            if (results == null || results.results.isEmpty()) {
                                if (m.folderName != null && m.folderName != m.name) {
                                    results = Anilist.query.searchAnime(
                                        "ANIME",
                                        search = m.folderName!!
                                    )
                                }
                            }
                            if (!results?.results.isNullOrEmpty()) {
                                mappedId = results!!.results[0].id
                                PrefManager.setCustomVal("local_mapping_$mapKeyStr", mappedId)
                            }
                        } catch (e: Exception) {
                            ani.dantotsu.util.Logger.log(e)
                        }
                    }

                    if (mappedId != 0) {
                        val newMedia = m.copy(id = mappedId)
                        val fetchedMedia = Anilist.query.mediaDetails(newMedia)
                        fetchedMedia?.format = m.format
                        fetchedMedia?.cover?.let {
                            PrefManager.setCustomVal("local_cover_$mapKeyStr", it)
                        }
                        fetchedMedia?.banner?.let {
                            PrefManager.setCustomVal("local_banner_$mapKeyStr", it)
                        }
                        fetchedMedia?.folderName = m.folderName ?: m.name
                        fetchedMedia?.selected = m.selected
                        media.postValue(fetchedMedia)
                    }
                } else if (m.id == 0) {
                    m.folderName = m.folderName ?: m.name
                    media.postValue(m)
                } else if (rescueMode && m.idMAL != null) {
                    tryWithSuspend {
                        val malId = m.idMAL!!
                        val malNode = MAL.query.getAnimeDetails(malId)
                        if (malNode != null) {
                            val detailed = Media(malNode, true)
                            detailed.userProgress = m.userProgress ?: detailed.userProgress
                            detailed.userStatus = m.userStatus ?: detailed.userStatus
                            detailed.userScore = if (m.userScore != 0) m.userScore else detailed.userScore
                            detailed.isListPrivate = m.isListPrivate
                            detailed.userListId = m.userListId
                            detailed.userRepeat = m.userRepeat
                            detailed.userUpdatedAt = m.userUpdatedAt ?: detailed.userUpdatedAt
                            detailed.userCompletedAt = m.userCompletedAt
                            detailed.userStartedAt = m.userStartedAt
                            detailed.cameFromContinue = m.cameFromContinue
                            detailed.selected = m.selected
                            detailed.isFav = m.isFav
                            detailed.shareLink = "https://myanimelist.net/anime/$malId"
                            detailed.anime?.episodes = m.anime?.episodes
                            enrichRescueModeDetails(detailed)
                            media.postValue(detailed)
                            launchBackgroundEnrichment(detailed)
                        } else {
                            val jikanData = MAL.jikan.getAnimeById(malId)
                            if (jikanData != null) {
                                val detailed = Media(jikanData, true)
                                detailed.userProgress = m.userProgress ?: detailed.userProgress
                                detailed.userStatus = m.userStatus ?: detailed.userStatus
                                detailed.userScore = if (m.userScore != 0) m.userScore else detailed.userScore
                                detailed.isListPrivate = m.isListPrivate
                                detailed.userListId = m.userListId
                                detailed.userRepeat = m.userRepeat
                                detailed.userUpdatedAt = m.userUpdatedAt
                                detailed.userCompletedAt = m.userCompletedAt
                                detailed.userStartedAt = m.userStartedAt
                                detailed.cameFromContinue = m.cameFromContinue
                                detailed.selected = m.selected
                                detailed.isFav = m.isFav
                                detailed.shareLink = "https://myanimelist.net/anime/$malId"
                                detailed.anime?.episodes = m.anime?.episodes
                                enrichRescueModeDetails(detailed)
                                media.postValue(detailed)
                                launchBackgroundEnrichment(detailed)
                            } else {
                                media.postValue(m)
                            }
                        }
                    }
                } else if (rescueMode) {
                    media.postValue(m)
                } else {
                    media.postValue(Anilist.query.mediaDetails(m))
                }
                loading = false
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (m.idIMDB == null) {
                    m.idIMDB = if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                        m.idMAL?.let { ani.dantotsu.others.IdMappers.getImdbIdFromMal(it) }
                    } else {
                        ani.dantotsu.others.IdMappers.getImdbId(m.id)
                    }
                }
            } catch (e: Exception) {
                ani.dantotsu.util.Logger.log(e)
            }
        }
    }

    private suspend fun enrichRescueModeDetails(media: Media) {
        val malId = media.idMAL ?: return
        supervisorScope {
            val fullDeferred = async { MAL.jikan.getAnimeById(malId) }
            val charactersDeferred = async { MAL.jikan.getAnimeCharacters(malId) }
            val staffDeferred = async { MAL.jikan.getAnimeStaff(malId) }
            val reviewsDeferred = async { MAL.jikan.getAnimeReviews(malId) }
            val recommendationsDeferred = async { MAL.jikan.getRecommendations(true, malId) }

            val fullData = fullDeferred.await()
            if (fullData != null) {
                val fullMapped = Media(fullData, true)
                if (media.description.isNullOrBlank() && !fullMapped.description.isNullOrBlank()) media.description = fullMapped.description
                if (media.synonyms.isEmpty() && fullMapped.synonyms.isNotEmpty()) media.synonyms = fullMapped.synonyms
                if (media.genres.isEmpty() && fullMapped.genres.isNotEmpty()) media.genres = fullMapped.genres
                if (media.externalLinks.isNullOrEmpty() && !fullMapped.externalLinks.isNullOrEmpty()) media.externalLinks = fullMapped.externalLinks
                if ((media.meanScore == null || media.meanScore == 0) && fullMapped.meanScore != null) media.meanScore = fullMapped.meanScore
                if (media.source.isNullOrBlank() && !fullMapped.source.isNullOrBlank()) media.source = fullMapped.source
                if (!fullMapped.relations.isNullOrEmpty()) {
                    if (media.relations.isNullOrEmpty() || (fullMapped.relations?.size ?: 0) > (media.relations?.size ?: 0)) {
                        media.relations = fullMapped.relations
                    }
                    if (media.prequel == null) media.prequel = fullMapped.prequel
                    if (media.sequel == null) media.sequel = fullMapped.sequel
                }
                if (!fullMapped.staff.isNullOrEmpty()) {
                    media.staff = ArrayList(((media.staff ?: arrayListOf()) + fullMapped.staff!!).distinctBy { it.id })
                }
                if (!fullMapped.recommendations.isNullOrEmpty() &&
                    (fullMapped.recommendations?.size ?: 0) > (media.recommendations?.size ?: 0)) {
                    media.recommendations = fullMapped.recommendations
                }
                if (media.trailer.isNullOrBlank() && !fullMapped.trailer.isNullOrBlank()) media.trailer = fullMapped.trailer
                fullMapped.anime?.let { anime ->
                    if (anime.op.isNotEmpty()) media.anime?.op = anime.op
                    if (anime.ed.isNotEmpty()) media.anime?.ed = anime.ed
                    if (media.anime?.mainStudio == null) anime.mainStudio?.let { media.anime?.mainStudio = it }
                    if (media.anime?.producers.isNullOrEmpty() && !anime.producers.isNullOrEmpty()) media.anime?.producers = anime.producers
                    if (media.anime?.season.isNullOrBlank()) anime.season?.let { media.anime?.season = it }
                    if (media.anime?.seasonYear == null) anime.seasonYear?.let { media.anime?.seasonYear = it }
                    if (media.anime?.nextAiringEpisodeTime == null && anime.nextAiringEpisodeTime != null) media.anime?.nextAiringEpisodeTime = anime.nextAiringEpisodeTime
                    val estimated = anime.nextAiringEpisode
                    val watched = media.userProgress ?: 0
                    val nextAiring = if (estimated != null) {
                        if (watched > 0 && watched >= estimated + 1) watched else estimated
                    } else if (watched > 0) watched else null
                    if (nextAiring != null) media.anime?.nextAiringEpisode = nextAiring
                }
            }

            val jRecommendations = try { recommendationsDeferred.await() } catch (_: Exception) { null }
            val mappedRecommendations = jRecommendations
                ?.mapNotNull { it.entry }
                ?.map {
                    Media(
                        id = it.malId,
                        idMAL = it.malId,
                        name = it.title,
                        nameRomaji = it.title ?: "",
                        userPreferredName = it.title ?: "",
                        cover = it.images?.jpg?.largeImageUrl ?: it.images?.jpg?.imageUrl,
                        banner = it.images?.jpg?.largeImageUrl,
                        isAdult = false,
                        anime = ani.dantotsu.media.anime.Anime(null, null, null)
                    )
                }
                ?.distinctBy { it.id }
                ?.let { ArrayList(it) }

            if (!mappedRecommendations.isNullOrEmpty() &&
                (media.recommendations.isNullOrEmpty() || mappedRecommendations.size > (media.recommendations?.size ?: 0))) {
                media.recommendations = mappedRecommendations
            }

            val mappedCharacters = charactersDeferred.await().mapNotNull { jChar ->
                val character = jChar.character ?: return@mapNotNull null
                Character(
                    id = character.malId,
                    name = character.name,
                    image = character.images?.jpg?.largeImageUrl ?: character.images?.jpg?.imageUrl,
                    banner = media.banner ?: media.cover,
                    role = jChar.role ?: "",
                    isFav = false,
                    voiceActor = jChar.voiceActors?.mapNotNull { va ->
                        va.person?.let { person ->
                            Author(
                                id = person.malId,
                                name = person.name,
                                image = person.images?.jpg?.largeImageUrl ?: person.images?.jpg?.imageUrl,
                                role = va.language
                            )
                        }
                    }?.let { ArrayList(it) }
                )
            }
            if (mappedCharacters.isNotEmpty()) media.characters = ArrayList(mappedCharacters.distinctBy { it.id })

            val mappedStaff = staffDeferred.await().mapNotNull { staff ->
                val person = staff.person ?: return@mapNotNull null
                Author(
                    id = person.malId,
                    name = person.name,
                    image = person.images?.jpg?.largeImageUrl ?: person.images?.jpg?.imageUrl,
                    role = staff.positions?.joinToString(", ")
                )
            }
            if (mappedStaff.isNotEmpty()) {
                media.staff = ArrayList(((media.staff ?: arrayListOf()) + mappedStaff).distinctBy { it.id })
            }

            mapJikanReviews(media, reviewsDeferred.await(), true, malId)
        }
    }

    private fun launchBackgroundEnrichment(media: Media) {
        val malId = media.idMAL ?: return
        val isAnime = media.anime != null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fetchRelationCovers(media, isAnime)

                enrichRecommendationDetails(media, isAnime)

                this@MediaDetailsViewModel.media.postValue(media)
            } catch (_: Exception) {}
        }
    }

    private suspend fun enrichRecommendationDetails(media: Media, isAnime: Boolean) {
        val recs = media.recommendations?.take(15) ?: return
        val recsToEnrich = recs.filter { rec ->
            rec.meanScore == null || rec.meanScore == 0 || rec.anime?.totalEpisodes == null
        }
        if (recsToEnrich.isEmpty()) return
        supervisorScope {
            val deferreds = recsToEnrich.map { rec ->
                async {
                    try {
                        val recMalId = rec.idMAL ?: return@async
                        val node = MAL.query.getAnimeDetails(recMalId)
                        val coverUrl: String?
                        val score: Int?
                        val statusStr: String?
                        val episodesCount: Int?
                        if (node != null) {
                            coverUrl = node.mainPicture?.large ?: node.mainPicture?.medium
                            score = ((node.mean ?: 0f) * 10f).toInt()
                            statusStr = node.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                            episodesCount = node.numEpisodes
                        } else {
                            val jikanNode = MAL.jikan.getAnimeById(recMalId)
                            coverUrl = jikanNode?.images?.jpg?.largeImageUrl ?: jikanNode?.images?.jpg?.imageUrl
                            score = jikanNode?.score?.let { (it * 10f).toInt() }
                            statusStr = jikanNode?.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                            episodesCount = jikanNode?.episodes
                        }
                        if (coverUrl != null) rec.cover = rec.cover ?: coverUrl
                        if (score != null) rec.meanScore = score
                        if (statusStr != null) rec.status = statusStr
                        if (episodesCount != null) {
                            rec.anime?.totalEpisodes = episodesCount
                        }
                    } catch (_: Exception) {}
                }
            }
            deferreds.forEach { it.await() }
        }
        media.recommendations = ArrayList(recs)
    }

    private fun mapJikanReviews(
        media: Media,
        jikanReviews: List<ani.dantotsu.connections.mal.JikanReview>,
        isAnime: Boolean,
        malId: Int
    ) {
        if (jikanReviews.isEmpty()) return
        val mapped = jikanReviews.mapNotNull { review ->
            val reviewText = review.review ?: return@mapNotNull null
            val summary = if (reviewText.length > 200) reviewText.take(200) + "…" else reviewText
            val userName = review.user?.username ?: "Anonymous"
            val userAvatar = review.user?.images?.jpg?.imageUrl
            val createdAt = try {
                java.time.Instant.parse(review.date ?: "").epochSecond.toInt()
            } catch (_: Exception) { 0 }
            ani.dantotsu.connections.anilist.api.Query.Review(
                id = review.malId,
                mediaId = malId,
                mediaType = "ANIME",
                summary = summary,
                body = reviewText,
                rating = review.reactions?.overall ?: 0,
                ratingAmount = review.reactions?.overall ?: 0,
                userRating = "NO_VOTE",
                score = (review.score ?: 0) * 10,
                private = false,
                siteUrl = review.url ?: "https://myanimelist.net",
                createdAt = createdAt,
                updatedAt = null,
                user = ani.dantotsu.connections.anilist.api.User(
                    id = review.malId,
                    name = userName,
                    avatar = ani.dantotsu.connections.anilist.api.UserAvatar(
                        large = userAvatar,
                        medium = userAvatar
                    ),
                    bannerImage = null,
                    isFollowing = null,
                    isFollower = null,
                    options = null,
                    mediaListOptions = null,
                    favourites = null,
                    statistics = null,
                    unreadNotificationCount = null,
                )
            )
        }
        if (mapped.isNotEmpty()) {
            media.review = ArrayList(mapped.take(5))
        }
    }

    private suspend fun fetchRelationCovers(media: Media, isAnime: Boolean) {
        val relationsToFetch = mutableListOf<Media>()
        media.prequel?.let { if (it.cover == null) relationsToFetch.add(it) }
        media.sequel?.let { if (it.cover == null) relationsToFetch.add(it) }
        media.relations?.forEach { rel ->
            if (rel.cover == null && !relationsToFetch.contains(rel)) relationsToFetch.add(rel)
        }
        if (relationsToFetch.isEmpty()) return
        supervisorScope {
            val deferreds = relationsToFetch.take(8).map { rel ->
                async {
                    try {
                        val relMalId = rel.idMAL ?: return@async
                        val node = MAL.query.getAnimeDetails(relMalId)
                        val coverUrl: String?
                        val score: Int?
                        val statusStr: String?
                        val episodesCount: Int?
                        var resolvedFmt: String? = null
                        if (node != null) {
                            coverUrl = node.mainPicture?.large ?: node.mainPicture?.medium
                            score = ((node.mean ?: 0f) * 10f).toInt()
                            statusStr = node.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                            episodesCount = node.numEpisodes
                            resolvedFmt = node.mediaType?.uppercase(java.util.Locale.US)
                        } else {
                            val jikanNode = MAL.jikan.getAnimeById(relMalId)
                            coverUrl = jikanNode?.images?.jpg?.largeImageUrl ?: jikanNode?.images?.jpg?.imageUrl
                            score = jikanNode?.score?.let { (it * 10f).toInt() }
                            statusStr = jikanNode?.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                            episodesCount = jikanNode?.episodes
                            resolvedFmt = jikanNode?.type?.uppercase(java.util.Locale.US)
                        }
                        if (coverUrl != null) {
                            rel.cover = coverUrl
                            rel.banner = coverUrl
                        }
                        if (score != null && rel.meanScore == null) rel.meanScore = score
                        if (statusStr != null && rel.status == null) rel.status = statusStr
                        if (episodesCount != null) {
                            rel.anime?.totalEpisodes = episodesCount
                        }
                        if (resolvedFmt != null) {
                            rel.format = resolvedFmt
                            val rawRelation = rel.relation?.substringBefore("
") ?: ""
                            if (rawRelation.isNotEmpty()) rel.relation = "$rawRelation
$resolvedFmt"
                        }
                    } catch (_: Exception) {}
                }
            }
            deferreds.forEach { it.await() }
        }
    }

    fun setMedia(m: Media) {
        media.postValue(m)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (m.idIMDB == null) {
                    m.idIMDB = if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                        m.idMAL?.let { ani.dantotsu.others.IdMappers.getImdbIdFromMal(it) }
                    } else {
                        ani.dantotsu.others.IdMappers.getImdbId(m.id)
                    }
                }
            } catch (e: Exception) {
                ani.dantotsu.util.Logger.log(e)
            }
        }
    }

    val responses = MutableLiveData<List<ShowResponse>?>(null)


    //Anime
    private val kitsuEpisodes: MutableLiveData<Map<String, Episode>> =
        MutableLiveData<Map<String, Episode>>(null)

    fun getKitsuEpisodes(): LiveData<Map<String, Episode>> = kitsuEpisodes
    suspend fun loadKitsuEpisodes(s: Media, force: Boolean = false) {
        tryWithSuspend {
            if (kitsuEpisodes.value == null || force) kitsuEpisodes.postValue(Kitsu.getKitsuEpisodesDetails(s))
        }
    }

    private val anifyEpisodes: MutableLiveData<Map<String, Episode>> =
        MutableLiveData<Map<String, Episode>>(null)

    fun getAnifyEpisodes(): LiveData<Map<String, Episode>> = anifyEpisodes
    suspend fun loadAnifyEpisodes(s: Media, force: Boolean = false) {
        tryWithSuspend {
            if (anifyEpisodes.value == null || force) {
                val anilistId = s.id.takeIf { it != 0 }
                val malId = s.idMAL
                var episodes = Anify.fetchAndParseMetadata(anilistId = anilistId, malId = malId)
                if (episodes.isEmpty()) {
                    val tmdbId = s.idTMDB?.toIntOrNull()
                        ?: runCatching { IdMappers.getIds(s.id)?.tmdbId }.getOrNull()
                    if (tmdbId != null && tmdbId > 0) {
                        val tmdbEpisodes = TmdbService.getEpisodeDetails(tmdbId)
                        if (!tmdbEpisodes.isNullOrEmpty()) {
                            episodes = tmdbEpisodes
                        }
                    }
                }
                anifyEpisodes.postValue(episodes)
            }
        }
    }
    suspend fun loadAnifyEpisodes(s: Int, force: Boolean = false) {
        tryWithSuspend {
            if (anifyEpisodes.value == null || force) anifyEpisodes.postValue(Anify.fetchAndParseMetadata(anilistId = s, malId = null))
        }
    }

    private val fillerEpisodes: MutableLiveData<Map<String, Episode>> =
        MutableLiveData<Map<String, Episode>>(null)

    fun getFillerEpisodes(): LiveData<Map<String, Episode>> = fillerEpisodes
    suspend fun loadFillerEpisodes(s: Media, force: Boolean = false) {
        tryWithSuspend {
            if (fillerEpisodes.value == null || force) fillerEpisodes.postValue(
                Jikan.getEpisodes(
                    s.idMAL ?: return@tryWithSuspend
                )
            )
        }
    }

    var watchSources: WatchSources? = null

    private val episodes = MutableLiveData<MutableMap<Int, MutableMap<String, Episode>>>(null)
    private val epsLoaded = mutableMapOf<Int, MutableMap<String, Episode>>()
    fun getEpisodes(): LiveData<MutableMap<Int, MutableMap<String, Episode>>> = episodes
    fun invalidateSource(sourceIndex: Int) {
        epsLoaded.remove(sourceIndex)
    }

    suspend fun loadEpisodes(media: Media, i: Int, invalidate: Boolean = false) {
        val isOffline = watchSources?.get(i) is OfflineAnimeParser
        val current = epsLoaded[i]
        if (current.isNullOrEmpty() || invalidate || isOffline) {
            val loaded = watchSources?.loadEpisodesFromMedia(
                i = i,
                media = media,
                invalidate = invalidate,
                onCachedLoaded = { cached ->
                    epsLoaded[i] = cached
                    episodes.postValue(epsLoaded)
                }
            ) ?: mutableMapOf()
            if (loaded.isNotEmpty() || invalidate) {
                epsLoaded[i] = loaded
            }
        }
        episodes.postValue(epsLoaded)
    }

    suspend fun forceLoadEpisode(media: Media, i: Int) {
        val loaded = watchSources?.loadEpisodesFromMedia(i, media, true) ?: mutableMapOf()
        if (loaded.isNotEmpty()) {
            epsLoaded[i] = loaded
        }
        episodes.postValue(epsLoaded)
    }

    suspend fun overrideEpisodes(i: Int, source: ShowResponse, id: Int) {
        if (source.sAnime == null) {
            source.sAnime = SAnime.create().apply {
                url = source.link
                title = source.name
                thumbnail_url = source.coverUrl.url
            }
        }
        watchSources?.saveResponse(i, id, source)
        epsLoaded[i] =
            watchSources?.loadEpisodes(i, source.link, source.extra, source.sAnime) ?: return
        episodes.postValue(epsLoaded)
    }

    private var episode = MutableLiveData<Episode?>(null)
    fun getEpisode(): LiveData<Episode?> = episode

    suspend fun loadEpisodeVideos(ep: Episode, i: Int, post: Boolean = true) {
        val link = ep.link ?: return
        if (!ep.allStreams || ep.extractors.isNullOrEmpty()) {
            val existingExtractors = ep.extractors?.toMutableList() ?: mutableListOf()
            val list = mutableListOf<VideoExtractor>()
            ep.extractors = list
            watchSources?.get(i)?.apply {
                if (!post && !allowsPreloading) return@apply
                ep.sEpisode?.let {
                    loadByVideoServers(link, ep.extra, it) { extractor ->
                        if (extractor.videos.isNotEmpty()) {
                            list.add(extractor)
                            ep.extractorCallback?.invoke(extractor)
                        }
                    }
                }
                ep.extractorCallback = null
                if (list.isNotEmpty())
                    ep.allStreams = true
                else if (existingExtractors.isNotEmpty())
                    ep.extractors = existingExtractors
            }
        }


        if (post) {
            episode.postValue(ep)
            MainScope().launch(Dispatchers.Main) {
                episode.value = null
            }
        }
    }

    val timeStamps = MutableLiveData<List<AniSkip.Stamp>?>()
    private val timeStampsMap: MutableMap<Int, List<AniSkip.Stamp>?> = mutableMapOf()
    suspend fun loadTimeStamps(
        malId: Int?,
        episodeNum: Int?,
        duration: Long,
        useProxyForTimeStamps: Boolean,
        extensionTimestamps: List<eu.kanade.tachiyomi.animesource.model.TimeStamp> = emptyList()
    ) {
        episodeNum ?: return
        if (timeStampsMap.containsKey(episodeNum) && !timeStampsMap[episodeNum].isNullOrEmpty()) {
            return timeStamps.postValue(timeStampsMap[episodeNum])
        }
        if (duration <= 0 && extensionTimestamps.isEmpty()) return

        // Extension timestamps take priority; fall back to AniSkip when the extension has none
        val result: List<AniSkip.Stamp>? = if (extensionTimestamps.isNotEmpty()) {
            extensionTimestamps.map { it.toAniSkipStamp() }
        } else {
            var stamps: List<AniSkip.Stamp>? = null
            // 1. Try Live AniSkip API
            if (malId != null) {
                stamps = AniSkip.getResult(malId, episodeNum, duration, useProxyForTimeStamps)
            }
            // 2. Try Offline AniSkip CSV Dump DB
            if (stamps.isNullOrEmpty() && malId != null) {
                val ctx = currContext()
                if (ctx != null) {
                    stamps = AniskipCsvFallback.getInstance(ctx).lookup(malId, episodeNum)
                }
            }
            // 3. Try IntroDB API Fallback
            if (stamps.isNullOrEmpty()) {
                val mediaVal = media.value
                val imdbId = mediaVal?.idIMDB ?: mediaVal?.id?.let { runCatching { IdMappers.getImdbId(it) }.getOrNull() }
                val tmdbId = mediaVal?.idTMDB?.toIntOrNull() ?: mediaVal?.id?.let { runCatching { IdMappers.getIds(it)?.tmdbId }.getOrNull() }
                stamps = IntroDbService.getSkipTimes(imdbId = imdbId, tmdbId = tmdbId, episode = episodeNum.toString())
            }
            stamps
        }
        if (result != null || duration > 0) {
            timeStampsMap[episodeNum] = result
        }
        timeStamps.postValue(result)
    }

    private fun eu.kanade.tachiyomi.animesource.model.TimeStamp.toAniSkipStamp(): AniSkip.Stamp {
        val skipType = when (type) {
            eu.kanade.tachiyomi.animesource.model.ChapterType.Opening -> "op"
            eu.kanade.tachiyomi.animesource.model.ChapterType.Ending -> "ed"
            eu.kanade.tachiyomi.animesource.model.ChapterType.Recap -> "recap"
            eu.kanade.tachiyomi.animesource.model.ChapterType.MixedOp -> "mixed-op"
            eu.kanade.tachiyomi.animesource.model.ChapterType.Other ->
                name.lowercase().replace(" ", "-").ifEmpty { "other" }
        }
        return AniSkip.Stamp(
            interval = AniSkip.AniSkipInterval(start, end),
            skipType = skipType,
            skipId = name,
            // episodeLength represents total episode duration; use 0.0 as a sentinel since
            // extension timestamps don't carry the full episode length
            episodeLength = 0.0
        )
    }

    suspend fun loadEpisodeSingleVideo(
        ep: Episode,
        selected: Selected,
        post: Boolean = true,
        selectedServerName: String? = null
    ): Boolean {

        val server = selectedServerName ?: selected.server ?: return false
        val link = ep.link ?: return false

        if (ep.extractors?.find{ it.server.name == server } == null) {
            Log.d("StreamResolver", "Loading Video Server for episode: ${ep.number}, selected server: $server")
            if(ep.extractors == null){
                ep.extractors = mutableListOf(watchSources?.get(selected.sourceIndex)?.let {
                    selected.sourceIndex = selected.sourceIndex
                    if (!post && !it.allowsPreloading) null
                    else ep.sEpisode?.let { it1 ->
                        it.loadSingleVideoServer(
                            server, link, ep.extra,
                            it1, post
                        )
                    }
                } ?: return false)
            }
            else{
                ep.extractors!!.add(watchSources?.get(selected.sourceIndex)?.let {
                    selected.sourceIndex = selected.sourceIndex
                    if (!post && !it.allowsPreloading) null
                    else ep.sEpisode?.let { it1 ->
                        it.loadSingleVideoServer(
                            server, link, ep.extra,
                            it1, post
                        )
                    }
                } ?: return false)
            }
            //ep.extractors?.forEach { Log.d("StreamResolver", "Extractor episode ${ep.number}: ${it.server.name}") }
            ep.allStreams = false
        }
        if (post) {
            episode.postValue(ep)
            MainScope().launch(Dispatchers.Main) {
                episode.value = null
            }
        }
        return true
    }

    fun setEpisode(ep: Episode?, who: String) {
        Logger.log("set episode ${ep?.number} - $who")
        episode.postValue(ep)
        MainScope().launch(Dispatchers.Main) {
            episode.value = null
        }
    }

    val epChanged = MutableLiveData(true)
    fun onEpisodeClick(
        media: Media,
        i: String = "",
        manager: FragmentManager,
        launch: Boolean = true,
        prevEp: String? = null,
        episodes: ArrayList<String> = arrayListOf() // episodes to view
    ) {
        Handler(Looper.getMainLooper()).post {
            if (manager.findFragmentByTag("dialog") == null && !manager.isDestroyed && !manager.isStateSaved) {
                if(episodes.isEmpty()){
                    episodes.add(i)
                }
                for (ep in episodes){
                    if (media.anime?.episodes?.getEpisode(ep) == null) {
                        snackString(currContext()?.getString(R.string.episode_not_found, ep))
                        return@post
                    }
                }
                media.selected = this.loadSelected(media)
                val selector =
                    SelectorDialogFragment.newInstance(
                        media.selected!!.server,
                        launch,
                        prevEp,
                        episodes
                    )
                try {
                    val ft = manager.beginTransaction()
                    ft.add(selector, "dialog")
                    ft.commitAllowingStateLoss()
                } catch (e: Exception) {
                    Logger.log(e)
                }
            }
        }
    }

    private val fetchedOnlineSubtitles = mutableMapOf<String, List<Any>>()

    fun saveFetchedSubtitles(id: String, subs: List<Any>) {
        fetchedOnlineSubtitles[id] = subs
    }

    fun getFetchedSubtitles(id: String): List<Any>? {
        return fetchedOnlineSubtitles[id]
    }

    fun clearFetchedSubtitles(id: String) {
        fetchedOnlineSubtitles.remove(id)
    }

    private val localSubtitlesMap = mutableMapOf<String, MutableList<Any>>()

    fun saveLocalSubtitle(id: String, sub: Any) {
        val list = localSubtitlesMap.getOrPut(id) { mutableListOf() }
        val isDuplicate = list.any { existing ->
            existing is ani.dantotsu.parsers.Subtitle &&
            sub is ani.dantotsu.parsers.Subtitle &&
            existing.file.url == sub.file.url
        }
        if (!isDuplicate) list.add(sub)
    }

    fun getLocalSubtitles(id: String): List<Any> {
        return localSubtitlesMap[id] ?: emptyList()
    }

    fun removeLocalSubtitle(id: String, sub: Any) {
        val list = localSubtitlesMap[id] ?: return
        list.removeAll { existing ->
            if (existing is ani.dantotsu.parsers.Subtitle && sub is ani.dantotsu.parsers.Subtitle) {
                existing.file.url == sub.file.url
            } else {
                existing == sub
            }
        }
        if (list.isEmpty()) {
            localSubtitlesMap.remove(id)
        }
    }

    fun clearLocalSubtitles(id: String) {
        localSubtitlesMap.remove(id)
    }

    // Watch Order and News integration
    data class WatchOrderItem(
        val id: String,
        val anilistId: String,
        val image: String,
        val name: String,
        val relationType: String,
        val isCurrent: Boolean = false,
        val airDate: String = "",
        val mediaType: String = "",
        val episodes: String = "",
        val rating: String = ""
    ) : java.io.Serializable

    data class NewsItem(
        val title: String,
        val url: String,
        val date: java.util.Date?
    ) : java.io.Serializable

    suspend fun getWatchOrder(media: Media): List<WatchOrderItem> {
        return tryWithSuspend {
            val headers = mapOf(
                "Referer" to "https://chiaki.site/?/tools/watch_order",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "X-Requested-With" to "XMLHttpRequest"
            )

            val malId = media.idMAL ?: ani.dantotsu.others.IdMappers.getMalId(media.id)
            if (media.idMAL == null && malId != null) {
                media.idMAL = malId
            }
            var targetId = malId?.toString()

            if (targetId == null) {
                val searchName = media.userPreferredName.ifEmpty { media.mainName() }
                val encodedTerm = java.net.URLEncoder.encode(searchName, "UTF-8")
                val searchUrl = "https://chiaki.site/?/tools/autocomplete_series&term=$encodedTerm"
                val res = client.get(searchUrl, headers = headers).text
                val json = org.json.JSONArray(res)
                if (json.length() > 0) {
                    targetId = json.getJSONObject(0).opt("id")?.toString()
                }
            }

            if (targetId == null) return@tryWithSuspend emptyList()

            val orderUrl = "https://chiaki.site/?/tools/watch_order/id/$targetId"
            val html = client.get(orderUrl, headers = headers).text
            val doc = org.jsoup.Jsoup.parse(html)

            val rows = doc.select("tr[data-id]")
            val relationsMap = media.relations?.associateBy { it.id.toString() } ?: emptyMap()

            // Parse relations relative to targetId from data-related JSON attribute
            val targetRow = rows.find { it.attr("data-id") == targetId }
            val targetRelatedJson = targetRow?.attr("data-related") ?: "{}"
            val relatedMap = try {
                val jsonObj = org.json.JSONObject(targetRelatedJson)
                val map = mutableMapOf<String, String>()
                jsonObj.keys().forEach { key ->
                    map[key] = jsonObj.getString(key)
                }
                map
            } catch (_: Exception) {
                emptyMap<String, String>()
            }

            rows.mapIndexedNotNull { index, row ->
                val title = row.select("span.wo_title").text().trim()
                    .ifEmpty { row.select(".wo_title").text().trim() }
                if (title.isEmpty() || title.equals("Unknown title", ignoreCase = true)) return@mapIndexedNotNull null

                val id = row.attr("data-id").ifEmpty { targetId }
                val anilistId = row.attr("data-anilist-id")

                // 1. Cover image from style background-image
                val style = row.select("div.wo_avatar_big").attr("style")
                val imgMatch = Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)
                var imageUrl = imgMatch?.groupValues?.get(1)?.let { path ->
                    when {
                        path.startsWith("http") -> path
                        path.startsWith("/") -> "https://chiaki.site$path"
                        else -> "https://chiaki.site/$path"
                    }
                } ?: ""

                if (imageUrl.isEmpty()) {
                    if (anilistId == media.id.toString()) {
                        imageUrl = media.cover ?: ""
                    } else if (relationsMap.containsKey(anilistId)) {
                        imageUrl = relationsMap[anilistId]?.cover ?: ""
                    }
                }

                // 2. Metadata text: Air Date | Media Type | Episodes | Rating
                val metaText = row.select("span.uk-text-muted.uk-text-small").text().trim()
                    .ifEmpty { row.select(".wo_meta").text().trim() }
                val parts = metaText.split('|').map { it.trim() }
                val airDate = if (parts.isNotEmpty()) parts[0] else ""
                val format = if (parts.size > 1) parts[1] else ""
                val eps = if (parts.size > 2) parts[2].substringBefore("★").substringBefore("(").trim() else ""
                val rating = ""

                // 3. Is current anime
                val cleanId = id.trim()
                val cleanAnilistId = anilistId.trim()
                val currentMalId = media.idMAL ?: malId
                val isCurrent = (cleanAnilistId.isNotEmpty() && cleanAnilistId == media.id.toString()) ||
                        (cleanId.isNotEmpty() && currentMalId != null && cleanId == currentMalId.toString()) ||
                        (cleanId.isNotEmpty() && targetId != null && cleanId == targetId.trim() && (currentMalId == null || currentMalId.toString() == targetId.trim())) ||
                        (title.isNotEmpty() && (title.equals(media.userPreferredName, ignoreCase = true) ||
                                title.equals(media.mainName(), ignoreCase = true) ||
                                title.equals(media.name, ignoreCase = true) ||
                                title.equals(media.nameRomaji, ignoreCase = true)))

                // 4. Relation type
                val relationType = when {
                    isCurrent -> "Selected"
                    relatedMap.containsKey(id) -> relatedMap[id]!!
                    relationsMap.containsKey(anilistId) -> {
                        relationsMap[anilistId]?.relation?.replace("_", " ")?.lowercase()
                            ?.split(" ")?.joinToString(" ") { it.replaceFirstChar(Char::titlecase) } ?: ""
                    }
                    index == 0 -> "Main Story"
                    format.equals("TV", ignoreCase = true) -> "Sequel"
                    format.equals("OVA", ignoreCase = true) || format.equals("Special", ignoreCase = true) -> "Side Story"
                    format.equals("Movie", ignoreCase = true) -> "Movie"
                    else -> format.ifEmpty { "Entry" }
                }

                WatchOrderItem(
                    id = id,
                    anilistId = anilistId,
                    image = imageUrl,
                    name = title,
                    relationType = relationType,
                    isCurrent = isCurrent,
                    airDate = airDate,
                    mediaType = format,
                    episodes = eps,
                    rating = rating
                )
            }
        } ?: emptyList()
    }


    suspend fun getAnimeNews(media: Media): List<NewsItem> {
        return tryWithSuspend {
            var malId = media.idMAL
            if (malId == null || malId == 0) {
                malId = ani.dantotsu.others.IdMappers.getMalId(media.id)
            }
            if (malId == null || malId == 0) {
                malId = runCatching {
                    Anilist.query.getMedia(media.id)?.idMAL
                }.getOrNull()
                if (malId != null && malId != 0) {
                    media.idMAL = malId
                }
            }
            val targetId = malId?.takeIf { it != 0 } ?: return@tryWithSuspend emptyList()

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Accept" to "application/json, text/plain, */*"
            )

            suspend fun fetchKuroiruNews(id: Int): List<NewsItem> {
                val jsonStr = runCatching {
                    val res = client.get("https://kuroiru.co/api/anime/$id", headers = headers)
                    if (res.isSuccessful) res.text else null
                }.getOrNull() ?: return emptyList()

                val root = runCatching {
                    Json.parseToJsonElement(jsonStr).jsonObject
                }.getOrNull() ?: return emptyList()
                val newsArr = root["news"]?.jsonArray ?: return emptyList()
                val list = mutableListOf<NewsItem>()
                for (item in newsArr) {
                    val itemObj = item.jsonObject
                    val timeMs = itemObj["time"]?.jsonPrimitive?.longOrNull ?: 0L
                    val rawTitle = itemObj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                    val title = org.jsoup.parser.Parser.unescapeEntities(rawTitle, false)
                    val rawLink = itemObj["link"]?.jsonPrimitive?.contentOrNull ?: ""
                    val link = normalizeKuroiruLink(rawLink)
                    if (title.isNotEmpty() && link.isNotEmpty()) {
                        list.add(
                            NewsItem(
                                title = title,
                                url = link,
                                date = if (timeMs > 0) java.util.Date(timeMs * 1000L) else null
                            )
                        )
                    }
                }
                return list
            }

            var result = fetchKuroiruNews(targetId)
            if (result.isEmpty()) {
                val candidateIds = mutableListOf<Int>()
                media.prequel?.idMAL?.let { candidateIds.add(it) }
                media.relations?.forEach { rel ->
                    rel.idMAL?.let { candidateIds.add(it) }
                }
                for (candId in candidateIds) {
                    if (candId != targetId && candId != 0) {
                        result = fetchKuroiruNews(candId)
                        if (result.isNotEmpty()) break
                    }
                }
            }

            result.sortedByDescending { it.date }
        } ?: emptyList()
    }

    private fun normalizeKuroiruLink(rawLink: String): String {
        val link = rawLink.trim()
        if (link.isEmpty()) return ""
        if (link.startsWith("http://", ignoreCase = true) ||
            link.startsWith("https://", ignoreCase = true)
        ) return link
       
        val digitId = link.removePrefix(".")
        if (digitId.isNotEmpty() && digitId.all { it.isDigit() }) {
            return "https://www.animenewsnetwork.com/news/.$digitId"
        }
        return link
    }

}
