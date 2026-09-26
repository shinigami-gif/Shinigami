package streamix.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamixBuiltInExtractorsTest {
    @Test
    fun jwPlayerRoutesKnownCustomDomains() {
        val registry = StreamixBuiltInExtractors.create(FakeHttp())
        val router = StreamixExtractorRouter(registry)

        assertEquals("jwplayer", router.resolve("https://desustream.me/moedesu/abc")?.id)
        assertEquals("jwplayer", router.resolve("https://desustream.info/dstream/updesu/abc")?.id)
    }

    @Test
    fun jwPlayerDoesNotBecomeGlobalHostnameFallback() {
        val registry = StreamixBuiltInExtractors.create(FakeHttp())
        val router = StreamixExtractorRouter(registry)

        assertNull(router.resolve("https://example.com/video"))
    }

    private class FakeHttp : StreamixHttp {
        override suspend fun get(
            url: String,
            headers: Map<String, String>,
            referer: String?,
            cookies: Map<String, String>,
            timeoutMs: Long
        ) = StreamixHttpResponse(200, url, "")

        override suspend fun post(
            url: String,
            body: String,
            headers: Map<String, String>,
            referer: String?,
            cookies: Map<String, String>,
            timeoutMs: Long
        ) = StreamixHttpResponse(200, url, "")

        override suspend fun probe(
            url: String,
            headers: Map<String, String>,
            referer: String?,
            cookies: Map<String, String>,
            timeoutMs: Long,
            maxBytes: Long
        ) = StreamixHttpProbeResponse(200, url, 0L, true)
    }
}
