package ani.dantotsu.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.connections.shinigami.ShinigamiBackendClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.databinding.ActivityFollowBinding
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.toast
import com.xwray.groupie.GroupieAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FollowActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFollowBinding
    private val adapter = GroupieAdapter()
    private var users = emptyList<ani.dantotsu.connections.shinigami.ShinigamiUser>()
    private lateinit var selected: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityFollowBinding.inflate(layoutInflater)
        binding.listToolbar.updateLayoutParams<MarginLayoutParams> { topMargin = statusBarHeight }
        binding.listFrameLayout.updateLayoutParams<MarginLayoutParams> { bottomMargin = navBarHeight }
        setContentView(binding.root)

        selected = getSelected(PrefManager.getVal<Int>(PrefName.FollowerLayout))
        binding.followFilterButton.visibility = View.GONE
        binding.followerGrid.alpha = 0.33f
        binding.followerList.alpha = 0.33f
        selected(selected)
        binding.listRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.listRecyclerView.adapter = adapter
        binding.listProgressBar.visibility = View.VISIBLE
        binding.listBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val title = intent.getStringExtra("title")
        val userId = intent.getStringExtra("userId")
        binding.listTitle.text = title

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = ShinigamiSessionStore(this@FollowActivity).getToken()
                    ?: throw IllegalStateException("Not signed in")
                require(!userId.isNullOrBlank()) { "Missing backend user id" }

                val page = when (title) {
                    "Following" -> ShinigamiBackendClient().getFollowing(token, userId)
                    "Followers" -> ShinigamiBackendClient().getFollowers(token, userId)
                    else -> throw IllegalArgumentException("Unknown relationship list")
                }
                users = page.items

                withContext(Dispatchers.Main) {
                    fillList()
                    binding.listProgressBar.visibility = View.GONE
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    binding.listProgressBar.visibility = View.GONE
                    toast(error.message ?: "Failed to load users")
                }
            }
        }

        binding.followerList.setOnClickListener {
            selected(it as ImageButton)
            PrefManager.setVal(PrefName.FollowerLayout, 0)
            fillList()
        }
        binding.followerGrid.setOnClickListener {
            selected(it as ImageButton)
            PrefManager.setVal(PrefName.FollowerLayout, 1)
            fillList()
        }
        binding.followSwipeRefresh.setOnRefreshListener { binding.followSwipeRefresh.isRefreshing = false }
    }

    private fun fillList() {
        adapter.clear()
        val screenWidth = resources.displayMetrics.run { widthPixels / density }
        binding.listRecyclerView.layoutManager = when (getLayoutType(selected)) {
            1 -> GridLayoutManager(this, (screenWidth / 120f).toInt(), GridLayoutManager.VERTICAL, false)
            else -> LinearLayoutManager(this)
        }

        val currentUserId = ShinigamiSessionStore(this).getUserId()
        users.forEach { user ->
            adapter.add(
                ShinigamiFollowerItem(
                    grid = getLayoutType(selected) == 1,
                    user = user,
                    scope = lifecycleScope,
                    currentUserId = currentUserId,
                    clickCallback = { id ->
                        startActivity(
                            Intent(this, ProfileActivity::class.java)
                                .putExtra("userId", id)
                        )
                    }
                )
            )
        }
    }

    private fun selected(view: ImageButton) {
        selected.alpha = 0.33f
        selected = view
        selected.alpha = 1f
    }

    private fun getSelected(pos: Int): ImageButton = when (pos) {
        1 -> binding.followerGrid
        else -> binding.followerList
    }

    private fun getLayoutType(view: ImageButton): Int =
        if (view == binding.followerGrid) 1 else 0
}
