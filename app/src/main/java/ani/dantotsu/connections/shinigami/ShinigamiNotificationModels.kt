package ani.dantotsu.connections.shinigami

data class ShinigamiNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val mediaId: Long? = null,
    val actor: ShinigamiUser? = null,
    val metadata: Map<String, String> = emptyMap(),
    val read: Boolean = false,
    val createdAt: String
)

data class ShinigamiNotificationPage(
    val items: List<ShinigamiNotification> = emptyList(),
    val page: Int = 1,
    val perPage: Int = 30,
    val hasNextPage: Boolean = false
)
