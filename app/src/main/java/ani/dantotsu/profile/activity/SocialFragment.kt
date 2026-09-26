package ani.dantotsu.profile.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.databinding.ActivitySocialBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.initActivity
import ani.dantotsu.statusBarHeight
import ani.dantotsu.connections.shinigami.ShinigamiChatClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.themes.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SocialFragment : Fragment(ani.dantotsu.R.layout.activity_social) {
    private var _binding: ActivitySocialBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private var featureIndex = 0
    private var leaderboardIndex = 0
    private var quickActionIndex = 0
    private lateinit var conversationAdapter: ShinigamiConversationAdapter

    private val featureRunnable = object : Runnable {
        override fun run() {
            val count = binding.socialFeaturePager.adapter?.itemCount ?: 0
            if (count > 1) {
                featureIndex = (featureIndex + 1) % count
                binding.socialFeaturePager.setCurrentItem(featureIndex, true)
            }
            handler.postDelayed(this, 4000)
        }
    }
    private val leaderboardRunnable = object : Runnable {
        override fun run() {
            val count = binding.socialLeaderboardPager.adapter?.itemCount ?: 0
            if (count > 1) {
                leaderboardIndex = (leaderboardIndex + 1) % count
                binding.socialLeaderboardPager.setCurrentItem(leaderboardIndex, true)
            }
            handler.postDelayed(this, 4000)
        }
    }
    private val quickActionRunnable = object : Runnable {
        override fun run() {
            val cards = listOf(binding.socialGlobalChatCard, binding.socialAnimeChatCard, binding.socialLeaderboardCard)
            cards.forEachIndexed { index, card ->
                val context = binding.root.context
                card.strokeColor = if (index == quickActionIndex) context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
                else context.getThemeColor(com.google.android.material.R.attr.colorOutline)
            }
            quickActionIndex = (quickActionIndex + 1) % cards.size
            handler.postDelayed(this, 4000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ActivitySocialBinding.bind(view)
        ThemeManager(requireActivity()).applyTheme()
        initActivity(requireActivity())
        binding.socialNavbar.navbarContainer.visibility = View.GONE
        binding.socialHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = statusBarHeight }

        binding.socialGlobalChatCard.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), ani.dantotsu.forum.ChatActivity::class.java).putExtra("chat_title", "Global Chat"))
        }
        binding.socialAnimeChatCard.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), ani.dantotsu.forum.ChatActivity::class.java).putExtra("chat_title", "Anime Chat"))
        }
        binding.socialLeaderboardCard.setOnClickListener {
            binding.socialScroll.smoothScrollTo(0, binding.socialLeaderboardHeader.top)
        }

        binding.socialFeaturePager.adapter = SocialFeatureAdapter(listOf(
            SocialFeature("Watch Together", "Create a room, invite friends, and enjoy anime together.")
        ))
        binding.socialFeaturePager.offscreenPageLimit = 3
        binding.socialLeaderboardPager.offscreenPageLimit = 1
        val leaderboardTitles = listOf("Community Leaderboard", "Anime Fans", "Watch Together", "Social")
        binding.socialLeaderboardPager.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.socialLeaderboardTitle.text =
                        leaderboardTitles.getOrNull(position) ?: "Community Leaderboard"
                }
            }
        )

        binding.socialLeaderboardPager.adapter = SocialLeaderboardAdapter(emptyList())
        binding.socialActivityList.layoutManager = LinearLayoutManager(requireContext())
        binding.socialActivityList.adapter = null
        binding.socialFriendsList.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.socialFriendsList.adapter = null
        binding.socialMessagesList.layoutManager = LinearLayoutManager(requireContext())
        conversationAdapter = ShinigamiConversationAdapter { conversation ->
            startActivity(
                android.content.Intent(requireContext(), ani.dantotsu.forum.ChatActivity::class.java)
                    .putExtra("chat_title", conversation.user.displayName?.takeIf { it.isNotBlank() }
                        ?: conversation.user.username)
                    .putExtra("message_user_id", conversation.user.id)
            )
        }
        binding.socialMessagesList.adapter = conversationAdapter
        binding.socialFriendsTitle.text = "Active Friends"

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val token = ShinigamiSessionStore(requireContext()).getToken()
                    ?: throw IllegalStateException("Not signed in")
                ShinigamiChatClient().conversations(token, 1, 20)
            }.onSuccess { page ->
                withContext(Dispatchers.Main) {
                    conversationAdapter.submit(page.items)
                }
            }
        }

        handler.removeCallbacks(featureRunnable)
        handler.removeCallbacks(leaderboardRunnable)
        handler.removeCallbacks(quickActionRunnable)
        handler.postDelayed(featureRunnable, 4000)
        handler.postDelayed(leaderboardRunnable, 4000)
        handler.postDelayed(quickActionRunnable, 0)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(featureRunnable)
        handler.removeCallbacks(leaderboardRunnable)
        handler.removeCallbacks(quickActionRunnable)
        _binding = null
        super.onDestroyView()
    }
}