package com.baseprovider.extractor

import com.baseprovider.streamix.*


class PlayPutarIn : StreamixExtractorApi() {
    override var name = "PlayPutarIn"
    override var mainUrl = "https://play.putar.in"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?,
        subtitleCallback: (StreamixSubtitleFile) -> Unit,
            callback: (StreamixExtractorLink) -> Unit) {
        val targetUrl = url.substringAfter("?url=").let { java.net
            .URLDecoder.decode(it, "UTF-8") }
        if (targetUrl.isNotBlank() && targetUrl.startsWith("http")) {
            loadExtractorWithFallbackCustom(
                targetUrl,
                url,
                subtitleCallback,
                callback = callback,
                providerTag = this.name,
                callChain = "PlayPutarIn"
            )
        }
    }
}
