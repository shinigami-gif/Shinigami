package ani.dantotsu.media

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.connections.anilist.AnimeSearchResults
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.anilist.AnilistSearch
import ani.dantotsu.connections.anilist.AnilistSearch.SearchType
import ani.dantotsu.connections.anilist.CharacterSearchResults
import ani.dantotsu.connections.anilist.StaffSearchResults
import ani.dantotsu.connections.anilist.StudioSearchResults
import ani.dantotsu.connections.anilist.UserSearchResults
import ani.dantotsu.connections.shinigami.ShinigamiBackendClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.connections.shinigami.ShinigamiUser
import ani.dantotsu.databinding.ActivitySearchBinding
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.profile.ShinigamiUsersAdapter
import ani.dantotsu.px
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private val scope = lifecycleScope
    val model: AnilistSearch by viewModels()

    var style: Int = 0
    lateinit var searchType: SearchType
    private var screenWidth: Float = 0f

    private lateinit var mediaAdaptor: MediaAdaptor
    private lateinit var characterAdaptor: CharacterAdapter
    private lateinit var studioAdaptor: StudioAdapter
    private lateinit var staffAdaptor: AuthorAdapter
    private lateinit var usersAdapter: ShinigamiUsersAdapter

    private lateinit var progressAdapter: ProgressAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var headerAdaptor: HeaderInterface

    lateinit var animeSearchResult: AnimeSearchResults
    lateinit var characterResult: CharacterSearchResults
    lateinit var studioResult: StudioSearchResults
    lateinit var staffResult: StaffSearchResults
    lateinit var userResult: UserSearchResults
    private val shinigamiUsers = mutableListOf<ShinigamiUser>()
    private var shinigamiUserPage = 1
    private var shinigamiUserHasNext = false

    lateinit var updateChips: (() -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        screenWidth = resources.displayMetrics.run { widthPixels / density }

        binding.searchRecyclerView.updatePaddingRelative(
            top = statusBarHeight,
            bottom = navBarHeight + 80f.px
        )

        val notSet = model.notSet
        searchType = SearchType.fromString(intent.getStringExtra("type") ?: "ANIME")
        when (searchType) {
            SearchType.ANIME -> {
                style = PrefManager.getVal(PrefName.SearchStyle)
                var listOnly: Boolean? = intent.getBooleanExtra("listOnly", false)
                if (!listOnly!!) listOnly = null

                if (model.notSet) {
                    model.notSet = false
                    model.animeSearchResults = AnimeSearchResults(
                        intent.getStringExtra("type") ?: "ANIME",
                        isAdult = if (Anilist.adult) intent.getBooleanExtra(
                            "hentai",
                            false
                        ) else false,
                        onList = listOnly,
                        search = intent.getStringExtra("query"),
                        genres = intent.getStringExtra("genre")?.let { mutableListOf(it) },
                        tags = intent.getStringExtra("tag")?.let { mutableListOf(it) },
                        sort = intent.getStringExtra("sortBy"),
                        status = intent.getStringExtra("status"),
                        source = intent.getStringExtra("source"),
                        countryOfOrigin = intent.getStringExtra("country"),
                        season = intent.getStringExtra("season"),
                        seasonYear = if (intent.getStringExtra("type") == "ANIME") intent.getStringExtra(
                            "seasonYear"
                        )
                            ?.toIntOrNull() else null,
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }

                animeSearchResult = model.animeSearchResults
                mediaAdaptor =
                    MediaAdaptor(
                        style,
                        model.animeSearchResults.results,
                        this,
                        matchParent = true
                    )
            }

            SearchType.CHARACTER -> {
                if (model.notSet) {
                    model.notSet = false
                    model.characterSearchResults = CharacterSearchResults(
                        search = intent.getStringExtra("query"),
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }
                characterResult = model.characterSearchResults
                characterAdaptor = CharacterAdapter(model.characterSearchResults.results)
            }

            SearchType.STUDIO -> {
                if (model.notSet) {
                    model.notSet = false
                    model.studioSearchResults = StudioSearchResults(
                        search = intent.getStringExtra("query"),
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }
                studioResult = model.studioSearchResults
                studioAdaptor = StudioAdapter(model.studioSearchResults.results)
            }

            SearchType.STAFF -> {
                if (model.notSet) {
                    model.notSet = false
                    model.staffSearchResults = StaffSearchResults(
                        search = intent.getStringExtra("query"),
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }
                staffResult = model.staffSearchResults
                staffAdaptor = AuthorAdapter(model.staffSearchResults.results)
            }

            SearchType.USER -> {
                if (model.notSet) {
                    model.notSet = false
                    model.userSearchResults = UserSearchResults(
                        search = intent.getStringExtra("query"),
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }
                userResult = model.userSearchResults
                usersAdapter = ShinigamiUsersAdapter(shinigamiUsers, grid = true)
            }
        }

        progressAdapter = ProgressAdapter(searched = model.searched)
        headerAdaptor = if (searchType == SearchType.ANIME) {
            SearchAdapter(this, searchType)
        } else {
            SupportingSearchAdapter(this, searchType)
        }

        val gridSize = (screenWidth / 120f).toInt()
        val gridLayoutManager = GridLayoutManager(this, gridSize)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (position) {
                    0 -> gridSize
                    concatAdapter.itemCount - 1 -> gridSize
                    else -> when (style) {
                        0 -> 1
                        else -> gridSize
                    }
                }
            }
        }

        concatAdapter = when (searchType) {
            SearchType.ANIME -> {
                ConcatAdapter(headerAdaptor, mediaAdaptor, progressAdapter)
            }

            SearchType.CHARACTER -> {
                ConcatAdapter(headerAdaptor, characterAdaptor, progressAdapter)
            }

            SearchType.STUDIO -> {
                ConcatAdapter(headerAdaptor, studioAdaptor, progressAdapter)
            }

            SearchType.STAFF -> {
                ConcatAdapter(headerAdaptor, staffAdaptor, progressAdapter)
            }

            SearchType.USER -> {
                ConcatAdapter(headerAdaptor, usersAdapter, progressAdapter)
            }
        }

        binding.searchRecyclerView.layoutManager = gridLayoutManager
        binding.searchRecyclerView.adapter = concatAdapter

        binding.searchRecyclerView.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(v: RecyclerView, dx: Int, dy: Int) {
                if (!v.canScrollVertically(1)) {
                    if (searchType == SearchType.USER) {
                        if (shinigamiUserHasNext && shinigamiUsers.isNotEmpty() && !loading) {
                            scope.launch(Dispatchers.IO) {
                                loadShinigamiUsers(userResult.search, shinigamiUserPage + 1)
                            }
                        }
                    } else if (model.hasNextPage(searchType) && model.resultsIsNotEmpty(searchType) && !loading) {
                        scope.launch(Dispatchers.IO) {
                            model.loadNextPage(searchType)
                        }
                    }
                }
                super.onScrolled(v, dx, dy)
            }
        })

        when (searchType) {
            SearchType.ANIME -> {
                model.getSearch<AnimeSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.animeSearchResults.apply {
                            onList = it.onList
                            isAdult = it.isAdult
                            perPage = it.perPage
                            search = it.search
                            sort = it.sort
                            genres = it.genres
                            excludedGenres = it.excludedGenres
                            excludedTags = it.excludedTags
                            tags = it.tags
                            season = it.season
                            startYear = it.startYear
                            seasonYear = it.seasonYear
                            status = it.status
                            source = it.source
                            format = it.format
                            countryOfOrigin = it.countryOfOrigin
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.animeSearchResults.results.size
                        val newResults = it.results.distinctBy { it.id }.filter { newItem -> model.animeSearchResults.results.none { oldItem -> oldItem.id == newItem.id } }
                        model.animeSearchResults.results.addAll(newResults)
                        mediaAdaptor.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }

            SearchType.CHARACTER -> {
                model.getSearch<CharacterSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.characterSearchResults.apply {
                            search = it.search
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.characterSearchResults.results.size
                        val newResults = it.results.distinctBy { it.id }.filter { newItem -> model.characterSearchResults.results.none { oldItem -> oldItem.id == newItem.id } }
                        model.characterSearchResults.results.addAll(newResults)
                        characterAdaptor.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }

            SearchType.STUDIO -> {
                model.getSearch<StudioSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.studioSearchResults.apply {
                            search = it.search
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.studioSearchResults.results.size
                        val newResults = it.results.distinctBy { it.id }.filter { newItem -> model.studioSearchResults.results.none { oldItem -> oldItem.id == newItem.id } }
                        model.studioSearchResults.results.addAll(newResults)
                        studioAdaptor.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }

            SearchType.STAFF -> {
                model.getSearch<StaffSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.staffSearchResults.apply {
                            search = it.search
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.staffSearchResults.results.size
                        val newResults = it.results.distinctBy { it.id }.filter { newItem -> model.staffSearchResults.results.none { oldItem -> oldItem.id == newItem.id } }
                        model.staffSearchResults.results.addAll(newResults)
                        staffAdaptor.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }

            SearchType.USER -> Unit
        }

        binding.searchRecyclerView.post { runInitialSearchActions(notSet) }
    }

    fun emptyMediaAdapter() {
        searchJob?.cancel()
        loading = false
        when (searchType) {
            SearchType.ANIME -> {
                mediaAdaptor.notifyItemRangeRemoved(0, model.animeSearchResults.results.size)
                model.animeSearchResults.results.clear()
            }

            SearchType.CHARACTER -> {
                characterAdaptor.notifyItemRangeRemoved(
                    0,
                    model.characterSearchResults.results.size
                )
                model.characterSearchResults.results.clear()
            }

            SearchType.STUDIO -> {
                studioAdaptor.notifyItemRangeRemoved(0, model.studioSearchResults.results.size)
                model.studioSearchResults.results.clear()
            }

            SearchType.STAFF -> {
                staffAdaptor.notifyItemRangeRemoved(0, model.staffSearchResults.results.size)
                model.staffSearchResults.results.clear()
            }

            SearchType.USER -> {
                val size = shinigamiUsers.size
                shinigamiUsers.clear()
                shinigamiUserPage = 1
                shinigamiUserHasNext = false
                if (size > 0) usersAdapter.notifyItemRangeRemoved(0, size)
            }
        }
        progressAdapter.bar?.visibility = View.GONE
    }

    private var searchJob: Job? = null
    @Volatile
    private var loading = false
    fun search() {
        headerAdaptor.setHistoryVisibility(false)
        val size = if (searchType == SearchType.USER) shinigamiUsers.size else model.size(searchType)
        if (searchType == SearchType.USER) {
            shinigamiUsers.clear()
            shinigamiUserPage = 1
            shinigamiUserHasNext = false
        } else {
            model.clearResults(searchType)
        }
        binding.searchRecyclerView.post {
            when (searchType) {
                SearchType.ANIME -> {
                    mediaAdaptor.notifyItemRangeRemoved(0, size)
                }

                SearchType.CHARACTER -> {
                    characterAdaptor.notifyItemRangeRemoved(0, size)
                }

                SearchType.STUDIO -> {
                    studioAdaptor.notifyItemRangeRemoved(0, size)
                }

                SearchType.STAFF -> {
                    staffAdaptor.notifyItemRangeRemoved(0, size)
                }

                SearchType.USER -> {
                    if (size > 0) usersAdapter.notifyItemRangeRemoved(0, size)
                }
            }
        }

        progressAdapter.bar?.visibility = View.VISIBLE

        searchJob?.cancel()
        searchJob = scope.launch(Dispatchers.IO) {
            delay(500)
            try {
                loading = true
                if (searchType == SearchType.USER) {
                    loadShinigamiUsers(userResult.search, 1)
                } else {
                    model.loadSearch(searchType)
                }
            } finally {
                loading = false
            }
        }
    }

    private suspend fun loadShinigamiUsers(query: String?, page: Int) {
        val token = ShinigamiSessionStore(this).getToken()
        if (token.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                shinigamiUserHasNext = false
                progressAdapter.bar?.isVisible = false
            }
            return
        }
        val cleanQuery = query?.trim().orEmpty()
        if (cleanQuery.isBlank()) {
            withContext(Dispatchers.Main) {
                shinigamiUserHasNext = false
                progressAdapter.bar?.isVisible = false
            }
            return
        }
        try {
            val result = ShinigamiBackendClient().searchUsers(token, cleanQuery, page = page, perPage = 30)
            withContext(Dispatchers.Main) {
                val existing = shinigamiUsers.mapTo(HashSet()) { it.id }
                val newUsers = result.items.filter { existing.add(it.id) }
                val previous = shinigamiUsers.size
                shinigamiUsers.addAll(newUsers)
                shinigamiUserPage = result.page
                shinigamiUserHasNext = result.hasNextPage
                if (newUsers.isNotEmpty()) usersAdapter.notifyItemRangeInserted(previous, newUsers.size)
                progressAdapter.bar?.isVisible = result.hasNextPage
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                shinigamiUserHasNext = false
                progressAdapter.bar?.isVisible = false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun recycler() {
        if (searchType == SearchType.ANIME) {
            mediaAdaptor.type = style
            mediaAdaptor.notifyDataSetChanged()
        }
    }

    private fun runInitialSearchActions(notSet: Boolean) {
        if (isFinishing || isDestroyed) return

        if (!notSet) {
            if (!model.searched) {
                model.searched = true
                headerAdaptor.search?.run()
            }
        } else {
            headerAdaptor.requestFocus?.run()
        }

        if (intent.getBooleanExtra("search", false)) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
            search()
        }
    }

    var state: Parcelable? = null
    override fun onPause() {
        if (this::headerAdaptor.isInitialized) {
            headerAdaptor.addHistory()
        }
        super.onPause()
        state = binding.searchRecyclerView.layoutManager?.onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()
        binding.searchRecyclerView.layoutManager?.onRestoreInstanceState(state)
    }
}