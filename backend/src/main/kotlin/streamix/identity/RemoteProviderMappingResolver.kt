package streamix.identity

import com.google.gson.JsonParser
import streamix.core.JvmStreamixHttp
import streamix.core.StreamixHttp
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Saikou-compatible remote mapping resolver.
 *
 * Expected endpoint shape:
 *   /api/anilist/anime/{anilistId}/mappings?provider={providerId}
 *
 * The response is expected to expose the provider ID at:
 *   res.provider.id
 *
 * No title search or fuzzy matching is performed here. A missing/invalid
 * mapping simply returns null so the caller can try another provider.
 *
 * HTTP is injected through the pure-JVM StreamixHttp boundary so this
 * identity layer does not depend on the legacy OCE runtime singleton.
 */
class RemoteProviderMappingResolver(
    baseUrl: URI,
    private val timeoutMs: Int = 8_000,
    private val http: StreamixHttp = JvmStreamixHttp()
) : ProviderMappingResolver {

    private val endpointBase = baseUrl.toString().trimEnd('/')

    override suspend fun resolve(anilistId: Long, providerId: String): String? {
        require(anilistId > 0L) { "anilistId must be positive" }
        if (providerId.isBlank()) return null

        return runCatching {
            val encodedProvider = URLEncoder.encode(
                providerId,
                StandardCharsets.UTF_8.name()
            )
            val url = "$endpointBase/api/anilist/anime/$anilistId/mappings?provider=$encodedProvider"
            val response = http.get(
                url,
                headers = mapOf("Accept" to "application/json"),
                timeoutMs = timeoutMs.toLong()
            )
            if (response.code !in 200..299) return null

            JsonParser.parseString(response.text)
                .asJsonObject
                .getAsJsonObject("res")
                ?.getAsJsonObject("provider")
                ?.get("id")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
