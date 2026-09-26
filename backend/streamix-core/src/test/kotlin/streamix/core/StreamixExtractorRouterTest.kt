package streamix.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamixExtractorRouterTest {
    @Test
    fun resolvesByHostname() {
        val extractor = FakeExtractor("filedon", setOf("filedon.co"))
        val router = StreamixExtractorRouter(StreamixExtractorRegistry(listOf(extractor)))

        assertEquals(extractor, router.resolve("https://filedon.co/embed/abc"))
        assertEquals(extractor, router.resolve("https://www.filedon.co/embed/abc"))
        assertEquals(extractor, router.resolve("https://cdn.filedon.co/embed/abc"))
    }

    @Test
    fun doesNotMatchUnrelatedHost() {
        val extractor = FakeExtractor("filedon", setOf("filedon.co"))
        val router = StreamixExtractorRouter(StreamixExtractorRegistry(listOf(extractor)))

        assertNull(router.resolve("https://example.com/embed/abc"))
    }

    private class FakeExtractor(
        override val id: String,
        override val domains: Set<String>
    ) : StreamixExtractor {
        override suspend fun extract(request: StreamixExtractorRequest) =
            StreamixExtractorResult()
    }
}
