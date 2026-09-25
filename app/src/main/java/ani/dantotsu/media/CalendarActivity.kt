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
import ani.dantotsu.Refresh
import ani.dantotsu.databinding.ActivityCalendarBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.hideSystemBarsExtendView
import ani.dantotsu.media.user.ListViewPagerAdapter
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
    private var selectedTabIdx = 0
    private var showOnlyLibrary = false
    private var showOnlyDubbed = false
    private var currentCalendar: Map<String, MutableList<Media>> = emptyMap()
    private val model: OtherDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityCalendarBinding.inflate(layoutInflater)

        val surface = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val primary = getThemeColor(com.google.android.material.R.attr.colorPrimary)
        val outline = getThemeColor(com.google.android.material.R.attr.colorOutline)

        window.statusBarColor = surface
        window.navigationBarColor = surface
        binding.calendarAppBar.setBackgroundColor(surface)
        binding.calendarTitle.setTextColor(getThemeColor(androidx.appcompat.R.attr.colorOnBackground))
        binding.calendarDays.setTabTextColors(outline, primary)

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

                binding.calendarViewPager.setCurrentItem(savedTab, false)
                binding.calendarDays.getTabAt(savedTab)?.select()
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
