package com.animein

import com.baseprovider.streamix.ProviderExtractorsNative
import com.baseprovider.streamix.StreamixRuntime
import streamix.core.ProviderAnime
import streamix.core.ProviderEpisode
import streamix.core.ProviderStream
import streamix.core.StreamixProvider
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class AnimeinProvider : StreamixProvider {
    override val id = "animein"
    private companion object {
        const val API = "https://xyz-api.animein.net"
        const val ALT = "https://api.animein.net"
        const val GATE = "https://gate.nextanimelist.com"
        const val VER = "5.2.2"
        val headers = mapOf("User-Agent" to "okhttp/4.12.0", "apk_ver" to VER)
    }
    @Volatile private var base = API
    @Volatile private var resolved = false

    private fun auth() = "id_user=0&key_client=guest&apk_ver=" + VER

    private suspend fun get(url: String): JSONObject? {
        val r = runCatching { StreamixRuntime.http?.get(url, headers = headers, referer = base) }.getOrNull() ?: return null
        val t = r.text.trim()
        return if (t.startsWith("{")) runCatching { JSONObject(t) }.getOrNull() else null
    }

    private suspend fun resolveBase(force: Boolean = false) {
        if (resolved && !force) return
        for (host in listOf(base, API, ALT, GATE).distinct()) {
            val root = get(host + "/data/setup/data?" + auth()) ?: continue
            val value = root.optJSONObject("data")?.optJSONObject("domain_api")?.opt("value")?.toString()
            if (!value.isNullOrBlank()) {
                base = if (value.startsWith("http", true)) value.trimEnd('/') else "https://" + value.trimEnd('/')
                break
            }
        }
        resolved = true
    }

    private fun endpoint(path: String, params: Map<String,String> = emptyMap()): String {
        val q = buildList {
            add(auth())
            params.forEach { add(it.key + "=" + URLEncoder.encode(it.value, "UTF-8")) }
        }.joinToString("&")
        return base + "/" + path.trimStart('/') + "?" + q
    }

    private suspend fun api(path: String, params: Map<String,String> = emptyMap()): JSONObject? {
        resolveBase()
        for (host in listOf(base, API, ALT).distinct()) {
            val target = endpoint(path, params).replaceFirst(base, host)
            val root = get(target) ?: continue
            if (!root.optBoolean("error", false)) { base = host; return root }
        }
        resolveBase(true)
        return get(endpoint(path, params))
    }

    private fun str(o: JSONObject?, vararg keys: String): String? {
        for (k in keys) {
            val v = o?.opt(k) ?: continue
            if (v == JSONObject.NULL || v is JSONObject || v is JSONArray || v is Boolean) continue
            val s = v.toString().trim()
            if (s.isNotBlank() && !s.equals("null", true)) return s
        }
        return null
    }

    private fun array(root: JSONObject?, vararg keys: String): JSONArray {
        val d = root?.opt("data")
        if (d is JSONArray) return d
        if (d is JSONObject) for (k in keys) d.optJSONArray(k)?.let { return it }
        for (k in keys) root?.optJSONArray(k)?.let { return it }
        return JSONArray()
    }

    private fun absolute(raw: String?): String? {
        val s = raw?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) } ?: return null
        return when {
            s.startsWith("//") -> "https:" + s
            s.contains("://") -> s
            else -> base + "/" + s.trimStart('/')
        }
    }

    override suspend fun search(query: String, page: Int): List<ProviderAnime> {
        if (query.isBlank()) return emptyList()
        val root = api("data/movie/find", mapOf("page" to page.toString(), "query" to query))
            ?: api("3/2/explore/movie", mapOf("page" to page.toString(), "q" to query))
            ?: return emptyList()
        val items = array(root, "movie", "movies", "list", "items", "results")
        return buildList {
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val itemId = str(o, "id", "id_movie") ?: continue
                val title = str(o, "title", "movie_title") ?: continue
                add(ProviderAnime(id, itemId, title, itemId, absolute(str(o, "image_poster", "image_cover", "poster", "image")),
                    year = str(o, "aired_start")?.take(4)?.toIntOrNull() ?: str(o, "year")?.toIntOrNull()))
            }
        }.distinctBy { it.id }
    }

    override suspend fun detail(anime: ProviderAnime): ProviderAnime? {
        val movieId = anime.id.substringAfterLast("/").substringBefore("?")
        val root = api("3/2/movie/detail/" + movieId) ?: return null
        val data = root.optJSONObject("data") ?: return null
        val movie = data.optJSONObject("movie") ?: data
        return anime.copy(
            title = str(movie, "title", "movie_title") ?: anime.title,
            poster = absolute(str(movie, "image_poster", "image_cover")) ?: anime.poster,
            banner = absolute(str(movie, "image_cover")) ?: anime.banner,
            description = str(movie, "synopsis", "description") ?: anime.description,
            year = str(movie, "aired_start")?.take(4)?.toIntOrNull() ?: str(movie, "year")?.toIntOrNull() ?: anime.year,
            status = str(movie, "status") ?: anime.status
        )
    }

    override suspend fun episodes(anime: ProviderAnime): List<ProviderEpisode> {
        val movieId = anime.id.substringAfterLast("/").substringBefore("?")
        val root = api("3/2/movie/episode/" + movieId) ?: return emptyList()
        val items = array(root, "episode", "episodes", "list")
        return buildList {
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val epId = str(o, "id") ?: continue
                val n = str(o, "index", "episode", "number")?.toDoubleOrNull()?.toInt() ?: i + 1
                add(ProviderEpisode(id, n, "animein://episode/" + epId, str(o, "title") ?: "Episode " + n))
            }
        }.sortedBy { it.number }
    }

    override suspend fun streams(episode: ProviderEpisode): List<ProviderStream> {
        val ep = episode.id.removePrefix("animein://episode/").substringAfterLast("/").substringBefore("?")
        if (ep.isBlank()) return emptyList()
        val root = api("3/2/episode/streamnew/" + ep) ?: return emptyList()
        val servers = root.optJSONObject("data")?.optJSONArray("server") ?: return emptyList()
        val out = mutableListOf<ProviderStream>()
        for (i in 0 until servers.length()) {
            val s = servers.optJSONObject(i) ?: continue
            val link = absolute(str(s, "link", "url")) ?: continue
            val q = Regex("\\d{3,4}").find(str(s, "quality").orEmpty())?.value?.toIntOrNull()
            val direct = str(s, "type").equals("direct", true) || link.contains(".mp4", true) || link.contains(".m3u8", true) || link.contains("googlevideo", true) || link.contains("storages.animein", true)
            if (direct) {
                out += ProviderStream(id, link, q, if (link.contains(".m3u8", true)) "m3u8" else "video", "id", referer = base)
            } else {
                runCatching {
                    ProviderExtractorsNative.resolve(link, base, callback = { x ->
                        out += ProviderStream(id, x.url, x.quality, x.type.name.lowercase(), "id", headers = x.headers, referer = x.referer.takeIf { it.isNotBlank() })
                    })
                }
            }
        }
        return out.distinctBy { it.url + "|" + (it.quality ?: -1) }
    }
}
