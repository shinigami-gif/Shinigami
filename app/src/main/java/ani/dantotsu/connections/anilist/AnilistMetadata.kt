package ani.dantotsu.connections.anilist

import ani.dantotsu.media.Author
import ani.dantotsu.media.Character
import ani.dantotsu.media.Media
import ani.dantotsu.media.Studio
import ani.dantotsu.connections.anilist.api.Query

/**
 * Explicit AniList boundary for anime metadata.
 *
 * New anime metadata callers must use this facade. User state, account,
 * social, forum, notifications and mutations are deliberately excluded.
 *
 * AnilistQueries remains intact during migration so the existing app can be
 * moved incrementally without a blind rewrite. It is exposed from Anilist
 * only as a deprecated legacy surface.
 */
class AnilistMetadata internal constructor(
    private val queries: AnilistQueries
) {
    suspend fun getAnime(id: Int, mal: Boolean = false): Media? =
        queries.getMedia(id, mal = mal, type = "ANIME")

    suspend fun getAnimeBatch(ids: List<Int>): List<Media>? =
        queries.getMediaList(ids)

    fun getAnimeDetails(media: Media): Media =
        queries.mediaDetails(media)

    suspend fun getAnimeRecommendations(
        page: Int,
        perPage: Int = 50
    ): Pair<ArrayList<Media>, Boolean> =
        queries.getRecommendations(page, perPage)

    suspend fun getGenresAndTags(): Boolean =
        queries.getGenresAndTags()

    suspend fun getGenres(
        genres: ArrayList<String>,
        listener: ((Pair<String, String>) -> Unit)
    ) = queries.getGenres(genres, listener)

    suspend fun searchAnime(
        page: Int = 1,
        perPage: Int = AnilistQueries.ITEMS_PER_PAGE,
        search: String? = null,
        sort: String? = null,
        genres: ArrayList<String>? = null,
        tags: ArrayList<String>? = null,
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
    ): AniMangaSearchResults? = queries.searchAniManga(
        type = "ANIME",
        page = page,
        perPage = perPage,
        search = search,
        sort = sort,
        genres = genres,
        tags = tags,
        status = status,
        source = source,
        format = format,
        countryOfOrigin = countryOfOrigin,
        isAdult = isAdult,
        onList = onList,
        excludedGenres = excludedGenres,
        excludedTags = excludedTags,
        startYear = startYear,
        seasonYear = seasonYear,
        season = season,
        id = id,
        hd = hd,
        adultOnly = adultOnly
    )

    suspend fun searchCharacters(
        page: Int,
        search: String?
    ): CharacterSearchResults? = queries.searchCharacters(page, search)

    suspend fun searchStudios(
        page: Int,
        search: String?
    ): StudioSearchResults? = queries.searchStudios(page, search)

    suspend fun searchStaff(
        page: Int,
        search: String?
    ): StaffSearchResults? = queries.searchStaff(page, search)

    suspend fun getCharacterDetails(character: Character): Character =
        queries.getCharacterDetails(character)

    suspend fun getStudioDetails(studio: Studio): Studio =
        queries.getStudioDetails(studio)

    suspend fun getAuthorDetails(author: Author): Author =
        queries.getAuthorDetails(author)

    suspend fun getMediaCharacters(
        mediaId: Int,
        page: Int = 1
    ): Query.Media? = queries.getMediaCharacters(mediaId, page)

    suspend fun getMediaStaff(
        mediaId: Int,
        page: Int = 1
    ): Query.Media? = queries.getMediaStaff(mediaId, page)
}
