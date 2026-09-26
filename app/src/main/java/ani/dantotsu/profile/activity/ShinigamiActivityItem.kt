package ani.dantotsu.profile.activity

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiActivity
import ani.dantotsu.databinding.ItemActivityBinding
import ani.dantotsu.loadImage
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.snackString
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.viewbinding.BindableItem
import kotlinx.coroutines.launch

class ShinigamiActivityItem(
    private val activity: ShinigamiActivity,
    private val parentAdapter: GroupieAdapter,
) : BindableItem<ItemActivityBinding>() {
    private lateinit var binding: ItemActivityBinding

    override fun bind(viewBinding: ItemActivityBinding, position: Int) {
        binding = viewBinding
        val context = binding.root.context
        val host = context as? FragmentActivity

        binding.activityUserName.text = activity.author.displayName ?: activity.author.username
        binding.activityUserAvatar.loadImage(activity.author.avatarUrl)
        binding.activityTime.text = activity.createdAt
        binding.activityLikeCount.text = activity.likeCount.toString()
        binding.activityRepliesContainer.setOnClickListener {
            host?.let {
                RepliesBottomDialog.newInstance(activity.id)
                    .show(it.supportFragmentManager, "replies")
            }
        }
        binding.replyCount.text = activity.replyCount.toString()

        val likeColor = ContextCompat.getColor(context, R.color.yt_red)
        val normalColor = ContextCompat.getColor(context, R.color.bg_opp)
        binding.activityLike.setColorFilter(if (activity.isLiked) likeColor else normalColor)
        binding.activityLikeContainer.setOnClickListener {
            host?.lifecycleScope?.launch {
                try {
                    val token = ani.dantotsu.connections.shinigami.ShinigamiSessionStore(context).getToken()
                    if (token.isNullOrBlank()) {
                        snackString("Login required")
                        return@launch
                    }
                    val updated = ani.dantotsu.connections.shinigami.ShinigamiSocialClient().likeActivity(token, activity.id)
                    activity.isLiked = updated.isLiked
                    activity.likeCount = updated.likeCount
                    binding.activityLikeCount.text = updated.likeCount.toString()
                    binding.activityLike.setColorFilter(if (updated.isLiked) likeColor else normalColor)
                } catch (_: Exception) {
                    snackString("Failed to like activity")
                }
            }
        }

        binding.activitySubscribe.isVisible = !ani.dantotsu.connections.shinigami.ShinigamiSessionStore(context).getToken().isNullOrBlank()
        binding.activitySubscribe.text = if (activity.isSubscribed) "Unsubscribe" else "Subscribe"
        binding.activitySubscribe.setOnClickListener {
            host?.lifecycleScope?.launch {
                try {
                    val token = ani.dantotsu.connections.shinigami.ShinigamiSessionStore(context).getToken()
                    if (token.isNullOrBlank()) return@launch
                    val updated = ani.dantotsu.connections.shinigami.ShinigamiSocialClient().subscribeActivity(token, activity.id)
                    activity.isSubscribed = updated.isSubscribed
                    binding.activitySubscribe.text = if (updated.isSubscribed) "Unsubscribe" else "Subscribe"
                } catch (_: Exception) {
                    snackString("Failed to update subscription")
                }
            }
        }

        binding.activityDelete.isVisible = false
        binding.activityPin.isVisible = false
        binding.activityEdit.isVisible = false
        binding.activityPrivate.isVisible = false
        binding.activityBannerContainer.isVisible = false
        binding.activityContent.isVisible = true

        val text = buildString {
            if (!activity.text.isNullOrBlank()) append(activity.text)
            if (!activity.mediaTitle.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append(activity.mediaTitle)
            }
        }
        binding.activityContent.text = text

        binding.activityAvatarContainer.setOnClickListener {
            host?.startActivity(android.content.Intent(context, ProfileActivity::class.java).putExtra("userId", activity.author.id))
        }
        binding.activityUserName.setOnClickListener {
            host?.startActivity(android.content.Intent(context, ProfileActivity::class.java).putExtra("userId", activity.author.id))
        }
    }

    override fun getLayout() = R.layout.item_activity

    override fun initializeViewBinding(view: View) = ItemActivityBinding.bind(view)
}
