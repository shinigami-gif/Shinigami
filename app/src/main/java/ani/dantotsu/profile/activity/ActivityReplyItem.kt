package ani.dantotsu.profile.activity

import android.content.Intent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.R
import ani.dantotsu.buildMarkwon
import ani.dantotsu.connections.shinigami.ShinigamiActivityReply
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.connections.shinigami.ShinigamiSocialClient
import ani.dantotsu.databinding.ItemActivityReplyBinding
import ani.dantotsu.loadImage
import ani.dantotsu.profile.User
import ani.dantotsu.profile.UsersDialogFragment
import ani.dantotsu.snackString
import ani.dantotsu.util.ActivityMarkdownCreator
import ani.dantotsu.util.AniMarkdown.Companion.getBasicAniHTML
import ani.dantotsu.util.customAlertDialog
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.viewbinding.BindableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivityReplyItem(
    private val reply: ShinigamiActivityReply,
    private val parentId: String,
    private val fragActivity: FragmentActivity,
    private val parentAdapter: GroupieAdapter,
    private val clickCallback: (Int, type: String) -> Unit,
) : BindableItem<ItemActivityReplyBinding>() {
    private lateinit var binding: ItemActivityReplyBinding

    override fun bind(viewBinding: ItemActivityReplyBinding, position: Int) {
        binding = viewBinding
        val context = binding.root.context
        val scope = fragActivity.lifecycleScope
        binding.activityUserAvatar.loadImage(reply.author.avatarUrl)
        binding.activityUserName.text = reply.author.displayName ?: reply.author.username
        binding.activityTime.text = ActivityItemBuilder.getDateTime(reply.createdAt)
        binding.activityLikeCount.text = reply.likeCount.toString()
        val likeColor = ContextCompat.getColor(context, R.color.yt_red)
        val normalColor = ContextCompat.getColor(context, R.color.bg_opp)
        binding.activityLike.setColorFilter(if (reply.isLiked) likeColor else normalColor)
        buildMarkwon(context).setMarkdown(binding.activityContent, getBasicAniHTML(reply.text))
        binding.activityLikeContainer.setOnClickListener {
            scope.launch {
                try {
                    val token = ShinigamiSessionStore(context).getToken() ?: return@launch
                    val updated = ShinigamiSocialClient().likeActivity(token, reply.id)
                    reply.likeCount = updated.likeCount
                    reply.isLiked = updated.isLiked
                    binding.activityLikeCount.text = updated.likeCount.toString()
                    binding.activityLike.setColorFilter(if (updated.isLiked) likeColor else normalColor)
                } catch (_: Exception) {
                    snackString("Failed to like activity reply")
                }
            }
        }
        binding.activityReply.setOnClickListener {
            ContextCompat.startActivity(
                context,
                Intent(context, ActivityMarkdownCreator::class.java)
                    .putExtra("type", "replyActivity")
                    .putExtra("parentId", parentId)
                    .putExtra("other", "@\${reply.author.username} "),
                null
            )
        }
        binding.activityEdit.isVisible = false
        binding.activityDelete.isVisible = false
        binding.activityAvatarContainer.setOnClickListener {
            clickCallback(reply.author.id.toIntOrNull() ?: -1, "USER")
        }
        binding.activityUserName.setOnClickListener {
            clickCallback(reply.author.id.toIntOrNull() ?: -1, "USER")
        }
    }

    override fun getLayout(): Int {
        return R.layout.item_activity_reply
    }

    override fun initializeViewBinding(view: View): ItemActivityReplyBinding {
        return ItemActivityReplyBinding.bind(view)
    }
}
