package ani.dantotsu.profile.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.MainActivity
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.connections.shinigami.ShinigamiSocialClient
import ani.dantotsu.databinding.ActivitySocialBinding
import ani.dantotsu.initActivity
import ani.dantotsu.getThemeColor
import ani.dantotsu.media.CalendarActivity
import ani.dantotsu.media.user.ListActivity
import ani.dantotsu.account.AccountActivity
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySocialBinding
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
            val cards = listOf(
                binding.socialGlobalChatCard,
                binding.socialAnimeChatCard,
                binding.socialLeaderboardCard
            )
            cards.forEachIndexed { index, card ->
                card.strokeColor = if (index == quickActionIndex) {
                    getThemeColor(androidx.appcompat.R.attr.colorPrimary)
                } else {
                    getThemeColor(com.google.android.material.R.attr.colorOutline)
                }
            }
            quickActionIndex = (quickActionIndex + 1) % cards.size
            handler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivitySocialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.socialHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }

        setupBottomNavigation()
        setupQuickActions()
        setupFeaturePager()
        setupLeaderboardPager()
        loadSocialData()
    }

    private fun setupQuickActions() {
        binding.socialGlobalChatCard.setOnClickListener {
            startActivity(Intent(this, ani.dantotsu.forum.ChatActivity::class.java).putExtra("chat_title", "Global Chat"))
        }
        binding.socialAnimeChatCard.setOnClickListener {
            startActivity(Intent(this, ani.dantotsu.forum.ChatActivity::class.java).putExtra("chat_title", "Global Chat"))
        }
        binding.socialLeaderboardCard.setOnClickListener {
            binding.socialScroll.smoothScrollTo(0, binding.socialLeaderboardHeader.top)
        }
    }

    private fun setupFeaturePager() {
        binding.socialFeaturePager.adapter = SocialFeatureAdapter(
            listOf(
                SocialFeature("Watch Together", "Create a room, invite friends, and enjoy anime together.")
            )
        )
        binding.socialFeaturePager.offscreenPageLimit = 3
    }

    private fun setupLeaderboardPager() {
        binding.socialLeaderboardPager.offscreenPageLimit = 1
    }

    private fun loadSocialData() {
        lifecycleScope.launch {
            val token = ShinigamiSessionStore(this@FeedActivity).getToken() ?: return@launch
            val activities = runCatching {
                withContext(Dispatchers.IO) {
                    ShinigamiSocialClient().feed(token, page = 1, perPage = 24).items
                }
            }.getOrElse { emptyList() }

            val users = activities.map {
                SocialLeaderboardUser(
                    it.author.id,
                    it.author.displayName ?: it.author.username,
                    it.author.avatarUrl,
                    (it.likeCount * 10) + (it.replyCount * 20) + 100
                )
            }.distinctBy { it.id }.take(12)

            val leaderboardPages = listOf("Community", "Anime Fans", "Watch Together", "Social")
                .mapIndexed { page, title ->
                    SocialLeaderboardPage(title, users.map { it.copy(points = it.points + (12 - page) * 15) })
                }

            binding.socialFeaturePager.adapter = SocialFeatureAdapter(
                listOf(SocialFeature("Watch Together", "Create a room, invite friends, and enjoy anime together."))
            )
            binding.socialLeaderboardPager.adapter = SocialLeaderboardAdapter(leaderboardPages)

            val activityItems = activities.take(3)
            binding.socialActivityList.layoutManager = LinearLayoutManager(this@FeedActivity)
            binding.socialActivityList.adapter = SocialActivityAdapter(activityItems)

            val friendUsers = users.take(8)
            binding.socialFriendsList.layoutManager =
                LinearLayoutManager(this@FeedActivity, RecyclerView.HORIZONTAL, false)
            binding.socialFriendsList.adapter = SocialFriendAdapter(friendUsers)
            binding.socialFriendsTitle.text = "Active Friends   • " + friendUsers.size + " online"

            val messages = activities.filter { it.type == "MESSAGE" || !it.text.isNullOrBlank() }.take(3)
            binding.socialMessagesList.layoutManager = LinearLayoutManager(this@FeedActivity)
            binding.socialMessagesList.adapter = SocialMessageAdapter(messages)

            handler.removeCallbacks(featureRunnable)
            handler.removeCallbacks(leaderboardRunnable)
            handler.removeCallbacks(quickActionRunnable)
            handler.postDelayed(featureRunnable, 4000)
            handler.postDelayed(leaderboardRunnable, 4000)
            handler.postDelayed(quickActionRunnable, 0)
        }
    }

    private fun setupBottomNavigation() {
        binding.socialNavbar.navbar.visibility = View.VISIBLE
        binding.socialNavbar.navbar.selectTabAt(2)
        binding.socialNavbar.navbar.setOnTabSelectListener(
            object : nl.joery.animatedbottombar.AnimatedBottomBar.OnTabSelectListener {
                override fun onTabSelected(
                    lastIndex: Int,
                    lastTab: nl.joery.animatedbottombar.AnimatedBottomBar.Tab?,
                    newIndex: Int,
                    newTab: nl.joery.animatedbottombar.AnimatedBottomBar.Tab
                ) {
                    when (newIndex) {
                        0 -> {
                            startActivity(
                                Intent(this@FeedActivity, MainActivity::class.java)
                                    .putExtra("goToHome", true)
                            )
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        1 -> {
                            startActivity(Intent(this@FeedActivity, CalendarActivity::class.java))
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        2 -> Unit
                        3 -> {
                            startActivity(
                                Intent(this@FeedActivity, ListActivity::class.java)
                                    .putExtra("anime", true)
                                    .putExtra("userId", ShinigamiSessionStore(this@FeedActivity).getUserId())
                            )
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                            finish()
                        }
                        4 -> {
                            startActivity(
                                Intent(this@FeedActivity, AccountActivity::class.java)
                                    .putExtra("userId", ShinigamiSessionStore(this@FeedActivity).getUserId())
                            )
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                            finish()
                        }
                    }
                    if (newIndex != 2) {
                        binding.socialNavbar.navbar.selectTabAt(2, false)
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(featureRunnable)
        handler.removeCallbacks(leaderboardRunnable)
        handler.removeCallbacks(quickActionRunnable)
        super.onDestroy()
    }
}
