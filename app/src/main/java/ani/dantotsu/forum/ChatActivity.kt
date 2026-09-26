package ani.dantotsu.forum

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ani.dantotsu.connections.shinigami.ShinigamiChatClient
import ani.dantotsu.connections.shinigami.ShinigamiChatMessage
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.getThemeColor
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {
    private lateinit var messageInput: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar
    private val adapter = MessageAdapter()
    private var mediaId: Long? = null
    private var directUserId: String? = null
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)

        val title = intent.getStringExtra("chat_title") ?: "Global Chat"
        mediaId = intent.getLongExtra("mediaId", -1L).takeIf { it > 0L }
        directUserId = intent.getStringExtra("message_user_id")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorSurface))
        }

        val toolbar = MaterialToolbar(this).apply {
            title = title
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            updateLayoutParams<LinearLayout.LayoutParams> {
                height = (56 * resources.displayMetrics.density).toInt()
                topMargin = statusBarHeight
            }
        }
        root.addView(toolbar)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        swipeRefresh = SwipeRefreshLayout(this)
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply { stackFromEnd = true }
            adapter = this@ChatActivity.adapter
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        swipeRefresh.addView(recyclerView)
        content.addView(swipeRefresh, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        emptyText = TextView(this).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        content.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        root.addView(content)

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
        }
        messageInput = EditText(this).apply {
            hint = "Write a message..."
            maxLines = 4
        }
        inputBar.addView(messageInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sendButton = MaterialButton(this).apply { text = "Send" }
        inputBar.addView(sendButton)
        root.addView(inputBar)

        setContentView(root)

        swipeRefresh.setOnRefreshListener { loadMessages() }
        sendButton.setOnClickListener { sendMessage() }
        loadMessages()
    }

    private fun loadMessages() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = ShinigamiSessionStore(this@ChatActivity).getToken()
                    ?: throw IllegalStateException("Not signed in")
                val page = when {
                    directUserId != null -> ShinigamiChatClient().messages(token, directUserId!!, 1, 100)
                    mediaId == null -> ShinigamiChatClient().global(token, 1, 100)
                    else -> ShinigamiChatClient().anime(token, mediaId!!, 1, 100)
                }
                if (directUserId != null) ShinigamiChatClient().markRead(token, directUserId!!)
                withContext(Dispatchers.Main) {
                    adapter.submit(page.items)
                    emptyText.text = if (page.items.isEmpty()) "No messages yet" else ""
                    emptyText.visibility = if (page.items.isEmpty()) View.VISIBLE else View.GONE
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    if (page.items.isNotEmpty()) recyclerView.scrollToPosition(page.items.lastIndex)
                    isLoading = false
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    emptyText.text = error.message ?: "Failed to load messages"
                    emptyText.visibility = View.VISIBLE
                    isLoading = false
                }
            }
        }
    }

    private fun sendMessage() {
        val content = messageInput.text?.toString()?.trim().orEmpty()
        if (content.isBlank()) return
        sendButton.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = ShinigamiSessionStore(this@ChatActivity).getToken()
                    ?: throw IllegalStateException("Not signed in")
                when {
                    directUserId != null -> ShinigamiChatClient().sendMessage(token, directUserId!!, content)
                    mediaId == null -> ShinigamiChatClient().sendGlobal(token, content)
                    else -> ShinigamiChatClient().sendAnime(token, mediaId!!, content)
                }
                withContext(Dispatchers.Main) {
                    messageInput.text?.clear()
                    sendButton.isEnabled = true
                    loadMessages()
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    sendButton.isEnabled = true
                    emptyText.text = error.message ?: "Failed to send message"
                    emptyText.visibility = View.VISIBLE
                }
            }
        }
    }

    private class MessageAdapter : RecyclerView.Adapter<MessageHolder>() {
        private var items: List<ShinigamiChatMessage> = emptyList()
        fun submit(value: List<ShinigamiChatMessage>) {
            items = value
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 10, 16, 10)
            }
            return MessageHolder(root)
        }
        override fun onBindViewHolder(holder: MessageHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.sender.displayName?.takeIf { it.isNotBlank() } ?: item.sender.username
            holder.name.setTypeface(null, Typeface.BOLD)
            holder.message.text = item.content
        }
        override fun getItemCount(): Int = items.size
    }

    private class MessageHolder(root: View) : RecyclerView.ViewHolder(root) {
        val name = TextView(root.context)
        val message = TextView(root.context)
        init {
            (root as LinearLayout).addView(name)
            root.addView(message)
        }
    }
}
