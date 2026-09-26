package ani.dantotsu.connections.shinigami

data class ShinigamiChatMessage(
    val id: String,
    val roomType: String,
    val sender: ShinigamiUser,
    val recipient: ShinigamiUser? = null,
    val mediaId: Long? = null,
    val content: String,
    val read: Boolean = false,
    val createdAt: String
)

data class ShinigamiMessageConversation(
    val user: ShinigamiUser,
    val lastMessage: ShinigamiChatMessage,
    val unreadCount: Int = 0
)

data class ShinigamiChatPage<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val perPage: Int = 30,
    val hasNextPage: Boolean = false
)

data class ShinigamiChatSend(
    val content: String
)
