package ani.dantotsu.profile.notification

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiNotification
import ani.dantotsu.connections.shinigami.ShinigamiNotificationClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.databinding.FragmentNotificationsBinding
import ani.dantotsu.forum.ChatActivity
import ani.dantotsu.media.MediaDetailsActivity
import ani.dantotsu.profile.ProfileActivity
import com.xwray.groupie.GroupieAdapter
import kotlinx.coroutines.launch

class NotificationFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val adapter = GroupieAdapter()
    private var page = 1
    private var hasNextPage = false
    private val client = ShinigamiNotificationClient()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.notificationRecyclerView.adapter = adapter
        binding.notificationRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.notificationProgressBar.isVisible = true
        binding.emptyTextView.text = getString(R.string.nothing_here)

        viewLifecycleOwner.lifecycleScope.launch {
            loadPage(true)
            binding.notificationProgressBar.isVisible = false
        }
        binding.notificationSwipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                loadPage(true)
                binding.notificationSwipeRefresh.isRefreshing = false
            }
        }
        binding.notificationRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!hasNextPage || binding.notificationRefresh.isVisible) return
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (manager.findLastVisibleItemPosition() >= adapter.itemCount - 1) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        binding.notificationRefresh.isVisible = true
                        loadPage(false)
                        binding.notificationRefresh.isVisible = false
                    }
                }
            }
        })
    }

    private suspend fun loadPage(reset: Boolean) {
        val token = ShinigamiSessionStore(requireContext()).getToken() ?: return
        if (reset) {
            page = 1
            hasNextPage = false
            adapter.clear()
        }
        val result = runCatching { client.list(token, page, 30) }.getOrNull() ?: return
        page = result.page + 1
        hasNextPage = result.hasNextPage
        adapter.addAll(result.items.map { NotificationItem(it, ::onNotificationClick) })
        binding.emptyTextView.isVisible = adapter.itemCount == 0
    }

    private fun onNotificationClick(notification: ShinigamiNotification) {
        viewLifecycleOwner.lifecycleScope.launch {
            val token = ShinigamiSessionStore(requireContext()).getToken() ?: return@launch
            runCatching { client.markRead(token, notification.id) }
            when (notification.targetType) {
                "anime", "episode" -> notification.mediaId?.let {
                    startActivity(Intent(requireContext(), MediaDetailsActivity::class.java).apply {
                        putExtra("mediaId", it.toInt())
                    })
                }
                "message" -> notification.targetId?.let {
                    startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                        putExtra("chat_title", notification.actor?.displayName ?: notification.actor?.username ?: "Message")
                        putExtra("message_user_id", it)
                    })
                }
                "profile" -> notification.targetId?.let {
                    startActivity(Intent(requireContext(), ProfileActivity::class.java).apply {
                        putExtra("userId", it)
                    })
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.notificationRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
