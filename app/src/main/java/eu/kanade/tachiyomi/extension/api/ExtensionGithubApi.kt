package eu.kanade.tachiyomi.extension.api

import ani.dantotsu.asyncMap
import ani.dantotsu.media.MediaType
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AvailableAnimeSources
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import java.io.ByteArrayInputStream
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

internal class ExtensionGithubApi {
    private val networkService: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private fun List<ExtensionSourceJsonObject>.toAnimeExtensionSources(): List<AvailableAnimeSources> {
        return this.map {
            AvailableAnimeSources(
                id = it.id,
                lang = it.lang,
                name = it.name,
                baseUrl = it.baseUrl,
            )
        }
    }

    private fun cleanRepoUrl(url: String): String {
        return url.trim()
            .removeSuffix("/")
            .removeSuffix("/index.min.json")
            .removeSuffix("/index.json")
            .removeSuffix("/repo.json")
            .removeSuffix("/index.pb")
            .removeSuffix("/")
    }

    private fun List<ExtensionJsonObject>.toAnimeExtensions(repository: String): List<AnimeExtension.Available> {
        val cleanRepo = cleanRepoUrl(repository)
        val badge = ani.dantotsu.parsers.ExtensionRepoMetaHelper.getRepoBadgeName(repository)
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                val majorLibVersion = libVersion.toInt()
                majorLibVersion >= ExtensionLoader.ANIME_LIB_VERSION_MIN && majorLibVersion <= ExtensionLoader.ANIME_LIB_VERSION_MAX
            }
            .map {
                AnimeExtension.Available(
                    name = it.name.removePrefix("Aniyomi: ").removePrefix("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    hasReadme = it.hasReadme == 1,
                    hasChangelog = it.hasChangelog == 1,
                    sources = it.sources?.toAnimeExtensionSources().orEmpty(),
                    apkName = it.apk,
                    repository = repository,
                    iconUrl = it.iconUrl ?: "$cleanRepo/icon/${it.pkg}.png",
                    repoName = badge,
                )
            }
    }

    private fun ByteArray.decompressIfGzipped(): ByteArray {
        if (this.size < 2) return this
        val isGzip = (this[0].toInt() and 0xFF == 0x1F) && (this[1].toInt() and 0xFF == 0x8B)
        if (!isGzip) return this
        return try {
            ByteArrayInputStream(this).source().gzip().buffer().readByteArray()
        } catch (e: Throwable) {
            this
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private suspend fun fetchExtensions(
        repoUrl: String,
        mediaType: MediaType,
        originalUrl: String = repoUrl
    ): List<ExtensionJsonObject> {
        val cleanBase = cleanRepoUrl(repoUrl)
        val candidateUrls = mutableListOf<String>()

        val trimmed = repoUrl.trim()
        if (trimmed.endsWith(".json") || trimmed.endsWith(".pb")) {
            candidateUrls.add(trimmed)
        }

        val defaultEndpoints = when (mediaType) {
            MediaType.ANIME -> listOf(
                "$cleanBase/index.pb",
                "$cleanBase/repo.json",
                "$cleanBase/index.min.json",
                "$cleanBase/index.json"
            )

        }

        for (endpoint in defaultEndpoints) {
            if (!candidateUrls.contains(endpoint)) {
                candidateUrls.add(endpoint)
            }
        }

        for (targetUrl in candidateUrls) {
            try {
                val response = try {
                    networkService.client
                        .newCall(GET(targetUrl))
                        .awaitSuccess()
                } catch (_: Throwable) {
                    continue
                }

                val rawBytes = response.body.bytes()
                if (rawBytes.isEmpty()) continue

                val responseBytes = rawBytes.decompressIfGzipped()
                if (responseBytes.isEmpty()) continue
                val firstByte = responseBytes[0]

                if (firstByte == 0x5B.toByte()) { // '[' - JSON array of extensions
                    val bodyString = responseBytes.toString(Charsets.UTF_8)
                    val list = runCatching {
                        json.decodeFromString<List<ExtensionJsonObject>>(bodyString)
                    }.getOrNull()

                    if (!list.isNullOrEmpty()) {
                        val hasDeprecation = list.any {
                            it.pkg.contains("keiyoushi") || it.pkg.contains("animiru") ||
                            it.name.contains("Outdated App", ignoreCase = true) ||
                            it.name.contains("Update to Mihon", ignoreCase = true) ||
                            it.name.contains("switch to Animiru", ignoreCase = true)
                        }
                        if (hasDeprecation && !targetUrl.endsWith("index.pb")) {
                            val pbUrl = "$cleanBase/index.pb"
                            return runCatching { fetchExtensions(pbUrl, mediaType, originalUrl) }.getOrElse { list }
                        }
                        return list
                    }
                } else {
                    // JSON Object '{' or Protobuf
                    if (firstByte == 0x7B.toByte()) { // '{'
                        val bodyString = responseBytes.toString(Charsets.UTF_8)
                        if (bodyString.contains("\"index_v2\"") || bodyString.contains("\"indexV2\"")) {
                            val legacyRepo = runCatching {
                                json.decodeFromString<NetworkLegacyExtensionRepo>(bodyString)
                            }.getOrNull()
                            if (legacyRepo?.meta != null) {
                                ani.dantotsu.parsers.ExtensionRepoMetaHelper.saveMeta(
                                    originalUrl,
                                    name = legacyRepo.meta.name,
                                    shortName = legacyRepo.meta.shortName,
                                    website = legacyRepo.meta.website,
                                    discord = null
                                )
                            }
                            val nextUrl = legacyRepo?.indexV2
                            if (nextUrl != null) {
                                return fetchExtensions(nextUrl, mediaType, originalUrl)
                            }
                        }

                        // If it's a legacy repo.json with only metadata, continue to next candidate
                        val isLegacyMetaOnly = bodyString.contains("\"meta\"") &&
                                !bodyString.contains("\"extensionList\"") &&
                                !bodyString.contains("\"extensions\"")

                        if (isLegacyMetaOnly) {
                            val legacy = runCatching {
                                json.decodeFromString<NetworkLegacyExtensionRepo>(bodyString)
                            }.getOrNull()
                            if (legacy?.meta != null) {
                                ani.dantotsu.parsers.ExtensionRepoMetaHelper.saveMeta(
                                    originalUrl,
                                    name = legacy.meta.name,
                                    shortName = legacy.meta.shortName,
                                    website = legacy.meta.website,
                                    discord = null
                                )
                            }
                            continue
                        }
                    }

                    val prefix = when (mediaType) {
                        MediaType.ANIME -> "Aniyomi: "
                        else -> ""
                    }

                    // Decode the anime extension store
                    if (mediaType == MediaType.ANIME) {
                        val animeStore = if (firstByte == 0x7B.toByte()) {
                            val bodyString = responseBytes.toString(Charsets.UTF_8)
                            runCatching { json.decodeFromString<NetworkAnimeExtensionStore>(bodyString) }.getOrNull()
                        } else {
                            runCatching { ProtoBuf.decodeFromByteArray<NetworkAnimeExtensionStore>(responseBytes) }.getOrNull()
                        }

                        if (animeStore != null) {
                            ani.dantotsu.parsers.ExtensionRepoMetaHelper.saveMeta(
                                originalUrl,
                                name = animeStore.name,
                                shortName = animeStore.badgeLabel,
                                website = animeStore.contact.website,
                                discord = animeStore.contact.discord
                            )

                            val extensionsList = if (animeStore.extensionListUrl != null) {
                                val listUrl = if (animeStore.extensionListUrl.startsWith("http")) {
                                    animeStore.extensionListUrl
                                } else {
                                    "$cleanBase/${animeStore.extensionListUrl.removePrefix("/")}"
                                }
                                val listResponse = runCatching {
                                    networkService.client.newCall(GET(listUrl)).awaitSuccess()
                                }.getOrNull()

                                val listBytes = listResponse?.body?.bytes()?.decompressIfGzipped()
                                if (listBytes != null && listBytes.isNotEmpty() && listBytes[0] == 0x7B.toByte()) {
                                    runCatching {
                                        json.decodeFromString<NetworkAnimeExtensionStore.ExtensionList>(listBytes.toString(Charsets.UTF_8))
                                    }.getOrNull()
                                } else if (listBytes != null && listBytes.isNotEmpty()) {
                                    runCatching {
                                        ProtoBuf.decodeFromByteArray<NetworkAnimeExtensionStore.ExtensionList>(listBytes)
                                    }.getOrNull()
                                } else {
                                    null
                                }
                            } else {
                                animeStore.extensionList
                            }

                            if (extensionsList != null && extensionsList.extensions.isNotEmpty()) {
                                return extensionsList.extensions.map { ext ->
                                    val sourcesMapped = ext.sources.map { src ->
                                        ExtensionSourceJsonObject(
                                            id = src.id,
                                            lang = src.language,
                                            name = src.name,
                                            baseUrl = src.homeUrl
                                        )
                                    }
                                    val primaryLang = ext.sources.firstOrNull()?.language ?: "all"
                                    val prefixName = if (ext.name.startsWith(prefix)) ext.name else "$prefix${ext.name}"
                                    val extLib = ext.extensionLib.ifBlank {
                                        ext.versionName.substringBefore('.').takeIf { it.toDoubleOrNull() != null }
                                    }
                                    ExtensionJsonObject(
                                        name = prefixName,
                                        pkg = ext.packageName,
                                        apk = ext.resources.apkUrl,
                                        lang = primaryLang,
                                        code = ext.versionCode,
                                        version = ext.versionName,
                                        nsfw = if (ext.contentWarning == NetworkAnimeExtensionStore.ContentWarning.NSFW || ext.contentWarning == NetworkAnimeExtensionStore.ContentWarning.MIXED) 1 else 0,
                                        hasReadme = 0,
                                        hasChangelog = 0,
                                        sources = sourcesMapped,
                                        iconUrl = ext.resources.iconUrl,
                                        extensionLib = extLib,
                                    )
                                }
                            }
                        }
                    }

                    // Fallback extension store
                    val store: NetworkExtensionStore? = if (firstByte == 0x7B.toByte()) {
                        val bodyString = responseBytes.toString(Charsets.UTF_8)
                        runCatching { json.decodeFromString<NetworkExtensionStore>(bodyString) }.getOrNull()
                    } else {
                        runCatching { ProtoBuf.decodeFromByteArray<NetworkExtensionStore>(responseBytes) }.getOrNull()
                    }

                    if (store != null) {
                        ani.dantotsu.parsers.ExtensionRepoMetaHelper.saveMeta(
                            originalUrl,
                            name = store.name,
                            shortName = store.badgeLabel,
                            website = store.contact.website,
                            discord = store.contact.discord
                        )
                        val resolvedList: NetworkExtensionStore.ExtensionList? = if (store.extensionListUrl != null) {
                            val listUrl = if (store.extensionListUrl.startsWith("http")) {
                                store.extensionListUrl
                            } else {
                                "$cleanBase/${store.extensionListUrl.removePrefix("/")}"
                            }
                            val listResponse = runCatching {
                                networkService.client.newCall(GET(listUrl)).awaitSuccess()
                            }.getOrNull()

                            val listBytes = listResponse?.body?.bytes()?.decompressIfGzipped()
                            if (listBytes != null && listBytes.isNotEmpty() && listBytes[0] == 0x7B.toByte()) { // '{'
                                runCatching {
                                    json.decodeFromString<NetworkExtensionStore.ExtensionList>(listBytes.toString(Charsets.UTF_8))
                                }.getOrNull()
                            } else if (listBytes != null && listBytes.isNotEmpty()) {
                                runCatching {
                                    ProtoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(listBytes)
                                }.getOrNull()
                            } else {
                                null
                            }
                        } else {
                            store.extensionList
                        }

                        if (resolvedList != null && resolvedList.extensions.isNotEmpty()) {
                            return resolvedList.extensions.map { ext ->
                                val sourcesMapped = ext.sources.map { src ->
                                    ExtensionSourceJsonObject(
                                        id = src.id,
                                        lang = src.language,
                                        name = src.name,
                                        baseUrl = src.homeUrl
                                    )
                                }
                                val primaryLang = ext.sources.firstOrNull()?.language ?: "all"
                                val prefixName = if (ext.name.startsWith(prefix)) ext.name else "$prefix${ext.name}"
                                ExtensionJsonObject(
                                    name = prefixName,
                                    pkg = ext.packageName,
                                    apk = ext.resources.apkUrl,
                                    lang = primaryLang,
                                    code = ext.versionCode,
                                    version = ext.versionName,
                                    nsfw = if (ext.contentWarning == NetworkExtensionStore.ContentWarning.NSFW || ext.contentWarning == NetworkExtensionStore.ContentWarning.MIXED) 1 else 0,
                                    hasReadme = 0,
                                    hasChangelog = 0,
                                    sources = sourcesMapped,
                                    iconUrl = ext.resources.iconUrl,
                                    extensionLib = ext.extensionLib,
                                )
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Logger.log("Failed candidate $targetUrl for $repoUrl: $e")
            }
        }

        return emptyList()
    }

    suspend fun findAnimeExtensions(): List<AnimeExtension.Available> {
        return withIOContext {
            val extensions: ArrayList<AnimeExtension.Available> = arrayListOf()
            val repos = PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos).toMutableList()

            repos.asyncMap {
                try {
                    var repoExtensions = fetchExtensions(it, MediaType.ANIME)
                    if (repoExtensions.isEmpty()) {
                        val fallback = fallbackRepoUrl(it)
                        if (fallback != null) {
                            repoExtensions = fetchExtensions(fallback, MediaType.ANIME)
                        }
                    }
                    extensions.addAll(repoExtensions.toAnimeExtensions(it))
                } catch (e: Throwable) {
                    Logger.log("Failed to get anime extensions")
                    Logger.log(e)
                }
            }
            extensions
        }
    }

    fun getAnimeApkUrl(extension: AnimeExtension.Available): String {
        return if (extension.apkName.startsWith("http")) {
            extension.apkName
        } else {
            "${cleanRepoUrl(extension.repository)}/apk/${extension.apkName.removePrefix("/")}"
        }
    }


}
