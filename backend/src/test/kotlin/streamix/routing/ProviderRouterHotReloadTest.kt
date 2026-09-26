package streamix.routing

import kotlin.test.Test
import kotlin.test.assertSame
import streamix.provider.ProviderRuntime
import streamix.runtime.ProviderRegistry

class ProviderRouterHotReloadTest {
    @Test
    fun registry_backed_router_sees_replaced_provider() {
        val registry = ProviderRegistry()
        val oldProvider = FakeProvider("otakudesu")
        val newProvider = FakeProvider("otakudesu")
        registry.register(oldProvider)

        val router = ProviderRouter(registry)
        assertSame(oldProvider, router.provider("otakudesu"))

        registry.replace(newProvider)

        assertSame(newProvider, router.provider("otakudesu"))
    }

    private class FakeProvider(
        override val providerId: String
    ) : ProviderRuntime {
        override suspend fun search(query: String, page: Int) = emptyList<streamix.api.ProviderAnime>()
        override suspend fun loadAnime(providerAnimeId: String) = null
        override suspend fun loadEpisodes(providerAnimeId: String) = emptyList<streamix.api.EpisodeRef>()
        override suspend fun loadStreams(episode: streamix.api.EpisodeRef) = emptyList<streamix.api.StreamRef>()
    }
}
