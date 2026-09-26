package ani.dantotsu.parsers

import ani.dantotsu.Lazier
import ani.dantotsu.lazyList
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

object AnimeSources : WatchSources() {
    override var list: List<Lazier<BaseParser>> = runtimeSources()
    var pinnedAnimeSources: List<String> = emptyList()
    var isInitialized = false

    suspend fun init(
        fromExtensions: Any? = null,
        extensionManager: Any? = null
    ) {
        pinnedAnimeSources =
            PrefManager.getNullableVal<List<String>>(PrefName.AnimeSourcesOrder, null)
                ?: emptyList()

        // Extension APKs are intentionally ignored. Anime providers are owned by
        // the embedded JVM Streamix runtime.
        list = sortPinnedAnimeSources(runtimeSources(), pinnedAnimeSources)
        isInitialized = true

    }

    private fun runtimeSources(): List<Lazier<BaseParser>> = listOf(
        Lazier({ StreamixAnimeParser() }, "Shinigami Runtime"),
    )

    private fun sortPinnedAnimeSources(
        sources: List<Lazier<BaseParser>>,
        pinnedAnimeSources: List<String>
    ): List<Lazier<BaseParser>> {
        val pinnedSourcesMap = sources.filter { pinnedAnimeSources.contains(it.name) }
            .associateBy { it.name }
        val orderedPinnedSources = pinnedAnimeSources.mapNotNull { name ->
            pinnedSourcesMap[name]
        }
        val unpinnedSources = sources.filterNot { pinnedAnimeSources.contains(it.name) }
        return orderedPinnedSources + unpinnedSources
    }
}


object HAnimeSources : WatchSources() {
    private val aList: List<Lazier<BaseParser>> = lazyList(
    )

    override val list = listOf(aList, AnimeSources.list).flatten()
}
