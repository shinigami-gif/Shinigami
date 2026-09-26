package com.baseprovider.streamix

import com.baseprovider.config.ProviderConfig
import com.baseprovider.model.FieldType
import com.baseprovider.model.MetadataPackage
import com.baseprovider.model.SelectorResolver
import com.baseprovider.model.safeCleanBloat
import com.baseprovider.model.safeDeduplicate
import com.baseprovider.model.safeExtractEpNum
import com.baseprovider.model.safeExtractImage
import com.baseprovider.model.safeExtractYear
import com.baseprovider.model.fixUrlSmart
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class StreamixProviderMapper(private val config: ProviderConfig) {
    private val excludeRegexCache = ConcurrentHashMap<String, Regex?>()
    private val hrefCleanCache = ConcurrentHashMap<String, Regex?>()
    private val movieRegexCache = ConcurrentHashMap<String, Regex?>()
    private val listingFirstSegments = config.mainPageLists
        .map { it.first.trim('/').substringBefore('/') }
        .filter { it.isNotBlank() }
        .toSet()

    fun isListingUrl(url: String): Boolean {
        val taxonomy = Regex(
            """^https?://[^/]+/(category|categories|tags?|genre|genres|country|countries|director|cast|artist|year|quality|network|production|studio|actor|actress)(/|$)""",
            RegexOption.IGNORE_CASE
        )
        if (taxonomy.containsMatchIn(url)) return true
        return runCatching {
            val path = URI(url).path?.trim('/') ?: return false
            if (path.isEmpty()) return false
            val first = path.substringBefore('/')
            first in listingFirstSegments && (!path.contains('/') || path.contains("/page/"))
        }.getOrDefault(false)
    }

    fun looksLikeMovieUrl(url: String): Boolean {
        if (config.tvPathSegment.isNotBlank() && url.contains(config.tvPathSegment)) return false
        if (listOf("/tv/", "/series/", "/anime/", "/drama/", "/episode/", "/eps/").any { url.contains(it, true) }) return false
        if (config.movieUrlRegex.isNotBlank()) {
            val rx = movieRegexCache.computeIfAbsent(config.movieUrlRegex) {
                runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull()
            }
            if (rx?.containsMatchIn(url) == true) return true
        }
        return Regex("""-\d{4}/?$""").containsMatchIn(url)
    }

    fun toAnime(element: Element, baseUrl: String = config.mainUrl): streamix.core.ProviderAnime? {
        val titleElement = if (config.searchTitle.isNotBlank()) {
            SelectorResolver.selectValidated(element, config.searchTitle, "\${config.id}:searchTitle", FieldType.TITLE) { it.text()?.trim() }
        } else element.selectFirst("h2, h3, a[title]")
        val rawTitle = titleElement?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: titleElement?.attr("title")?.trim() ?: return null
        val title = rawTitle.safeCleanBloat(rawTitle, config.bloatRegex).safeDeduplicate()

        val hrefElement = if (config.searchHref.isNotBlank()) {
            SelectorResolver.selectFirst(element, config.searchHref, "\${config.id}:searchHref")
                ?: element.selectFirst("a[href]")
        } else element.selectFirst("a[href]") ?: element.parent()?.selectFirst("a[href]")
        var href = fixUrlSmart(hrefElement?.attr("href"), baseUrl)
        if (href.isBlank()) return null

        hrefCleanRegex()?.let { rx ->
            if (config.hrefCleanReplace.isNotBlank()) href = runCatching { href.replace(rx, config.hrefCleanReplace) }.getOrDefault(href)
        }
        if (isListingUrl(href) || isExcluded(href)) return null

        val poster = if (config.searchPoster.isNotBlank()) {
            SelectorResolver.selectValidated(element, config.searchPoster, "\${config.id}:searchPoster", FieldType.POSTER) {
                it.safeExtractImage(config.attrImage)
            }?.safeExtractImage(config.attrImage)
        } else element.selectFirst("img")?.safeExtractImage(config.attrImage)

        val rating = if (config.searchRating.isNotBlank())
            SelectorResolver.selectFirst(element, config.searchRating, "\${config.id}:searchRating")?.text()
        else null

        return streamix.core.ProviderAnime(
            providerId = config.id, id = href, title = title, url = href,
            poster = poster, rating = rating
        )
    }

    fun toAnimeFromJson(item: JSONObject, baseUrl: String = config.mainUrl): streamix.core.ProviderAnime? {
        val rawTitle = item.optString(config.searchJsonTitle).trim()
        if (rawTitle.isBlank()) return null
        val title = rawTitle.safeCleanBloat(rawTitle, config.bloatRegex).safeDeduplicate()
        if (title.isBlank()) return null

        val slug = item.optString(config.searchJsonHref).trim()
        if (slug.isBlank()) return null
        val posterRaw = item.optString(config.searchJsonPoster).trim()
        val poster = when {
            posterRaw.isBlank() -> null
            posterRaw.startsWith("http", true) -> posterRaw
            config.searchJsonPosterPrefix.isNotBlank() -> fixUrlSmart(config.searchJsonPosterPrefix + posterRaw, baseUrl)
            else -> fixUrlSmart(posterRaw, baseUrl)
        }
        val type = item.optString(config.searchJsonType).trim()
        val finalBase = if (type.contains("series", true) || type.contains("tv", true))
            config.seriesUrl?.takeIf { it.isNotBlank() } ?: baseUrl else config.mainUrl
        val url = fixUrlSmart(slug, finalBase)
        if (url.isBlank() || isListingUrl(url) || isExcluded(url)) return null
        return streamix.core.ProviderAnime(providerId = config.id, id = url, title = title, url = url, poster = poster)
    }

    fun extractMetadata(document: Document, currentUrl: String): MetadataPackage {
        val rawTitle = if (config.loadTitle.isNotBlank())
            SelectorResolver.textValidated(document, config.loadTitle, "\${config.id}:loadTitle", FieldType.TITLE)
        else null
        val finalTitle = rawTitle?.safeCleanBloat(rawTitle, config.bloatRegex)?.safeDeduplicate()
            ?.takeIf { it.isNotBlank() } ?: "Unknown Title"

        val poster = if (config.loadPoster.isNotBlank())
            SelectorResolver.selectValidated(document, config.loadPoster, "\${config.id}:loadPoster", FieldType.POSTER) {
                it.safeExtractImage(config.attrImage)
            }?.safeExtractImage(config.attrImage)
        else null

        val banner = if (config.loadBanner.isNotBlank())
            SelectorResolver.selectFirst(document, config.loadBanner, "\${config.id}:loadBanner")?.safeExtractImage(config.attrImage)
        else null

        val description = if (config.loadDesc.isNotBlank())
            SelectorResolver.text(document, config.loadDesc, "\${config.id}:loadDesc").orEmpty() else ""

        val infoText = if (config.loadInfoBox.isNotBlank())
            SelectorResolver.text(document, config.loadInfoBox, "\${config.id}:loadInfoBox").orEmpty() else ""

        val year = infoText.safeExtractYear() ?: if (config.yearSelector.isNotBlank() && config.yearExtractorRegex.isNotBlank()) {
            val text = SelectorResolver.text(document, config.yearSelector, "\${config.id}:yearSelector").orEmpty()
            runCatching { Regex(config.yearExtractorRegex).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() }.getOrNull()
        } else null

        val statusText = if (config.loadStatus.isNotBlank())
            SelectorResolver.text(document, config.loadStatus, "\${config.id}:loadStatus") else null

        val tags = if (config.loadTags.isNotBlank())
            SelectorResolver.select(document, config.loadTags, "\${config.id}:loadTags")
                .map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        else emptyList()

        val rating = if (config.loadRating.isNotBlank())
            SelectorResolver.text(document, config.loadRating, "\${config.id}:loadRating") else null

        val imdbId = if (config.imdbExternal.isNotBlank()) {
            SelectorResolver.selectFirst(document, config.imdbExternal, "\${config.id}:imdbExternal")
                ?.let { firstAttr(it, config.attrHref) }?.split("/")?.firstOrNull { it.startsWith("tt") }
        } else null

        val tmdbId = if (config.tmdbExternal.isNotBlank()) {
            SelectorResolver.selectFirst(document, config.tmdbExternal, "\${config.id}:tmdbExternal")
                ?.let { firstAttr(it, config.attrHref) }?.split("/")?.lastOrNull()?.toIntOrNull()
        } else null

        val trailer = if (config.loadTrailer.isNotBlank()) {
            SelectorResolver.selectFirst(document, config.loadTrailer, "\${config.id}:loadTrailer")?.let { element ->
                val raw = if (element.tagName().equals("iframe", true))
                    firstAttr(element, config.iframeSources) else firstAttr(element, config.attrHref)
                raw?.let { fixUrlSmart(it, currentUrl) }
            }
        } else null

        return MetadataPackage(
            title = finalTitle, poster = poster.orEmpty(), banner = banner,
            description = description, year = year, statusText = statusText,
            tags = tags, rating = rating,
            status = if (statusText?.contains(config.ongoingKeyword, true) == true)
                StreamixShowStatus.Ongoing else StreamixShowStatus.Completed,
            imdbId = imdbId, tmdbId = tmdbId, trailer = trailer
        )
    }

    fun parseEpisode(element: Element, currentUrl: String): streamix.core.ProviderEpisode? {
        val anchor = if (config.episodeHref.isNotBlank())
            SelectorResolver.selectFirst(element, config.episodeHref, "\${config.id}:episodeHref")
        else null
        ?: element.selectFirst("a[href]")
        ?: if (element.tagName() == "a") element else null
        ?: return null

        val rawHref = anchor.attr("href").trim()
        if (rawHref.isBlank()) return null
        val href = fixUrlSmart(rawHref, currentUrl)
        if (href.isBlank()) return null

        val title = if (config.episodeTitle.isNotBlank())
            SelectorResolver.text(element, config.episodeTitle, "\${config.id}:episodeTitle")
        else anchor.text().trim().takeIf { it.isNotBlank() }

        val number = if (config.episodeNum.isNotBlank())
            SelectorResolver.text(element, config.episodeNum, "\${config.id}:episodeNum")?.safeExtractEpNum()
        else null ?: title?.safeExtractEpNum() ?: element.text().safeExtractEpNum()

        val description = if (config.episodeDesc.isNotBlank())
            SelectorResolver.text(element, config.episodeDesc, "\${config.id}:episodeDesc") else null
        val duration = if (config.episodeTime.isNotBlank())
            SelectorResolver.text(element, config.episodeTime, "\${config.id}:episodeTime") else null

        val finalUrl = config.episodeDataUrlPattern.replace("{url}", href)
        return streamix.core.ProviderEpisode(
            providerId = config.id, number = number ?: 0, id = finalUrl, title = title,
            url = finalUrl, description = description, duration = duration
        )
    }

    private fun hrefCleanRegex(): Regex? {
        val pattern = config.hrefCleanRegex
        if (pattern.isBlank()) return null
        return hrefCleanCache.computeIfAbsent(pattern) { runCatching { Regex(it) }.getOrNull() }
    }

    private fun isExcluded(url: String): Boolean {
        for (raw in config.excludeUrlPatterns.split(',')) {
            val pattern = raw.trim()
            if (pattern.isBlank()) continue
            val rx = excludeRegexCache.computeIfAbsent(pattern) {
                runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull()
            }
            if (rx?.containsMatchIn(url) == true) return true
        }
        return false
    }

    private fun firstAttr(element: Element, attrs: String): String? =
        attrs.split(',').asSequence().map { it.trim() }.filter { it.isNotBlank() }
            .map { element.attr(it).trim() }.firstOrNull { it.isNotBlank() }
}
