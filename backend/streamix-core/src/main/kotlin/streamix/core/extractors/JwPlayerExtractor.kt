package streamix.core.extractors

import streamix.core.ProviderStream
import streamix.core.ProviderSubtitle
import streamix.core.StreamixExtractor
import streamix.core.StreamixExtractorRequest
import streamix.core.StreamixExtractorResult
import streamix.core.StreamixHttp
import streamix.core.StreamixM3u8Parser
import java.net.URI

class JwPlayerExtractor(
    private val http: StreamixHttp,
    override val domains: Set<String> = emptySet()
) : StreamixExtractor {
    override val id: String = "jwplayer"

    private val m3u8Parser = StreamixM3u8Parser()

    override suspend fun extract(request: StreamixExtractorRequest): StreamixExtractorResult {
        val response = http.get(request.url, headers = request.headers, referer = request.referer)
        return parseScript(response.text, request.url, request)
    }

    suspend fun parseScript(
        script: String,
        baseUrl: String,
        request: StreamixExtractorRequest
    ): StreamixExtractorResult {
        val sources = SOURCE_REGEX.findAll(script)
            .flatMap { parseSourceArray(it.groupValues[1]) }
            .toList()

        var streams = sources.map { source ->
            val cleanUrl = unescape(source.file)
            ProviderStream(
                providerId = request.providerId ?: id,
                url = fixUrl(cleanUrl, baseUrl),
                quality = source.label?.let(::qualityFromLabel),
                type = if (
                    source.type?.contains("mpegurl", true) == true ||
                    cleanUrl.contains(".m3u8", true) ||
                    cleanUrl.contains("master.txt", true)
                ) "M3U8" else "VIDEO",
                headers = request.headers,
                referer = request.referer ?: baseUrl
            )
        }

        if (streams.isEmpty()) {
            streams = M3U8_REGEX.findAll(script).map { match ->
                val cleanUrl = unescape(match.groupValues[1])
                ProviderStream(
                    providerId = request.providerId ?: id,
                    url = fixUrl(cleanUrl, baseUrl),
                    type = "M3U8",
                    headers = request.headers,
                    referer = request.referer ?: baseUrl
                )
            }.toList()
        }

        streams = expandMasterPlaylists(streams, request)

        val subtitles = TRACKS_REGEX.findAll(script)
            .flatMap { parseTrackArray(it.groupValues[1]) }
            .filter {
                val kind = it.kind.orEmpty()
                !it.file.isNullOrBlank() &&
                    !it.label.isNullOrBlank() &&
                    (kind.contains("caption", true) || kind.contains("subtitle", true))
            }
            .map {
                ProviderSubtitle(
                    url = fixUrl(unescape(it.file!!), baseUrl),
                    language = it.label,
                    headers = request.headers,
                    referer = request.referer ?: baseUrl
                )
            }
            .toList()

        return StreamixExtractorResult(streams, subtitles)
    }

    private suspend fun expandMasterPlaylists(
        streams: List<ProviderStream>,
        request: StreamixExtractorRequest
    ): List<ProviderStream> {
        val expanded = mutableListOf<ProviderStream>()

        for (stream in streams) {
            if (!isHlsPlaylist(stream.url)) {
                expanded += stream
                continue
            }

            val variants = runCatching {
                val response = http.get(
                    stream.url,
                    headers = stream.headers,
                    referer = stream.referer
                )
                m3u8Parser.parseMaster(
                    text = response.text,
                    baseUrl = response.url.ifBlank { stream.url },
                    providerId = request.providerId ?: id,
                    headers = stream.headers,
                    referer = stream.referer
                )
            }.getOrDefault(emptyList())

            if (variants.isEmpty()) {
                expanded += stream
            } else {
                expanded += variants
            }
        }

        return expanded.distinctBy { it.url to it.quality }

    }

    private fun isHlsPlaylist(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith("master.txt")

    }

    private fun parseSourceArray(array: String): List<Source> =
        OBJECT_REGEX.findAll(array).mapNotNull { match ->
            val text = match.groupValues[1]
            Source(
                file = field(text, "file") ?: return@mapNotNull null,
                label = field(text, "label"),
                type = field(text, "type")
            )
        }.toList()

    private fun parseTrackArray(array: String): List<Track> =
        OBJECT_REGEX.findAll(array).map { match ->
            val text = match.groupValues[1]
            Track(field(text, "file"), field(text, "kind"), field(text, "label"))
        }.toList()

    private fun field(text: String, name: String): String? =
        Regex("""["']?$name["']?\s*:\s*["']([^"']*)["']""")
            .find(text)?.groupValues?.getOrNull(1)

    private fun qualityFromLabel(label: String): Int? =
        Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
            .find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun unescape(value: String): String =
        value.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\\"", "\"")

    private fun fixUrl(url: String, baseUrl: String): String {
        val parsed = runCatching { URI(baseUrl) }.getOrNull()
        val origin = parsed?.let { it.scheme + "://" + it.authority }
            ?: baseUrl.substringBeforeLast("/")

        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> (parsed?.scheme ?: "https") + ":" + url
            url.startsWith("/") -> origin + url
            else -> baseUrl.substringBeforeLast("/").trimEnd('/') + "/" + url.trimStart('/')
        }
    }

    private data class Source(val file: String, val label: String?, val type: String?)
    private data class Track(val file: String?, val kind: String?, val label: String?)

    companion object {
        private val SOURCE_REGEX =
            Regex("""["']?sources["']?\s*:\s*(\[.*?\])""", RegexOption.DOT_MATCHES_ALL)
        private val TRACKS_REGEX =
            Regex("""["']?tracks["']?\s*:\s*(\[.*?\])""", RegexOption.DOT_MATCHES_ALL)
        private val M3U8_REGEX =
            Regex("""[:=]\s*["']([^"'\s]+(?:\.m3u8|master\.txt)[^"'\s]*)["']""")
        private val OBJECT_REGEX =
            Regex("""\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
    }
}
