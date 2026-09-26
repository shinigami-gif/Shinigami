package ani.dantotsu.connections.shinigami

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class ShinigamiCommentsClient(
    private val baseUrl: String = ShinigamiBackendConfig.baseUrl,
    private val http: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    suspend fun list(token: String, mediaId: Long, page: Int = 1, perPage: Int = 20, parentCommentId: String? = null): ShinigamiCommentPage =
        withContext(Dispatchers.IO) {
            val query = "?mediaId=" + mediaId + "&page=" + page + "&perPage=" + perPage +
                (parentCommentId?.let { "&parentCommentId=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            executePage(Request.Builder().url(baseUrl + "/api/v1/social/comments" + query)
                .header("Authorization", "Bearer " + token).get().build())
        }

    suspend fun replies(token: String, commentId: String, page: Int = 1, perPage: Int = 20): ShinigamiCommentPage =
        withContext(Dispatchers.IO) {
            executePage(Request.Builder().url(baseUrl + "/api/v1/social/comments/" + commentId + "/replies?page=" + page + "&perPage=" + perPage)
                .header("Authorization", "Bearer " + token).get().build())
        }

    suspend fun create(token: String, mediaId: Long, content: String, parentCommentId: String? = null): ShinigamiComment =
        withContext(Dispatchers.IO) {
            executeComment(Request.Builder().url(baseUrl + "/api/v1/social/comments")
                .header("Authorization", "Bearer " + token).post(json(mapOf("mediaId" to mediaId, "content" to content, "parentCommentId" to parentCommentId))).build())
        }

    suspend fun edit(token: String, commentId: String, content: String): ShinigamiComment =
        withContext(Dispatchers.IO) {
            executeComment(Request.Builder().url(baseUrl + "/api/v1/social/comments/" + commentId)
                .header("Authorization", "Bearer " + token).put(json(mapOf("content" to content))).build())
        }

    suspend fun delete(token: String, commentId: String) = withContext(Dispatchers.IO) {
        executeEmpty(Request.Builder().url(baseUrl + "/api/v1/social/comments/" + commentId)
            .header("Authorization", "Bearer " + token).delete().build())
    }

    suspend fun vote(token: String, commentId: String, vote: Int?): ShinigamiComment =
        withContext(Dispatchers.IO) {
            executeComment(Request.Builder().url(baseUrl + "/api/v1/social/comments/" + commentId + "/vote")
                .header("Authorization", "Bearer " + token).post(json(mapOf("vote" to vote))).build())
        }

    suspend fun report(token: String, targetUserId: String?, targetContentId: String?, type: String, description: String) =
        withContext(Dispatchers.IO) {
            executeEmpty(Request.Builder().url(baseUrl + "/api/v1/social/reports")
                .header("Authorization", "Bearer " + token)
                .post(json(ShinigamiReportRequest(targetUserId, targetContentId, type, description))).build())
        }

    private fun json(value: Any) = gson.toJson(value).toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun executePage(request: Request): ShinigamiCommentPage {
        val response = http.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            check(it.isSuccessful) { "Backend comments request failed: HTTP " + it.code }
            return gson.fromJson(body, ShinigamiCommentPage::class.java) ?: error("Backend returned an empty comment page")
        }
    }

    private fun executeComment(request: Request): ShinigamiComment {
        val response = http.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            check(it.isSuccessful) { "Backend comment request failed: HTTP " + it.code }
            return gson.fromJson(body, ShinigamiComment::class.java) ?: error("Backend returned an empty comment")
        }
    }

    private fun executeEmpty(request: Request) {
        val response = http.newCall(request).execute()
        response.use { check(it.isSuccessful) { "Backend comment request failed: HTTP " + it.code } }
    }
}
