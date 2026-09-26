package ani.dantotsu.streamix

import streamix.provider.ProviderRuntime
import streamix.runtime.StreamixRuntime

/**
 * Android entry point for the embedded JVM Streamix backend.
 *
 * The UI talks to this boundary only; provider implementations remain owned
 * by the backend runtime.
 */
object StreamixBackend {
    val runtime: StreamixRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        StreamixRuntime.native()
    }

    val router by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runtime.router()
    }

    fun provider(providerId: String): ProviderRuntime? =
        runtime.providers.get(providerId)
}
