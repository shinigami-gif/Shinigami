package ani.dantotsu.home.status

import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiActivity
import ani.dantotsu.databinding.ActivityStatusBinding
import ani.dantotsu.home.status.listener.StoriesCallback
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.Logger

class StatusActivity : AppCompatActivity(), StoriesCallback {
    private lateinit var activity: ArrayList<StatusUser>
    private lateinit var binding: ActivityStatusBinding
    private var position: Int = -1
    private lateinit var slideInLeft: Animation
    private lateinit var slideOutRight: Animation
    private lateinit var slideOutLeft: Animation
    private lateinit var slideInRight: Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)
        activity = user
        position = intent.getIntExtra("position", -1)
        binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }
        slideInLeft = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
        slideOutRight = AnimationUtils.loadAnimation(this, R.anim.slide_out_right)
        slideOutLeft = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
        slideInRight = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
        showCurrentUser()
    }

    private fun watchedActivityIds(): Set<String> =
        PrefManager.getCustomVal<Set<String>>("activities", emptySet())

    private fun firstUnwatchedIndex(activities: List<ShinigamiActivity>): Int {
        val watched = watchedActivityIds()
        return activities.indexOfFirst { it.id !in watched }.let { if (it < 0) 0 else it }
    }

    private fun showCurrentUser() {
        val current = activity.getOrNull(position)
        if (current == null) {
            Logger.log("index out of bounds for position $position of size ${activity.size}")
            finish()
            return
        }
        binding.stories.setStoriesList(current.activities, firstUnwatchedIndex(current.activities) + 1)
    }

    override fun onPause() { super.onPause(); binding.stories.pause() }
    override fun onResume() { super.onResume(); if (hasWindowFocus()) binding.stories.resume() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) binding.stories.resume() else binding.stories.pause()
    }

    override fun onStoriesEnd() {
        position += 1
        if (position < activity.size) {
            binding.stories.startAnimation(slideOutLeft)
            showCurrentUser()
            binding.stories.startAnimation(slideInRight)
        } else finish()
    }

    override fun onStoriesStart() {
        position -= 1
        if (position >= 0 && activity[position].activities.isNotEmpty()) {
            binding.stories.startAnimation(slideOutRight)
            showCurrentUser()
            binding.stories.startAnimation(slideInLeft)
        } else finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        val current = PrefManager.getVal<Boolean>(PrefName.RefreshStatus)
        PrefManager.setVal(PrefName.RefreshStatus, !current)
    }

    companion object { var user: ArrayList<StatusUser> = arrayListOf() }
}
