package ani.dantotsu.home

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LayoutAnimationController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.dantotsu.MediaPageTransformer
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.shinigami.ShinigamiNotificationClient
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.databinding.ItemAnimePageBinding
import ani.dantotsu.databinding.LayoutProfileHeaderBinding
import ani.dantotsu.databinding.LayoutTrendingBinding
import ani.dantotsu.getAppString
import ani.dantotsu.getThemeColor
import ani.dantotsu.loadImage
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaAdaptor
import ani.dantotsu.media.MediaListViewActivity
import ani.dantotsu.media.SearchActivity
import ani.dantotsu.openLinkInCustomTab
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.px
import ani.dantotsu.setSafeOnClickListener
import ani.dantotsu.setSlideIn
import ani.dantotsu.setSlideUp
import ani.dantotsu.settings.SettingsDialogFragment
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout

class AnimePageAdapter : RecyclerView.Adapter<AnimePageAdapter.AnimePageViewHolder>() {
    val ready = MutableLiveData(false)
    lateinit var binding: ItemAnimePageBinding
    private lateinit var trendingBinding: LayoutTrendingBinding
    private lateinit var profileHeaderBinding: LayoutProfileHeaderBinding
    private var trendHandler: Handler? = null
    private lateinit var trendRun: Runnable
    var trendingViewPager: ViewPager2? = null
    private var shinigamiUserId: String? = null
    private var shinigamiAvatar: String? = null
    private var shinigamiUsername: String? = null
    private var shinigamiTokenAvailable = false
    private var shinigamiNotificationCount = 0

    fun updateShinigamiIdentity(userId: String?, username: String?, avatar: String?, notificationCount: Int) {
        shinigamiUserId = userId
        shinigamiUsername = username
        shinigamiAvatar = avatar
        shinigamiTokenAvailable = userId != null
        shinigamiNotificationCount = notificationCount
        if (this::binding.isInitialized) {
            notifyItemChanged(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimePageViewHolder {
        val binding =
            ItemAnimePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnimePageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimePageViewHolder, position: Int) {
        binding = holder.binding
        profileHeaderBinding = LayoutProfileHeaderBinding.bind(binding.root)
        trendingBinding = LayoutTrendingBinding.bind(binding.root)
        trendingViewPager = trendingBinding.trendingViewPager

        profileHeaderBinding.profileHeaderRoot.updatePadding(top = statusBarHeight + 10f.px)
        val rescueModeForHeader = PrefManager.getVal<Boolean>(PrefName.RescueMode)
        val headerAvatarUrl = if (rescueModeForHeader) MAL.avatar else shinigamiAvatar
        val headerUsername = if (rescueModeForHeader) MAL.username else shinigamiUsername
        profileHeaderBinding.profileHeaderName.text =
            headerUsername?.takeIf { it.isNotBlank() } ?: getAppString(R.string.app_name)
        if (!headerAvatarUrl.isNullOrBlank()) {
            profileHeaderBinding.profileHeaderAvatar.loadImage(headerAvatarUrl)
        }
        profileHeaderBinding.profileHeaderNotification.setSafeOnClickListener {
            if (!rescueModeForHeader && shinigamiTokenAvailable) {
                ContextCompat.startActivity(
                    it.context,
                    Intent(it.context, ani.dantotsu.profile.notification.NotificationActivity::class.java),
                    null
                )
            } else {
                val dialog = SettingsDialogFragment.newInstance(
                    SettingsDialogFragment.Companion.PageType.ANIME
                )
                dialog.show(
                    (it.context as AppCompatActivity).supportFragmentManager,
                    "dialog"
                )
            }
        }
        profileHeaderBinding.profileHeaderRoot.setSafeOnClickListener {
            trendingBinding.userAvatar.performLongClick()
        }

        val textInputLayout = holder.itemView.findViewById<TextInputLayout>(R.id.searchBar)
        val currentColor = textInputLayout.boxBackgroundColor
        val semiTransparentColor = (currentColor and 0x00FFFFFF) or 0xA8000000.toInt()
        textInputLayout.boxBackgroundColor = semiTransparentColor
        val materialCardView =
            holder.itemView.findViewById<MaterialCardView>(R.id.userAvatarContainer)
        materialCardView.setCardBackgroundColor(semiTransparentColor)
        val color = binding.root.context.getThemeColor(android.R.attr.windowBackground)
        textInputLayout.boxBackgroundColor = (color and 0x00FFFFFF) or 0x28000000
        materialCardView.setCardBackgroundColor((color and 0x00FFFFFF) or 0x28000000)

        trendingBinding.titleContainer.updatePadding(top = 8f.px)

        if (PrefManager.getVal(PrefName.SmallView)) trendingBinding.trendingContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = (-108f).px
        }

        updateAvatar()

        trendingBinding.searchBar.hint = binding.root.context.getString(R.string.search)
        trendingBinding.searchBarText.setOnClickListener {
            val context = binding.root.context
            if (PrefManager.getVal(PrefName.AniMangaSearchDirect) && shinigamiTokenAvailable) {
                ContextCompat.startActivity(
                    context,
                    Intent(context, SearchActivity::class.java).putExtra("type", "ANIME"),
                    null
                )
            } else {
                SearchBottomSheet.newInstance().show(
                    (context as AppCompatActivity).supportFragmentManager,
                    "search"
                )
            }
        }

        trendingBinding.userAvatar.setSafeOnClickListener {
            val dialogFragment =
                SettingsDialogFragment.newInstance(SettingsDialogFragment.Companion.PageType.ANIME)
            dialogFragment.show((it.context as AppCompatActivity).supportFragmentManager, "dialog")
        }
        trendingBinding.userAvatar.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            if (!rescueMode) {
                ContextCompat.startActivity(
                    view.context,
                    Intent(view.context, ProfileActivity::class.java)
                        .putExtra("userId", shinigamiUserId), null
                )
            } else {
                val malUsername = MAL.username
                if (!malUsername.isNullOrBlank()) {
                    openLinkInCustomTab("https://myanimelist.net/profile/$malUsername")
                } else {
                    ani.dantotsu.toast(view.context.getString(R.string.rescue_mode_active))
                }
            }
            false
        }

        trendingBinding.searchBar.setEndIconOnClickListener {
            trendingBinding.searchBar.performClick()
        }

        val isRescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        trendingBinding.notificationCount.isVisible = !isRescueMode && shinigamiNotificationCount > 0
                && PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot) == true
        trendingBinding.notificationCount.text = shinigamiNotificationCount.toString()

        listOf(
            binding.animePreviousSeason,
            binding.animeThisSeason,
            binding.animeNextSeason
        ).forEachIndexed { i, it ->
            it.setSafeOnClickListener { onSeasonClick.invoke(i) }
            it.setOnLongClickListener { onSeasonLongClick.invoke(i) }
        }

        val rescueMode = PrefManager.getVal<Boolean>(PrefName.RescueMode)
        binding.animeIncludeList.isVisible = if (rescueMode) MAL.token != null else shinigamiTokenAvailable

        binding.animeIncludeList.isChecked = PrefManager.getVal(PrefName.PopularAnimeList)

        binding.animeIncludeList.setOnCheckedChangeListener { _, isChecked ->
            onIncludeListClick.invoke(isChecked)

            PrefManager.setVal(PrefName.PopularAnimeList, isChecked)
        }
        if (ready.value == false)
            ready.postValue(true)
    }

    lateinit var onSeasonClick: ((Int) -> Unit)
    lateinit var onSeasonLongClick: ((Int) -> Boolean)
    lateinit var onIncludeListClick: ((Boolean) -> Unit)

    override fun getItemCount(): Int = 1

    fun updateHeight() {
        trendingViewPager!!.updateLayoutParams { height += statusBarHeight }
    }

    fun updateTrending(adaptor: MediaAdaptor) {
        trendingBinding.trendingProgressBar.visibility = View.GONE
        trendingBinding.trendingViewPager.adapter = adaptor
        trendingBinding.trendingViewPager.offscreenPageLimit = 3
        trendingBinding.trendingViewPager.getChildAt(0)?.overScrollMode =
            RecyclerView.OVER_SCROLL_NEVER
        trendingBinding.trendingViewPager.setPageTransformer(MediaPageTransformer())
        trendHandler = Handler(Looper.getMainLooper())
        trendRun = Runnable {
            trendingBinding.trendingViewPager.currentItem += 1
        }
        trendingBinding.trendingViewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    trendHandler?.removeCallbacks(trendRun)
                    if (PrefManager.getVal(PrefName.TrendingScroller)) {
                        trendHandler!!.postDelayed(trendRun, 4000)
                    }
                }
            }
        )

        trendingBinding.trendingViewPager.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
        trendingBinding.titleContainer.startAnimation(setSlideUp())
        binding.animeSeasonsCont.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
    }

    fun updateRecent(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeUpdatedRecyclerView,
                animeUpdatedProgressBar,
                animeRecently,
                animeRecentlyMore,
                getAppString(R.string.updated),
                media
            )
            animePopular.visibility = View.VISIBLE
            animePopular.startAnimation(setSlideUp())
            if (adaptor.itemCount == 0) {
                animeRecentlyContainer.visibility = View.GONE
            }
        }

    }

    fun updateMovies(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeMoviesRecyclerView,
                animeMoviesProgressBar,
                animeMovies,
                animeMoviesMore,
                getAppString(R.string.trending_movies),
                media
            )
        }
    }

    fun updateTopRated(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeTopRatedRecyclerView,
                animeTopRatedProgressBar,
                animeTopRated,
                animeTopRatedMore,
                getAppString(R.string.top_rated),
                media
            )
        }
    }

    fun updateMostFav(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeMostFavRecyclerView,
                animeMostFavProgressBar,
                animeMostFav,
                animeMostFavMore,
                getAppString(R.string.most_favourite),
                media
            )
        }
    }

    private val sharedMediaPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(0, 25)
    }

    fun init(
        adaptor: MediaAdaptor,
        recyclerView: RecyclerView,
        progress: View,
        title: View,
        more: View,
        string: String,
        media: MutableList<Media>
    ) {
        progress.visibility = View.GONE
        recyclerView.setRecycledViewPool(sharedMediaPool)
        recyclerView.setHasFixedSize(true)
        val llm = LinearLayoutManager(
            recyclerView.context,
            LinearLayoutManager.HORIZONTAL,
            false
        ).apply {
            initialPrefetchItemCount = 4
        }
        recyclerView.layoutManager = llm
        recyclerView.adapter = adaptor

        more.setOnClickListener {
            MediaListViewActivity.passedMedia = media.toCollection(ArrayList())
            ContextCompat.startActivity(
                it.context, Intent(it.context, MediaListViewActivity::class.java)
                    .putExtra("title", string),
                null
            )
        }
        recyclerView.visibility = View.VISIBLE
        title.visibility = View.VISIBLE
        more.visibility = View.VISIBLE
        title.startAnimation(setSlideUp())
        more.startAnimation(setSlideUp())
        recyclerView.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
    }

    fun updateAvatar() {
        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        val avatarUrl = if (rescueMode) MAL.avatar else shinigamiAvatar
        if (avatarUrl != null && ready.value == true) {
            trendingBinding.userAvatar.loadImage(avatarUrl)
            trendingBinding.userAvatar.imageTintList = null
        }
    }

    fun updateNotificationCount() {
        if (this::binding.isInitialized && this::trendingBinding.isInitialized) {
            val isRescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            trendingBinding.notificationCount.isVisible = !isRescueMode && Anilist.unreadNotificationCount > 0
                    && PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot) == true
            trendingBinding.notificationCount.text = Anilist.unreadNotificationCount.toString()
        }
    }

    inner class AnimePageViewHolder(val binding: ItemAnimePageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
