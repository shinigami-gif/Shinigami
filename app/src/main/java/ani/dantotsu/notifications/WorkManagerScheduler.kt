package ani.dantotsu.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequest
import ani.dantotsu.notifications.TaskScheduler.TaskType
import ani.dantotsu.notifications.subscription.SubscriptionNotificationWorker

class WorkManagerScheduler(private val context: Context) : TaskScheduler {
    override fun scheduleRepeatingTask(taskType: TaskType, interval: Long) {
        if (java.util.concurrent.TimeUnit.MINUTES.toMillis(interval) < PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS) {
            cancelTask(taskType)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val recurringWork = PeriodicWorkRequest.Builder(
            SubscriptionNotificationWorker::class.java,
            interval,
            java.util.concurrent.TimeUnit.MINUTES,
            PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
            java.util.concurrent.TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SubscriptionNotificationWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            recurringWork
        )
    }

    override fun cancelTask(taskType: TaskType) {
        androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork(SubscriptionNotificationWorker.WORK_NAME)
    }
}
