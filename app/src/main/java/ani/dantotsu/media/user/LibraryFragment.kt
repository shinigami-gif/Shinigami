package ani.dantotsu.media.user
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.R
import ani.dantotsu.Refresh
import ani.dantotsu.databinding.ActivityListBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.themes.ThemeManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryFragment : Fragment(R.layout.activity_list) {
    private var _binding: ActivityListBinding? = null
    private val binding get() = _binding!!
    private var selectedTabIdx = 0
    private val model: ListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ActivityListBinding.bind(view)
        ThemeManager(requireContext()).applyTheme()
        binding.includedNavbar.navbarContainer.visibility = View.GONE
        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val primaryTextColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        val secondaryTextColor = getThemeColor(com.google.android.material.R.attr.colorOutline)
        requireActivity().window.statusBarColor = primaryColor
        requireActivity().window.navigationBarColor = primaryColor
        binding.listed.visibility = View.GONE
        binding.listTabLayout.setBackgroundColor(primaryColor)
        binding.listAppBar.setBackgroundColor(primaryColor)
        binding.listTitle.setTextColor(primaryTextColor)
        binding.listTabLayout.setTabTextColors(secondaryTextColor, primaryTextColor)
        binding.listTabLayout.setSelectedTabIndicatorColor(primaryTextColor)
        binding.listTitle.text = getString(R.string.library)
        binding.listTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { selectedTabIdx = tab?.position ?: 0 }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        model.getLists().observe(viewLifecycleOwner) {
            val defaultKeys = listOf("Reading","Watching","Completed","Paused","Dropped","Planning","Favourites","Rewatching","Rereading","All")
            val userKeys = resources.getStringArray(R.array.keys)
            if (it != null) {
                binding.listProgressBar.visibility = View.GONE
                binding.listViewPager.adapter = ListViewPagerAdapter(it.size, false, childFragmentManager, viewLifecycleOwner.lifecycle)
                val keys = it.keys.toList().map { key -> userKeys.getOrNull(defaultKeys.indexOf(key)) ?: key }
                val values = it.values.toList()
                TabLayoutMediator(binding.listTabLayout, binding.listViewPager) { tab, position ->
                    tab.text = "${keys[position]} (${values[position].size})"
                }.attach()
                binding.listViewPager.setCurrentItem(selectedTabIdx, false)
            }
        }
        val live = Refresh.activity.getOrPut(hashCode()) { MutableLiveData(true) }
        live.observe(viewLifecycleOwner) {
            if (it) viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { model.loadLists(true) }
                live.postValue(false)
            }
        }
        if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) binding.listSort.visibility = View.GONE
        binding.filter.setOnClickListener {
            val popup = PopupMenu(requireContext(), it)
            popup.menu.add(Menu.NONE, 0, Menu.NONE, "All")
            val sortSubMenu = popup.menu.addSubMenu("Sort by")
            listOf("Score" to "score","Title" to "title","Release Date" to "release","Last Updated" to "updatedAt").forEachIndexed { index, pair ->
                sortSubMenu.add(3, index + 20000, Menu.NONE, pair.first).setOnMenuItemClickListener {
                    PrefManager.setVal(PrefName.AnimeListSortOrder, pair.second)
                    binding.listProgressBar.visibility = View.VISIBLE
                    binding.listViewPager.adapter = null
                    viewLifecycleOwner.lifecycleScope.launch { withContext(Dispatchers.IO) { model.loadLists(true, ani.dantotsu.connections.anilist.Anilist.userid ?: 0, pair.second) } }
                    true
                }
            }
            model.getAllGenres().takeIf { it.isNotEmpty() }?.let { genres ->
                val sub = popup.menu.addSubMenu("Filter by Genre")
                genres.forEachIndexed { index, genre -> sub.add(1,index+1,Menu.NONE,genre) }
            }
            model.getAllTags().takeIf { it.isNotEmpty() }?.let { tags ->
                val sub = popup.menu.addSubMenu("Filter by Tag")
                tags.forEachIndexed { index, tag -> sub.add(2,index+10000,Menu.NONE,tag) }
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.groupId) { 0 -> model.unfilterLists(); 1 -> model.filterLists(item.title.toString()); 2 -> model.filterListsByTag(item.title.toString()) }
                true
            }
            popup.show()
        }
        binding.random.setOnClickListener {
            val currentTab = binding.listTabLayout.getTabAt(binding.listTabLayout.selectedTabPosition)
            val currentFragment = childFragmentManager.findFragmentByTag("f" + currentTab?.position.toString()) as? ListFragment
            currentFragment?.randomOptionClick()
        }
        binding.searchViewText.addTextChangedListener { model.searchLists(binding.searchViewText.text.toString()) }
    }

    override fun onDestroyView() {
        Refresh.activity.remove(hashCode())
        binding.listViewPager.adapter = null
        _binding = null
        super.onDestroyView()
    }
}