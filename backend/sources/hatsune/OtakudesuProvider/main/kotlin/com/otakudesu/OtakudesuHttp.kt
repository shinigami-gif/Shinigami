package com.otakudesu

import streamix.core.JvmStreamixHttp
import streamix.core.StreamixHttp
import streamix.core.StreamixHttpResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object OtakudesuHttp {
    @Volatile
    var client: StreamixHttp = JvmStreamixHttp()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), referer: String? = null): StreamixHttpResponse =
        client.get(url, headers, referer)

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap(), referer: String? = null): StreamixHttpResponse =
        client.post(url, json, headers + ("Content-Type" to "application/json"), referer)

    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap(), referer: String? = null): StreamixHttpResponse {
        val body = form.entries.joinToString("&") { (key, value) ->
            "${encodeValue(key)}=${encodeValue(value)}"
        }
        return client.post(url, body, headers + ("Content-Type" to "application/x-www-form-urlencoded"), referer)
    }

    private fun encodeValue(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}