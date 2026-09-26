package ani.dantotsu.connections.shinigami

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ShinigamiBackendClient(
    private val baseUrl: String = ShinigamiBackendConfig.baseUrl,
    private val http: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    suspend fun createSession(provider: String, credential: String): ShinigamiSession =
        withContext(Dispatchers.IO) {
            val payload = gson.toJson(mapOf("provider" to provider, "credential" to credential))
            val request = Request.Builder()
                .url("$baseUrl/api/v1/auth/session")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            execute(request)
        }

    suspend fun currentSession(token: String): ShinigamiSession =
        withContext(Dispatchers.IO) {
            execute(
                Request.Builder()
                    .url("$baseUrl/api/v1/auth/session")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            )
        }

    suspend fun getMe(token: String): ShinigamiUserProfile =
        withContext(Dispatchers.IO) {
            executeUserProfile(
                Request.Builder()
                    .url("$baseUrl/api/v1/users/me")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            )
        }

    suspend fun updateProfile(token: String, username: String, bio: String?): ShinigamiUser =
        withContext(Dispatchers.IO) {
            val payload = gson.toJson(mapOf("username" to username, "bio" to bio))
            executeUser(
                Request.Builder()
                    .url("$baseUrl/api/v1/users/me/profile")
                    .header("Authorization", "Bearer $token")
                    .put(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            )
        }

    suspend fun getProfile(token: String, userId: String): ShinigamiUserProfile =
        withContext(Dispatchers.IO) {
            executeUserProfile(
                Request.Builder()
                    .url("$baseUrl/api/v1/users/$userId")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            )
        }

    suspend fun searchUsers(token: String, query: String, page: Int = 1, perPage: Int = 20):
        ShinigamiChatPage<ShinigamiUser> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        executeUserPage(
            Request.Builder()
                .url("$baseUrl/api/v1/users/search?q=$encoded&page=$page&perPage=$perPage")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        )
    }

    suspend fun getFollowers(token: String, userId: String, page: Int = 1, perPage: Int = 50):
        ShinigamiChatPage<ShinigamiUser> = withContext(Dispatchers.IO) {
        executeUserPage(
            Request.Builder()
                .url("$baseUrl/api/v1/users/$userId/followers?page=$page&perPage=$perPage")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        )
    }

    suspend fun getFollowing(token: String, userId: String, page: Int = 1, perPage: Int = 50):
        ShinigamiChatPage<ShinigamiUser> = withContext(Dispatchers.IO) {
        executeUserPage(
            Request.Builder()
                .url("$baseUrl/api/v1/users/$userId/following?page=$page&perPage=$perPage")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        )
    }

    suspend fun setFollow(token: String, userId: String, enabled: Boolean): ShinigamiUser =
        withContext(Dispatchers.IO) {
            executeUser(
                Request.Builder()
                    .url("$baseUrl/api/v1/users/$userId/follow?enabled=$enabled")
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody("application/json; charset=utf-8"))
                    .build()
            )
        }

    suspend fun setBlock(token: String, userId: String, enabled: Boolean): ShinigamiUser =
        withContext(Dispatchers.IO) {
            executeUser(
                Request.Builder()
                    .url("$baseUrl/api/v1/users/$userId/block?enabled=$enabled")
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody("application/json; charset=utf-8"))
                    .build()
            )
        }

    private fun executeUserPage(request: Request): ShinigamiChatPage<ShinigamiUser> {
        val response = http.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw IllegalStateException("Backend user list request failed: HTTP " + it.code)
            }
            val json = gson.fromJson(body, JsonObject::class.java)
                ?: throw IllegalStateException("Backend returned an empty user page")
            val items = json.getAsJsonArray("users")
                ?.map { item -> gson.fromJson(item, ShinigamiUser::class.java) }
                ?: emptyList()
            return ShinigamiChatPage(
                items = items,
                page = json.get("page")?.asInt ?: 1,
                perPage = json.get("perPage")?.asInt ?: 50,
                hasNextPage = json.get("hasNextPage")?.asBoolean ?: false
            )
        }
    }

    private fun executeUserProfile(request: Request): ShinigamiUserProfile {
        val response = http.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw IllegalStateException("Backend profile request failed: HTTP " + it.code)
            }
            return gson.fromJson(body, ShinigamiUserProfile::class.java)
                ?: throw IllegalStateException("Backend returned an empty profile")
        }
    }

    private fun executeUser(request: Request): ShinigamiUser {
        val response = http.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw IllegalStateException("Backend user request failed: HTTP " + it.code)
            }
            return gson.fromJson(body, ShinigamiUser::class.java)
                ?: throw IllegalStateException("Backend returned an empty user")
        }
    }

    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        val response = http.newCall(
            Request.Builder()
                .url("$baseUrl/api/v1/auth/logout")
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json; charset=utf-8"))
                .build()
        ).execute()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Backend logout failed: HTTP " + it.code)
            }
        }
    }

    private fun execute(request: Request): ShinigamiSession {
        val response = http.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val error = runCatching {
                    gson.fromJson(body, ErrorResponse::class.java)?.error
                }.getOrNull()
                throw IllegalStateException(error ?: "Backend request failed: HTTP " + it.code)
            }
            return gson.fromJson(body, ShinigamiSession::class.java)
                ?: throw IllegalStateException("Backend returned an empty session")
        }
    }

    private data class ErrorResponse(val error: String? = null)
}
