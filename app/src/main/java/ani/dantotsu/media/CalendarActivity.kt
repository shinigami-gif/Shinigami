package ani.dantotsu.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.R
import ani.dantotsu.MainActivity
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.Refresh
import ani.dantotsu.databinding.ActivityCalendarBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.hideSystemBarsExtendView
import ani.dantotsu.media.user.ListViewPagerAdapter
import ani.dantotsu.media.user.ListActivity
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.profile.activity.FeedActivity
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class CalendarActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCalendarBinding
    private val scope = lifecycleScope
    private var selectedTabIdx = 1
    private var showOnlyLibrary = false
    private var showOnlyDubbed = false
    private var currentCalendar: Map<String, MutableList<Media>> = emptyMap()
    private val model: OtherDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityCalendarBinding.inflate(layoutInflater)

        val surface = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val primary = getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        val outline = getThemeColor(com.google.android.material.R.attr.colorOutline)

        window.statusBarColor = surface
        window.navigationBarColor = surface
        binding.calendarAppBar.setBackgroundColor(surface)
        binding.calendarTitle.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnBackground))
        binding.calendarDays.setTabTextColors(outline, getThemeColor(com.google.android.material.R.attr.colorOnPrimary))

        if (!(PrefManager.getVal(PrefName.ImmersiveMode) as Boolean)) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.nav_bg_inv)
            binding.root.fitsSystemWindows = true
            binding.calendarHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
            }
        } else {
            binding.root.fitsSystemWindows = false
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            hideSystemBarsExtendView()
            binding.calendarHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
            }
        }

        setContentView(binding.root)

        binding.calendarNavbar.navbar.selectTabAt(1)
        binding.calendarNavbar.navbar.setOnTabSelectListener(
            object : nl.joery.animatedbottombar.AnimatedBottomBar.OnTabSelectListener {
                override fun onTabSelected(
                    lastIndex: Int,
                    lastTab: nl.joery.animatedbottombar.AnimatedBottomBar.Tab?,
                    newIndex: Int,
                    newTab: nl.joery.animatedbottombar.AnimatedBottomBar.Tab
                ) {
                    when (newIndex) {
                        0 -> {
                            startActivity(android.content.Intent(this@CalendarActivity, MainActivity::class.java)
                                .putExtra("goToHome", true))
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        1 -> Unit
                        2 -> startActivity(android.content.Intent(this@CalendarActivity, FeedActivity::class.java))
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                            finish()
                        3 -> startActivity(
                            android.content.Intent(this@CalendarActivity, ListActivity::class.java)
                                .putExtra("anime", true)
                                .putExtra("userId", Anilist.userid)
                        )
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        finish()
                        4 -> startActivity(
                            android.content.Intent(this@CalendarActivity, ProfileActivity::class.java)
                                .putExtra("userId", Anilist.userid)
                        )
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        finish()
                    }
                    if (newIndex != 1) {
                        binding.calendarNavbar.navbar.selectTabAt(1, false)
                    }
                }
            }
        )

        binding.calendarSearch.setOnClickListener {
            startActivity(
                android.content.Intent(this, SearchActivity::class.java)
                    .putExtra("type", "ANIME")
            )
        }

        binding.calendarMore.setOnClickListener {
            showOnlyLibrary = !showOnlyLibrary
            scope.launch { model.loadCalendar(showOnlyLibrary, showOnlyDubbed) }
        }

        binding.calendarDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTabIdx = tab?.position ?: 0
                binding.calendarDays.post {
                    binding.calendarDays.setScrollPosition(selectedTabIdx, 0f, true)
                    centerSelectedDay(selectedTabIdx)
                }
                updateSummary()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        model.getCalendar().observe(this) { data ->
            currentCalendar = data ?: emptyMap()
            binding.calendarProgressBar.visibility =
                if (currentCalendar.isEmpty()) View.VISIBLE else View.GONE

            if (currentCalendar.isNotEmpty()) {
                val keys = currentCalendar.keys.toList()
                val savedTab = selectedTabIdx.coerceIn(0, keys.lastIndex)
                binding.calendarViewPager.adapter =
                    ListViewPagerAdapter(keys.size, true, this)

                TabLayoutMediator(binding.calendarDays, binding.calendarViewPager) { tab, position ->
                    tab.text = formatDayTab(keys[position])
                }.attach()

                val initialTab = 1.coerceIn(0, keys.lastIndex)
                selectedTabIdx = initialTab
                binding.calendarViewPager.setCurrentItem(initialTab, false)
                binding.calendarDays.getTabAt(initialTab)?.select()
                binding.calendarDays.setScrollPosition(initialTab, 0f, true)
                centerSelectedDay(initialTab)
                updateSummary()
            }
        }

        binding.calendarViewPager.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    selectedTabIdx = position
                    binding.calendarDays.getTabAt(position)?.select()
                    updateSummary()
                }
            }
        )

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        model.loadCalendar(showOnlyLibrary, showOnlyDubbed)
                    }
                    live.postValue(false)
                }
            }
        }
    }

    private fun centerSelectedDay(position: Int) {
        binding.calendarDays.post {
            val strip = binding.calendarDays.getChildAt(0) as? ViewGroup ?: return@post
            val tab = strip.getChildAt(position) ?: return@post
            val target = tab.left - ((binding.calendarDays.width - tab.width) / 2)
            binding.calendarDays.scrollTo(target.coerceAtLeast(0), 0)
        }
    }

    private fun formatDayTab(value: String): String {
        return try {
            val date = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).parse(value)
                ?: return value
            val day = java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(date).uppercase()
            val number = java.text.SimpleDateFormat("dd", Locale.getDefault()).format(date)
            val month = java.text.SimpleDateFormat("MMM", Locale.getDefault()).format(date)
            "$day\n$number\n$month"
        } catch (_: Exception) {
            value
        }
    }

    private fun updateSummary() {
        val key = currentCalendar.keys.toList().getOrNull(selectedTabIdx) ?: return
        val date = try {
            DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).parse(key)
        } catch (_: Exception) {
            null
        }
        if (date != null) {
            val today = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(Date())
            val dayLabel = if (key == today) "Today"
            else java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(date)
            binding.calendarSummary.text =
                "$dayLabel • " + java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        } else {
            binding.calendarSummary.text = key
        }
        binding.calendarCount.text =
            "${currentCalendar[key]?.size ?: 0} episodes"
    }

    override fun onDestroy() {
        super.onDestroy()
        Refresh.activity.remove(this.hashCode())
        if (::binding.isInitialized) {
            binding.calendarViewPager.adapter = null
        }
    }
}
