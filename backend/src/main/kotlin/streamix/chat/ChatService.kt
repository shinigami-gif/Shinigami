package streamix.chat

import streamix.api.ChatPage
import streamix.notification.NotificationService

class ChatService(
    private val repository: ChatRepository,
    private val notifications: NotificationService? = null
) {
    fun global(viewerId: String, page: Int = 1, perPage: Int = 30) = page(repository.global(viewerId, page, perPage), page, perPage)
    fun anime(viewerId: String, mediaId: Long, page: Int = 1, perPage: Int = 30) = page(repository.anime(viewerId, mediaId, page, perPage), page, perPage)
    fun sendGlobal(senderId: String, content: String) = repository.sendGlobal(senderId, content)
    fun sendAnime(senderId: String, mediaId: Long, content: String) = repository.sendAnime(senderId, mediaId, content)
    fun conversations(userId: String, page: Int = 1, perPage: Int = 30) = page(repository.conversations(userId, page, perPage), page, perPage)
    fun messages(userId: String, otherUserId: String, page: Int = 1, perPage: Int = 30) = page(repository.messages(userId, otherUserId, page, perPage), page, perPage)
    fun sendMessage(senderId: String, recipientId: String, content: String) =
        repository.sendMessage(senderId, recipientId, content).also { message ->
            notifications?.message(
                recipientId = recipientId,
                senderId = senderId,
                preview = message.content,
                messageId = message.id
            )
        }
    fun unreadCount(userId: String) = repository.unreadCount(userId)
    fun markRead(userId: String, otherUserId: String) = repository.markRead(userId, otherUserId)

    private fun <T> page(items: List<T>, page: Int, perPage: Int): ChatPage<T> {
        val safePage = page.coerceAtLeast(1)
        val safePerPage = perPage.coerceIn(1, 100)
        return ChatPage(items, safePage, safePerPage, items.size == safePerPage)
    }
}
