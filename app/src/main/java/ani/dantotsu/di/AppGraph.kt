package ani.dantotsu.di

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import ani.dantotsu.App
import ani.dantotsu.MainActivity
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.dantotsu.di.injekt.MetroInteropModule
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@OptIn(ExperimentalSerializationApi::class)
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppBindings::class],
)
interface AppGraph {
    fun inject(app: App)
    fun inject(mainActivity: MainActivity)

    val context: Context
    val application: Application

    val preferenceStore: PreferenceStore
    val sourcePreferences: SourcePreferences
    val basePreferences: BasePreferences
    val networkHelper: NetworkHelper
    val javaScriptEngine: JavaScriptEngine
    val animeExtensionManager: AnimeExtensionManager
    val downloadAddonManager: DownloadAddonManager
    val animeSourceManager: AnimeSourceManager
    val json: Json
    val protoBuf: ProtoBuf
    val metroInteropModule: MetroInteropModule

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AppGraph
    }
}
