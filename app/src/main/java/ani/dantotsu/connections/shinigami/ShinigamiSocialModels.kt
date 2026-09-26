package ani.dantotsu.connections.shinigami

data class ShinigamiActivity(
    val id: String,
    val author: ShinigamiUser,
    val type: String,
    val text: String? = null,
    val mediaId: Long? = null,
    val mediaTitle: String? = null,
    val replyCount: Int = 0,
    var likeCount: Int = 0,
    var isLiked: Boolean = false,
    var isSubscribed: Boolean = false,
    val createdAt: String
)

data class ShinigamiActivityReply(
    val id: String,
    val activityId: String,
    val author: ShinigamiUser,
    val text: String,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val createdAt: String
)

data class ShinigamiSocialPage<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val perPage: Int = 20,
    val hasNextPage: Boolean = false,
    val total: Long? = null
)
