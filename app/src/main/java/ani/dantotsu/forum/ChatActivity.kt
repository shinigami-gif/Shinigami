package ani.dantotsu.forum

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiChatClient
import ani.dantotsu.connections.shinigami.ShinigamiChatMessage
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.databinding.ActivityForumBinding
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityForumBinding
    private lateinit var messageInput: EditText
    private lateinit var sendButton: com.google.android.material.button.MaterialButton
    private val adapter = MessageAdapter()
    private var mediaId: Long? = null
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivityForumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        val title = intent.getStringExtra("chat_title") ?: "Global Chat"
        mediaId = intent.getLongExtra("mediaId", -1L).takeIf { it > 0L }
        binding.forumTitle.text = title
        binding.forumSearch.visibility = View.GONE
        binding.forumCategoryChips.parent?.let { (it as? View)?.visibility = View.GONE }
        binding.forumCreateThreadFab.visibility = View.GONE
        binding.forumAppBar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.forumRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navBarHeight + 72
        }

        binding.forumBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.forumRecyclerView.adapter = adapter
        binding.forumRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorSurface))
        }
        messageInput = EditText(this).apply {
            hint = "Write a message..."
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        sendButton = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.send)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        inputBar.addView(messageInput)
        inputBar.addView(sendButton)
        addContentView(
            inputBar,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        inputBar.translationY = -navBarHeight.toFloat()
        sendButton.setOnClickListener { sendMessage() }

        binding.forumSwipeRefresh.setOnRefreshListener { loadMessages() }
        loadMessages()
    }

    private fun loadMessages() {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = ShinigamiSessionStore(this@ChatActivity).getToken()
                    ?: throw IllegalStateException("Not signed in")
                val page = if (mediaId == null) {
                    ShinigamiChatClient().global(token, 1, 100)
                } else {
                    ShinigamiChatClient().anime(token, mediaId!!, 1, 100)
                }
                withContext(Dispatchers.Main) {
                    adapter.submit(page.items)
                    binding.forumEmptyText.visibility = if (page.items.isEmpty()) View.VISIBLE else View.GONE
                    binding.forumEmptyText.text = "No messages yet"
                    binding.forumProgressBar.visibility = View.GONE
                    binding.forumSwipeRefresh.isRefreshing = false
                    binding.forumRecyclerView.scrollToPosition((page.items.size - 1).coerceAtLeast(0))
                    isLoading = false
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    binding.forumProgressBar.visibility = View.GONE
                    binding.forumSwipeRefresh.isRefreshing = false
                    binding.forumEmptyText.text = error.message ?: "Failed to load messages"
                    binding.forumEmptyText.visibility = View.VISIBLE
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
                if (mediaId == null) {
                    ShinigamiChatClient().sendGlobal(token, content)
                } else {
                    ShinigamiChatClient().sendAnime(token, mediaId!!, content)
                }
                withContext(Dispatchers.Main) {
                    messageInput.text?.clear()
                    sendButton.isEnabled = true
                    loadMessages()
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    sendButton.isEnabled = true
                    binding.forumEmptyText.text = error.message ?: "Failed to send message"
                    binding.forumEmptyText.visibility = View.VISIBLE
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
