package ani.dantotsu.media.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.shinigami.ShinigamiLibraryClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.media.Media
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.tryWithSuspend

class ListViewModel : ViewModel() {
    var grid = MutableLiveData(PrefManager.getVal<Boolean>(PrefName.ListGrid))

    private val lists = MutableLiveData<MutableMap<String, ArrayList<Media>>>()
    private val unfilteredLists = MutableLiveData<MutableMap<String, ArrayList<Media>>>()
    fun getLists(): LiveData<MutableMap<String, ArrayList<Media>>> = lists

    suspend fun loadLists(anime: Boolean, userId: Int = 0, sortOrder: String? = null) {
        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        if (rescueMode) {
            loadListsFromMAL(anime)
            return
        }
        if (!anime) {
            lists.postValue(mutableMapOf())
            unfilteredLists.postValue(mutableMapOf())
            return
        }

        tryWithSuspend {
            val context = ani.dantotsu.App.instance
                ?: error("Application context is unavailable")
            val token = ShinigamiSessionStore(context).getToken()
                ?: error("Shinigami session is not available")

            val page = ShinigamiLibraryClient().getLibrary(token)
            val ids = page.items.map { it.mediaId.toInt() }.distinct()
            if (ids.isEmpty()) {
                lists.postValue(mutableMapOf())
                unfilteredLists.postValue(mutableMapOf())
                return@tryWithSuspend
            }

            val metadata = Anilist.metadata.getAnimeBatch(ids).orEmpty()
            val byId = metadata.associateBy { it.id }
            val result = mutableMapOf<String, ArrayList<Media>>()

            page.items.forEach { state ->
                val media = byId[state.mediaId.toInt()] ?: return@forEach
                media.userProgress = state.progress
                media.userScore = state.score?.toInt() ?: 0
                media.userStatus = state.status
                media.isFav = state.isFavorite
                media.userUpdatedAt = state.updatedAt?.let {
                    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                }

                val label = when (state.status) {
                    "WATCHING" -> "Watching"
                    "COMPLETED" -> "Completed"
                    "PLANNING" -> "Planned"
                    "PAUSED" -> "Paused"
                    "DROPPED" -> "Dropped"
                    "REWATCHING" -> "Rewatching"
                    else -> "Planning"
                }
                result.getOrPut(label) { ArrayList() }.add(media)
            }

            if (sortOrder != null) {
                result.values.forEach { mediaList ->
                    when (sortOrder) {
                        "score" -> mediaList.sortByDescending { it.userScore }
                        "title" -> mediaList.sortBy { it.name.orEmpty().lowercase() }
                        "updatedAt" -> mediaList.sortByDescending { it.userUpdatedAt ?: 0L }
                    }
                }
            }

            lists.postValue(result)
            unfilteredLists.postValue(result)
        }
    }

    private suspend fun loadListsFromMAL(anime: Boolean) {
        tryWithSuspend {
            val statuses = if (anime)
                listOf("watching" to "Watching", "completed" to "Completed", "plan_to_watch" to "Planned",
                    "on_hold" to "Paused", "dropped" to "Dropped")
            else
                listOf("reading" to "Reading", "completed" to "Completed", "plan_to_read" to "Planned",
                    "on_hold" to "Paused", "dropped" to "Dropped")

            val result = mutableMapOf<String, ArrayList<Media>>()
            for ((malStatus, label) in statuses) {
                var offset = 0
                val limit = 1000
                val mediaList = ArrayList<Media>()
                var hasNext = true
                while (hasNext) {
                    val response = if (anime)
                        MAL.query.getUserAnimeList(status = malStatus, limit = limit, offset = offset)
                    else
                        MAL.query.getUserMangaList(status = malStatus, limit = limit, offset = offset)

                    response?.data?.let { entries ->
                        mediaList.addAll(entries.map { Media(it, anime) })
                    }
                    if (response?.paging?.next != null) {
                        offset += limit
                    } else {
                        hasNext = false
                    }
                }
                if (mediaList.isNotEmpty()) result[label] = mediaList
            }
            lists.postValue(result)
            unfilteredLists.postValue(result)
        }
    }

    fun filterLists(genre: String) {
        if (genre == "All") {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        lists.postValue(currentLists.mapValues { entry ->
            entry.value.filter { genre in it.genres } as ArrayList<Media>
        }.toMutableMap())
    }

    fun filterListsByTag(tag: String) {
        if (tag == "All") {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        lists.postValue(currentLists.mapValues { entry ->
            entry.value.filter { tag in it.tags } as ArrayList<Media>
        }.toMutableMap())
    }

    fun getAllTags(): List<String> =
        unfilteredLists.value?.values?.flatten()?.flatMap { it.tags }?.distinct()?.sorted() ?: emptyList()

    fun getAllGenres(): List<String> {
        val allMedia = unfilteredLists.value?.values?.flatten() ?: return emptyList()
        val listGenres = allMedia.flatMap { it.genres }.distinct().sorted()
        return if (listGenres.isNotEmpty()) listGenres else PrefManager.getVal<Set<String>>(PrefName.GenresList).sorted()
    }

    fun searchLists(search: String) {
        if (search.isEmpty()) {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        lists.postValue(currentLists.mapValues { entry ->
            entry.value.filter {
                it.name?.contains(search, ignoreCase = true) == true ||
                    it.synonyms.any { synonym -> synonym.contains(search, ignoreCase = true) } ||
                    it.nameRomaji.contains(search, ignoreCase = true)
            } as ArrayList<Media>
        }.toMutableMap())
    }

    fun unfilterLists() {
        lists.postValue(unfilteredLists.value)
    }
}
