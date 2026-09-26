package streamix.core

import org.jsoup.nodes.Document
import org.jsoup.Jsoup

interface StreamixHttp {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        cookies: Map<String, String> = emptyMap(),
        timeoutMs: Long = 15_000L
    ): StreamixHttpResponse

    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        cookies: Map<String, String> = emptyMap(),
        timeoutMs: Long = 15_000L
    ): StreamixHttpResponse

    suspend fun probe(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        cookies: Map<String, String> = emptyMap(),
        timeoutMs: Long = 15_000L,
        maxBytes: Long = Long.MAX_VALUE
    ): StreamixHttpProbeResponse
}

data class StreamixHttpProbeResponse(
    val code: Int,
    val url: String,
    val bytesRead: Long,
    val eof: Boolean
)

data class StreamixHttpResponse(
    val code: Int,
    val url: String,
    val text: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val cookies: Map<String, String> = emptyMap()
) {
    val document: Document
        get() = Jsoup.parse(text, url)
}
