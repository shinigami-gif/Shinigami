package ani.dantotsu.home.status

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import ani.dantotsu.R
import ani.dantotsu.blurImage
import ani.dantotsu.buildMarkwon
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.shinigami.ShinigamiActivity
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.connections.shinigami.ShinigamiSocialClient
import ani.dantotsu.databinding.FragmentStatusBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.home.status.listener.StoriesCallback
import ani.dantotsu.loadImage
import ani.dantotsu.media.MediaDetailsActivity
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.profile.activity.ActivityItemBuilder
import ani.dantotsu.profile.activity.RepliesBottomDialog
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.util.AniMarkdown
import ani.dantotsu.util.AnilistLinkParser
import ani.dantotsu.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs


class Stories @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), View.OnTouchListener {
    private lateinit var binding: FragmentStatusBinding
    private lateinit var activityList: List<ShinigamiActivity>
    private lateinit var storiesListener: StoriesCallback
    private var userClicked: Boolean = false
    private var storyIndex: Int = 1
    private var primaryColor: Int = 0
    private var onPrimaryColor: Int = 0
    private var storyDuration: Int = 6
    private val timer: StoryTimer = StoryTimer(secondsToMillis(storyDuration))

    init {
        initLayout()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun initLayout() {
        val inflater: LayoutInflater = LayoutInflater.from(context)
        binding = FragmentStatusBinding.inflate(inflater, this, false)
        addView(binding.root)

        primaryColor = context.getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        onPrimaryColor = context.getThemeColor(com.google.android.material.R.attr.colorOnPrimary)

        if (context is StoriesCallback) storiesListener = context as StoriesCallback

        binding.touchPanel.setOnTouchListener(this)
    }


    fun setStoriesList(
        activityList: List<ShinigamiActivity>, startIndex: Int = 1
    ) {
        this.activityList = activityList
        this.storyIndex = startIndex
        addLoadingViews(activityList)
    }

    private fun addLoadingViews(storiesList: List<ShinigamiActivity>) {
        var idCounter = 1
        storiesList.forEach { _ ->
            binding.progressBarContainer.removeView(findViewWithTag<ProgressBar>("story${idCounter}"))
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
            progressBar.visibility = VISIBLE
            progressBar.id = idCounter
            progressBar.tag = "story${idCounter++}"
            progressBar.progressBackgroundTintList = ColorStateList.valueOf(primaryColor)
            progressBar.progressTintList = ColorStateList.valueOf(onPrimaryColor)
            val params = LayoutParams(0, LayoutParams.WRAP_CONTENT)
            params.marginEnd = 5
            params.marginStart = 5
            binding.progressBarContainer.addView(progressBar, params)
        }

        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.progressBarContainer)

        var counter = storiesList.size
        storiesList.forEach { _ ->
            val progressBar = findViewWithTag<ProgressBar>("story${counter}")
            if (progressBar != null) {
                if (storiesList.size > 1) {
                    when (counter) {
                        storiesList.size -> {
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.END,
                                LayoutParams.PARENT_ID,
                                ConstraintSet.END
                            )
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.TOP,
                                LayoutParams.PARENT_ID,
                                ConstraintSet.TOP
                            )
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.START,
                                getId("story${counter - 1}"),
                                ConstraintSet.END
                            )
                        }

                        1 -> {
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.TOP,
                                LayoutParams.PARENT_ID,
                                ConstraintSet.TOP
                            )
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.START,
                                LayoutParams.PARENT_ID,
                                ConstraintSet.START
                            )
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.END,
                                getId("story${counter + 1}"),
                                ConstraintSet.START
                            )
                        }

                        else -> {
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.TOP,
                                LayoutParams.PARENT_ID,
                                ConstraintSet.TOP
                            )
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.START,
                                getId("story${counter - 1}"),
                                ConstraintSet.END
                            )
                            constraintSet.connect(
                                getId("story${counter}"),
                                ConstraintSet.END,
                                getId("story${counter + 1}"),
                                ConstraintSet.START
                            )
                        }
                    }
                } else {
                    constraintSet.connect(
                        getId("story${counter}"),
                        ConstraintSet.END,
                        LayoutParams.PARENT_ID,
                        ConstraintSet.END
                    )
                    constraintSet.connect(
                        getId("story${counter}"),
                        ConstraintSet.TOP,
                        LayoutParams.PARENT_ID,
                        ConstraintSet.TOP
                    )
                    constraintSet.connect(
                        getId("story${counter}"),
                        ConstraintSet.START,
                        LayoutParams.PARENT_ID,
                        ConstraintSet.START
                    )
                }
            }
            counter--
        }
        constraintSet.applyTo(binding.progressBarContainer)
        startShowContent()
    }

    private fun startShowContent() {
        showStory()
    }

    private fun showStory() {
        if (storyIndex > 1) {
            completeProgressBar(storyIndex - 1)
        }
        val progressBar = findViewWithTag<ProgressBar>("story${storyIndex}")
        binding.androidStoriesLoadingView.visibility = VISIBLE
        timer.setOnTimerCompletedListener {
            Logger.log("onAnimationEnd: $storyIndex")
            if (storyIndex - 1 <= activityList.size) {
                Logger.log("userNotClicked: $storyIndex")
                if (storyIndex < activityList.size) {
                    storyIndex += 1
                    showStory()
                } else {
                    // on stories end
                    binding.androidStoriesLoadingView.visibility = GONE
                    onStoriesCompleted()
                }
            } else {
                // on stories end
                binding.androidStoriesLoadingView.visibility = GONE
                onStoriesCompleted()
            }
        }
        timer.setOnPercentTickListener {
            progressBar.progress = it
        }
        loadStory(activityList[storyIndex - 1])
    }

    private fun getId(tag: String): Int {
        return findViewWithTag<ProgressBar>(tag).id
    }

    private fun secondsToMillis(seconds: Int): Long {
        return (seconds.toLong()).times(1000)
    }

    private fun resetProgressBar(storyIndex: Int) {
        for (i in storyIndex until activityList.size + 1) {
            val progressBar = findViewWithTag<ProgressBar>("story${i}")
            progressBar?.let {
                it.progress = 0
            }
        }
    }

    private fun completeProgressBar(storyIndex: Int) {
        for (i in 1 until storyIndex + 1) {
            val progressBar = findViewWithTag<ProgressBar>("story${i}")
            progressBar?.let {
                it.progress = 100
            }
        }
    }


    private fun rightPanelTouch() {
        Logger.log("rightPanelTouch: $storyIndex")
        if (storyIndex == activityList.size) {
            completeProgressBar(storyIndex)
            onStoriesCompleted()
            return
        }
        userClicked = true
        timer.cancel()
        if (storyIndex <= activityList.size) storyIndex += 1
        showStory()
    }

    private fun leftPanelTouch() {
        Logger.log("leftPanelTouch: $storyIndex")
        if (storyIndex == 1) {
            onStoriesPrevious()
            return
        }
        userClicked = true
        timer.cancel()
        resetProgressBar(storyIndex)
        if (storyIndex > 1) storyIndex -= 1
        showStory()
    }

    private fun onStoriesCompleted() {
        Logger.log("onStoriesCompleted")
        if (::storiesListener.isInitialized) {
            storyIndex = 1
            storiesListener.onStoriesEnd()
            resetProgressBar(storyIndex)
        }
    }

    private fun onStoriesPrevious() {
        if (::storiesListener.isInitialized) {
            storyIndex = 1
            storiesListener.onStoriesStart()
            resetProgressBar(storyIndex)
        }
    }

    fun pause() {
        timer.pause()
    }

    fun resume() {
        timer.resume()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun loadStory(story: ShinigamiActivity) {
        binding.linkPreviewContainer.removeAllViews()
        binding.linkPreviewContainer.visibility = GONE

        val watched = PrefManager.getCustomVal<Set<String>>("activities", emptySet()).toMutableSet()
        watched.add(story.id)
        PrefManager.setCustomVal("activities", watched.takeLast(200).toSet())

        binding.statusUserAvatar.loadImage(story.author.avatarUrl)
        binding.statusUserName.text = story.author.displayName ?: story.author.username
        binding.statusUserTime.text = ActivityItemBuilder.getDateTime(story.createdAt)
        binding.statusUserContainer.setOnClickListener {
            context.startActivity(Intent(context, ProfileActivity::class.java).putExtra("userId", story.author.id))
        }

        binding.textActivity.setOnTouchListener { v, event -> onTouchView(v, event, true); v.onTouchEvent(event) }
        binding.textActivityContainer.setOnTouchListener { v, event -> onTouchView(v, event, true); v.onTouchEvent(event) }

        val isMediaActivity = story.type == "ANIME_LIST" || story.type == "MANGA_LIST"
        binding.textActivity.isVisible = !isMediaActivity
        binding.textActivityContainer.isVisible = !isMediaActivity
        binding.infoText.isVisible = isMediaActivity
        binding.coverImage.isVisible = isMediaActivity
        binding.contentImageViewKen.isVisible = isMediaActivity
        binding.contentImageView.isVisible = isMediaActivity

        if (isMediaActivity) {
            binding.infoText.text = story.mediaTitle ?: story.text.orEmpty()
            story.mediaId?.toInt()?.let { mediaId ->
                binding.coverImage.setOnClickListener {
                    context.startActivity(Intent(context, MediaDetailsActivity::class.java).putExtra("mediaId", mediaId))
                }
                val host = context as? FragmentActivity
                host?.lifecycleScope?.launch {
                    val media = withContext(Dispatchers.IO) { Anilist.query.getMediaList(listOf(mediaId))?.firstOrNull() }
                    if (media != null && !host.isDestroyed) {
                        val cover = media.cover
                        binding.coverImage.loadImage(cover)
                        blurImage(if (PrefManager.getVal(PrefName.BannerAnimations)) binding.contentImageViewKen else binding.contentImageView, media.banner ?: cover)
                    }
                }
            }
        } else {
            val originalText = story.text.orEmpty()
            if (!(context as android.app.Activity).isDestroyed) {
                val links = AnilistLinkParser.extractAnilistLinks(originalText)
                val html = AnilistLinkParser.removeAnilistUrlsFromHtml(AniMarkdown.getBasicAniHTML(originalText))
                buildMarkwon(context, false).setMarkdown(binding.textActivity, html)
                addLinkPreviews(links, originalText)
            }
        }

        val likeColor = ContextCompat.getColor(context, R.color.yt_red)
        val notLikeColor = ContextCompat.getColor(context, R.color.bg_opp)
        binding.replyCount.text = story.replyCount.toString()
        binding.activityReplies.setColorFilter(notLikeColor)
        binding.activityRepliesContainer.setOnClickListener {
            val hostActivity = it.context as? FragmentActivity ?: return@setOnClickListener
            pause()
            RepliesBottomDialog.newInstance(story.id).apply {
                onDialogClosed = { hostActivity.window?.decorView?.post { if (!hostActivity.isFinishing && !hostActivity.isDestroyed && hostActivity.hasWindowFocus()) resume() } }
            }.show(hostActivity.supportFragmentManager, "replies")
        }
        binding.activityLike.setColorFilter(if (story.isLiked) likeColor else notLikeColor)
        binding.activityLikeCount.text = story.likeCount.toString()
        binding.activityLikeContainer.setOnClickListener { like() }
        binding.androidStoriesLoadingView.visibility = GONE
        timer.start()
    }

    fun like() {
        val story = activityList[storyIndex - 1]
        val token = ShinigamiSessionStore(context).getToken()
        if (token.isNullOrBlank()) { snackString("Login required"); return }
        val likeColor = ContextCompat.getColor(context, R.color.yt_red)
        val notLikeColor = ContextCompat.getColor(context, R.color.bg_opp)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val res = runCatching { ShinigamiSocialClient().likeActivity(token, story.id) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (res != null) {
                    story.isLiked = !story.isLiked
                    story.likeCount += if (story.isLiked) 1 else -1
                    binding.activityLikeCount.text = story.likeCount.toString()
                    binding.activityLike.setColorFilter(if (story.isLiked) likeColor else notLikeColor)
                } else snackString("Failed to like activity")
            }
        }
    }
    private fun addLinkPreviews(links: List<AnilistLinkParser.AnilistLink>, originalText: String) {
        binding.linkPreviewContainer.removeAllViews()
        if (links.isEmpty()) {
            binding.linkPreviewContainer.visibility = GONE
            return
        }
        binding.linkPreviewContainer.visibility = VISIBLE
        val mediaIds = links.map { it.id }.distinct()
        val fragmentActivity = context as? FragmentActivity
        if (fragmentActivity != null && mediaIds.isNotEmpty()) {
            fragmentActivity.lifecycleScope.launch {
                val mediaList = withContext(Dispatchers.IO) {
                    Anilist.query.getMediaList(mediaIds)
                }
                val mediaMap = mediaList?.associateBy { it.id } ?: emptyMap()
                withContext(Dispatchers.Main) {
                    // Re-render the text view: replace AniList URLs with media titles
                    if (mediaMap.isNotEmpty() && !(context as android.app.Activity).isDestroyed) {
                        val titleMap = mediaMap.mapValues { (_, media) -> media.userPreferredName }
                        val htmlWithTitles = AnilistLinkParser.replaceAnilistUrlsInHtml(
                            AniMarkdown.getBasicAniHTML(originalText), titleMap
                        )
                        val markwon = buildMarkwon(context, false)
                        markwon.setMarkdown(binding.textActivity, htmlWithTitles)
                    }
                    // Add preview cards
                    links.forEach { link ->
                        val previewView = AnilistLinkPreviewView(context)
                        val media = mediaMap[link.id]
                        if (media != null) {
                            previewView.setMediaData(media)
                        } else {
                            previewView.visibility = GONE
                        }
                        binding.linkPreviewContainer.addView(previewView)
                    }
                }
            }
        }
    }

    private var startClickTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var isLongPress = false
    private val swipeThreshold = 100
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        onTouchView(view, event)
        return true
    }

    private fun onTouchView(view: View, event: MotionEvent, isText: Boolean = false) {
        val maxClickDuration = 200
        val screenWidth = view.width
        val leftHalf = screenWidth / 2
        val leftQuarter = screenWidth * 0.15
        val rightQuarter = screenWidth * 0.85
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                startClickTime = Calendar.getInstance().timeInMillis
                pause()
                isLongPress = false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - startX
                val deltaY = event.y - startY
                if (!isLongPress && (abs(deltaX) > swipeThreshold || abs(deltaY) > swipeThreshold)) {
                    isLongPress = true
                }
            }

            MotionEvent.ACTION_UP -> {
                val clickDuration = Calendar.getInstance().timeInMillis - startClickTime
                if (isText) {
                    if (clickDuration < maxClickDuration && !isLongPress) {
                        if (event.x < leftQuarter) {
                            leftPanelTouch()
                        } else if (event.x > rightQuarter) {
                            rightPanelTouch()
                        } else {
                            resume()
                        }
                    } else {
                        resume()
                    }
                } else {
                    if (clickDuration < maxClickDuration && !isLongPress) {
                        if (event.x < leftHalf) {
                            leftPanelTouch()
                        } else {
                            rightPanelTouch()
                        }
                    } else {
                        resume()
                    }
                }
                val deltaX = event.x - startX
                val deltaY = event.y - startY
                if (abs(deltaX) > swipeThreshold && !(abs(deltaY) > 10)) {
                    if (deltaX > 0) onStoriesPrevious()
                    else onStoriesCompleted()
                }

            }
        }
    }
}
