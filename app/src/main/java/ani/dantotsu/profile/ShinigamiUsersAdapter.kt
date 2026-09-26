package ani.dantotsu.profile

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiBackendClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.connections.shinigami.ShinigamiUser
import ani.dantotsu.databinding.ItemFollowerBinding
import ani.dantotsu.databinding.ItemFollowerGridBinding
import ani.dantotsu.loadImage
import ani.dantotsu.setAnimation
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShinigamiUsersAdapter(
    private val users: MutableList<ShinigamiUser>,
    private val grid: Boolean = false
) : RecyclerView.Adapter<ShinigamiUsersAdapter.UsersViewHolder>() {

    private val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)

    inner class UsersViewHolder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos < 0 || pos >= users.size) return@setOnClickListener
                val user = users[pos]
                if (!rescueMode) {
                    ContextCompat.startActivity(
                        binding.root.context,
                        Intent(binding.root.context, ProfileActivity::class.java)
                            .putExtra("userId", user.id),
                        null
                    )
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsersViewHolder =
        UsersViewHolder(
            if (grid) ItemFollowerGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            else ItemFollowerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: UsersViewHolder, position: Int) {
        setAnimation(holder.binding.root.context, holder.binding.root)
        val user = users.getOrNull(position) ?: return
        if (grid) {
            val b = holder.binding as ItemFollowerGridBinding
            b.profileUserAvatar.loadImage(user.avatarUrl)
            b.profileUserName.text = user.displayName?.takeIf { it.isNotBlank() } ?: user.username
            b.profileCompactScoreBG.isVisible = false
            b.profileInfo.isVisible = false
            b.profileCompactProgressContainer.isVisible = false
        } else {
            val b = holder.binding as ItemFollowerBinding
            b.profileUserAvatar.loadImage(user.avatarUrl)
            b.profileBannerImage.loadImage(user.bannerUrl ?: user.avatarUrl)
            b.profileUserName.text = user.displayName?.takeIf { it.isNotBlank() } ?: user.username
            val currentUserId = ShinigamiSessionStore(b.root.context).getUserId()
            b.followStatusChip.isVisible = !rescueMode && currentUserId != user.id
            if (b.followStatusChip.isVisible) {
                fun followText() = b.root.context.getString(
                    when {
                        user.isFollowing && user.isFollower -> R.string.mutual
                        user.isFollowing -> R.string.unfollow
                        user.isFollower -> R.string.follows_you
                        else -> R.string.follow
                    }
                )
                b.followStatusChip.text = followText()
                b.followStatusChip.setOnClickListener {
                    b.root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                        try {
                            val token = ShinigamiSessionStore(b.root.context).getToken()
                                ?: throw IllegalStateException("Not signed in")
                            val updated = ShinigamiBackendClient().setFollow(token, user.id, !user.isFollowing)
                            val index = holder.bindingAdapterPosition
                            if (index >= 0 && index < users.size) {
                                users[index] = updated
                                withContext(Dispatchers.Main) { notifyItemChanged(index) }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                snackString(e.message ?: "Failed to update follow status")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = users.size
}
