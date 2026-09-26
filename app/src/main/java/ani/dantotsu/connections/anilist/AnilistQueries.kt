package ani.dantotsu.connections.anilist

import android.util.Base64
import ani.dantotsu.R
import ani.dantotsu.checkGenreTime
import ani.dantotsu.checkId
import ani.dantotsu.connections.anilist.Anilist.authorRoles
import ani.dantotsu.connections.anilist.Anilist.executeQuery
import ani.dantotsu.App
import ani.dantotsu.database.AnimeStateDatabase
import ani.dantotsu.database.AnimeStateRepository
import ani.dantotsu.database.applyTo
import ani.dantotsu.database.toAnimeStateRecord
import ani.dantotsu.connections.anilist.api.FuzzyDate
import ani.dantotsu.connections.anilist.api.MediaEdge
import ani.dantotsu.connections.anilist.api.MediaList
import ani.dantotsu.connections.anilist.api.Page
import ani.dantotsu.connections.anilist.api.Query
import ani.dantotsu.currContext
import ani.dantotsu.isOnline
import ani.dantotsu.logError
import ani.dantotsu.media.Author
import ani.dantotsu.media.Character
import ani.dantotsu.media.Media
import ani.dantotsu.media.Studio
import ani.dantotsu.others.MalScraper
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.tryWithSuspend
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.Calendar
import kotlin.system.measureTimeMillis

class AnilistQueries {
    companion object {

        const val ITEMS_PER_PAGE = 25
    }

    private data class MissingSequelsCache(
        val sourceIds: Set<Int>,
        val media: ArrayList<Media>,
        val cachedAt: Long
    ) : Serializable

    suspend fun getMedia(id: Int, mal: Boolean = false, type: String? = null): Media? {
        val typeArg = if (type != null) "type: $type," else ""
        val response = executeQuery<Query.Media>(
            """{Media($typeArg${if (!mal) "id:" else "idMal:"}$id){id idMal status chapters episodes nextAiringEpisode{episode}type meanScore format bannerImage coverImage{large}title{english romaji userPreferred}}}""",
            force = true
        )
        val fetchedMedia = response?.data?.media ?: return null
        val media = Media(fetchedMedia)
        val localStateRepository = AnimeStateRepository(AnimeStateDatabase.get(App.instance!!))
        val localState = localStateRepository.get(media.id)
        if (localState != null) {
            localState.applyTo(media)
        } else {
            localStateRepository.upsert(media.toAnimeStateRecord())
        }
        return media
    }

    suspend fun getMediaList(ids: List<Int>): List<Media>? {
        if (ids.isEmpty()) return null

        val idsString = ids.joinToString(",")
        val response = executeQuery<Query.MediaList>(
            """{Page(page:1,perPage:50){media(id_in:[${idsString}],isAdult:false){id idMal type isAdult popularity status(version:2) chapters episodes nextAiringEpisode{episode} meanScore format bannerImage coverImage{large} title{english romaji userPreferred} startDate{year}}}}""",
            force = true
        )
        val fetchedMediaList = response?.data?.page?.media ?: return null
        val localStateRepository = AnimeStateRepository(AnimeStateDatabase.get(App.instance!!))
        return fetchedMediaList.map {
            val media = Media(it)
            val localState = localStateRepository.get(media.id)
            if (localState != null) {
                localState.applyTo(media)
            } else {
                localStateRepository.upsert(media.toAnimeStateRecord())
            }
            media
        }
    }

    suspend fun mediaDetails(media: Media): Media {
        media.cameFromContinue = false
        runBlocking {
            val anilist = async {
                var response =
                    executeQuery<Query.Media>(fullMediaInformation(media.id), force = true)
                if (response != null) {
                    fun parse() {
                        val fetchedMedia = response?.data?.media ?: return
                        val user = response?.data?.page
                        if (fetchedMedia.idMal != null) media.idMAL = fetchedMedia.idMal
                        media.isFav = false
                        media.source = fetchedMedia.source?.toString()?.replace("_", " ")?.lowercase()?.split(" ")?.joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
                        media.countryOfOrigin = fetchedMedia.countryOfOrigin
                        media.format = fetchedMedia.format?.toString()
                        media.cover = fetchedMedia.coverImage?.large ?: media.cover
                        media.banner = fetchedMedia.bannerImage ?: media.banner
                        media.favourites = fetchedMedia.favourites
                        media.popularity = fetchedMedia.popularity
                        media.startDate = fetchedMedia.startDate
                        media.endDate = fetchedMedia.endDate
                        media.streamingEpisodes = fetchedMedia.streamingEpisodes
                        if (fetchedMedia.genres != null) {
                            media.genres = arrayListOf()
                            fetchedMedia.genres?.forEach { i ->
                                media.genres.add(i)
                            }
                        }

                        media.trailer = fetchedMedia.trailer?.let { i ->
                            if (i.site != null && i.site.toString() == "youtube")
                                i.id.toString().trim('"')
                            else null
                        }

                        fetchedMedia.synonyms?.apply {
                            media.synonyms = arrayListOf()
                            this.forEach { i ->
                                media.synonyms.add(
                                    i
                                )
                            }
                        }

                        fetchedMedia.tags?.apply {
                            media.tags = arrayListOf()
                            media.tagsIsSpoiler = arrayListOf()
                            this.forEach { i ->
                                media.tags.add("${i.name} : ${i.rank.toString()}%")
                                media.tagsIsSpoiler.add(i.isMediaSpoiler == true)
                            }
                        }

                        media.description = fetchedMedia.description.toString()

                        if (fetchedMedia.characters != null) {
                            media.characters = arrayListOf()
                            fetchedMedia.characters?.edges?.forEach { i ->
                                i.node?.apply {
                                    media.characters?.add(
                                        Character(
                                            id = id,
                                            name = i.node?.name?.userPreferred,
                                            image = i.node?.image?.medium,
                                            banner = media.banner ?: media.cover,
                                            isFav = i.node?.isFavourite ?: false,
                                            role = when (i.role.toString()) {
                                                "MAIN" -> currContext()?.getString(R.string.main_role)
                                                    ?: "MAIN"

                                                "SUPPORTING" -> currContext()?.getString(R.string.supporting_role)
                                                    ?: "SUPPORTING"

                                                else -> i.role.toString()
                                            },
                                            voiceActor = i.voiceActors?.map {
                                                Author(
                                                    id = it.id,
                                                    name = it.name?.userPreferred,
                                                    image = it.image?.large,
                                                    role = it.languageV2
                                                )
                                            }?.distinctBy { it.id }?.let { ArrayList(it) }
                                        )
                                    )
                                }
                            }
                        }
                        if (fetchedMedia.staff != null) {
                            media.staff = arrayListOf()
                            fetchedMedia.staff?.edges?.forEach { i ->
                                i.node?.apply {
                                    media.staff?.add(
                                        Author(
                                            id = id,
                                            name = i.node?.name?.userPreferred,
                                            image = i.node?.image?.large,
                                            role = when (i.role.toString()) {
                                                "MAIN" -> currContext()?.getString(R.string.main_role)
                                                    ?: "MAIN"

                                                "SUPPORTING" -> currContext()?.getString(R.string.supporting_role)
                                                    ?: "SUPPORTING"

                                                else -> i.role.toString()
                                            }
                                        )
                                    )
                                }
                            }
                        }
                        if (fetchedMedia.relations != null) {
                            media.relations = arrayListOf()
                            fetchedMedia.relations?.edges?.forEach { mediaEdge ->
                                val m = Media(mediaEdge)
                                media.relations?.add(m)
                                if (m.relation == "SEQUEL") {
                                    media.sequel =
                                        if ((media.sequel?.popularity ?: 0) < (m.popularity
                                                ?: 0)
                                        ) m else media.sequel

                                } else if (m.relation == "PREQUEL") {
                                    media.prequel =
                                        if ((media.prequel?.popularity ?: 0) < (m.popularity
                                                ?: 0)
                                        ) m else media.prequel
                                }
                            }
                            media.relations?.sortByDescending { it.popularity }
                            media.relations?.sortByDescending { it.startDate?.year }
                            media.relations?.sortBy { it.relation }
                        }
                        if (fetchedMedia.recommendations != null) {
                            media.recommendations = arrayListOf()
                            media.recommendationList = arrayListOf()
                            fetchedMedia.recommendations?.nodes?.forEach { i ->
                                media.recommendationList?.add(i)
                                i.mediaRecommendation?.apply {
                                    media.recommendations?.add(
                                        Media(this)
                                    )
                                }
                            }
                        }
                        media.stats = fetchedMedia.stats
                        media.rankings = fetchedMedia.rankings
                        if (fetchedMedia.reviews?.nodes != null) {
                            media.review = fetchedMedia.reviews!!.nodes as ArrayList<Query.Review>
                        }

                        if (media.anime != null) {
                            media.anime.episodeDuration = fetchedMedia.duration
                            media.anime.season = fetchedMedia.season?.toString()
                            media.anime.seasonYear = fetchedMedia.seasonYear

                            val studioEdges = fetchedMedia.studios?.edges
                            if (!studioEdges.isNullOrEmpty()) {
                                val mainNode = studioEdges.firstOrNull { it.isMain == true }?.node
                                    ?: studioEdges.firstOrNull { it.node?.isAnimationStudio == true }?.node
                                    ?: studioEdges.firstOrNull()?.node
                                if (mainNode != null) {
                                    media.anime.mainStudio = Studio(
                                        mainNode.id.toString(),
                                        mainNode.name ?: "N/A",
                                        mainNode.isFavourite ?: false,
                                        mainNode.favourites ?: 0,
                                        null
                                    )
                                }
                                val producerNodes = studioEdges.filter { it.isMain != true }.mapNotNull { it.node }
                                    .filter { it.id.toString() != media.anime.mainStudio?.id }
                                if (producerNodes.isNotEmpty()) {
                                    media.anime.producers = ArrayList(producerNodes.map {
                                        Studio(
                                            it.id.toString(),
                                            it.name ?: "N/A",
                                            it.isFavourite ?: false,
                                            it.favourites ?: 0,
                                            null
                                        )
                                    })
                                }
                            } else {
                                fetchedMedia.studios?.nodes?.apply {
                                    if (isNotEmpty()) {
                                        val studioNode = firstOrNull { it.isAnimationStudio == true } ?: get(0)
                                        media.anime.mainStudio = Studio(
                                            studioNode.id.toString(),
                                            studioNode.name ?: "N/A",
                                            studioNode.isFavourite ?: false,
                                            studioNode.favourites ?: 0,
                                            null
                                        )
                                    }
                                }
                                if (media.anime.mainStudio == null) {
                                    fetchedMedia.producers?.nodes?.firstOrNull { it.isAnimationStudio == true }?.let { studioNode ->
                                        media.anime.mainStudio = Studio(
                                            studioNode.id.toString(),
                                            studioNode.name ?: "N/A",
                                            studioNode.isFavourite ?: false,
                                            studioNode.favourites ?: 0,
                                            null
                                        )
                                    }
                                }
                                fetchedMedia.producers?.nodes?.apply {
                                    val remaining = filter { it.id.toString() != media.anime.mainStudio?.id }
                                    if (remaining.isNotEmpty()) {
                                        media.anime.producers = ArrayList(remaining.map {
                                            Studio(
                                                it.id.toString(),
                                                it.name ?: "N/A",
                                                it.isFavourite ?: false,
                                                it.favourites ?: 0,
                                                null
                                            )
                                        })
                                    }
                                }
                            }

                            fetchedMedia.staff?.edges?.find { authorRoles.contains(it.role?.trim()) }?.node?.let {
                                media.anime.author = Author(
                                    it.id,
                                    it.name?.userPreferred ?: "N/A",
                                    it.image?.medium,
                                    "AUTHOR"
                                )
                            }

                            media.anime.nextAiringEpisodeTime =
                                fetchedMedia.nextAiringEpisode?.airingAt?.toLong()

                            fetchedMedia.externalLinks?.forEach { i ->
                                when (i.site.lowercase()) {
                                    "youtube" -> media.anime.youtube = i.url
                                    "crunchyroll" -> media.crunchySlug =
                                        i.url?.split("/")?.getOrNull(3)

                                    "vrv" -> media.vrvId = i.url?.split("/")?.getOrNull(4)
                                }
                            }


                        if (!fetchedMedia.externalLinks.isNullOrEmpty()) {
                            media.externalLinks = ArrayList(fetchedMedia.externalLinks!!)
                        }

                        media.shareLink = fetchedMedia.siteUrl
                    }

                    if (response.data?.media != null) parse()
                    else {
                        snackString(currContext()?.getString(R.string.adult_stuff))
                        response = executeQuery(
                            fullMediaInformation(media.id),
                            force = true,
                            useToken = false
                        )
                        if (response?.data?.media != null) parse()
                        else snackString(currContext()?.getString(R.string.what_did_you_open))
                    }
                } else {
                    if (currContext()?.let { isOnline(it) } == true) {
                        snackString(currContext()?.getString(R.string.error_getting_data))
                    } else {
                    }
                }
            }
            val mal = async {
                if (media.idMAL == null) {
                    anilist.await()
                }
                if (media.idMAL == null && media.id != 0) {
                    media.idMAL = ani.dantotsu.others.IdMappers.getMalId(media.id)
                }
                if (media.idMAL != null) {
                    MalScraper.loadMedia(media)
                }
            }
            awaitAll(anilist, mal)

            // Local AnimeState is the source for persistent user progress/state.
            // Seed it once from the existing remote state so the migration is non-destructive.
            val localStateRepository = AnimeStateRepository(AnimeStateDatabase.get(App.instance!!))
            val localState = localStateRepository.get(media.id)
            if (localState != null) {
                localState.applyTo(media)
            } else if (
                media.isFav ||
                media.userProgress != null ||
                media.userStatus != null ||
                media.userScore != 0 ||
                media.userRepeat != 0
            ) {
                localStateRepository.upsert(media.toAnimeStateRecord())
            }
        }
        return media
    }

    private fun recommendationQuery(sort: String = "RATING_DESC", page: Int = 1, perPage: Int = 50): String {
        return """ Page(page: $page, perPage:$perPage) { $standardPageInformation recommendations(sort: $sort, onList: false) { rating userRating mediaRecommendation { id idMal isAdult chapters volumes format episodes nextAiringEpisode {episode} popularity meanScore format title {english romaji userPreferred } type status(version: 2) bannerImage coverImage { large } description genres tags { name isMediaSpoiler } } } } """
    }

    suspend fun getRecommendations(page: Int, perPage: Int = 50): Pair<ArrayList<Media>, Boolean> {
        val query = """{
            recRating: ${recommendationQuery("RATING_DESC", page, perPage)}
            recNew: ${recommendationQuery("ID_DESC", page, perPage)}
        }"""
        val response = executeQuery<Query.RecommendationsResponse>(query, show = true)
        val subMap = mutableMapOf<Int, Media>()
        val localStateRepository = AnimeStateRepository(AnimeStateDatabase.get(App.instance!!))

        response?.data?.recRating?.recommendations?.forEach {
            it.mediaRecommendation?.let { json ->
                val media = Media(json)
                val localState = localStateRepository.get(media.id)
                if (localState != null) {
                    localState.applyTo(media)
                }
                if (media.userStatus == null) {
                    media.relation = json.type?.toString()
                    subMap[media.id] = media
                }
            }
        }
        response?.data?.recNew?.recommendations?.forEach {
            it.mediaRecommendation?.let { json ->
                val media = Media(json)
                val localState = localStateRepository.get(media.id)
                if (localState != null) {
                    localState.applyTo(media)
                }
                if (media.userStatus == null) {
                    media.relation = json.type?.toString()
                    subMap[media.id] = media
                }
            }
        }
        val list = ArrayList(subMap.values).apply { sortByDescending { it.meanScore } }
        val hasNext = response?.data?.recRating?.pageInfo?.hasNextPage == true ||
                response?.data?.recNew?.pageInfo?.hasNextPage == true
        return Pair(list, hasNext)
    }

    suspend fun initHomePage(): Map<String, ArrayList<Media>> {
        val toShow = PrefManager.getVal<List<Boolean>>(PrefName.HomeLayout)
        val removeList = PrefManager.getCustomVal<Set<String>>("removeList", emptySet())
            .mapNotNull { it.toIntOrNull() }.toSet()
        val repository = AnimeStateRepository(AnimeStateDatabase.get(App.instance!!))

        suspend fun loadLocalMedia(states: List<ani.dantotsu.database.AnimeStateRecord>): ArrayList<Media> {
            val ids = states.map { it.animeId }.distinct()
            if (ids.isEmpty()) return arrayListOf()
            val result = arrayListOf<Media>()
            ids.chunked(50).forEach { chunk ->
                getMediaList(chunk)?.let { result.addAll(it) }
            }
            val byId = result.associateBy { it.id }
            return ArrayList(states.mapNotNull { state ->
                byId[state.animeId]?.apply {
                    state.applyTo(this)
                    cameFromContinue = true
                }
            }.filter { it.id !in removeList })
        }

        val returnMap = mutableMapOf<String, ArrayList<Media>>()
        if (toShow.getOrNull(0) == true) {
            returnMap["currentAnime"] = loadLocalMedia(repository.continueWatching())
        }
        if (toShow.getOrNull(1) == true) {
            returnMap["favoriteAnime"] = loadLocalMedia(repository.favorites())
        }
        if (toShow.getOrNull(2) == true) {
            val planned = repository.planned().map { it.copy(lastWatchedAt = it.updatedAt) }
            returnMap["currentAnimePlanned"] = loadLocalMedia(planned)
        }
        if (toShow.getOrNull(6) == true) {
            val (recommendations, _) = getRecommendations(1, 50)
            returnMap["recommendations"] = recommendations
        }
        returnMap["missingSequels"] = arrayListOf()
        returnMap["hidden"] = arrayListOf()
        return returnMap
    }
    private suspend fun bannerImage(type: String): String? {
        if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
            return if (MAL.token != null) MAL.avatar else null
        }
        val cached = BannerImage(
            PrefManager.getCustomVal("banner_${type}_url", ""),
            PrefManager.getCustomVal("banner_${type}_time", 0L)
        )
        if (!cached.url.isNullOrEmpty() && !cached.checkTime()) return cached.url
        val repository = AnimeStateRepository(AnimeStateDatabase.get(App.instance!!))
        val states = (repository.continueWatching() + repository.favorites()).distinctBy { it.animeId }
        val media = states.map { it.animeId }.chunked(50).flatMap { getMediaList(it).orEmpty() }
        val random = media.filter { !it.cover.isNullOrBlank() }.mapNotNull { it.banner ?: it.cover }.randomOrNull()
        if (random != null) {
            PrefManager.setCustomVal("banner_${type}_url", random)
            PrefManager.setCustomVal("banner_${type}_time", System.currentTimeMillis())
        }
        return random
    }
    suspend fun getBannerImages(): ArrayList<String?> {
        return coroutineScope {
            val anime = async { bannerImage("ANIME") }
            val manga = async { bannerImage("MANGA") }
            arrayListOf(anime.await(), manga.await())
        }
    }

    suspend fun getGenresAndTags(): Boolean {
        var genres: ArrayList<String>? = PrefManager.getVal<Set<String>>(PrefName.GenresList)
            .toMutableList() as ArrayList<String>?
        val adultTags = PrefManager.getVal<Set<String>>(PrefName.TagsListIsAdult).toMutableList()
        val nonAdultTags =
            PrefManager.getVal<Set<String>>(PrefName.TagsListNonAdult).toMutableList()
        var tags = if (adultTags.isEmpty() || nonAdultTags.isEmpty()) null else
            mapOf(
                true to adultTags.sortedBy { it },
                false to nonAdultTags.sortedBy { it }
            )

        if (genres.isNullOrEmpty()) {
            executeQuery<Query.GenreCollection>(
                """{GenreCollection}""",
                force = true,
                useToken = false
            )?.data?.genreCollection?.apply {
                val list = arrayListOf<String>()
                forEach {
                    list.add(it)
                }
                genres = list
                PrefManager.setVal(PrefName.GenresList, list.toSet())
            }
        }
        if (tags == null) {
            executeQuery<Query.MediaTagCollection>(
                """{ MediaTagCollection { name isAdult } }""",
                force = true
            )?.data?.mediaTagCollection?.apply {
                val adult = mutableListOf<String>()
                val good = mutableListOf<String>()
                forEach { node ->
                    if (node.isAdult == true) adult.add(node.name)
                    else good.add(node.name)
                }
                tags = mapOf(
                    true to adult,
                    false to good
                )
                PrefManager.setVal(PrefName.TagsListIsAdult, adult.toSet())
                PrefManager.setVal(PrefName.TagsListNonAdult, good.toSet())
            }
        }
        return if (!genres.isNullOrEmpty() && tags != null) {
            Anilist.genres = ArrayList(genres.sorted())
            Anilist.tags = tags
            true
        } else false
    }

    suspend fun getGenres(genres: ArrayList<String>, listener: ((Pair<String, String>) -> Unit)) {
        genres.forEach {
            val thumbnail = getGenreThumbnail(it)
            if (thumbnail != null) {
                listener.invoke(it to thumbnail.thumbnail)
            } else {
                listener.invoke(it to "")
            }
        }
    }

    private fun <K, V : Serializable> saveSerializableMap(prefKey: String, map: Map<K, V>) {
        val byteStream = ByteArrayOutputStream()

        ObjectOutputStream(byteStream).use { outputStream ->
            outputStream.writeObject(map)
        }
        val serializedMap = Base64.encodeToString(byteStream.toByteArray(), Base64.DEFAULT)
        PrefManager.setCustomVal(prefKey, serializedMap)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <K, V : Serializable> loadSerializableMap(prefKey: String): Map<K, V>? {
        try {
            val serializedMap = PrefManager.getCustomVal(prefKey, "")
            if (serializedMap.isEmpty()) return null

            val bytes = Base64.decode(serializedMap, Base64.DEFAULT)
            val byteArrayStream = ByteArrayInputStream(bytes)

            return ObjectInputStream(byteArrayStream).use { inputStream ->
                inputStream.readObject() as? Map<K, V>
            }
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun getGenreThumbnail(genre: String): Genre? {
        val genres: MutableMap<String, Genre> =
            loadSerializableMap<String, Genre>("genre_thumb")?.toMutableMap()
                ?: mutableMapOf()
        if (genres.checkGenreTime(genre)) {
            try {
                val genreQuery =
                    """{ Page(perPage: 10){media(genre:"$genre", sort: TRENDING_DESC, type: ANIME, countryOfOrigin:"JP") {id bannerImage title{english romaji userPreferred} } } }"""
                executeQuery<Query.Page>(genreQuery, force = true)?.data?.page?.media?.forEach {
                    if (genres.checkId(it.id) && it.bannerImage != null) {
                        genres[genre] = Genre(
                            genre,
                            it.id,
                            it.bannerImage!!,
                            System.currentTimeMillis()
                        )
                        saveSerializableMap("genre_thumb", genres)
                        return genres[genre]
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        } else {
            return genres[genre]
        }
        return null
    }

    suspend fun searchAnime(
        type: String,
        page: Int? = null,
        perPage: Int? = null,
        search: String? = null,
        sort: String? = null,
        genres: MutableList<String>? = null,
        tags: MutableList<String>? = null,
        status: String? = null,
        source: String? = null,
        format: String? = null,
        countryOfOrigin: String? = null,
        isAdult: Boolean = false,
        onList: Boolean? = null,
        excludedGenres: MutableList<String>? = null,
        excludedTags: MutableList<String>? = null,
        startYear: Int? = null,
        seasonYear: Int? = null,
        season: String? = null,
        id: Int? = null,
        hd: Boolean = false,
        adultOnly: Boolean = false
    ): AnimeSearchResults? {
        val variables = """{"type":"$type","isAdult":$isAdult
            ${if (adultOnly) ""","isAdult":true""" else ""}
            ${if (onList != null) ""","onList":$onList""" else ""}
            ${if (page != null) ""","page":"$page"""" else ""}
            ${if (id != null) ""","id":"$id"""" else ""}
            ${if (type == "ANIME" && seasonYear != null) ""","seasonYear":"$seasonYear"""" else ""}
                        ${if (season != null) ""","season":"$season"""" else ""}
            ${if (search != null) ""","search":"$search"""" else ""}
            ${if (source != null) ""","source":"$source"""" else ""}
            ${if (sort != null) ""","sort":"$sort"""" else ""}
            ${if (status != null) ""","status":"$status"""" else ""}
            ${if (format != null) ""","format":"${format.replace(" ", "_")}"""" else ""}
            ${if (countryOfOrigin != null) ""","countryOfOrigin":"$countryOfOrigin"""" else ""}
            ${if (genres?.isNotEmpty() == true) ""","genres":[${genres.joinToString { "\"$it\"" }}]""" else ""}
            ${
            if (excludedGenres?.isNotEmpty() == true)
                ""","excludedGenres":[${
                    excludedGenres.joinToString {
                        "\"${
                            it.replace(
                                "Not ",
                                ""
                            )
                        }\""
                    }
                }]"""
            else ""
        }
            ${if (tags?.isNotEmpty() == true) ""","tags":[${tags.joinToString { "\"$it\"" }}]""" else ""}
            ${
            if (excludedTags?.isNotEmpty() == true)
                ""","excludedTags":[${
                    excludedTags.joinToString {
                        "\"${
                            it.replace(
                                "Not ",
                                ""
                            )
                        }\""
                    }
                }]"""
            else ""
        }
            }""".prepare()
        val response =
            executeQuery<Query.Page>(searchAnimeQuery(perPage), variables, true)?.data?.page
        if (response?.media != null) {
            val responseArray = arrayListOf<Media>()
            response.media?.forEach { i ->
                val genresArr = arrayListOf<String>()
                if (i.genres != null) {
                    i.genres?.forEach { genre ->
                        genresArr.add(genre)
                    }
                }
                val media = Media(i)
                if (!hd) media.cover = i.coverImage?.large
                media.relation = null
                media.genres = genresArr
                responseArray.add(media)
            }

            val pageInfo = response.pageInfo ?: return null

            return AnimeSearchResults(
                type = type,
                perPage = perPage,
                search = search,
                sort = sort,
                isAdult = isAdult,
                onList = onList,
                genres = genres,
                excludedGenres = excludedGenres,
                tags = tags,
                excludedTags = excludedTags,
                status = status,
                source = source,
                format = format,
                countryOfOrigin = countryOfOrigin,
                startYear = startYear,
                seasonYear = seasonYear,
                season = season,
                results = responseArray,
                page = pageInfo.currentPage.toString().toIntOrNull() ?: 0,
                hasNextPage = pageInfo.hasNextPage == true,
            )
        }
        return null
    }

    suspend fun searchCharacters(page: Int, search: String?): CharacterSearchResults? {
        if (search.isNullOrBlank()) return null
        val query = """
           {
             Page(page: $page, perPage: $ITEMS_PER_PAGE) {
               $standardPageInformation
               characters(search: "$search") {
                  ${characterInformation(false)}
               }
             }
           }
        """.prepare()

        val response = executeQuery<Query.Page>(query, force = true)?.data?.page

        if (response?.characters != null) {
            val responseArray = arrayListOf<Character>()
            response.characters?.forEach { i ->
                responseArray.add(
                    Character(
                        i.id,
                        i.name?.full,
                        i.image?.medium ?: i.image?.large,
                        null,
                        null.toString(),
                        i.isFavourite ?: false,
                        i.description,
                        i.age,
                        i.gender,
                        i.dateOfBirth,
                    )
                )
            }

            val pageInfo = response.pageInfo ?: return null

            return CharacterSearchResults(
                search = search,
                results = responseArray,
                page = pageInfo.currentPage ?: 0,
                hasNextPage = pageInfo.hasNextPage == true
            )
        }
        return null
    }

    suspend fun searchStudios(page: Int, search: String?): StudioSearchResults? {
        if (search.isNullOrBlank()) return null
        val query = """
           {
             Page(page: $page, perPage: $ITEMS_PER_PAGE) {
               $standardPageInformation
               studios(search: "$search") {
                  ${studioInformation(1, 1)}
               }
             }
           }
        """.prepare()

        val response = executeQuery<Query.Page>(query, force = true)?.data?.page

        if (response?.studios != null) {
            val responseArray = arrayListOf<Studio>()
            response.studios?.forEach { i ->
                responseArray.add(
                    Studio(
                        i.id.toString(),
                        i.name ?: return null,
                        i.isFavourite ?: false,
                        i.favourites,
                        i.media?.edges?.firstOrNull()?.node?.let { it.coverImage?.large }
                    )
                )
            }

            val pageInfo = response.pageInfo ?: return null

            return StudioSearchResults(
                search = search,
                results = responseArray,
                page = pageInfo.currentPage ?: 0,
                hasNextPage = pageInfo.hasNextPage == true
            )
        }
        return null
    }

    suspend fun searchStaff(page: Int, search: String?): StaffSearchResults? {
        if (search.isNullOrBlank()) return null
        val query = """
           {
             Page(page: $page, perPage: $ITEMS_PER_PAGE) {
               $standardPageInformation
               staff(search: "$search") {
                  ${staffInformation(1, 1)}
               }
             }
           }
        """.prepare()

        val response = executeQuery<Query.Page>(query, force = true)?.data?.page

        if (response?.staff != null) {
            val responseArray = arrayListOf<Author>()
            response.staff?.forEach { i ->
                responseArray.add(
                    Author(
                        i.id,
                        i.name?.userPreferred ?: return null,
                        i.image?.large,
                        null,
                        null,
                        null
                    )
                )
            }

            val pageInfo = response.pageInfo ?: return null

            return StaffSearchResults(
                search = search,
                results = responseArray,
                page = pageInfo.currentPage ?: 0,
                hasNextPage = pageInfo.hasNextPage == true
            )
        }
        return null
    }

    private suspend fun mediaList(media1: Page?): ArrayList<Media> {
        val combinedList = arrayListOf<Media>()
        val localStateRepository = AnimeStateRepository(AnimeStateDatabase.get(App.instance!!))
        media1?.media?.forEach { item ->
            val media = Media(item)
            val localState = localStateRepository.get(media.id)
            if (localState != null) {
                localState.applyTo(media)
            } else {
                localStateRepository.upsert(media.toAnimeStateRecord())
            }
            combinedList.add(media)
        }
        return combinedList
    }

    private fun getPreference(pref: PrefName): Boolean = PrefManager.getVal(pref)

    private fun buildQueryString(
        sort: String,
        type: String,
        format: String? = null,
        country: String? = null
    ): String {
        val includeList = when {
            type == "ANIME" && !getPreference(PrefName.IncludeAnimeList) -> "onList:false"
            type == "MANGA" && !getPreference(PrefName.IncludeMangaList) -> "onList:false"
            else -> ""
        }
        val isAdult = if (getPreference(PrefName.AdultOnly)) "isAdult:true" else ""
        val formatFilter = format?.let { "format:$it, " } ?: ""
        val countryFilter = country?.let { "countryOfOrigin:$it, " } ?: ""

        return buildString {
            append("""Page(page:1,perPage:50){$standardPageInformation media(sort:$sort, type:$type, $formatFilter $countryFilter $includeList $isAdult){id idMal status chapters episodes nextAiringEpisode{episode} isAdult type meanScore  format bannerImage countryOfOrigin coverImage{large} title{english romaji userPreferred} description genres tags { name isMediaSpoiler } }}""")
        }
    }

    private fun recentAnimeUpdates(page: Int): String {
        val currentTime = System.currentTimeMillis() / 1000
        return buildString {
            append("""Page(page:$page,perPage:50){$standardPageInformation airingSchedules(airingAt_greater:0 airingAt_lesser:${currentTime - 10000} sort:TIME_DESC){episode airingAt media{id idMal status chapters episodes nextAiringEpisode{episode} isAdult type meanScore  format bannerImage countryOfOrigin coverImage{large} title{english romaji userPreferred} description genres tags { name isMediaSpoiler } }}}""")
        }
    }

    private fun queryAnimeList(onList: Boolean = true): String {
        val (season, year) = Anilist.currentSeasons[1]
        val includeList = if (!onList) "onList:false" else ""
        val isAdult = if (getPreference(PrefName.AdultOnly)) "isAdult:true" else ""
        return buildString {
            append(
                """{recentUpdates:${recentAnimeUpdates(1)} trendingMovies:${
                    buildQueryString(
                        "POPULARITY_DESC",
                        "ANIME",
                        "MOVIE"
                    )
                } topRated:${
                    buildQueryString(
                        "SCORE_DESC",
                        "ANIME"
                    )
                } mostFav:${buildQueryString("FAVOURITES_DESC", "ANIME")} trending: Page(page:1, perPage:12) { $standardPageInformation media(sort:TRENDING_DESC, type:ANIME, season:$season, seasonYear:$year, $isAdult) { id idMal status episodes nextAiringEpisode{episode} isAdult type meanScore  format bannerImage countryOfOrigin coverImage{large} title{english romaji userPreferred} description genres tags { name isMediaSpoiler }  } } popular: Page(page:1, perPage:50) { $standardPageInformation media(sort:POPULARITY_DESC, type:ANIME, $includeList $isAdult) { id idMal status chapters episodes nextAiringEpisode{episode} isAdult type meanScore  format bannerImage countryOfOrigin coverImage{large} title{english romaji userPreferred} description genres tags { name isMediaSpoiler }  } }}"""
            )
        }
    }

    private fun queryMangaList(onList: Boolean = true): String {
        val includeList = if (!onList) "onList:false" else ""
        val isAdult = if (getPreference(PrefName.AdultOnly)) "isAdult:true" else ""
        return buildString {
            append(
                """{trendingManga:${
                    buildQueryString(
                        "POPULARITY_DESC",
                        "MANGA",
                        country = "JP"
                    )
                } trendingManhwa:${
                    buildQueryString(
                        "POPULARITY_DESC",
                        "MANGA",
                        country = "KR"
                    )
                } trendingNovel:${
                    buildQueryString(
                        "POPULARITY_DESC",
                        "MANGA",
                        format = "NOVEL",
                        country = "JP"
                    )
                } topRated:${
                    buildQueryString(
                        "SCORE_DESC",
                        "MANGA"
                    )
                } mostFav:${buildQueryString("FAVOURITES_DESC", "MANGA")} trending: Page(page:1, perPage:10) { $standardPageInformation media(sort:TRENDING_DESC, type:MANGA, $isAdult) { id idMal status chapters episodes nextAiringEpisode{episode} isAdult type meanScore  format bannerImage countryOfOrigin coverImage{large} title{english romaji userPreferred} description genres tags { name isMediaSpoiler }  } } popular: Page(page:1, perPage:50) { $standardPageInformation media(sort:POPULARITY_DESC, type:MANGA, $includeList $isAdult) { id idMal status chapters episodes nextAiringEpisode{episode} isAdult type meanScore  format bannerImage countryOfOrigin coverImage{large} title{english romaji userPreferred} description genres tags { name isMediaSpoiler }  } }}"""
            )
        }
    }

    suspend fun loadAnimeMetadataList(): Map<String, ArrayList<Media>> = coroutineScope {
        val list = mutableMapOf<String, ArrayList<Media>>()

        fun filterRecentUpdates(page: Page?): ArrayList<Media> {
            val adultOnly = getPreference(PrefName.AdultOnly)
            val idArr = mutableSetOf<Int>()
            return page?.airingSchedules?.mapNotNull { i ->
                i.media?.takeIf { !idArr.contains(it.id) }?.let {
                    val shouldAdd = when {
                        adultOnly && it.isAdult == true -> true
                        !adultOnly && it.countryOfOrigin == "JP" && it.isAdult == false -> true
                        else -> false
                    }
                    if (shouldAdd) {
                        idArr.add(it.id)
                        Media(it)
                    } else null
                }
            }?.toCollection(ArrayList()) ?: arrayListOf()
        }

        val animeList = async { executeQuery<Query.AnimeList>(queryAnimeMetadataList(), force = true) }

        animeList.await()?.data?.apply {
            list["recentUpdates"] = filterRecentUpdates(recentUpdates)
            list["trendingMovies"] = mediaList(trendingMovies)
            list["topRated"] = mediaList(topRated)
            list["mostFav"] = mediaList(mostFav)
            list["trending"] = mediaList(trending)
            list["popular"] = mediaList(popular)
        }

        list
    }

    suspend fun loadMangaList(onList: Boolean = true): Map<String, ArrayList<Media>> = coroutineScope {
        val list = mutableMapOf<String, ArrayList<Media>>()

        val mangaList = async { executeQuery<Query.MangaList>(queryMangaList(onList), force = true) }

        mangaList.await()?.data?.apply {
            list["trendingManga"] = mediaList(trendingManga)
            list["trendingManhwa"] = mediaList(trendingManhwa)
            list["trendingNovel"] = mediaList(trendingNovel)
            list["topRated"] = mediaList(topRated)
            list["mostFav"] = mediaList(mostFav)
            list["trending"] = mediaList(trending)
            list["popular"] = mediaList(popular)
        }

        list
    }

    suspend fun recentlyUpdated(
        greater: Long = 0,
        lesser: Long = System.currentTimeMillis() / 1000 - 10000
    ): MutableList<Media> {
        suspend fun execute(page: Int = 1): Page? {
            val query = """{
Page(page:$page,perPage:50) {
    $standardPageInformation
    airingSchedules(
        airingAt_greater: $greater
        airingAt_lesser: $lesser
        sort:TIME_DESC
    ) {
        episode
        airingAt
        media {
            ${standardMediaInformation()}
        }
    }
}
        }""".prepare()
            return executeQuery<Query.Page>(query, force = true)?.data?.page
        }

        var i = 1
        val list = mutableListOf<Media>()
        var res: Page? = null
        suspend fun next() {
            res = execute(i)
            list.addAll(res?.airingSchedules?.mapNotNull { j ->
                j.media?.let {
                    if (it.countryOfOrigin == "JP" && (if (!Anilist.adult) it.isAdult == false else true)) {
                        val media = Media(it).apply { relation = "${j.episode},${j.airingAt}" }
                        val localStateRepository = AnimeStateRepository(AnimeStateDatabase.get(App.instance!!))
                        val localState = localStateRepository.get(media.id)
                        if (localState != null) {
                            localState.applyTo(media)
                        } else {
                            localStateRepository.upsert(media.toAnimeStateRecord())
                        }
                        media
                    } else null
                }
            } ?: listOf())
        }
        next()
        while (res?.pageInfo?.hasNextPage == true) {
            next()
            i++
        }
        return list.reversed().toMutableList()
    }

    suspend fun getCharacterDetails(character: Character): Character {
        val query = """ {
  Character(id: ${character.id}) {
    ${characterInformation(true)}
  }
}""".prepare()
        executeQuery<Query.Character>(query, force = true)?.data?.character?.let { i ->
            return Character(
                i.id,
                i.name?.full,
                i.image?.large ?: i.image?.medium,
                null,
                null.toString(),
                i.isFavourite ?: false,
                i.description,
                i.age,
                i.gender,
                i.dateOfBirth,
                i.media?.edges?.map {
                    val m = Media(it)
                    m.relation = it.characterRole.toString()
                    m
                }?.let { ArrayList(it) },
                i.media?.edges?.flatMap { edge ->
                    edge.voiceActors?.map { va ->
                        Author(
                            va.id,
                            va.name?.userPreferred,
                            va.image?.large ?: va.image?.medium,
                            va.languageV2
                        )
                    } ?: emptyList()
                }?.distinctBy { it.id }?.let { ArrayList(it) }
            )
        }
        return character
    }

    suspend fun getStudioDetails(studio: Studio): Studio {
        fun query(page: Int = 0) = """ {
  Studio(id: ${studio.id}) {
    ${studioInformation(page, ITEMS_PER_PAGE)}
  }
}""".prepare()

        var hasNextPage = true
        val yearMedia = mutableMapOf<String, ArrayList<Media>>()
        var page = 0
        val seenMediaIds = hashSetOf<Int>()
        while (hasNextPage) {
            page++
            hasNextPage =
                executeQuery<Query.Studio>(query(page), force = true)?.data?.studio?.media?.let {
                    it.edges?.forEach { i ->
                        i.node?.apply {
                            if (id !in seenMediaIds) {
                                seenMediaIds.add(id)
                                val status = status.toString()
                                val year = startDate?.year?.toString() ?: "TBA"
                                val title = if (status != "CANCELLED") year else status
                                if (!yearMedia.containsKey(title))
                                    yearMedia[title] = arrayListOf()
                                yearMedia[title]?.add(Media(this))
                            }
                        }
                    }
                    it.pageInfo?.hasNextPage == true
                } ?: false
        }
        if (yearMedia.contains("CANCELLED")) {
            val a = yearMedia["CANCELLED"]!!
            yearMedia.remove("CANCELLED")
            yearMedia["CANCELLED"] = a
        }
        studio.yearMedia = yearMedia
        return studio
    }


    suspend fun getAuthorDetails(author: Author): Author {
        fun query(page: Int = 0) = """ {
  Staff(id: ${author.id}) {
    ${staffInformation(page, ITEMS_PER_PAGE)}
    characters(page: $page,sort:FAVOURITES_DESC) {
      $standardPageInformation
      nodes{
        ${characterInformation(false)}
      }
    }
  }
}""".prepare()

        var hasNextPage = true
        val yearMedia = mutableMapOf<String, ArrayList<Media>>()
        var page = 0
        val characters = arrayListOf<Character>()
        while (hasNextPage) {
            page++
            val query = executeQuery<Query.Author>(
                query(page), force = true
            )?.data?.author
            author.age = query?.age
            author.yearsActive =
                if (query?.yearsActive?.isEmpty() == true) null else query?.yearsActive
            author.homeTown = if (query?.homeTown?.isBlank() == true) null else query?.homeTown
            author.dateOfDeath = if (query?.dateOfDeath?.toStringOrEmpty()
                    ?.isBlank() == true
            ) null else query?.dateOfDeath?.toStringOrEmpty()
            author.dateOfBirth = if (query?.dateOfBirth?.toStringOrEmpty()
                    ?.isBlank() == true
            ) null else query?.dateOfBirth?.toStringOrEmpty()
            hasNextPage = query?.staffMedia?.let {
                it.edges?.forEach { i ->
                    i.node?.apply {
                        val status = status.toString()
                        val year = startDate?.year?.toString() ?: "TBA"
                        val title = if (status != "CANCELLED") year else status
                        if (!yearMedia.containsKey(title))
                            yearMedia[title] = arrayListOf()
                        val media = Media(this)
                        media.relation = i.staffRole
                        yearMedia[title]?.add(media)
                    }
                }
                it.pageInfo?.hasNextPage == true
            } ?: false
            query?.characters?.let {
                it.nodes?.forEach { i ->
                    characters.add(
                        Character(
                            i.id,
                            i.name?.userPreferred,
                            i.image?.large,
                            i.image?.medium,
                            "",
                            false
                        )
                    )
                }
            }
        }

        if (yearMedia.contains("CANCELLED")) {
            val a = yearMedia["CANCELLED"]!!
            yearMedia.remove("CANCELLED")
            yearMedia["CANCELLED"] = a
        }
        author.character = characters
        author.yearMedia = yearMedia
        return author
    }

    suspend fun getReviews(
        mediaId: Int,
        page: Int = 1,
        sort: String = "SCORE_DESC"
    ): Query.ReviewsResponse? {
        return executeQuery<Query.ReviewsResponse>(
            """{Page(page:$page,perPage:10){$standardPageInformation reviews(mediaId:$mediaId,sort:$sort){id,mediaId,mediaType,summary,body(asHtml:true)rating,ratingAmount,userRating,score,private,siteUrl,createdAt,updatedAt,user{id,name,bannerImage avatar{medium,large}}}}}""",
            force = true
        )
    }

    suspend fun getMediaCharacters(mediaId: Int, page: Int = 1): Query.Media? {
        val query = """{Media(id:$mediaId){characters(sort:[ROLE,FAVOURITES_DESC],page:$page,perPage:25){pageInfo{hasNextPage currentPage}edges{role voiceActors{id name{userPreferred}image{medium large}languageV2}node{id name{userPreferred}image{medium large}}}}}}"""
        return executeQuery(query, force = true)
    }

    suspend fun getMediaStaff(mediaId: Int, page: Int = 1): Query.Media? {
        val query = """{Media(id:$mediaId){staff(sort:[RELEVANCE,ID],page:$page,perPage:25){pageInfo{hasNextPage currentPage}edges{role node{id name{userPreferred}image{medium large}}}}}}"""
        return executeQuery(query, force = true)
    }

