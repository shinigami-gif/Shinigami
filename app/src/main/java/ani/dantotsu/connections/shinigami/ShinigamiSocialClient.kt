package ani.dantotsu.connections.shinigami

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class ShinigamiSocialClient(private val baseUrl: String = ShinigamiBackendConfig.baseUrl, private val http: OkHttpClient = OkHttpClient(), private val gson: Gson = Gson()) {
    suspend fun feed(token: String, page: Int = 1, perPage: Int = 20) = fetchPage(token, "/api/v1/social/feed", page, perPage, ShinigamiActivity::class.java)
    suspend fun activities(token: String, page: Int = 1, perPage: Int = 20) = page(token, "/api/v1/social/activities", page, perPage, ShinigamiActivity::class.java)
    suspend fun activity(token: String, activityId: String) = get(token, "/api/v1/social/activities/" + activityId, ShinigamiActivity::class.java)
    suspend fun replies(token: String, activityId: String, page: Int = 1, perPage: Int = 20) = page(token, "/api/v1/social/activities/" + activityId + "/replies", page, perPage, ShinigamiActivityReply::class.java)
    suspend fun likeActivity(token: String, activityId: String) = post(token, "/api/v1/social/activities/" + activityId + "/like", ShinigamiActivity::class.java)
    suspend fun subscribeActivity(token: String, activityId: String) = post(token, "/api/v1/social/activities/" + activityId + "/subscribe", ShinigamiActivity::class.java)
    suspend fun forumThreads(token: String, query: String? = null, page: Int = 1, perPage: Int = 20): ShinigamiSocialPage<ShinigamiForumThread> {
        val q = query?.takeIf { it.isNotBlank() }?.let { "?q=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return fetchPage(token, "/api/v1/social/forum/threads" + q, page, perPage, ShinigamiForumThread::class.java)
    }
    suspend fun forumThread(token: String, threadId: String) = get(token, "/api/v1/social/forum/threads/" + threadId, ShinigamiForumThread::class.java)
    suspend fun forumComments(token: String, threadId: String, page: Int = 1, perPage: Int = 20) = page(token, "/api/v1/social/forum/threads/" + threadId + "/comments", page, perPage, ShinigamiForumComment::class.java)
    suspend fun likeThread(token: String, threadId: String) = post(token, "/api/v1/social/forum/threads/" + threadId + "/like", ShinigamiForumThread::class.java)
    suspend fun subscribeThread(token: String, threadId: String) = post(token, "/api/v1/social/forum/threads/" + threadId + "/subscribe", ShinigamiForumThread::class.java)

    private suspend fun <T> fetchPage(token: String, path: String, page: Int, perPage: Int, type: Class<T>): ShinigamiSocialPage<T> = withContext(Dispatchers.IO) {
        val separator = if (path.contains("?")) "&" else "?"
        val response = http.newCall(Request.Builder().url(baseUrl + path + separator + "page=" + page + "&perPage=" + perPage).header("Authorization", "Bearer " + token).get().build()).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            check(it.isSuccessful) { "Backend social request failed: HTTP " + it.code }
            val root = gson.fromJson(body, JsonObject::class.java) ?: error("Empty social page")
            return@withContext ShinigamiSocialPage(items = root.getAsJsonArray("items")?.map { item -> gson.fromJson(item, type) } ?: emptyList(), page = root.get("page")?.asInt ?: 1, perPage = root.get("perPage")?.asInt ?: 20, hasNextPage = root.get("hasNextPage")?.asBoolean ?: false, total = root.get("total")?.takeIf { !it.isJsonNull }?.asLong)
        }
    }
    private suspend fun <T> get(token: String, path: String, type: Class<T>): T = request(token, Request.Builder().url(baseUrl + path).get().build(), type)
    private suspend fun <T> post(token: String, path: String, type: Class<T>): T = request(token, Request.Builder().url(baseUrl + path).post("{}".toRequestBody("application/json; charset=utf-8".toMediaType())).build(), type)
    private suspend fun <T> request(token: String, request: Request, type: Class<T>): T = withContext(Dispatchers.IO) {
        val response = http.newCall(request.newBuilder().header("Authorization", "Bearer " + token).build()).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            check(it.isSuccessful) { "Backend social request failed: HTTP " + it.code }
            return@withContext gson.fromJson(body, type) ?: error("Empty social response")
        }
    }
}