package streamix.identity

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import streamix.api.CanonicalAnimeIdentity
import streamix.routing.ProviderRouter
import streamix.runtime.StreamixService
import java.net.URI

class ProviderMappingResolverTest {
    @Test
    fun blankProviderReturnsNull() = runBlocking {
        val resolver = RemoteProviderMappingResolver(URI("http://127.0.0.1"))
        assertEquals(null, resolver.resolve(21L, ""))
    }

    @Test
    fun missingMappingResolverNeverInventsProviderIds() = runBlocking {
        val anime = CanonicalAnimeIdentity(
            anilistId = 21L,
            titles = listOf("One Piece")
        )
        val service = StreamixService(
            router = ProviderRouter(emptyList())
        )

        val mapped = service.mapProviders(
            anime,
            listOf("otakudesu")
        )

        assertEquals(emptyMap<String, String>(), mapped.providerMappings)
    }
}
