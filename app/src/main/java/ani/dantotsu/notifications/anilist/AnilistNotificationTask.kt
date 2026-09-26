package ani.dantotsu.notifications.anilist

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ani.dantotsu.MainActivity
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiNotificationClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.notifications.Task
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.data.notification.Notifications

class AnilistNotificationTask : Task {
    override suspend fun execute(context: Context): Boolean {
        return try {
            val token = ShinigamiSessionStore(context).getToken()
                ?: return true
            val count = ShinigamiNotificationClient().unreadCount(token)
            if (count > 0 && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(
                    Notifications.CHANNEL_ANILIST,
                    System.currentTimeMillis().toInt(),
                    createNotification(context, count)
                )
            }
            true
        } catch (error: Exception) {
            Logger.log("ShinigamiNotificationTask: ${error.message}")
            Logger.log(error)
            false
        }
    }

    private fun createNotification(context: Context, count: Int): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FRAGMENT_TO_LOAD", "NOTIFICATIONS")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, Notifications.CHANNEL_ANILIST)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Shinigami Notifications")
            .setContentText("$count unread notification${if (count == 1) "" else "s"}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }
}
