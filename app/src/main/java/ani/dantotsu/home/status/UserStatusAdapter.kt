package ani.dantotsu.home.status

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiActivity
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.databinding.ItemUserStatusBinding
import ani.dantotsu.getAppString
import ani.dantotsu.loadImage
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.setAnimation
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.snackString
import ani.dantotsu.util.ActivityMarkdownCreator

data class StatusUser(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val activities: List<ShinigamiActivity>
)

fun sortUserStatusList(users: List<StatusUser>): ArrayList<StatusUser> {
    if (users.isEmpty()) return arrayListOf()
    val watchedActivity = PrefManager.getCustomVal<Set<String>>("activities", emptySet())
    return ArrayList(users.sortedWith(
        compareBy<StatusUser> { user ->
            user.activities.isNotEmpty() && user.activities.all { watchedActivity.contains(it.id) }
        }.thenByDescending { user ->
            user.activities.maxOfOrNull { it.createdAt } ?: ""
        }
    ))
}

class UserStatusAdapter(userList: ArrayList<StatusUser>) :
    RecyclerView.Adapter<UserStatusAdapter.UsersViewHolder>() {

    private val user: ArrayList<StatusUser> = sortUserStatusList(userList)

    inner class UsersViewHolder(val binding: ItemUserStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || user[position].activities.isEmpty()) {
                    snackString("No activity")
                    return@setOnClickListener
                }
                StatusActivity.user = user
                ContextCompat.startActivity(
                    itemView.context,
                    Intent(itemView.context, StatusActivity::class.java)
                        .putExtra("position", position),
                    null
                )
            }
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnLongClickListener true
                if (user[position].id == ShinigamiSessionStore(itemView.context).getUserId()) {
                    ContextCompat.startActivity(
                        itemView.context,
                        Intent(itemView.context, ActivityMarkdownCreator::class.java)
                            .putExtra("type", "activity"),
                        null
                    )
                } else {
                    ContextCompat.startActivity(
                        itemView.context,
                        Intent(itemView.context, ProfileActivity::class.java)
                            .putExtra("userId", user[position].id),
                        null
                    )
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsersViewHolder =
        UsersViewHolder(
            ItemUserStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: UsersViewHolder, position: Int) {
        val b = holder.binding
        setAnimation(b.root.context, b.root)
        val current = user[position]
        val currentUserId = ShinigamiSessionStore(b.root.context).getUserId()
        b.profileUserAvatar.loadImage(current.avatarUrl)
        b.profileUserName.text =
            if (currentUserId == current.id) getAppString(R.string.your_story) else current.name
        val watchedActivity = PrefManager.getCustomVal<Set<String>>("activities", emptySet())
        val booleanList = current.activities.map { watchedActivity.contains(it.id) }
        b.profileUserStatusIndicator.setParts(
            current.activities.size,
            booleanList,
            current.id == currentUserId
        )
    }

    override fun getItemCount(): Int = user.size
}
