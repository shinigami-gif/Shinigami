package com.baseprovider.extractor

import com.baseprovider.streamix.*


class BloggerVideo : StreamixExtractorApi() {
    override var name = "BloggerVideo"
    override var mainUrl = "https://www.blogger.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?,
        subtitleCallback: (StreamixSubtitleFile) -> Unit,
            callback: (StreamixExtractorLink) -> Unit) {
        val doc = app.get(url, referer = referer).document
        doc.select("video source[src], video[src], iframe[src]")
            .forEach { el ->
            val src = el.attr("src")
            if (src.isNotBlank() && (src.contains(".mp4") || src.contains(".m3u8") || src.contains("youtube"))) {
                loadExtractorWithFallbackCustom(
                    src, url, subtitleCallback,
                    callback = callback,
                    providerTag = name,
                    callChain = "BloggerVideo"
                )
            }
        }
    }
}
