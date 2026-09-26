package ani.dantotsu.connections.shinigami

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ShinigamiNotificationClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    suspend fun list(token: String, page: Int = 1, perPage: Int = 30): ShinigamiNotificationPage =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/notifications?page=$page&perPage=$perPage")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val root = executeJson(request)
            ShinigamiNotificationPage(
                items = root.getAsJsonArray("items")?.mapNotNull {
                    runCatching { gson.fromJson(it, ShinigamiNotification::class.java) }.getOrNull()
                }.orEmpty(),
                page = root.get("page")?.asInt ?: 1,
                perPage = root.get("perPage")?.asInt ?: 30,
                hasNextPage = root.get("hasNextPage")?.asBoolean ?: false
            )
        }

    suspend fun unreadCount(token: String): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/notifications/unread-count")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request).get("count")?.asInt
            ?: throw IllegalStateException("Backend returned an invalid notification count")
    }

    suspend fun markRead(token: String, notificationId: String) = withContext(Dispatchers.IO) {
        require(notificationId.isNotBlank()) { "Notification id cannot be blank" }
        val request = Request.Builder()
            .url("$baseUrl/api/v1/notifications/${notificationId}/read")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody(JSON))
            .build()
        execute(request)
    }

    suspend fun markAllRead(token: String): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/notifications/read-all")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody(JSON))
            .build()
        executeJson(request).get("marked")?.asInt
            ?: throw IllegalStateException("Backend returned an invalid read-all result")
    }

    private fun executeJson(request: Request): JsonObject =
        gson.fromJson(execute(request), JsonObject::class.java)
            ?: throw IllegalStateException("Backend returned invalid JSON")

    private fun execute(request: Request): String {
        val response = http.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val error = runCatching { gson.fromJson(body, ErrorResponse::class.java)?.error }.getOrNull()
                throw IllegalStateException(error ?: "Backend notification request failed: HTTP " + it.code)
            }
            return body
        }
    }

    private val baseUrl: String
        get() = ShinigamiBackendConfig.baseUrl

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private data class ErrorResponse(val error: String? = null)
}
