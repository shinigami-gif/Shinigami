package streamix.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class StreamixProviderCoreTest {
    @Test
    fun registryNormalizesProviderIds() {
        val provider = FakeProvider("OtakuDesu")
        val registry = StreamixProviderRegistry(listOf(provider))

        assertEquals(provider, registry.get("otakudesu"))
        assertEquals(provider, registry.get("OTAKUDESU"))
    }

    @Test
    fun routerSearchAggregatesProviders() = runTest {
        val registry = StreamixProviderRegistry(
            listOf(FakeProvider("one"), FakeProvider("two"))
        )
        val router = StreamixProviderRouter(registry)

        val result = router.search("naruto")

        assertEquals(listOf("one", "two"), result.map { it.providerId })
    }

    private class FakeProvider(
        override val id: String
    ) : StreamixProvider {
        override suspend fun search(query: String, page: Int): List<ProviderAnime> =
            listOf(ProviderAnime(id, "$id-id", "$query-$id"))

        override suspend fun detail(anime: ProviderAnime): ProviderAnime = anime

        override suspend fun episodes(anime: ProviderAnime): List<ProviderEpisode> =
            listOf(ProviderEpisode(id, 1, "ep1"))

        override suspend fun streams(episode: ProviderEpisode): List<ProviderStream> =
            listOf(ProviderStream(id, "https://example.invalid/video.m3u8"))
    }
}
