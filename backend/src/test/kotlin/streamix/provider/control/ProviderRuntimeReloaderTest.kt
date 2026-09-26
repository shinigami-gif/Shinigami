package streamix.provider.control

import kotlinx.coroutines.test.runTest
import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.api.StreamRef
import streamix.provider.ProviderRuntime
import streamix.runtime.ProviderRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderRuntimeReloaderTest {
    @Test
    fun activation_replaces_registered_runtime_only_after_artifact_loads() = runTest {
        val registry = ProviderRegistry()
        registry.register(FakeProvider("otakudesu", "old"))

        val release = ProviderRelease(
            providerId = "otakudesu",
            version = "1.2.4",
            upstream = "Hatsune/AnimeX",
            upstreamCommit = "new",
            artifactRef = "artifact://otakudesu/1.2.4",
            publishedAt = java.time.Instant.now()
        )

        val reloader = RegistryProviderRuntimeReloader(
            registry = registry,
            loader = object : ProviderRuntimeArtifactLoader {
                override suspend fun load(release: ProviderRelease): ProviderRuntime =
                    FakeProvider(release.providerId, release.version)
            }
        )

        assertTrue(reloader.activate(release))
        assertEquals("1.2.4", registry.get("otakudesu")?.loadAnime("id")?.title)
    }

    @Test
    fun activation_rejects_provider_id_mismatch() = runTest {
        val registry = ProviderRegistry()
        registry.register(FakeProvider("otakudesu", "old"))

        val release = ProviderRelease(
            providerId = "otakudesu",
            version = "1.2.4",
            upstream = "Hatsune/AnimeX",
            upstreamCommit = "new",
            artifactRef = "artifact://otakudesu/1.2.4",
            publishedAt = java.time.Instant.now()
        )

        val reloader = RegistryProviderRuntimeReloader(
            registry = registry,
            loader = object : ProviderRuntimeArtifactLoader {
                override suspend fun load(release: ProviderRelease): ProviderRuntime =
                    FakeProvider("samehadaku", release.version)
            }
        )

        assertTrue(!reloader.activate(release))
        assertEquals("old", registry.get("otakudesu")?.loadAnime("id")?.title)
    }

    private class FakeProvider(
        override val providerId: String,
        private val version: String
    ) : ProviderRuntime {
        override suspend fun search(query: String, page: Int): List<ProviderAnime> = emptyList()
        override suspend fun loadAnime(providerAnimeId: String): ProviderAnime =
            ProviderAnime(providerId, providerAnimeId, version)
        override suspend fun loadEpisodes(providerAnimeId: String): List<EpisodeRef> = emptyList()
        override suspend fun loadStreams(episode: EpisodeRef): List<StreamRef> = emptyList()
    }
}
