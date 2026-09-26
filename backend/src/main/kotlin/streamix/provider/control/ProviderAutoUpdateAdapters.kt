package streamix.provider.control

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import streamix.core.StreamixProvider
import streamix.provider.NativeStreamixProviderRuntime
import streamix.provider.ProviderRuntime
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Properties
import java.util.zip.ZipFile
import java.net.URLClassLoader
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONObject

class HatsuneProviderSourceFetcher(
    private val upstreamRoot: Path,
    private val repository: String = "HatsuneMikuUwU/AnimeX",
    private val gitExecutable: String = "git"
) : ProviderSourceFetcher {
    override suspend fun fetch(candidate: ProviderUpdateCandidate): ProviderSource =
        withContext(Dispatchers.IO) {
            require(candidate.upstream.equals("Hatsune/AnimeX", ignoreCase = true)) {
                "Unsupported provider upstream: " + candidate.upstream
            }
            require(candidate.targetCommit.isNotBlank()) { "targetCommit must not be blank" }
            Files.createDirectories(upstreamRoot)
            val checkout = upstreamRoot.resolve(
                candidate.providerId.lowercase() + "-" + candidate.targetCommit.take(12)
            )
            if (!Files.exists(checkout.resolve(".git"))) {
                if (Files.exists(checkout)) checkout.toFile().deleteRecursively()
                runCommand(
                    listOf(
                        gitExecutable, "clone", "--filter=blob:none", "--no-checkout",
                        "https://github.com/" + repository + ".git", checkout.toString()
                    )
                )
                runCommand(listOf(gitExecutable, "-C", checkout.toString(), "checkout", candidate.targetCommit))
            }
            val providerRoot = findProviderRoot(checkout, candidate.providerId)
                ?: error("Provider source not found for " + candidate.providerId + "@" + candidate.targetCommit)
            ProviderSource(
                providerId = candidate.providerId,
                version = candidate.targetVersion,
                upstreamCommit = candidate.targetCommit,
                sourceRef = providerRoot.toAbsolutePath().normalize().toString()
            )
        }

    private fun findProviderRoot(checkout: Path, providerId: String): Path? {
        var providerSourceDir: Path? = null
        Files.walk(checkout).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (!Files.isRegularFile(path) || !path.fileName.toString().endsWith(".kt")) continue
                val matches = runCatching {
                    val text = Files.readString(path)
                    Regex("(?m)\\b(?:class|object)\\s+\\w+\\s*:\\s*StreamixProvider\\b").containsMatchIn(text) &&
                    Regex("(?m)\\boverride\\s+val\\s+id\\s*=\\s*\"" + Regex.escape(providerId) + "\"").containsMatchIn(text)
                }.getOrDefault(false)
                if (matches) {
                    providerSourceDir = path.parent
                    break
                }
            }
        }
        return providerSourceDir?.let(::locateProviderRoot)
    }

    private fun locateProviderRoot(sourceDir: Path): Path {
        var current = sourceDir
        repeat(8) {
            val name = current.fileName?.toString().orEmpty()
            if (name.endsWith("Provider", ignoreCase = true)) return current
            current = current.parent ?: return@repeat
        }
        return sourceDir
    }
}

class HatsuneJvmProviderAdapter : ProviderJvmAdapter {
    override suspend fun adapt(source: ProviderSource): ProviderBuildInput {
        val root = Path.of(source.sourceRef)
        require(Files.exists(root)) { "Provider source does not exist: " + source.sourceRef }
        require(Files.walk(root).use { it.anyMatch { p -> p.toString().endsWith(".kt") } }) {
            "Provider source contains no Kotlin files: " + source.sourceRef
        }
        return ProviderBuildInput(
            providerId = source.providerId,
            version = source.version,
            upstreamCommit = source.upstreamCommit,
            sourceRef = root.toAbsolutePath().normalize().toString()
        )
    }
}

class GradleJvmProviderBuildRunner(
    private val backendRoot: Path,
    private val workRoot: Path = backendRoot.resolve("build/provider-updates"),
    private val gradleExecutable: String = backendRoot.resolve("gradlew").toAbsolutePath().normalize().toString()
) : ProviderBuildRunner {
    override suspend fun build(input: ProviderBuildInput): ProviderBuildArtifact =
        withContext(Dispatchers.IO) {
            val sourceRoot = Path.of(input.sourceRef)
            require(Files.exists(sourceRoot)) { "Provider source does not exist: " + input.sourceRef }
            Files.createDirectories(workRoot)
            val buildDir = workRoot.resolve(input.providerId + "-" + input.version)
            if (Files.exists(buildDir)) buildDir.toFile().deleteRecursively()
            Files.createDirectories(buildDir)

            val backendJar = ensureBackendJar()
            val streamixCoreJar = ensureStreamixCoreJar()
            val sourceDir = buildDir.resolve("src/main/kotlin")
            val resourcesDir = buildDir.resolve("src/main/resources")
            val kotlinSourceRoot = when {
                Files.isDirectory(sourceRoot.resolve("main/kotlin")) -> sourceRoot.resolve("main/kotlin")
                Files.isDirectory(sourceRoot.resolve("src/main/kotlin")) -> sourceRoot.resolve("src/main/kotlin")
                else -> sourceRoot
            }
            copyTree(kotlinSourceRoot, sourceDir)
            val providerClass = discoverProviderClass(sourceDir, input.providerId)
            val metadata = resourcesDir.resolve("META-INF/streamix-provider.properties")
            Files.createDirectories(metadata.parent)
            Files.writeString(
                metadata,
"providerId=" + input.providerId + "\n" +
                    "version=" + input.version + "\n" +
                    "upstreamCommit=" + input.upstreamCommit + "\n" +
                    "providerClass=" + providerClass + "\n"
            )
            Files.writeString(
                buildDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"shinigami-provider-" + input.providerId + "\"\n"
            )
            Files.writeString(
                buildDir.resolve("build.gradle.kts"),
                """
                    plugins {
                        id("org.jetbrains.kotlin.jvm") version "2.3.0"
                    }
                    repositories { mavenCentral() }
                    dependencies {
                        implementation(files("backend.jar"))
                        implementation(files("shinigami-core.jar"))
                        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                        implementation("org.jsoup:jsoup:1.22.1")
                        implementation("com.squareup.okhttp3:okhttp:5.4.0")
                        implementation("org.mozilla:rhino:1.8.1")
                        implementation("org.json:json:20260814")
                        implementation("com.google.code.gson:gson:2.13.2")
                    }
                    kotlin { jvmToolchain(17) }
                """.trimIndent()
            )
            Files.copy(backendJar, buildDir.resolve("backend.jar"), StandardCopyOption.REPLACE_EXISTING)
            Files.copy(streamixCoreJar, buildDir.resolve("shinigami-core.jar"), StandardCopyOption.REPLACE_EXISTING)
            runCommand(listOf(gradleExecutable, "jar", "--no-daemon", "--stacktrace"), buildDir)

            val jar = buildDir.resolve("build/libs/shinigami-provider-" + input.providerId + ".jar")
            require(Files.exists(jar)) { "Provider build produced no jar: " + jar }
            ProviderBuildArtifact(
                providerId = input.providerId,
                version = input.version,
                upstreamCommit = input.upstreamCommit,
                artifactRef = jar.toAbsolutePath().normalize().toString()
            )
        }

    private fun ensureBackendJar(): Path {
        val existing = backendRoot.resolve("build/libs/shinigami-backend-1.0.0.jar")
        if (Files.exists(existing)) return existing
        runCommand(listOf(gradleExecutable, "jar", "--no-daemon", "--stacktrace"), backendRoot)
        require(Files.exists(existing)) { "Backend jar was not produced: " + existing }
        return existing
    }

    private fun ensureStreamixCoreJar(): Path {
        val existing = backendRoot.resolve("streamix-core/build/libs/shinigami-core.jar")
        if (Files.exists(existing)) return existing
        runCommand(
            listOf(gradleExecutable, ":streamix-core:jar", "--no-daemon", "--stacktrace"),
            backendRoot
        )
        require(Files.exists(existing)) { "Streamix core jar was not produced: " + existing }
        return existing
    }

    private fun discoverProviderClass(sourceRoot: Path, providerId: String): String {
        var providerFile: Path? = null
        Files.walk(sourceRoot).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (!Files.isRegularFile(path) || !path.fileName.toString().endsWith(".kt")) continue
                val text = runCatching { Files.readString(path) }.getOrNull() ?: continue
                val classMatch = Regex("(?m)\\b(?:class|object)\\s+(\\w+)\\s*:\\s*StreamixProvider\\b").find(text)
                val idMatches = Regex("(?m)\\boverride\\s+val\\s+id\\s*=\\s*\"" + Regex.escape(providerId) + "\"").containsMatchIn(text)
                if (classMatch != null && idMatches) {
                    providerFile = path
                    break
                }
            }
        }

        val file = providerFile ?: error("No StreamixProvider implementation found for " + providerId)
        val text = Files.readString(file)
        val pkg = Regex("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)").find(text)?.groupValues?.get(1)
        val cls = Regex("(?m)\\b(?:class|object)\\s+(\\w+)\\s*:\\s*StreamixProvider\\b")
            .find(text)?.groupValues?.get(1)
        return listOfNotNull(pkg, cls).joinToString(".")
    }

    private fun copyTree(from: Path, to: Path) {
        Files.walk(from).use { paths ->
            paths.forEach { source ->
                val relative = from.relativize(source)
                val target = to.resolve(relative)
                if (Files.isDirectory(source)) Files.createDirectories(target)
                else {
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}

class FileSystemProviderArtifactPublisher(
    private val releaseRoot: Path
) : ProviderArtifactPublisher {
    override suspend fun publish(artifact: ProviderBuildArtifact): ProviderRelease =
        withContext(Dispatchers.IO) {
            val targetDir = releaseRoot.resolve(artifact.providerId.lowercase()).resolve(artifact.version)
            Files.createDirectories(targetDir)
            val source = Path.of(artifact.artifactRef)
            require(Files.exists(source)) { "Build artifact does not exist: " + artifact.artifactRef }
            val target = targetDir.resolve(source.fileName.toString())
            val temp = targetDir.resolve(source.fileName.toString() + ".tmp")
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING)
            val digest = sha256(temp)
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            ProviderRelease(
                providerId = artifact.providerId,
                version = artifact.version,
                upstream = "Hatsune/AnimeX",
                upstreamCommit = artifact.upstreamCommit,
                artifactRef = target.toAbsolutePath().normalize().toString(),
                publishedAt = Instant.now(),
                artifactSha256 = digest
            )
        }
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

class JarProviderRuntimeArtifactLoader : ProviderRuntimeArtifactLoader {
    override suspend fun load(release: ProviderRelease): ProviderRuntime =
        withContext(Dispatchers.IO) {
            val jar = Path.of(release.artifactRef)
            require(Files.exists(jar)) { "Provider release artifact does not exist: " + release.artifactRef }
            ZipFile(jar.toFile()).use { zip ->
                val metadata = zip.getEntry("META-INF/streamix-provider.properties")
                    ?: error("Provider metadata missing from artifact")
                val properties = Properties()
                zip.getInputStream(metadata).use(properties::load)
                require(properties.getProperty("providerId").equals(release.providerId, ignoreCase = true)) {
                    "Artifact provider id mismatch"
                }
                require(properties.getProperty("version") == release.version) {
                    "Artifact version mismatch"
                }
                val className = properties.getProperty("providerClass")
                    ?: error("Provider class missing from artifact metadata")
                val loader = ChildFirstProviderClassLoader(
                    arrayOf(jar.toUri().toURL()),
                    ProviderRuntime::class.java.classLoader,
                    properties.getProperty("providerClass")
                        .substringBeforeLast(".", missingDelimiterValue = "")
                        .let { if (it.isBlank()) "" else "$it." }
                )
                val provider = loader.loadClass(className).getDeclaredConstructor().newInstance() as StreamixProvider
                require(provider.id.equals(release.providerId, ignoreCase = true)) {
                    "Loaded provider id mismatch: " + provider.id
                }
                ManagedProviderRuntime(runtime = NativeStreamixProviderRuntime(provider), classLoader = loader)
            }
        }
}

private class ManagedProviderRuntime(
    private val runtime: ProviderRuntime,
    private val classLoader: URLClassLoader
) : ProviderRuntime, AutoCloseable {
    override val providerId: String
        get() = runtime.providerId

    override suspend fun search(query: String, page: Int) =
        runtime.search(query, page)

    override suspend fun loadAnime(providerAnimeId: String) =
        runtime.loadAnime(providerAnimeId)

    override suspend fun loadEpisodes(providerAnimeId: String) =
        runtime.loadEpisodes(providerAnimeId)

    override suspend fun loadStreams(episode: streamix.api.EpisodeRef) =
        runtime.loadStreams(episode)

    override fun close() {
        classLoader.close()
    }
}

private class ChildFirstProviderClassLoader(
    urls: Array<java.net.URL>,
    parent: ClassLoader,
    private val providerPackage: String
) : URLClassLoader(urls, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (providerPackage.isNotEmpty() && name.startsWith(providerPackage)) {
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)?.let { return it }
                runCatching { findClass(name) }.getOrNull()?.let {
                    if (resolve) resolveClass(it)
                    return it
                }
            }
        }
        return super.loadClass(name, resolve)
    }

}



class HatsuneProviderUpdateDetector(
    private val apiBase: String = "https://api.github.com/repos/HatsuneMikuUwU/AnimeX/commits/master"
) {
    suspend fun detect(providerId: String, activeVersion: ProviderVersion?): ProviderUpdateCandidate? =
        withContext(Dispatchers.IO) {
            val connection = URI(apiBase).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Shinigami-Provider-AutoUpdate")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            try {
                require(connection.responseCode in 200..299) {
                    "Upstream check failed: HTTP " + connection.responseCode
                }
                val sha = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .getString("sha")
                if (activeVersion?.upstreamCommit.equals(sha, ignoreCase = true)) return@withContext null
                ProviderUpdateCandidate(
                    providerId = providerId,
                    currentVersion = activeVersion?.version,
                    currentCommit = activeVersion?.upstreamCommit,
                    targetVersion = sha.take(12),
                    targetCommit = sha,
                    upstream = "Hatsune/AnimeX"
                )
            } finally {
                connection.disconnect()
            }
        }
}

private class LiveProviderArtifactVerifier(
    private val loader: ProviderRuntimeArtifactLoader,
    private val queries: List<String>,
    private val timeoutMs: Long
) {
    suspend fun contract(artifact: ProviderBuildArtifact): ProviderVerification =
        runVerification("contract") {
            val runtime = loadRuntime(artifact)
            require(runtime.providerId.equals(artifact.providerId, ignoreCase = true))
            runtime.search("Naruto", 1)
        }

    suspend fun provider(artifact: ProviderBuildArtifact): ProviderVerification =
        runVerification("provider") {
            val runtime = loadRuntime(artifact)
            val anyResult = queries.any { query ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeout(timeoutMs) {
                            runtime.search(query, 1)
                        }
                    }
                }.getOrDefault(emptyList()).isNotEmpty()
            }
            require(anyResult) { "Provider returned no search results for configured queries" }
        }

    suspend fun streamE2E(artifact: ProviderBuildArtifact): ProviderVerification =
        runVerification("stream-e2e") {
            val runtime = loadRuntime(artifact)
            var streamCount = 0
            for (query in queries) {
                val results = runCatching {
                    kotlinx.coroutines.withTimeout(timeoutMs) { runtime.search(query, 1) }
                }.getOrDefault(emptyList())
                for (anime in results.take(2)) {
                    val detail = runCatching {
                        kotlinx.coroutines.withTimeout(timeoutMs) { runtime.loadAnime(anime.id) }
                    }.getOrNull() ?: continue
                    val episodes = runCatching {
                        kotlinx.coroutines.withTimeout(timeoutMs) { runtime.loadEpisodes(detail.id) }
                    }.getOrDefault(emptyList())
                    for (episode in episodes.sortedBy { it.number }.take(3)) {
                        val streams = runCatching {
                            kotlinx.coroutines.withTimeout(timeoutMs) { runtime.loadStreams(episode) }
                        }.getOrDefault(emptyList())
                        streamCount += streams.size
                        if (streamCount > 0) return@runVerification
                    }
                }
            }
            require(streamCount > 0) { "Provider produced no streams for configured E2E queries" }
        }

    private suspend fun loadRuntime(artifact: ProviderBuildArtifact): ProviderRuntime =
        loader.load(
            ProviderRelease(
                providerId = artifact.providerId,
                version = artifact.version,
                upstream = "Hatsune/AnimeX",
                upstreamCommit = artifact.upstreamCommit,
                artifactRef = artifact.artifactRef,
                publishedAt = Instant.now()
            )
        )

    private suspend fun runVerification(
        stage: String,
        action: suspend () -> Unit
    ): ProviderVerification =
        try {
            action()
            ProviderVerification(true, stage + " passed")
        } catch (error: Throwable) {
            ProviderVerification(false, error.message ?: stage + " failed")
        }
}

class LiveProviderArtifactVerificationRunner(
    loader: ProviderRuntimeArtifactLoader,
    queries: List<String> = listOf(
        "Naruto",
        "Demon Slayer",
        "Jujutsu Kaisen",
        "Solo Leveling"
    ),
    timeoutMs: Long = 20_000L
) {
    private val verifier = LiveProviderArtifactVerifier(loader, queries, timeoutMs)

    val contractTests: ProviderContractTestRunner = object : ProviderContractTestRunner {
        override suspend fun test(artifact: ProviderBuildArtifact): ProviderVerification =
            verifier.contract(artifact)
    }

    val providerTests: ProviderTestRunner = object : ProviderTestRunner {
        override suspend fun test(artifact: ProviderBuildArtifact): ProviderVerification =
            verifier.provider(artifact)
    }

    val streamE2ETests: ProviderStreamE2ERunner = object : ProviderStreamE2ERunner {
        override suspend fun test(artifact: ProviderBuildArtifact): ProviderVerification =
            verifier.streamE2E(artifact)
    }
}

class ProviderAutoUpdateScheduler(
    private val providers: () -> List<String>,
    private val manager: ProviderUpdateManager,
    private val incidentStore: ProviderIncidentStore? = null,
    private val intervalMs: Long = configuredIntervalMs()
) : AutoCloseable {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )
    private var job: kotlinx.coroutines.Job? = null
    private val running = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun start() {
        check(intervalMs > 0) { "intervalMs must be positive" }
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                runOnce()
                delay(intervalMs)
            }
        }
    }

    suspend fun runOnce() {
        for (providerId in providers()) {
            if (!running.add(providerId.lowercase())) continue
            runCatching {
                val checked = manager.check(providerId)
                if (checked.status != ProviderUpdateStatus.UPDATE_AVAILABLE) return@runCatching
                manager.enqueue(providerId)
                val updated = manager.update(providerId)
                if (updated.status == ProviderUpdateStatus.READY_TO_DEPLOY) {
                    manager.activate(providerId)
                }
            }.onFailure { error ->
                incidentStore?.record(ProviderIncident("provider-auto-update-${providerId.lowercase()}-${System.currentTimeMillis()}", IncidentSeverity.HIGH, IncidentCategory.UPDATE, providerId, "Provider auto-update failed", error.message ?: "auto-update failed", Instant.now()))
            }.also {
                running.remove(providerId.lowercase())
            }
        }
    }

    override fun close() {
        job?.cancel()
        scope.cancel()
    }

    companion object {
        private const val DEFAULT_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private fun configuredIntervalMs(): Long =
            System.getenv("SHINIGAMI_PROVIDER_UPDATE_INTERVAL_MS")?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_INTERVAL_MS
    }
}
class HatsuneProviderAutoUpdateRuntime(
    registry: streamix.runtime.ProviderRegistry,
    controlPlane: ProviderControlPlane,
    backendRoot: Path,
    workRoot: Path = backendRoot.resolve("build/provider-updates"),
    upstreamRoot: Path = backendRoot.resolve("build/upstream"),
    releaseRoot: Path = backendRoot.resolve("build/provider-releases"),
    releaseStore: ProviderReleaseStore = InMemoryProviderReleaseStore(),
    incidentStore: ProviderIncidentStore? = null,
    detector: HatsuneProviderUpdateDetector = HatsuneProviderUpdateDetector()
) : AutoCloseable {
    private val loader = JarProviderRuntimeArtifactLoader()
    private val verifier = LiveProviderArtifactVerificationRunner(loader)
    private val executor = PipelineProviderUpdateExecutor(
        detector = { providerId, active ->
            detector.detect(providerId, active)
        },
        sourceFetcher = HatsuneProviderSourceFetcher(upstreamRoot),
        jvmAdapter = HatsuneJvmProviderAdapter(),
        buildRunner = GradleJvmProviderBuildRunner(backendRoot, workRoot),
        contractTests = verifier.contractTests,
        providerTests = verifier.providerTests,
        streamE2ETests = verifier.streamE2ETests,
        publisher = FileSystemProviderArtifactPublisher(releaseRoot),
        runtimeReloader = RegistryProviderRuntimeReloader(registry, loader),
        releaseStore = releaseStore
    )

    val updateManager: ProviderUpdateManager by lazy {
        ProviderUpdateManager(
            controlPlane = controlPlane,
            executor = executor
        )
    }

    val scheduler: ProviderAutoUpdateScheduler by lazy {
        ProviderAutoUpdateScheduler(
            providers = { registry.all().map { it.providerId } },
            manager = updateManager,
            incidentStore = incidentStore
        )
    }

    fun startAutoUpdate() {
        scheduler.start()
    }

    override fun close() {
        scheduler.close()
    }
}


private fun runCommand(command: List<String>, workingDirectory: Path? = null) {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .apply { if (workingDirectory != null) directory(workingDirectory.toFile()) }
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exit = process.waitFor()
    check(exit == 0) {
        "Command failed (" + exit + "): " + command.joinToString(" ") + "\n" + output
    }
}