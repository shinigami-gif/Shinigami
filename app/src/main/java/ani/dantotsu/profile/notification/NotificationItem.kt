package ani.dantotsu.profile.notification

import android.view.View
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiNotification
import ani.dantotsu.databinding.ItemNotificationBinding
import ani.dantotsu.loadImage
import ani.dantotsu.setAnimation
import com.xwray.groupie.viewbinding.BindableItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NotificationItem(
    private val notification: ShinigamiNotification,
    private val clickCallback: (ShinigamiNotification) -> Unit
) : BindableItem<ItemNotificationBinding>() {
    override fun bind(viewBinding: ItemNotificationBinding, position: Int) {
        setAnimation(viewBinding.root.context, viewBinding.root)
        viewBinding.notificationText.text = "\${notification.title}\\n\${notification.body}"
        viewBinding.notificationDate.text = formatDate(notification.createdAt)
        viewBinding.notificationTypeIcon.setImageResource(iconFor(notification.type))
        if (!notification.imageUrl.isNullOrBlank()) {
            viewBinding.notificationBannerImage.loadImage(notification.imageUrl)
            viewBinding.notificationBannerImage.visibility = View.VISIBLE
        } else {
            viewBinding.notificationBannerImage.visibility = View.GONE
            viewBinding.notificationGradiant.visibility = View.GONE
        }
        viewBinding.notificationCoverContainer.visibility = View.GONE
        viewBinding.notificationCoverUserContainer.visibility = View.GONE
        viewBinding.root.alpha = if (notification.read) 0.72f else 1f
        viewBinding.root.setOnClickListener { clickCallback(notification) }
    }

    override fun getLayout(): Int = R.layout.item_notification
    override fun initializeViewBinding(view: View): ItemNotificationBinding =
        ItemNotificationBinding.bind(view)

    companion object {
        fun iconFor(type: String): Int = when (type.uppercase()) {
            "EPISODE_NEW" -> R.drawable.ic_round_play_arrow_24
            "MESSAGE" -> R.drawable.ic_round_message_24
            "FOLLOW" -> R.drawable.ic_round_person_24
            "MENTION", "REPLY" -> R.drawable.ic_round_comment_24
            "MODERATION", "ANNOUNCEMENT" -> R.drawable.ic_round_notifications_active_24
            else -> R.drawable.ic_round_notifications_none_24
        }

        private fun formatDate(value: String): String =
            runCatching {
                DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(value))
            }.getOrDefault(value)
    }
}
