package eu.kanade.tachiyomi.extension.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import ani.dantotsu.media.MediaType
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import tachiyomi.core.util.lang.launchNow
import androidx.core.net.toUri
import ani.dantotsu.BuildConfig

/**
 * Broadcast receiver that listens for the system's packages installed, updated or removed, and only
 * notifies the given [listener] when the package is an extension.
 *
 * @param listener The listener that should be notified of extension installation events.
 */
internal class ExtensionInstallReceiver : BroadcastReceiver() {

    private var animeListener: AnimeListener? = null
    private var type: MediaType? = null

    /**
     * Registers this broadcast receiver
     */
    fun register(context: Context) {
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun setAnimeListener(listener: AnimeListener): ExtensionInstallReceiver {
        this.type = MediaType.ANIME
        animeListener = listener
        this.animeListener
        return this
    }


    /**
     * Called when one of the events of the [filter] is received. When the package is an extension,
     * it's loaded in background and it notifies the [listener] when finished.
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, ACTION_EXTENSION_ADDED -> {
                if (isReplacing(intent) && intent.action == Intent.ACTION_PACKAGE_ADDED) return
                launchNow {
                    when (val result = getAnimeExtensionFromIntent(context, intent)) {
                        is AnimeLoadResult.Success -> animeListener?.onExtensionInstalled(result.extension)
                        is AnimeLoadResult.Untrusted -> animeListener?.onExtensionUntrusted(result.extension)
                        else -> {}
                    }
                }
            }
            Intent.ACTION_PACKAGE_REPLACED, ACTION_EXTENSION_REPLACED -> {
                launchNow {
                    when (val result = getAnimeExtensionFromIntent(context, intent)) {
                        is AnimeLoadResult.Success -> animeListener?.onExtensionUpdated(result.extension)
                        else -> {}
                    }
                }
            }
            Intent.ACTION_PACKAGE_REMOVED, ACTION_EXTENSION_REMOVED -> {
                if (isReplacing(intent) && intent.action == Intent.ACTION_PACKAGE_REMOVED) return
                getPackageNameFromIntent(intent)?.let { animeListener?.onPackageUninstalled(it) }
            }
        }
    }

    /**
     * Returns the extension triggered by the given intent.
     *
     * @param context The application context.
     * @param intent The intent containing the package name of the extension.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun getAnimeExtensionFromIntent(
        context: Context,
        intent: Intent?
    ): AnimeLoadResult {
        val pkgName = getPackageNameFromIntent(intent)
        if (pkgName == null) {
            Logger.log("Package name not found")
            return AnimeLoadResult.Error
        }
        return GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT) {
            ExtensionLoader.loadAnimeExtensionFromPkgName(
                context,
                pkgName,
            )
        }.await()
    }

    /**
     * Listener that receives extension installation events.
     */
    interface AnimeListener {
        fun onExtensionInstalled(extension: AnimeExtension.Installed)
        fun onExtensionUpdated(extension: AnimeExtension.Installed)
        fun onExtensionUntrusted(extension: AnimeExtension.Untrusted)
        fun onPackageUninstalled(pkgName: String)
    }


    companion object {
        private const val ACTION_EXTENSION_ADDED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_ADDED"
        private const val ACTION_EXTENSION_REPLACED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REPLACED"
        private const val ACTION_EXTENSION_REMOVED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REMOVED"

        /**
         * Returns the intent filter this receiver should subscribe to.
         */
        val filter
            get() = IntentFilter().apply {
                priority = 100
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(ACTION_EXTENSION_ADDED)
                addAction(ACTION_EXTENSION_REPLACED)
                addAction(ACTION_EXTENSION_REMOVED)
                addDataScheme("package")
            }

        /**
         * Returns true if this package is performing an update.
         *
         * @param intent The intent that triggered the event.
         */
        fun isReplacing(intent: Intent): Boolean {
            return intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        }


        /**
         * Returns the package name of the installed, updated or removed application.
         */
        fun getPackageNameFromIntent(intent: Intent?): String? {
            return intent?.data?.encodedSchemeSpecificPart ?: return null
        }

        fun notifyAdded(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_ADDED)
        }

        fun notifyReplaced(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REPLACED)
        }

        fun notifyRemoved(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REMOVED)
        }

        private fun notify(context: Context, pkgName: String, action: String) {
            Intent(action).apply {
                data = "package:$pkgName".toUri()
                `package` = context.packageName
                context.sendBroadcast(this)
            }
        }
    }
}
