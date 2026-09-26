package ani.dantotsu.profile

import android.text.SpannableString
import android.view.View
import androidx.core.view.isGone
import ani.dantotsu.R
import ani.dantotsu.blurImage
import ani.dantotsu.connections.shinigami.ShinigamiBackendClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.connections.shinigami.ShinigamiUser
import ani.dantotsu.databinding.ItemFollowerBinding
import ani.dantotsu.databinding.ItemFollowerGridBinding
import ani.dantotsu.loadImage
import ani.dantotsu.snackString
import com.xwray.groupie.viewbinding.BindableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShinigamiFollowerItem(
    private val grid: Boolean,
    private val user: ShinigamiUser,
    private val scope: CoroutineScope,
    private val currentUserId: String?,
    val clickCallback: (String) -> Unit
) : BindableItem<androidx.viewbinding.ViewBinding>() {

    private var following = user.isFollowing
    private var follower = user.isFollower

    override fun bind(viewBinding: androidx.viewbinding.ViewBinding, position: Int) {
        val username = SpannableString(user.displayName?.takeIf { it.isNotBlank() } ?: user.username)

        if (grid) {
            val binding = viewBinding as ItemFollowerGridBinding
            binding.profileUserName.text = username
            user.avatarUrl?.let { binding.profileUserAvatar.loadImage(it) }
            binding.root.setOnClickListener { clickCallback(user.id) }
        } else {
            val binding = viewBinding as ItemFollowerBinding
            binding.profileUserName.text = username
            user.avatarUrl?.let { binding.profileUserAvatar.loadImage(it) }
            blurImage(binding.profileBannerImage, user.bannerUrl ?: user.avatarUrl)
            setupFollowButton(binding.followStatusChip)
            binding.root.setOnClickListener { clickCallback(user.id) }
        }
    }

    private fun setupFollowButton(followButton: View) {
        val button = followButton as? com.google.android.material.chip.Chip ?: return
        button.isGone = currentUserId == null || currentUserId == user.id

        fun followText(): String = button.context.getString(
            when {
                following && follower -> R.string.mutual
                following -> R.string.unfollow
                follower -> R.string.follows_you
                else -> R.string.follow
            }
        )

        button.text = followText()
        button.setOnClickListener {
            scope.launch(Dispatchers.IO) {
                try {
                    val token = ShinigamiSessionStore(button.context).getToken()
                        ?: throw IllegalStateException("Not signed in")
                    val updated = ShinigamiBackendClient().setFollow(token, user.id, !following)
                    following = updated.isFollowing
                    follower = updated.isFollower
                    withContext(Dispatchers.Main) {
                        button.text = followText()
                        snackString(R.string.success)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackString(e.message ?: "Failed to update follow status")
                    }
                }
            }
        }
    }

    override fun getLayout(): Int =
        if (grid) R.layout.item_follower_grid else R.layout.item_follower

    override fun initializeViewBinding(view: View): androidx.viewbinding.ViewBinding =
        if (grid) ItemFollowerGridBinding.bind(view) else ItemFollowerBinding.bind(view)
}
