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
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.connections.anilist.Anilist
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
                card.strokeColor = if (index == quickActionIndex) getThemeColor(androidx.appcompat.R.attr.colorPrimary)
                else getThemeColor(com.google.android.material.R.attr.colorOutline)
            }
            quickActionIndex = (quickActionIndex + 1) % cards.size
            handler.postDelayed(this, 4000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ActivitySocialBinding.bind(view)
        ThemeManager(requireContext()).applyTheme()
        initActivity(requireActivity())
        binding.socialNavbar.navbarContainer.visibility = View.GONE
        binding.socialHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = statusBarHeight }

        binding.socialGlobalChatCard.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), ani.dantotsu.forum.ForumActivity::class.java))
        }
        binding.socialAnimeChatCard.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), ani.dantotsu.forum.ForumActivity::class.java))
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

        viewLifecycleOwner.lifecycleScope.launch {
            val activities = withContext(Dispatchers.IO) {
                Anilist.query.getFeed(userId = null, global = true, page = 1, activityId = null)
                    ?.data?.page?.activities.orEmpty()
                    .filter { Anilist.adult || it.media?.isAdult != true }.take(24)
            }
            val users = activities.mapNotNull {
                val user = it.user ?: it.messenger ?: return@mapNotNull null
                SocialLeaderboardUser(user.id, user.name ?: "User", user.avatar?.medium,
                    ((it.likeCount ?: 0) * 10) + ((it.replyCount ?: 0) * 20) + 100)
            }.distinctBy { it.id }.take(12)
            val pages = listOf("Community","Anime Fans","Watch Together","Social").mapIndexed { page,title ->
                SocialLeaderboardPage(title, users.map { it.copy(points = it.points + (12-page)*15) })
            }
            binding.socialFeaturePager.adapter = SocialFeatureAdapter(listOf(
                SocialFeature("Watch Together", "Create a room, invite friends, and enjoy anime together.",
                    activities.firstOrNull()?.media?.bannerImage ?: activities.firstOrNull()?.media?.coverImage?.large)
            ))
            binding.socialLeaderboardPager.adapter = SocialLeaderboardAdapter(pages)
            val activityItems = activities.filter { it.typename == "ListActivity" || it.typename == "TextActivity" }.take(3)
            binding.socialActivityList.layoutManager = LinearLayoutManager(requireContext())
            binding.socialActivityList.adapter = SocialActivityAdapter(activityItems)
            val friendUsers = users.take(8)
            binding.socialFriendsList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            binding.socialFriendsList.adapter = SocialFriendAdapter(friendUsers)
            binding.socialFriendsTitle.text = "Active Friends   • " + friendUsers.size + " online"
            val messages = activities.filter { it.typename == "MessageActivity" || it.typename == "TextActivity" }.take(3)
            binding.socialMessagesList.layoutManager = LinearLayoutManager(requireContext())
            binding.socialMessagesList.adapter = SocialMessageAdapter(messages)
            handler.removeCallbacks(featureRunnable)
            handler.removeCallbacks(leaderboardRunnable)
            handler.removeCallbacks(quickActionRunnable)
            handler.postDelayed(featureRunnable, 4000)
            handler.postDelayed(leaderboardRunnable, 4000)
            handler.postDelayed(quickActionRunnable, 0)
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(featureRunnable)
        handler.removeCallbacks(leaderboardRunnable)
        handler.removeCallbacks(quickActionRunnable)
        _binding = null
        super.onDestroyView()
    }
}