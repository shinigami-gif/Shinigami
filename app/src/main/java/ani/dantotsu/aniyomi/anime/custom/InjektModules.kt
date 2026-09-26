package ani.dantotsu.aniyomi.anime.custom


import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.preference.AndroidPreferenceStore
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
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
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {
    @kotlin.OptIn(ExperimentalSerializationApi::class)
    @OptIn(UnstableApi::class)
    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingleton<Application>(app)
        addSingleton<Context>(app)

        addSingletonFactory { NetworkHelper(app) }
        addSingletonFactory { get<NetworkHelper>().client }
        addSingletonFactory { AnimeExtensionManager(app, get()) }

        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get()) }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory<ProtoBuf> { ProtoBuf }

        addSingletonFactory { StandaloneDatabaseProvider(app) }


        ContextCompat.getMainExecutor(app).execute {
            get<AnimeSourceManager>()
        }
    }
}

class PreferenceModule(val application: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<PreferenceStore> {
            AndroidPreferenceStore(application)
        }

        addSingletonFactory {
            SourcePreferences(get())
        }

        addSingletonFactory {
            BasePreferences(application, get())
        }
    }
}
