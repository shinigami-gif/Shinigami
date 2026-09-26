package ani.dantotsu.connections.shinigami

data class ShinigamiComment(
    val id: String,
    val author: ShinigamiUser,
    val mediaId: Long,
    val parentCommentId: String? = null,
    var content: String,
    val tag: Int? = null,
    var upvotes: Int = 0,
    var downvotes: Int = 0,
    var userVote: Int? = null,
    val replyCount: Int = 0,
    val deleted: Boolean = false,
    val createdAt: String,
    var updatedAt: String? = null
)

data class ShinigamiCommentPage(
    val items: List<ShinigamiComment> = emptyList(),
    val page: Int = 1,
    val perPage: Int = 20,
    val hasNextPage: Boolean = false,
    val total: Long? = null
)

data class ShinigamiReportRequest(
    val targetUserId: String? = null,
    val targetContentId: String? = null,
    val type: String,
    val description: String
)
