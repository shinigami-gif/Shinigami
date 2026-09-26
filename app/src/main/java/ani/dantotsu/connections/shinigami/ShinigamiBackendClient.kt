package ani.dantotsu.connections.shinigami

import com.google.gson.Gson
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
