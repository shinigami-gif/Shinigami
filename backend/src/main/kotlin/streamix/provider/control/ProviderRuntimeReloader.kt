package streamix.provider.control

import streamix.provider.ProviderRuntime
import streamix.runtime.ProviderRegistry

/**
 * Loads a verified release artifact into the application-facing JVM provider
 * contract. The loader is deliberately injected so artifact storage/format
 * stays outside the provider registry.
 */
interface ProviderRuntimeArtifactLoader {
    suspend fun load(release: ProviderRelease): ProviderRuntime
}

/**
 * Hot-reload boundary used by ProviderUpdateExecutor.activate().
 *
 * Registry replacement is atomic from the registry's perspective: readers
 * either see the old runtime or the new runtime. Existing in-flight calls are
 * not forcibly interrupted.
 */
class RegistryProviderRuntimeReloader(
    private val registry: ProviderRegistry,
    private val loader: ProviderRuntimeArtifactLoader
) : ProviderRuntimeReloader {

    override suspend fun activate(release: ProviderRelease): Boolean {
        if (release.providerId.isBlank() || release.version.isBlank()) return false

        val previous = registry.get(release.providerId)
        val runtime = runCatching {
            loader.load(release).also {
                require(it.providerId.equals(release.providerId, ignoreCase = true)) {
                    "Loaded provider id does not match release: " +
                        it.providerId + " != " + release.providerId
                }
            }
        }.getOrElse {
            return false
        }

        return runCatching {
            registry.replace(runtime)
            (previous as? AutoCloseable)?.close()
            true
        }.getOrElse {
            (runtime as? AutoCloseable)?.close()
            false
        }
    }
}
