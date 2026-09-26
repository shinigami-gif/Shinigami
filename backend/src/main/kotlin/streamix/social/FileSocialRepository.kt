package streamix.social

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import streamix.api.ActivityRef
import streamix.api.ActivityReplyRef
import streamix.api.ShinigamiUser
import streamix.api.SocialComment
import streamix.api.NotificationRef
import streamix.auth.UserRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

private data class StoredActivity(
    val id: String,
    val authorId: String,
    val type: String,
    val text: String?,
    val mediaId: Long?,
    val mediaTitle: String?,
    val createdAt: String,
    val likedBy: Set<String> = emptySet(),
    val subscribedBy: Set<String> = emptySet()
)

private data class StoredActivityReply(
    val id: String,
    val activityId: String,
    val authorId: String,
    val text: String,
    val createdAt: String,
    val likedBy: Set<String> = emptySet()
)

private data class StoredComment(
    val id: String,
    val mediaId: Long,
    val parentCommentId: String?,
    val authorId: String,
    val content: String,
    val upvotesBy: Set<String> = emptySet(),
    val downvotesBy: Set<String> = emptySet(),
    val deleted: Boolean = false,
    val createdAt: String,
    val updatedAt: String? = null
)

private data class StoredNotification(
    val id: String,
    val userId: String,
    val type: String,
    val actorId: String?,
    val activityId: String?,
    val commentId: String?,
    val mediaId: Long?,
    val message: String?,
    val read: Boolean,
    val createdAt: String
)

class FileSocialRepository(
    private val root: Path,
    private val users: UserRepository,
    private val gson: Gson = Gson()
) : SocialRepository {
    private val lock = Any()

    init {
        Files.createDirectories(root)
    }

    override fun activities(userId: String?, page: Int, perPage: Int): List<ActivityRef> = synchronized(lock) {
        paginate(read<StoredActivity>("activities.json").sortedByDescending { it.createdAt }, page, perPage)
            .mapNotNull { activity(it, userId) }
    }

    override fun activity(activityId: String, viewerId: String?): ActivityRef? = synchronized(lock) {
        read<StoredActivity>("activities.json").firstOrNull { it.id == activityId }?.let { activity(it, viewerId) }
    }

    override fun createActivity(authorId: String, type: String, text: String?, mediaId: Long?, mediaTitle: String?): ActivityRef =
        synchronized(lock) {
            require(users.findById(authorId) != null) { "author not found" }
            val stored = StoredActivity(UUID.randomUUID().toString(), authorId, type, text, mediaId, mediaTitle, Instant.now().toString())
            write("activities.json", read<StoredActivity>("activities.json") + stored)
            activity(stored, authorId)!!
        }

    override fun deleteActivity(activityId: String, actorId: String): Boolean = synchronized(lock) {
        val items = read<StoredActivity>("activities.json")
        val item = items.firstOrNull { it.id == activityId } ?: return false
        if (item.authorId != actorId) return false
        write("activities.json", items.filterNot { it.id == activityId })
        true
    }

    override fun replies(activityId: String, viewerId: String?, page: Int, perPage: Int): List<ActivityReplyRef> = synchronized(lock) {
        paginate(read<StoredActivityReply>("activity-replies.json").filter { it.activityId == activityId }.sortedBy { it.createdAt }, page, perPage)
            .mapNotNull { reply ->
                val author = users.findById(reply.authorId) ?: return@mapNotNull null
                ActivityReplyRef(reply.id, reply.activityId, author, reply.text, reply.likedBy.size, viewerId in reply.likedBy, reply.createdAt)
            }
    }

    override fun createReply(activityId: String, authorId: String, text: String): ActivityReplyRef = synchronized(lock) {
        require(activity(activityId, authorId) != null) { "activity not found" }
        require(users.findById(authorId) != null) { "author not found" }
        val stored = StoredActivityReply(UUID.randomUUID().toString(), activityId, authorId, text.trim(), Instant.now().toString())
        write("activity-replies.json", read<StoredActivityReply>("activity-replies.json") + stored)
        replies(activityId, authorId, 1, 100000).first { it.id == stored.id }
    }

    override fun toggleActivityLike(activityId: String, userId: String): ActivityRef? = synchronized(lock) {
        mutateActivity(activityId) { it.copy(likedBy = toggle(it.likedBy, userId)) }?.let { activity(it, userId) }
    }

    override fun toggleActivitySubscription(activityId: String, userId: String): ActivityRef? = synchronized(lock) {
        mutateActivity(activityId) { it.copy(subscribedBy = toggle(it.subscribedBy, userId)) }?.let { activity(it, userId) }
    }

    override fun comments(mediaId: Long, viewerId: String?, parentCommentId: String?, page: Int, perPage: Int): List<SocialComment> = synchronized(lock) {
        paginate(read<StoredComment>("comments.json").filter { it.mediaId == mediaId && it.parentCommentId == parentCommentId && !it.deleted }.sortedBy { it.createdAt }, page, perPage)
            .mapNotNull { comment -> commentToApi(comment, viewerId) }
    }

    override fun commentReplies(commentId: String, viewerId: String?, page: Int, perPage: Int): List<SocialComment> = synchronized(lock) {
        val items = read<StoredComment>("comments.json")
        val parent = items.firstOrNull { it.id == commentId && !it.deleted } ?: return@synchronized emptyList()
        paginate(items.filter { it.mediaId == parent.mediaId && it.parentCommentId == commentId && !it.deleted }.sortedBy { it.createdAt }, page, perPage)
            .mapNotNull { commentToApi(it, viewerId) }
    }

    override fun createComment(mediaId: Long, authorId: String, content: String, parentCommentId: String?): SocialComment = synchronized(lock) {
        require(users.findById(authorId) != null) { "author not found" }
        val stored = StoredComment(UUID.randomUUID().toString(), mediaId, parentCommentId, authorId, content.trim(), createdAt = Instant.now().toString())
        write("comments.json", read<StoredComment>("comments.json") + stored)
        commentToApi(stored, authorId)!!
    }

    override fun deleteComment(commentId: String, actorId: String): Boolean = synchronized(lock) {
        val items = read<StoredComment>("comments.json")
        val item = items.firstOrNull { it.id == commentId } ?: return false
        if (item.authorId != actorId) return false
        write("comments.json", items.map { if (it.id == commentId) it.copy(deleted = true, updatedAt = Instant.now().toString()) else it })
        true
    }

    override fun editComment(commentId: String, actorId: String, content: String): SocialComment? = synchronized(lock) {
        val normalized = content.trim()
        require(normalized.isNotBlank()) { "content is required" }
        val items = read<StoredComment>("comments.json")
        val item = items.firstOrNull { it.id == commentId && !it.deleted } ?: return null
        if (item.authorId != actorId) return null
        val updated = item.copy(content = normalized, updatedAt = Instant.now().toString())
        write("comments.json", items.map { if (it.id == commentId) updated else it })
        commentToApi(updated, actorId)
    }

    override fun voteComment(commentId: String, userId: String, vote: Int?): SocialComment? = synchronized(lock) {
        val items = read<StoredComment>("comments.json")
        val item = items.firstOrNull { it.id == commentId } ?: return null
        val updated = item.copy(
            upvotesBy = item.upvotesBy - userId + if (vote == 1) setOf(userId) else emptySet(),
            downvotesBy = item.downvotesBy - userId + if (vote == -1) setOf(userId) else emptySet()
        )
        write("comments.json", items.map { if (it.id == commentId) updated else it })
        commentToApi(updated, userId)
    }

    override fun notifications(userId: String, page: Int, perPage: Int): List<NotificationRef> = synchronized(lock) {
        paginate(read<StoredNotification>("notifications.json").filter { it.userId == userId }.sortedByDescending { it.createdAt }, page, perPage)
            .mapNotNull { notificationToApi(it) }
    }

    override fun unreadNotificationCount(userId: String): Int =
        synchronized(lock) { read<StoredNotification>("notifications.json").count { it.userId == userId && !it.read } }

    override fun markNotificationRead(notificationId: String, userId: String): Boolean = synchronized(lock) {
        val items = read<StoredNotification>("notifications.json")
        if (items.none { it.id == notificationId && it.userId == userId }) return false
        write("notifications.json", items.map { if (it.id == notificationId) it.copy(read = true) else it })
        true
    }

    private fun activity(item: StoredActivity, viewerId: String?): ActivityRef? {
        val author = users.findById(item.authorId) ?: return null
        val replies = read<StoredActivityReply>("activity-replies.json").count { it.activityId == item.id }
        return ActivityRef(item.id, author, item.type, item.text, item.mediaId, item.mediaTitle, replies, item.likedBy.size, viewerId in item.likedBy, viewerId in item.subscribedBy, item.createdAt)
    }

    private fun commentToApi(item: StoredComment, viewerId: String?): SocialComment? {
        val author = users.findById(item.authorId) ?: return null
        val vote = when {
            viewerId != null && viewerId in item.upvotesBy -> 1
            viewerId != null && viewerId in item.downvotesBy -> -1
            else -> null
        }
        val replies = read<StoredComment>("comments.json").count { it.parentCommentId == item.id && !it.deleted }
        return SocialComment(item.id, author, item.mediaId, item.parentCommentId, item.content, item.upvotesBy.size, item.downvotesBy.size, vote, replies, item.deleted, item.createdAt, item.updatedAt)
    }

    private fun notificationToApi(item: StoredNotification): NotificationRef? {
        val actor = item.actorId?.let(users::findById)
        return NotificationRef(item.id, item.type, actor, item.activityId, item.commentId, item.mediaId, item.message, item.read, item.createdAt)
    }

    private fun mutateActivity(id: String, transform: (StoredActivity) -> StoredActivity): StoredActivity? {
        val items = read<StoredActivity>("activities.json")
        val current = items.firstOrNull { it.id == id } ?: return null
        val updated = transform(current)
        write("activities.json", items.map { if (it.id == id) updated else it })
        return updated
    }

    private fun toggle(current: Set<String>, userId: String): Set<String> =
        if (userId in current) current - userId else current + userId

    private inline fun <reified T> read(name: String): List<T> {
        val file = root.resolve(name)
        if (!Files.exists(file)) return emptyList()
        val text = Files.readString(file)
        if (text.isBlank()) return emptyList()
        return gson.fromJson(text, object : TypeToken<List<T>>() {}.type) ?: emptyList()
    }

    private fun <T> write(name: String, items: List<T>) {
        val file = root.resolve(name)
        val temp = root.resolve("$name.tmp")
        Files.writeString(temp, gson.toJson(items))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun <T> paginate(items: List<T>, page: Int, perPage: Int): List<T> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = perPage.coerceIn(1, 100)
        return items.drop((safePage - 1) * safeSize).take(safeSize)
    }
}
