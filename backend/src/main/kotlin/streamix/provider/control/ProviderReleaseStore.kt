package streamix.provider.control

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ProviderRelease(
    val providerId: String,
    val version: String,
    val upstream: String,
    val upstreamCommit: String,
    val artifactRef: String,
    val publishedAt: Instant,
    val previousVersion: String? = null,
    val artifactSha256: String? = null
)

/**
 * Release storage boundary for provider artifacts.
 *
 * The active provider remains untouched until an update has passed every
 * verification stage and the release is explicitly activated.
 */
interface ProviderReleaseStore {
    fun get(providerId: String, version: String): ProviderRelease?
    fun active(providerId: String): ProviderRelease?
    fun publish(release: ProviderRelease)
    fun activate(providerId: String, version: String): ProviderRelease
    fun rollback(providerId: String, targetVersion: String): ProviderRelease
}

/**
 * In-memory implementation for JVM tests and local runtime wiring.
 * Production persistence/artifact storage can implement the same boundary.
 */
class InMemoryProviderReleaseStore : ProviderReleaseStore {
    private val releases = ConcurrentHashMap<String, ConcurrentHashMap<String, ProviderRelease>>()
    private val activeVersions = ConcurrentHashMap<String, String>()

    override fun get(providerId: String, version: String): ProviderRelease? =
        releases[providerId.lowercase()]?.get(version)

    override fun active(providerId: String): ProviderRelease? {
        val key = providerId.lowercase()
        val version = activeVersions[key] ?: return null
        return releases[key]?.get(version)
    }

    override fun publish(release: ProviderRelease) {
        require(release.providerId.isNotBlank()) { "providerId must not be blank" }
        require(release.version.isNotBlank()) { "version must not be blank" }
        require(release.artifactRef.isNotBlank()) { "artifactRef must not be blank" }

        releases
            .computeIfAbsent(release.providerId.lowercase()) { ConcurrentHashMap() }[release.version] = release
    }

    override fun activate(providerId: String, version: String): ProviderRelease {
        val release = requireNotNull(get(providerId, version)) {
            "Provider release not found: $providerId@$version"
        }
        activeVersions[providerId.lowercase()] = release.version
        return release
    }

    override fun rollback(providerId: String, targetVersion: String): ProviderRelease =
        activate(providerId, targetVersion)
}


/**
 * Durable JVM implementation backed by a single atomically replaced JSON file.
 * The interface remains unchanged so deployments can choose their persistence
 * mechanism without changing the update pipeline.
 */
class FileProviderReleaseStore(
    private val stateFile: java.nio.file.Path
) : ProviderReleaseStore {
    private val lock = Any()
    private val releases = linkedMapOf<String, LinkedHashMap<String, ProviderRelease>>()
    private val activeVersions = linkedMapOf<String, String>()

    init {
        synchronized(lock) { load() }
    }

    override fun get(providerId: String, version: String): ProviderRelease? =
        synchronized(lock) { releases[providerId.lowercase()]?.get(version) }

    override fun active(providerId: String): ProviderRelease? =
        synchronized(lock) {
            val key = providerId.lowercase()
            activeVersions[key]?.let { releases[key]?.get(it) }
        }

    override fun publish(release: ProviderRelease) {
        require(release.providerId.isNotBlank()) { "providerId must not be blank" }
        require(release.version.isNotBlank()) { "version must not be blank" }
        require(release.artifactRef.isNotBlank()) { "artifactRef must not be blank" }
        synchronized(lock) {
            val key = release.providerId.lowercase()
            releases.getOrPut(key) { linkedMapOf() }[release.version] = release
            persist()
        }
    }

    override fun activate(providerId: String, version: String): ProviderRelease =
        synchronized(lock) {
            val key = providerId.lowercase()
            val release = requireNotNull(releases[key]?.get(version)) {
                "Provider release not found: $providerId@$version"
            }
            activeVersions[key] = release.version
            persist()
            release
        }

    override fun rollback(providerId: String, targetVersion: String): ProviderRelease =
        activate(providerId, targetVersion)

    private fun load() {
        if (!java.nio.file.Files.exists(stateFile)) return
        val root = org.json.JSONObject(java.nio.file.Files.readString(stateFile))
        val active = root.optJSONObject("activeVersions") ?: org.json.JSONObject()
        val stored = root.optJSONArray("releases") ?: org.json.JSONArray()
        for (i in 0 until stored.length()) {
            val value = stored.getJSONObject(i)
            val release = ProviderRelease(
                providerId = value.getString("providerId"),
                version = value.getString("version"),
                upstream = value.getString("upstream"),
                upstreamCommit = value.getString("upstreamCommit"),
                artifactRef = value.getString("artifactRef"),
                publishedAt = Instant.parse(value.getString("publishedAt")),
                previousVersion = value.optString("previousVersion").takeIf { it.isNotBlank() },
                artifactSha256 = value.optString("artifactSha256").takeIf { it.isNotBlank() }
            )
            releases.getOrPut(release.providerId.lowercase()) { linkedMapOf() }[release.version] = release
        }
        active.keys().forEach { key ->
            activeVersions[key] = active.getString(key)
        }
    }

    private fun persist() {
        val root = org.json.JSONObject()
        val active = org.json.JSONObject()
        activeVersions.forEach { (providerId, version) -> active.put(providerId, version) }
        root.put("activeVersions", active)

        val stored = org.json.JSONArray()
        releases.values.forEach { byVersion ->
            byVersion.values.forEach { release ->
                stored.put(
                    org.json.JSONObject()
                        .put("providerId", release.providerId)
                        .put("version", release.version)
                        .put("upstream", release.upstream)
                        .put("upstreamCommit", release.upstreamCommit)
                        .put("artifactRef", release.artifactRef)
                        .put("publishedAt", release.publishedAt.toString())
                        .apply {
                            release.previousVersion?.let { put("previousVersion", it) }
                            release.artifactSha256?.let { put("artifactSha256", it) }
                        }
                )
            }
        }
        root.put("releases", stored)

        val parent = stateFile.parent
        if (parent != null) java.nio.file.Files.createDirectories(parent)
        val temp = stateFile.resolveSibling(stateFile.fileName.toString() + ".tmp")
        java.nio.file.Files.writeString(
            temp,
            root.toString(2),
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
            java.nio.file.StandardOpenOption.WRITE
        )
        try {
            java.nio.file.Files.move(
                temp,
                stateFile,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                temp,
                stateFile,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
