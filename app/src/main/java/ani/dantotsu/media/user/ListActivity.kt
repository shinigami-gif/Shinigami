package ani.dantotsu.media.user

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.R
import ani.dantotsu.MainActivity
import ani.dantotsu.account.AccountActivity
import ani.dantotsu.media.CalendarActivity
import ani.dantotsu.profile.activity.FeedActivity
import ani.dantotsu.Refresh
import ani.dantotsu.databinding.ActivityListBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.hideSystemBarsExtendView
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListBinding
    private val scope = lifecycleScope
    private var selectedTabIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityListBinding.inflate(layoutInflater)

        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val primaryTextColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        val secondaryTextColor = getThemeColor(com.google.android.material.R.attr.colorOutline)

        window.statusBarColor = primaryColor
        window.navigationBarColor = primaryColor
        binding.listed.visibility = View.GONE
        binding.listTabLayout.setBackgroundColor(primaryColor)
        binding.listAppBar.setBackgroundColor(primaryColor)
        binding.listTitle.setTextColor(primaryTextColor)
        binding.listTabLayout.setTabTextColors(secondaryTextColor, primaryTextColor)
        binding.listTabLayout.setSelectedTabIndicatorColor(primaryTextColor)
        if (!PrefManager.getVal<Boolean>(PrefName.ImmersiveMode)) {
            this.window.statusBarColor =
                ContextCompat.getColor(this, R.color.nav_bg_inv)
            binding.root.fitsSystemWindows = true

        } else {
            binding.root.fitsSystemWindows = false
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            hideSystemBarsExtendView()
            binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
            }
        }
        setContentView(binding.root)

        binding.includedNavbar.navbar.visibility = View.VISIBLE
        binding.includedNavbar.navbar.selectTabAt(3)
        binding.includedNavbar.navbar.setOnTabSelectListener(
            object : nl.joery.animatedbottombar.AnimatedBottomBar.OnTabSelectListener {
                override fun onTabSelected(
                    lastIndex: Int,
                    lastTab: nl.joery.animatedbottombar.AnimatedBottomBar.Tab?,
                    newIndex: Int,
                    newTab: nl.joery.animatedbottombar.AnimatedBottomBar.Tab
                ) {
                    when (newIndex) {
                        0 -> {
                            startActivity(Intent(this@ListActivity, MainActivity::class.java).putExtra("goToHome", true))
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        1 -> {
                            startActivity(Intent(this@ListActivity, CalendarActivity::class.java))
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        2 -> {
                            startActivity(Intent(this@ListActivity, FeedActivity::class.java))
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        3 -> Unit
                        4 -> {
                            startActivity(Intent(this@ListActivity, AccountActivity::class.java))
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                            finish()
                        }
                    }
                }
            }
        )

        val anime = intent.getBooleanExtra("anime", true)
        binding.listTitle.text = getString(R.string.library)
        binding.listTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                this@ListActivity.selectedTabIdx = tab?.position ?: 0
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val model: ListViewModel by viewModels()
        model.getLists().observe(this) {
            val defaultKeys = listOf(
                "Reading",
                "Watching",
                "Completed",
                "Paused",
                "Dropped",
                "Planning",
                "Favourites",
                "Rewatching",
                "Rereading",
                "All"
            )
            val userKeys: Array<String> = resources.getStringArray(R.array.keys)

            if (it != null) {
                binding.listProgressBar.visibility = View.GONE
                binding.listViewPager.adapter = ListViewPagerAdapter(it.size, false, supportFragmentManager, lifecycle)
                val keys = it.keys.toList()
                    .map { key -> userKeys.getOrNull(defaultKeys.indexOf(key)) ?: key }
                val values = it.values.toList()
                val savedTab = this.selectedTabIdx
                TabLayoutMediator(binding.listTabLayout, binding.listViewPager) { tab, position ->
                    tab.text = "${keys[position]} (${values[position].size})"
                }.attach()
                binding.listViewPager.setCurrentItem(savedTab, false)
            }
        }

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        model.loadLists(anime)
                    }
                    live.postValue(false)
                }
            }
        }

        if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
            binding.listSort.visibility = View.GONE
        }
        binding.filter.setOnClickListener {
            val popup = PopupMenu(this, it)
            popup.menu.add(Menu.NONE, 0, Menu.NONE, "All")

            val sortSubMenu = popup.menu.addSubMenu("Sort by")
            listOf(
                "Score" to "score",
                "Title" to "title",
                "Release Date" to "release",
                "Last Updated" to "updatedAt"
            ).forEachIndexed { index, (label, sort) ->
                sortSubMenu.add(3, index + 20000, Menu.NONE, label).setOnMenuItemClickListener {
                    PrefManager.setVal(
                        if (anime) PrefName.AnimeListSortOrder else PrefName.MangaListSortOrder,
                        sort
                    )
                    binding.listProgressBar.visibility = View.VISIBLE
                    binding.listViewPager.adapter = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            model.loadLists(anime, sortOrder = sort)
                        }
                    }
                    true
                }
            }

            val genres = model.getAllGenres()
            if (genres.isNotEmpty()) {
                val genreSubMenu = popup.menu.addSubMenu("Filter by Genre")
                genres.forEachIndexed { index, genre ->
                    genreSubMenu.add(1, index + 1, Menu.NONE, genre)
                }
            }

            val tags = model.getAllTags()
            if (tags.isNotEmpty()) {
                val tagSubMenu = popup.menu.addSubMenu("Filter by Tag")
                tags.forEachIndexed { index, tag ->
                    tagSubMenu.add(2, index + 10000, Menu.NONE, tag)
                }
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.groupId) {
                    0 -> model.unfilterLists()
                    1 -> model.filterLists(menuItem.title.toString())
                    2 -> model.filterListsByTag(menuItem.title.toString())
                    else -> {
                        if (menuItem.title == "All") model.unfilterLists()
                    }
                }
                true
            }
            popup.show()
        }

        binding.random.setOnClickListener {
            //get the current tab
            val currentTab =
                binding.listTabLayout.getTabAt(binding.listTabLayout.selectedTabPosition)
            val currentFragment =
                supportFragmentManager.findFragmentByTag("f" + currentTab?.position.toString()) as? ListFragment
            currentFragment?.randomOptionClick()
        }

        binding.searchViewText.addTextChangedListener {
            model.searchLists(binding.searchViewText.text.toString())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Refresh.activity.remove(this.hashCode())
        if (::binding.isInitialized) {
            binding.listViewPager.adapter = null
        }
    }
}
