package com.baseprovider.extractor

import com.baseprovider.streamix.StreamixRuntime
import com.baseprovider.streamix.StreamixExtractorApi

/**
 * Extractor dengan helper request HTTP seragam (GET / POST form / POST JSON).
 *
 * TANPA cache hasil fetch — selaras aturan "no-cache extractor": extractor
 * selalu fetch ulang dari website. Hasil decrypt/embed bisa mati dalam
 * hitungan detik–menit (mis. CDN sssrr.org AbyssPlayer), jadi menyimpan
 * response di cache berisiko menyajikan link basi ke player.
 *
 * Nama metode [cachedGetText] / [cachedPostText] / [cachedPostJsonText]
 * dipertahankan agar pemanggil tidak berubah; mereka kini fetch langsung.
 */
abstract class CachedExtractorApi : StreamixExtractorApi() {
    protected val streamixHttp get() = StreamixRuntime.http
    protected val streamixWebResolver get() = StreamixRuntime.webResolver

    protected suspend fun cachedGetText(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String = streamixHttp.get(url, referer = referer, headers = headers).text

    protected suspend fun cachedPostText(
        url: String,
        data: Map<String, String>? = null,
        referer: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String {
        val body = data.orEmpty().entries.joinToString("&") {
            "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }
        return streamixHttp.post(
            url,
            body = body,
            referer = referer,
            headers = headers + ("Content-Type" to "application/x-www-form-urlencoded")
        ).text
    }

    protected suspend fun cachedPostJsonText(
        url: String,
        jsonBody: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String = streamixHttp.post(
        url,
        body = jsonBody,
        referer = referer,
        headers = headers + ("Content-Type" to "application/json")
    ).text
}
