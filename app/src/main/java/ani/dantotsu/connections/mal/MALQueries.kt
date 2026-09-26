package ani.dantotsu.connections.mal

import ani.dantotsu.client
import ani.dantotsu.tryWithSuspend
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * Public MyAnimeList metadata API.
 * No user/session/list mutation operations belong here.
 */
class MALQueries {
    private val apiUrl = "https://api.myanimelist.net/v2"
    private val clientIdHeader = mapOf(
        "X-MAL-CLIENT-ID" to "86b35cf02205a0303da3aaea1c9e33f3"
    )

    private suspend fun executeRequest(
        requestBlock: suspend () -> com.lagradost.nicehttp.NiceResponse
    ): com.lagradost.nicehttp.NiceResponse {
        var lastResponse: com.lagradost.nicehttp.NiceResponse? = null
        var lastException: Exception? = null
        var delayMs = 1000L
        repeat(3) { attempt ->
            try {
                val response = requestBlock()
                lastResponse = response
                if (response.code != 429) return response
            } catch (e: Exception) {
                lastException = e
            }
            if (attempt < 2) {
                kotlinx.coroutines.delay(delayMs + (0..200).random())
                delayMs *= 2
            }
        }
        return lastResponse ?: throw (lastException ?: Exception("MAL metadata request failed"))
    }

    private val rankingFields =
        "mean,status,media_type,num_episodes,num_chapters,main_picture,genres,start_date,start_season"

    suspend fun searchAnime(query: String, limit: Int = 25, offset: Int = 0): MalRankingResponse? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return tryWithSuspend {
            executeRequest {
                client.get(
                    "$apiUrl/anime?q=$encodedQuery&limit=$limit&offset=$offset&fields=$rankingFields",
                    clientIdHeader
                )
            }.parsed<MalRankingResponse>()
        }
    }


    suspend fun getAnimeRanking(
        rankingType: String = "all",
        limit: Int = 15,
        offset: Int = 0
    ): MalRankingResponse? = tryWithSuspend {
        executeRequest {
            client.get(
                "$apiUrl/anime/ranking?ranking_type=$rankingType&limit=$limit&offset=$offset&fields=$rankingFields",
                clientIdHeader
            )
        }.parsed<MalRankingResponse>()
    }

    suspend fun getMangaRanking(
        rankingType: String = "all",
        limit: Int = 15,
        offset: Int = 0
    ): MalRankingResponse? = tryWithSuspend {
        executeRequest {
            client.get(
                "$apiUrl/manga/ranking?ranking_type=$rankingType&limit=$limit&offset=$offset&fields=$rankingFields",
                clientIdHeader
            )
        }.parsed<MalRankingResponse>()
    }

    private val relationFields =
        "%7Bnode%7Bid,title,main_picture,num_episodes,num_chapters,mean,media_type,status,alternative_titles,rating,popularity%7D%7D"
    private val detailFields =
        "mean,status,media_type,synopsis,genres,num_episodes,num_chapters,main_picture," +
            "alternative_titles,title_synonyms,start_date,end_date,start_season,source,rating," +
            "average_episode_duration,studios,authors,rank,popularity,recommendations$relationFields," +
            "related_anime$relationFields,related_manga$relationFields"

    suspend fun getAnimeDetails(malId: Int): MalAnimeNode? = tryWithSuspend {
        executeRequest {
            client.get("$apiUrl/anime/$malId?fields=$detailFields", clientIdHeader)
        }.parsed<MalAnimeNode>()
    }


    suspend fun getSeasonalAnime(
        year: Int,
        season: String,
        sort: String = "anime_num_list_users",
        limit: Int = 15
    ): MalRankingResponse? = tryWithSuspend {
        executeRequest {
            client.get(
                "$apiUrl/anime/season/$year/$season?sort=$sort&limit=$limit&fields=$rankingFields",
                clientIdHeader
            )
        }.parsed<MalRankingResponse>()
    }
}
