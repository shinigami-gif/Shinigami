package ani.dantotsu.profile

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.dantotsu.R
import ani.dantotsu.blurImage
import ani.dantotsu.copyToClipboard
import ani.dantotsu.databinding.ActivityProfileBinding
import ani.dantotsu.databinding.ItemProfileAppBarBinding
import ani.dantotsu.initActivity
import ani.dantotsu.loadImage
import ani.dantotsu.openImage
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.toast
import ani.dantotsu.util.customAlertDialog
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.joery.animatedbottombar.AnimatedBottomBar
import kotlin.math.abs


class ProfileActivity : AppCompatActivity(), AppBarLayout.OnOffsetChangedListener {
    lateinit var binding: ActivityProfileBinding
    private lateinit var bindingProfileAppBar: ItemProfileAppBarBinding
    private var selected: Int = 0
    lateinit var navBar: AnimatedBottomBar

    lateinit var shinigamiProfile: ani.dantotsu.connections.shinigami.ShinigamiUserProfile
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        if (savedInstanceState != null) selected = savedInstanceState.getInt("selectedTab", 0)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val context = this
        screenWidth = resources.displayMetrics.widthPixels.toFloat()

        navBar = binding.profileNavBar
        val profileTab = navBar.createTab(R.drawable.ic_round_person_24, "Profile")
        val statsTab = navBar.createTab(R.drawable.ic_stats_24, "Stats")
        navBar.addTab(profileTab)
        navBar.addTab(statsTab)
        navBar.visibility = View.GONE
        binding.profileViewPager.isUserInputEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = ani.dantotsu.connections.shinigami.ShinigamiSessionStore(context).getToken()
                    ?: throw IllegalStateException("Not signed in")
                val userId = intent.getStringExtra("userId")
                    ?: throw IllegalArgumentException("Missing backend user id")
                val profile = ani.dantotsu.connections.shinigami.ShinigamiBackendClient()
                    .getProfile(token, userId)
                shinigamiProfile = profile

                withContext(Dispatchers.Main) {
                    binding.profileViewPager.adapter =
                        ViewPagerAdapter(supportFragmentManager, lifecycle)
                    binding.profileViewPager.setOffscreenPageLimit(2)
                    binding.profileViewPager.setCurrentItem(selected.coerceIn(0, 1), false)
                    navBar.visibility = View.VISIBLE
                    navBar.selectTabAt(selected.coerceIn(0, 1))
                    navBar.setOnTabSelectListener(object : AnimatedBottomBar.OnTabSelectListener {
                        override fun onTabSelected(
                            lastIndex: Int,
                            lastTab: AnimatedBottomBar.Tab?,
                            newIndex: Int,
                            newTab: AnimatedBottomBar.Tab
                        ) {
                            selected = newIndex
                            binding.profileViewPager.setCurrentItem(selected, true)
                        }
                    })

                    bindingProfileAppBar = ItemProfileAppBarBinding.bind(binding.root).apply {
                        binding.profileProgressBar.visibility = View.GONE
                        val user = profile.user
                        val currentUserId =
                            ani.dantotsu.connections.shinigami.ShinigamiSessionStore(context).getUserId()
                        followButton.isGone = currentUserId == null || currentUserId == user.id

                        fun followText(): String = getString(
                            when {
                                user.isFollowing && user.isFollower -> R.string.mutual
                                user.isFollowing -> R.string.unfollow
                                user.isFollower -> R.string.follows_you
                                else -> R.string.follow
                            }
                        )

                        followButton.text = followText()
                        followButton.setOnClickListener {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val updated = ani.dantotsu.connections.shinigami.ShinigamiBackendClient()
                                        .setFollow(token, user.id, !user.isFollowing)
                                    user.isFollowing = updated.isFollowing
                                    user.isFollower = updated.isFollower
                                    withContext(Dispatchers.Main) {
                                        followButton.text = followText()
                                        snackString(R.string.success)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        snackString(e.message ?: "Failed to update follow status")
                                    }
                                }
                            }
                        }

                        profileAppBar.visibility = View.VISIBLE
                        profileMenuButton.setOnClickListener {
                            val popup = PopupMenu(context, profileMenuButton)
                            popup.menuInflater.inflate(R.menu.menu_profile, popup.menu)
                            popup.menu.findItem(R.id.action_view_on_anilist)?.isVisible = false
                            popup.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    R.id.action_share_profile -> {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "@" + user.username)
                                        }
                                        startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                                        true
                                    }

                                    R.id.action_copy_user_id -> {
                                        copyToClipboard(user.id, true)
                                        true
                                    }

                                    R.id.action_block_user -> {
                                        if (currentUserId == user.id) {
                                            snackString("Cannot block yourself")
                                            return@setOnMenuItemClickListener true
                                        }
                                        customAlertDialog().apply {
                                            setTitle(R.string.warning)
                                            setMessage("Toggle block status for " + user.username + "?")
                                            setPosButton(R.string.ok) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val updated = ani.dantotsu.connections.shinigami.ShinigamiBackendClient()
                                                            .setBlock(token, user.id, !user.isBlocked)
                                                        withContext(Dispatchers.Main) {
                                                            snackString(
                                                                if (updated.isBlocked) "User blocked" else "User unblocked"
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            snackString(e.message ?: "Failed to update block status")
                                                        }
                                                    }
                                                }
                                            }
                                            setNegButton(R.string.cancel)
                                            show()
                                        }
                                        true
                                    }

                                    else -> false
                                }
                            }
                            popup.show()
                        }

                        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.username
                        profileUserAvatar.loadImage(user.avatarUrl)
                        profileUserAvatar.openImage(
                            context.getString(R.string.avatar, displayName),
                            user.avatarUrl ?: ""
                        )
                        profileUserName.text = displayName
                        profileUserName.setOnClickListener {
                            copyToClipboard(user.username, true)
                        }

                        val bannerAnimations: ImageView =
                            if (PrefManager.getVal(PrefName.BannerAnimations)) profileBannerImage
                            else profileBannerImageNoKen

                        blurImage(bannerAnimations, user.bannerUrl ?: user.avatarUrl)
                        profileBannerImage.updateLayoutParams { height += statusBarHeight }
                        profileBannerImageNoKen.updateLayoutParams { height += statusBarHeight }
                        profileBannerGradient.updateLayoutParams { height += statusBarHeight }
                        profileCloseButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            topMargin += statusBarHeight
                        }
                        profileMenuButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            topMargin += statusBarHeight
                        }
                        profileButtonContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            topMargin += statusBarHeight
                        }

                        profileBannerImage.openImage(
                            context.getString(R.string.banner, displayName),
                            user.bannerUrl ?: user.avatarUrl ?: ""
                        )

                        mMaxScrollSize = profileAppBar.totalScrollRange
                        profileAppBar.addOnOffsetChangedListener(context)

                        profileFollowerCount.text = profile.followerCount.toString()
                        profileFollowerCountContainer.setOnClickListener {
                            ContextCompat.startActivity(
                                context,
                                Intent(context, FollowActivity::class.java)
                                    .putExtra("title", getString(R.string.followers))
                                    .putExtra("userId", user.id),
                                null
                            )
                        }
                        profileFollowingCount.text = profile.followingCount.toString()
                        profileFollowingCountContainer.setOnClickListener {
                            ContextCompat.startActivity(
                                context,
                                Intent(context, FollowActivity::class.java)
                                    .putExtra("title", "Following")
                                    .putExtra("userId", user.id),
                                null
                            )
                        }

                        profileAnimeCount.text = profile.stats.animeTotal.toString()
                        profileAnimeCountContainer.setOnClickListener(null)
                        profileMangaCount.text = "—"
                        profileMangaCountContainer.setOnClickListener(null)

                        profileCloseButton.setOnClickListener {
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast(e.message ?: "Failed to load profile")
                    finish()
                }
            }
        }
    }

    //Collapsing UI Stuff
    private var isCollapsed = false
    private val percent = 65
    private var mMaxScrollSize = 0
    private var screenWidth: Float = 0f

    override fun onOffsetChanged(appBar: AppBarLayout, i: Int) {
        if (mMaxScrollSize == 0) mMaxScrollSize = appBar.totalScrollRange
        val percentage = abs(i) * 100 / mMaxScrollSize

        with(bindingProfileAppBar) {
            profileUserAvatarContainer.visibility =
                if (profileUserAvatarContainer.scaleX == 0f) View.GONE else View.VISIBLE
            val duration = (200 * (PrefManager.getVal(PrefName.AnimationSpeed) as Float)).toLong()
            if (percentage >= percent && !isCollapsed) {
                isCollapsed = true
                ObjectAnimator.ofFloat(profileUserDataContainer, "translationX", screenWidth)
                    .setDuration(duration).start()
                ObjectAnimator.ofFloat(profileUserAvatarContainer, "translationX", screenWidth)
                    .setDuration(duration).start()
                ObjectAnimator.ofFloat(profileButtonContainer, "translationX", screenWidth)
                    .setDuration(duration).start()
                profileBannerImage.pause()
            }
            if (percentage <= percent && isCollapsed) {
                isCollapsed = false
                ObjectAnimator.ofFloat(profileUserDataContainer, "translationX", 0f)
                    .setDuration(duration).start()
                ObjectAnimator.ofFloat(profileUserAvatarContainer, "translationX", 0f)
                    .setDuration(duration).start()
                ObjectAnimator.ofFloat(profileButtonContainer, "translationX", 0f)
                    .setDuration(duration).start()

                if (PrefManager.getVal(PrefName.BannerAnimations)) profileBannerImage.resume()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selectedTab", selected)
    }

    override fun onResume() {
        if (this::navBar.isInitialized) {
            navBar.selectTabAt(selected)
        }
        super.onResume()
    }

    private class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> ShinigamiProfileFragment()
            1 -> ShinigamiStatsFragment()
            else -> ShinigamiProfileFragment()
        }
    }

}
