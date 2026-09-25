package ani.dantotsu.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.R
import ani.dantotsu.Refresh
import ani.dantotsu.databinding.ActivityCalendarBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.hideSystemBarsExtendView
import ani.dantotsu.media.user.ListViewPagerAdapter
import ani.dantotsu.px
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

class CalendarFragment : Fragment(R.layout.activity_calendar) {
    private var _binding: ActivityCalendarBinding? = null
    private val binding get() = _binding!!
    private var selectedTabIdx = 1
    private var showOnlyLibrary = false
    private var showOnlyDubbed = false
    private var currentCalendar: Map<String, MutableList<Media>> = emptyMap()
    private val model: OtherDetailsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ActivityCalendarBinding.bind(view)
        ThemeManager(requireActivity()).applyTheme()
        val activity = requireActivity()
        val surface = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val outline = getThemeColor(com.google.android.material.R.attr.colorOutline)
        activity.window.statusBarColor = surface
        activity.window.navigationBarColor = surface
        binding.calendarAppBar.setBackgroundColor(surface)
        binding.calendarTitle.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnBackground))
        binding.calendarDays.setTabTextColors(outline, getThemeColor(com.google.android.material.R.attr.colorOnPrimary))
        binding.calendarNavbar.navbarContainer.visibility = View.GONE
        if (!(PrefManager.getVal(PrefName.ImmersiveMode) as Boolean)) {
            activity.window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.nav_bg_inv)
            binding.root.fitsSystemWindows = true
            binding.calendarHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = (statusBarHeight - 8f.px).coerceAtLeast(0f.px) }
        } else {
            binding.root.fitsSystemWindows = false
            requireActivity().hideSystemBarsExtendView()
            binding.calendarHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = statusBarHeight }
        }
        binding.calendarSearch.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), SearchActivity::class.java).putExtra("type", "ANIME"))
        }
        binding.calendarMore.setOnClickListener {
            showOnlyLibrary = !showOnlyLibrary
            viewLifecycleOwner.lifecycleScope.launch { model.loadCalendar(showOnlyLibrary, showOnlyDubbed) }
        }
        binding.calendarDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTabIdx = tab?.position ?: 0
                binding.calendarDays.post { binding.calendarDays.setScrollPosition(selectedTabIdx, 0f, true); centerSelectedDay(selectedTabIdx) }
                updateSummary()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        model.getCalendar().observe(viewLifecycleOwner) { data ->
            currentCalendar = data ?: emptyMap()
            binding.calendarProgressBar.visibility = if (currentCalendar.isEmpty()) View.VISIBLE else View.GONE
            if (currentCalendar.isNotEmpty()) {
                val keys = currentCalendar.keys.toList()
                binding.calendarViewPager.adapter = ListViewPagerAdapter(keys.size, true, childFragmentManager, viewLifecycleOwner.lifecycle)
                TabLayoutMediator(binding.calendarDays, binding.calendarViewPager) { tab, position -> tab.text = formatDayTab(keys[position]) }.attach()
                val initialTab = 1.coerceIn(0, keys.lastIndex)
                selectedTabIdx = initialTab
                binding.calendarViewPager.setCurrentItem(initialTab, false)
                binding.calendarDays.getTabAt(initialTab)?.select()
                binding.calendarDays.setScrollPosition(initialTab, 0f, true)
                centerSelectedDay(initialTab)
                updateSummary()
            }
        }
        binding.calendarViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedTabIdx = position
                binding.calendarDays.getTabAt(position)?.select()
                updateSummary()
            }
        })
        val live = Refresh.activity.getOrPut(hashCode()) { MutableLiveData(true) }
        live.observe(viewLifecycleOwner) {
            if (it) viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { model.loadCalendar(showOnlyLibrary, showOnlyDubbed) }
                live.postValue(false)
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
    private fun formatDayTab(value: String): String = try {
        val date = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).parse(value) ?: return value
        val day = java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(date).uppercase()
        val number = java.text.SimpleDateFormat("dd", Locale.getDefault()).format(date)
        val month = java.text.SimpleDateFormat("MMM", Locale.getDefault()).format(date)
        "$day\\n$number\\n$month"
    } catch (_: Exception) { value }
    private fun updateSummary() {
        val key = currentCalendar.keys.toList().getOrNull(selectedTabIdx) ?: return
        val date = try { DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).parse(key) } catch (_: Exception) { null }
        if (date != null) {
            val today = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(Date())
            val dayLabel = if (key == today) "Today" else java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(date)
            binding.calendarSummary.text = "$dayLabel • " + java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        } else binding.calendarSummary.text = key
        binding.calendarCount.text = "${currentCalendar[key]?.size ?: 0} episodes"
    }
    override fun onDestroyView() {
        Refresh.activity.remove(hashCode())
        binding.calendarViewPager.adapter = null
        _binding = null
        super.onDestroyView()
    }
}