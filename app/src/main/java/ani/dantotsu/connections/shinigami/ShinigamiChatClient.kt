package ani.dantotsu.connections.shinigami

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ShinigamiChatClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    suspend fun global(
        token: String,
        page: Int = 1,
        perPage: Int = 30
    ): ShinigamiChatPage<ShinigamiChatMessage> =
        getPage("$baseUrl/api/v1/chat/global/messages?page=$page&perPage=$perPage", token)

    suspend fun anime(
        token: String,
        mediaId: Long,
        page: Int = 1,
        perPage: Int = 30
    ): ShinigamiChatPage<ShinigamiChatMessage> =
        getPage("$baseUrl/api/v1/chat/anime/$mediaId/messages?page=$page&perPage=$perPage", token)

    suspend fun sendGlobal(
        token: String,
        content: String
    ): ShinigamiChatMessage =
        postMessage("$baseUrl/api/v1/chat/global/messages", token, content)

    suspend fun sendAnime(
        token: String,
        mediaId: Long,
        content: String
    ): ShinigamiChatMessage =
        postMessage("$baseUrl/api/v1/chat/anime/$mediaId/messages", token, content)

    suspend fun conversations(
        token: String,
        page: Int = 1,
        perPage: Int = 30
    ): ShinigamiChatPage<ShinigamiMessageConversation> =
        getPage("$baseUrl/api/v1/messages?page=$page&perPage=$perPage", token)

    suspend fun messages(
        token: String,
        userId: String,
        page: Int = 1,
        perPage: Int = 30
    ): ShinigamiChatPage<ShinigamiChatMessage> =
        getPage("$baseUrl/api/v1/messages/$userId?page=$page&perPage=$perPage", token)

    suspend fun sendMessage(
        token: String,
        userId: String,
        content: String
    ): ShinigamiChatMessage =
        postMessage("$baseUrl/api/v1/messages/$userId", token, content)

    suspend fun unreadCount(token: String): Int =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/messages/unread-count")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            executeJson(request).get("count")?.asInt
                ?: throw IllegalStateException("Backend returned an invalid unread count")
        }

    suspend fun markRead(token: String, userId: String): Int =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/messages/$userId/read")
                .header("Authorization", "Bearer $token")
                .post(emptyBody())
                .build()
            executeJson(request).get("marked")?.asInt
                ?: throw IllegalStateException("Backend returned an invalid read result")
        }

    private suspend fun postMessage(
        url: String,
        token: String,
        content: String
    ): ShinigamiChatMessage = withContext(Dispatchers.IO) {
        require(content.isNotBlank()) { "Message content cannot be blank" }
        val payload = gson.toJson(ShinigamiChatSend(content.trim()))
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody(JSON))
            .build()
        gson.fromJson(execute(request), ShinigamiChatMessage::class.java)
            ?: throw IllegalStateException("Backend returned an empty message")
    }

    private suspend inline fun <reified T> getPage(
        url: String,
        token: String
    ): ShinigamiChatPage<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val root = executeJson(request)
        val items = root.getAsJsonArray("items")?.mapNotNull { element ->
            runCatching { gson.fromJson(element, T::class.java) }.getOrNull()
        }.orEmpty()
        ShinigamiChatPage(
            items = items,
            page = root.get("page")?.asInt ?: 1,
            perPage = root.get("perPage")?.asInt ?: 30,
            hasNextPage = root.get("hasNextPage")?.asBoolean ?: false
        )
    }

    private fun executeJson(request: Request): JsonObject {
        val body = execute(request)
        return gson.fromJson(body, JsonObject::class.java)
            ?: throw IllegalStateException("Backend returned invalid JSON")
    }

    private fun execute(request: Request): String {
        val response = http.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val error = runCatching {
                    gson.fromJson(body, ErrorResponse::class.java)?.error
                }.getOrNull()
                throw IllegalStateException(
                    error ?: "Backend chat request failed: HTTP " + it.code
                )
            }
            return body
        }
    }

    private fun emptyBody() =
        "{}".toRequestBody(JSON)

    private val baseUrl: String
        get() = ShinigamiBackendConfig.baseUrl

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private data class ErrorResponse(val error: String? = null)
}
