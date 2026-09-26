package ani.dantotsu.connections.shinigami

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ShinigamiLibraryClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    suspend fun deleteFromLibrary(token: String, mediaId: Long) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(ShinigamiBackendConfig.baseUrl + "/api/v1/users/me/library/" + mediaId)
            .header("Authorization", "Bearer " + token)
            .delete()
            .build()
        http.newCall(request).execute().use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Library delete failed: HTTP " + it.code)
            }
        }
    }

    suspend fun getLibrary(token: String, page: Int = 1, perPage: Int = 50): ShinigamiLibraryPage =
        withContext(Dispatchers.IO) {
            val baseUrl = ShinigamiBackendConfig.baseUrl
            val request = Request.Builder()
                .url("$baseUrl/api/v1/users/me/library?page=$page&perPage=$perPage")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val response = http.newCall(request).execute()
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw IllegalStateException("Library request failed: HTTP " + it.code)
                }
                return@withContext gson.fromJson(body, ShinigamiLibraryPage::class.java)
                    ?: throw IllegalStateException("Backend returned an empty library")
            }
        }
}
