package com.baseprovider.streamix

/**
 * Streamix-owned HTTP boundary.
 *
 * Providers should depend on this contract instead of a concrete HTTP library.
 * The current bridge may delegate to the existing runtime; that implementation
 * can be replaced without changing provider contracts.
 */
interface StreamixHttp {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeoutMs: Long = 15_000L
    ): StreamixHttpResponse

    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeoutMs: Long = 15_000L
    ): StreamixHttpResponse
}

data class StreamixHttpResponse(
    val code: Int,
    val url: String,
    val text: String,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap()
)
