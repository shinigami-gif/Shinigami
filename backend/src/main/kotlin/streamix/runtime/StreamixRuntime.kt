package streamix.runtime

import streamix.api.ProviderControlApi
import streamix.core.StreamixExtractorRegistry
import com.baseprovider.streamix.ProviderExtractorsNative
import streamix.provider.control.InMemoryProviderIncidentStore
import streamix.provider.control.ProviderControlPlane
import streamix.provider.control.ProviderIncidentStore
import streamix.provider.control.ProviderUpdateManager
import streamix.routing.ProviderRouter
import streamix.provider.control.HatsuneProviderAutoUpdateRuntime
import java.nio.file.Path

class StreamixRuntime(
    val providers: ProviderRegistry,
    val controlPlane: ProviderControlPlane = ProviderControlPlane(
        providers.all().map { it.providerId }
    ),
    val incidentStore: ProviderIncidentStore = InMemoryProviderIncidentStore(),
    val extractors: StreamixExtractorRegistry = StreamixExtractorRegistry(ProviderExtractorsNative.all())
) {
    fun router(): ProviderRouter =
        ProviderRouter(
            registry = providers,
            controlPlane = controlPlane
        )

    fun controlApi(): ProviderControlApi =
        ProviderControlApi(
            controlPlane = controlPlane,
            incidentStore = incidentStore,
            extractorRegistry = extractors
        )

    fun startProviderAutoUpdate(backendRoot: Path): AutoCloseable {
        val runtime = providerAutoUpdate(backendRoot)
        runtime.startAutoUpdate()
        return runtime
    }

    fun providerAutoUpdate(
        backendRoot: Path,
        workRoot: Path = backendRoot.resolve("build/provider-updates"),
        upstreamRoot: Path = backendRoot.resolve("build/upstream"),
        releaseRoot: Path = backendRoot.resolve("build/provider-releases"),
        releaseStore: streamix.provider.control.ProviderReleaseStore =
            streamix.provider.control.FileProviderReleaseStore(
                backendRoot.resolve("build/provider-releases/releases.json")
            )
    ): HatsuneProviderAutoUpdateRuntime =
        HatsuneProviderAutoUpdateRuntime(
            registry = providers,
            controlPlane = controlPlane,
            backendRoot = backendRoot,
            workRoot = workRoot,
            upstreamRoot = upstreamRoot,
            releaseRoot = releaseRoot,
            releaseStore = releaseStore,
            incidentStore = incidentStore
        )

    fun controlApi(updateManager: ProviderUpdateManager): ProviderControlApi =
        ProviderControlApi(
            controlPlane = controlPlane,
            updateManager = updateManager,
            incidentStore = incidentStore,
            extractorRegistry = extractors
        )

    companion object {
        fun native(): StreamixRuntime =
            StreamixRuntime(
                ProviderRegistry().also { registry ->
                    NativeProviderHost.runtimes().forEach(registry::register)
                }
            )
    }
}
