package ani.dantotsu.profile.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiActivity
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.connections.shinigami.ShinigamiSocialClient
import ani.dantotsu.databinding.FragmentFeedBinding
import ani.dantotsu.media.MediaDetailsActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.util.ActivityMarkdownCreator
import com.xwray.groupie.GroupieAdapter
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import kotlinx.coroutines.launch

class ActivityFragment : Fragment() {
    private lateinit var type: ActivityType
    private var userId: String? = null
    private var activityId: String? = null
    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private var adapter: GroupieAdapter = GroupieAdapter()
    private var page: Int = 1
    private var allActivities: MutableList<ShinigamiActivity> = mutableListOf()
    private var currentFilter: ActivityFilterType = ActivityFilterType.ALL
    private var hasMoreActivities: Boolean = true
    private var shouldRefreshOnResume: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.listRecyclerView?.adapter = null
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            type = it.getSerializableCompat<ActivityType>("type") as ActivityType
            userId = if (it.containsKey("userId")) it.getString("userId").takeIf { id -> id.isNotBlank() } else null
            activityId = if (it.containsKey("activityId")) it.getString("activityId").takeIf { id -> id.isNotBlank() } else null
        }
        val isUserActivity = type == ActivityType.USER || type == ActivityType.GLOBAL || userId == null || userId == currentUserId()
        binding.titleBar.visibility =
            if (type != ActivityType.ONE) View.VISIBLE else View.GONE
        binding.titleText.text = when (type) {
            ActivityType.OTHER_USER -> if (userId == null || userId == currentUserId()) getString(R.string.create_new_activity) else getString(R.string.write_a_message)
            ActivityType.USER, ActivityType.GLOBAL -> getString(R.string.create_new_activity)
            ActivityType.ONE -> ""
        }
        binding.titleImage.visibility = when (type) {
            ActivityType.OTHER_USER -> View.VISIBLE
            ActivityType.USER, ActivityType.GLOBAL -> if (!ShinigamiSessionStore(requireContext()).getToken().isNullOrBlank()) View.VISIBLE else View.GONE
            else -> View.GONE
        }
        
        // Set up filter icon visibility
        binding.filterButton.visibility = if (type != ActivityType.ONE) View.VISIBLE else View.GONE
        binding.filterButton.setOnClickListener {
            showFilterBottomSheet()
        }
        
        binding.titleImage.setOnClickListener { handleTitleImageClick() }
        binding.listRecyclerView.adapter = adapter
        binding.listRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.listProgressBar.isVisible = true

        binding.feedRefresh.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navBarHeight
        }
        binding.emptyTextView.text = getString(R.string.nothing_here)
        viewLifecycleOwner.lifecycleScope.launch {
            getList()
            val currentBinding = _binding ?: return@launch
            if (adapter.itemCount == 0) {
                currentBinding.emptyTextView.isVisible = true
            }
            currentBinding.listProgressBar.isVisible = false
        }
        binding.feedSwipeRefresh.setOnRefreshListener {
            refreshFeed()
        }
        binding.listRecyclerView.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (shouldLoadMore()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        _binding?.feedRefresh?.isVisible = true
                        getList()
                        _binding?.feedRefresh?.isVisible = false
                    }
                }
            }
        })
    }

    private fun showFilterBottomSheet() {
        ActivityFilterBottomSheet.newInstance(currentFilter) { filterType ->
            currentFilter = filterType
            viewLifecycleOwner.lifecycleScope.launch {
                _binding?.listProgressBar?.isVisible = true
                adapter.clear()
                allActivities.clear()
                page = 1
                hasMoreActivities = true
                getList()
                _binding?.listProgressBar?.isVisible = false
            }
        }.show(childFragmentManager, "ActivityFilterBottomSheet")
    }

    private fun applyFilter() {
        val filteredActivities = getFilteredActivities()
        
        adapter.clear()
        adapter.addAll(filteredActivities.map { ShinigamiActivityItem(it, adapter) })
        
        binding.emptyTextView.isVisible = filteredActivities.isEmpty()
        binding.emptyTextView.text = when (currentFilter) {
            ActivityFilterType.ALL -> getString(R.string.nothing_here)
            ActivityFilterType.TEXT -> getString(R.string.no_text_activities)
            ActivityFilterType.ANIME_PROGRESS -> getString(R.string.no_anime_progress)
            ActivityFilterType.ALL_PROGRESS -> getString(R.string.no_all_progress)
            ActivityFilterType.MESSAGES -> getString(R.string.no_messages)
            ActivityFilterType.PINNED -> getString(R.string.no_pinned_activities)
            ActivityFilterType.SUBSCRIBED -> getString(R.string.no_subscribed_activities)
        }
    }

    private fun handleTitleImageClick() {
        shouldRefreshOnResume = true
        val isUserActivity = type == ActivityType.USER || type == ActivityType.GLOBAL || userId == null || userId == currentUserId()
        val targetUserId = if (isUserActivity) currentUserId() else userId
        val intent = Intent(context, ActivityMarkdownCreator::class.java).apply {
            putExtra("type", if (isUserActivity) "activity" else "message")
            if (targetUserId != null) {
                putExtra("userId", targetUserId)
            }
        }
        ContextCompat.startActivity(requireContext(), intent, null)
    }

    private fun refreshFeed() {
        if (!isAdded || _binding == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.clear()
            allActivities.clear()
            page = 1
            hasMoreActivities = true
            _binding?.listProgressBar?.isVisible = true
            getList()
            val currentBinding = _binding ?: return@launch
            currentBinding.emptyTextView.isVisible = adapter.itemCount == 0
            currentBinding.listProgressBar.isVisible = false
            currentBinding.feedSwipeRefresh.isRefreshing = false
        }
    }

    private suspend fun getList() {
        val maxPagesPerRequest = 10
        var pagesFetched = 0
        val initialFilteredCount = getFilteredActivities().size
        var currentFilteredCount = initialFilteredCount
        do {
            pagesFetched++
            val list = when (type) {
                ActivityType.GLOBAL -> getActivities(global = true)
                ActivityType.USER -> getActivities(filter = true)
                ActivityType.OTHER_USER -> getActivities(userId = userId)
                ActivityType.ONE -> getActivities(activityId = activityId)
            }
            allActivities.addAll(list)
            currentFilteredCount = getFilteredActivities().size
        } while (
            currentFilter != ActivityFilterType.ALL &&
            hasMoreActivities &&
            pagesFetched < maxPagesPerRequest &&
            currentFilteredCount == initialFilteredCount
        )
        applyFilter()
    }

    private fun getFilteredActivities(): List<ShinigamiActivity> = when (currentFilter) {
        ActivityFilterType.ALL -> allActivities
        ActivityFilterType.TEXT -> allActivities.filter { it.type == "TEXT" }
        ActivityFilterType.ANIME_PROGRESS -> allActivities.filter { it.type == "ANIME_LIST" }
        ActivityFilterType.ALL_PROGRESS -> allActivities.filter { it.type == "ANIME_LIST" }
        ActivityFilterType.MESSAGES -> allActivities.filter { it.type == "MESSAGE" }
        ActivityFilterType.PINNED -> emptyList()
        ActivityFilterType.SUBSCRIBED -> allActivities.filter { it.isSubscribed }
    }

    private suspend fun getActivities(
        global: Boolean = false,
        userId: String? = null,
        activityId: String? = null,
        filter: Boolean = false
    ): List<ShinigamiActivity> {
        val token = ShinigamiSessionStore(requireContext()).getToken()
            ?: run { hasMoreActivities = false; return emptyList() }
        val client = ShinigamiSocialClient()
        if (activityId != null) {
            hasMoreActivities = false
            return listOfNotNull(client.activity(token, activityId))
        }
        val pageData = if (global) client.feed(token, page) else client.activities(token, page)
        hasMoreActivities = pageData.hasNextPage
        if (hasMoreActivities) page += 1
        return if (userId == null) pageData.items else pageData.items.filter { it.author.id == userId }
    }

    private fun currentUserId(): String? = ShinigamiSessionStore(requireContext()).getUserId()

    private fun shouldLoadMore(): Boolean {
        val layoutManager =
            (binding.listRecyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
        val adapter = binding.listRecyclerView.adapter
        return hasMoreActivities &&
                !binding.listRecyclerView.canScrollVertically(1) &&
                !binding.feedRefresh.isVisible && adapter?.itemCount != 0 &&
                layoutManager == (adapter!!.itemCount - 1)

    }

    private fun onActivityClick(id: Int, type: String) {
        val intent = when (type) {
            "USER" -> Intent(requireContext(), ProfileActivity::class.java).putExtra("userId", id)
            "MEDIA" -> Intent(
                requireContext(),
                MediaDetailsActivity::class.java
            ).putExtra("mediaId", id)

            else -> return
        }
        ContextCompat.startActivity(requireContext(), intent, null)
    }

    override fun onResume() {
        super.onResume()
        if (isAdded && _binding != null) {
            binding.root.requestLayout()
            if (shouldRefreshOnResume) {
                shouldRefreshOnResume = false
                refreshFeed()
            }
        }
    }

    companion object {
        enum class ActivityType { GLOBAL, USER, OTHER_USER, ONE }

        fun newInstance(
            type: ActivityType,
            userId: String? = null,
            activityId: String? = null
        ): ActivityFragment {
            return ActivityFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("type", type)
                    userId?.let { putString("userId", it) }
                    activityId?.let { putString("activityId", it) }
                }
            }
        }
    }
}
