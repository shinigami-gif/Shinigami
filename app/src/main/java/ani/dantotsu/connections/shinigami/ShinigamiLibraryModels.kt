package ani.dantotsu.connections.shinigami

data class ShinigamiLibraryItem(
    val mediaId: Long,
    val status: String? = null,
    val progress: Int = 0,
    val score: Double? = null,
    val isFavorite: Boolean = false,
    val notes: String? = null,
    val updatedAt: String? = null
)

data class ShinigamiLibraryPage(
    val items: List<ShinigamiLibraryItem> = emptyList(),
    val page: Int = 1,
    val perPage: Int = 50,
    val hasNextPage: Boolean = false,
    val total: Long? = null
)
