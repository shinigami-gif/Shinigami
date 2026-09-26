package ani.dantotsu.di.injekt

import android.app.Application
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import ani.dantotsu.addons.download.DownloadAddonManager
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

@OptIn(ExperimentalSerializationApi::class)
@Inject
class MetroInteropModule(
    private val context: Context,
    private val preferenceStore: PreferenceStore,
    private val sourcePreferences: SourcePreferences,
    private val basePreferences: BasePreferences,
    private val networkHelper: NetworkHelper,
    private val javaScriptEngine: JavaScriptEngine,
    private val animeExtensionManager: AnimeExtensionManager,
    private val downloadAddonManager: DownloadAddonManager,
    private val animeSourceManager: AnimeSourceManager,
    private val databaseProvider: StandaloneDatabaseProvider,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(context)
        if (context is Application) {
            addSingleton<Application>(context)
        }

        addSingleton(preferenceStore)
        addSingleton(sourcePreferences)
        addSingleton(basePreferences)
        addSingleton(networkHelper)
        addSingleton(networkHelper.client)
        addSingleton(javaScriptEngine)

        addSingleton(animeExtensionManager)
        addSingleton(downloadAddonManager)

        addSingleton(animeSourceManager)

        addSingleton(json)
        addSingleton(protoBuf)
        addSingleton(databaseProvider)
    }
}
