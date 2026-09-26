package ani.dantotsu.profile.activity

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.connections.shinigami.ShinigamiMessageConversation

class ShinigamiConversationAdapter(
    private val onClick: (ShinigamiMessageConversation) -> Unit
) : RecyclerView.Adapter<ShinigamiConversationAdapter.Holder>() {

    private var items: List<ShinigamiMessageConversation> = emptyList()

    fun submit(value: List<ShinigamiMessageConversation>) {
        items = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = TextView(parent.context).apply {
            setPadding(16, 18, 16, 18)
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val name = item.user.displayName?.takeIf { it.isNotBlank() } ?: item.user.username
        holder.view.text = if (item.unreadCount > 0) {
            name + "  •  " + item.unreadCount + " unread\n" + item.lastMessage.content
        } else {
            name + "\n" + item.lastMessage.content
        }
        holder.view.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(val view: TextView) : RecyclerView.ViewHolder(view)
}
