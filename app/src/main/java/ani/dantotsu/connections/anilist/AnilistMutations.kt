package ani.dantotsu.connections.anilist

import ani.dantotsu.App
import ani.dantotsu.connections.anilist.Anilist.executeQuery
import ani.dantotsu.database.AnimeStateDatabase
import ani.dantotsu.database.AnimeStateRecord
import ani.dantotsu.database.AnimeStateRepository
import ani.dantotsu.connections.anilist.api.FuzzyDate
import ani.dantotsu.connections.anilist.api.Query
import ani.dantotsu.connections.anilist.api.ToggleLike
import ani.dantotsu.currContext
import com.google.gson.Gson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class AnilistMutations {

    private fun fuzzyDateToEpochDay(date: FuzzyDate): Long? {
        val year = date.year ?: return null
        val month = date.month ?: 1
        val day = date.day ?: 1
        return try {
            java.time.LocalDate.of(year, month, day).toEpochDay()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun toggleFav(type: FavType, id: Int): Boolean {
        val filter = when (type) {
            FavType.ANIME -> "animeId"
            FavType.MANGA -> "mangaId"
            FavType.CHARACTER -> "characterId"
            FavType.STAFF -> "staffId"
            FavType.STUDIO -> "studioId"
        }
        val query = """
            mutation {
                ToggleFavourite($filter: $id) {
                    anime {
                        pageInfo {
                            total
                        }
                    }
                }
            }
        """.trimIndent()
        val result = executeQuery<JsonObject>(query)
        return result?.get("errors") == null && result != null
    }

    enum class FavType {
        ANIME, MANGA, CHARACTER, STAFF, STUDIO
    }

    suspend fun rateReview(reviewId: Int, rating: String): Query.RateReviewResponse? {
        val query = """
            mutation {
                RateReview(reviewId: $reviewId, rating: $rating) {
                    id
                    mediaId
                    mediaType
                    summary
                    body(asHtml: true)
                    rating
                    ratingAmount
                    userRating
                    score
                    private
                    siteUrl
                    createdAt
                    updatedAt
                    user {
                        id
                        name
                        bannerImage
                        avatar {
                            medium
                            large
                        }
                    }
                }
            }
        """.trimIndent()
        return executeQuery<Query.RateReviewResponse>(query)
    }

    private fun JsonObject.extractErrorMessage(): String? {
        val errors = this["errors"]?.let { it as? JsonArray } ?: return null
        val messages = errors.mapNotNull { elem ->
            val obj = elem.jsonObject
            val validationObj = obj["validation"]?.let { it as? JsonObject }
            if (validationObj != null) {
                val validationMessages = validationObj.values.flatMap { v ->
                    (v as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                }
                if (validationMessages.isNotEmpty()) {
                    validationMessages.joinToString("\n")
                } else {
                    obj["message"]?.jsonPrimitive?.contentOrNull
                }
            } else {
                obj["message"]?.jsonPrimitive?.contentOrNull
            }
        }
        return if (messages.isNotEmpty()) messages.joinToString("\n") else null
    }

    suspend fun postReview(
        summary: String,
        body: String,
        mediaId: Int,
        score: Int,
        edit: Int? = null,
        isPrivate: Boolean = false
    ): String {
        val encodedSummary = summary.stringSanitizer()
        val encodedBody = body.stringSanitizer()
        val query = """
            mutation {
                SaveReview(
                    ${if (edit != null) "id: $edit," else ""}
                    mediaId: $mediaId,
                    summary: $encodedSummary,
                    body: $encodedBody,
                    score: $score,
                    private: $isPrivate
                ) {
                    siteUrl
                    id
                }
            }
        """.trimIndent()
        val result = executeQuery<JsonObject>(query)
        val errorMessage = result?.extractErrorMessage() ?: Anilist.lastError
        return if (result == null || errorMessage != null) {
            errorMessage ?: "Failed to post review"
        } else {
            currContext()?.getString(ani.dantotsu.R.string.success) ?: "Success"
        }
    }

    suspend fun deleteReview(reviewId: Int): Boolean {
        val query = """
            mutation {
                DeleteReview(id: $reviewId) {
                    deleted
                }
            }
        """.trimIndent()
        val result = executeQuery<JsonObject>(query)
        val errors = result?.get("errors")
        return errors == null && result != null
    }

    private fun String.stringSanitizer(): String {
        val sb = StringBuilder()
        var i = 0
        while (i < this.length) {
            val codePoint = this.codePointAt(i)
            if (codePoint > 0xFFFF) {
                sb.append("&#").append(codePoint).append(";")
                i += 2
            } else {
                sb.append(this[i])
                i++
            }
        }
        return Gson().toJson(sb.toString())
    }
}
