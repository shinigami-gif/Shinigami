package ani.dantotsu

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import ani.dantotsu.connections.crashlytics.CrashlyticsInterface
import ani.dantotsu.notifications.TaskScheduler
import ani.dantotsu.others.DisabledReports
import ani.dantotsu.settings.SettingsActivity
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.FinalExceptionHandler
import ani.dantotsu.util.Logger
import com.google.android.material.color.DynamicColors
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ani.dantotsu.core.metro.GraphProvider
import ani.dantotsu.di.AppGraph
import ani.dantotsu.di.injekt.MetroInteropModule
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get


@SuppressLint("StaticFieldLeak")
class App : Application(), GraphProvider<AppGraph> {

    override val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(context = this)
    }

    @Inject lateinit var interopModule: MetroInteropModule


    init {
        instance = this
    }

    val mFTActivityLifecycleCallbacks = FTActivityLifecycleCallbacks()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        PrefManager.init(this)
        graph.inject(this)
        Injekt.importModule(interopModule)

        val crashlytics =
            ani.dantotsu.connections.crashlytics.CrashlyticsFactory.createCrashlytics()
        Injekt.addSingletonFactory<CrashlyticsInterface> { crashlytics }
        crashlytics.initialize(this)
        Logger.init(this)
        Thread.setDefaultUncaughtExceptionHandler(FinalExceptionHandler())
        Logger.log(Log.WARN, "App: Logging started")


        val useMaterialYou: Boolean = PrefManager.getVal(PrefName.UseMaterialYou)
        if (useMaterialYou) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        registerActivityLifecycleCallbacks(mFTActivityLifecycleCallbacks)

        val lastSummary = PrefManager.getVal<Long>(PrefName.LastLeakSummaryTimestamp)
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        if (lastSummary == 0L) {
            PrefManager.setVal(PrefName.LastLeakSummaryTimestamp, now)
        } else if (now - lastSummary >= oneDayMs) {
            val dailyLeaks = PrefManager.getVal<Int>(PrefName.DailyLeakCount)
            if (dailyLeaks > 0 && PrefManager.getVal<Boolean>(PrefName.TrackMemoryLeaks)) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val msg = getString(R.string.daily_leaks_summary, dailyLeaks)
                    android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
                }, 3000)
            }
            PrefManager.setVal(PrefName.DailyLeakCount, 0)
            PrefManager.setVal(PrefName.LastLeakSummaryTimestamp, now)
        }

        crashlytics.setCrashlyticsCollectionEnabled(!DisabledReports)
        (PrefManager.getVal(PrefName.SharedUserID) as Boolean).let {
            if (!it) return@let
            val shinigamiUserId = ani.dantotsu.connections.shinigami.ShinigamiSessionStore(this).getUserId()
            if (shinigamiUserId != null) {
                crashlytics.setUserId(shinigamiUserId)
            }
        }
        crashlytics.setCustomKey("device Info", SettingsActivity.getDeviceInfo())

        initializeNetwork()

        setupNotificationChannels()
        if (!LogcatLogger.isInstalled) {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
        }

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val store = ani.dantotsu.connections.shinigami.ShinigamiSessionStore(this@App)
                val token = store.getToken()
                if (token != null) {
                    ani.dantotsu.connections.shinigami.ShinigamiBackendClient()
                        .currentSession(token)
                        .also(store::save)
                }
            }.onFailure {
                Logger.log("Shinigami session restore failed")
                Logger.log(it)
            }

            val useAlarmManager = PrefManager.getVal<Boolean>(PrefName.UseAlarmManager)
            val scheduler = TaskScheduler.create(this@App, useAlarmManager)
            try {
                scheduler.scheduleAllTasks(this@App)
            } catch (e: IllegalStateException) {
                Logger.log("Failed to schedule tasks")
                Logger.log(e)
            }
            try {
                ani.dantotsu.settings.data.AutoBackupWorker.schedule(
                    this@App,
                    PrefManager.getVal(PrefName.AutoBackupInterval)
                )
            } catch (e: Exception) {
                Logger.log("Failed to schedule auto backup: ${e.message}")
            }
        }
        applicationScope.launch(Dispatchers.IO) {
            delay(10000)
            ani.dantotsu.others.AniskipCsvFallback.getInstance(this@App).syncIfNeeded()
        }
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            Logger.log("Failed to modify notification channels")
            Logger.log(e)
        }
    }

    inner class FTActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
        var currentActivity: Activity? = null
        var lastActivity: String? = null
        private var startedActivityCount = 0

        override fun onActivityCreated(p0: Activity, p1: Bundle?) {
            lastActivity = p0.javaClass.simpleName
        }

        override fun onActivityStarted(p0: Activity) {
            currentActivity = p0
            startedActivityCount++
        }

        override fun onActivityResumed(p0: Activity) {
            currentActivity = p0
            applySystemFont(p0)
        }

        override fun onActivityPaused(p0: Activity) {}

        override fun onActivityStopped(p0: Activity) {
            startedActivityCount--
            if (startedActivityCount == 0) {
            }
        }

        override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
        override fun onActivityDestroyed(p0: Activity) {
            if (currentActivity === p0) {
                currentActivity = null
            }
        }
    }

    companion object {
        var instance: App? = null

        /** Reference to the application context.
         *
         * USE WITH EXTREME CAUTION!**/
        var context: Context? = null
        fun currentContext(): Context? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity ?: context
        }

        fun currentActivity(): Activity? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity
        }
    }
}
