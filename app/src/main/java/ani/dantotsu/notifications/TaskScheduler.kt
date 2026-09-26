package ani.dantotsu.notifications

import android.content.Context
import ani.dantotsu.notifications.subscription.SubscriptionNotificationWorker
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

interface TaskScheduler {
    fun scheduleRepeatingTask(taskType: TaskType, interval: Long)
    fun cancelTask(taskType: TaskType)

    fun cancelAllTasks() {
        for (taskType in TaskType.entries) cancelTask(taskType)
    }

    fun scheduleAllTasks(context: Context) {
        for (taskType in TaskType.entries) {
            val interval = when (taskType) {
                TaskType.SUBSCRIPTION_NOTIFICATION -> SubscriptionNotificationWorker.checkIntervals[
                    PrefManager.getVal(PrefName.SubscriptionNotificationInterval)
                ]
            }
            scheduleRepeatingTask(taskType, interval)
        }
    }

    companion object {
        fun create(context: Context, useAlarmManager: Boolean): TaskScheduler {
            return if (useAlarmManager) AlarmManagerScheduler(context)
            else WorkManagerScheduler(context)
        }

        fun scheduleSingleWork(context: Context) {
            val workManager = androidx.work.WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                SubscriptionNotificationWorker.WORK_NAME + "_single",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequest.Builder(SubscriptionNotificationWorker::class.java).build()
            )
        }
    }

    enum class TaskType {
        SUBSCRIPTION_NOTIFICATION
    }
}

interface Task {
    suspend fun execute(context: Context): Boolean
}
