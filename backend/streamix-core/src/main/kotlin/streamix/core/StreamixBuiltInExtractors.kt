package streamix.core

import streamix.core.extractors.FiledonExtractor
import streamix.core.extractors.JwPlayerExtractor

object StreamixBuiltInExtractors {
    fun create(http: StreamixHttp = JvmStreamixHttp()): StreamixExtractorRegistry =
        StreamixExtractorRegistry(
            listOf(
                FiledonExtractor(http),
                JwPlayerExtractor(
                    http = http,
                    domains = setOf(
                        "desustream.me",
                        "desustream.info"
                    )
                )
            )
        )
}
