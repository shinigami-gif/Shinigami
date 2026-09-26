package streamix.notification

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import streamix.api.NotificationType
import streamix.api.ShinigamiNotification
import streamix.api.ShinigamiUser
import streamix.auth.UserRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

private data class StoredNotification(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val targetType: String?,
    val targetId: String?,
    val mediaId: Long?,
    val actorId: String?,
    val metadata: Map<String, String>,
    val read: Boolean,
    val createdAt: String
)

class FileNotificationRepository(
    private val root: Path,
    private val users: UserRepository,
    private val gson: Gson = Gson()
) : NotificationRepository {
    private val lock = Any()

    init {
        Files.createDirectories(root)
    }

    override fun list(userId: String, page: Int, perPage: Int): List<ShinigamiNotification> =
        synchronized(lock) {
            paginate(
                read().filter { it.userId == userId }.sortedByDescending { it.createdAt },
                page,
                perPage
            ).mapNotNull(::toApi)
        }

    override fun unreadCount(userId: String): Int =
        synchronized(lock) {
            read().count { it.userId == userId && !it.read }
        }

    override fun create(
        userId: String,
        type: String,
        title: String,
        body: String,
        imageUrl: String?,
        targetType: String?,
        targetId: String?,
        mediaId: Long?,
        actorId: String?,
        metadata: Map<String, String>
    ): ShinigamiNotification = synchronized(lock) {
        require(users.findById(userId) != null) { "notification recipient not found" }
        require(title.isNotBlank()) { "notification title is required" }
        require(body.isNotBlank()) { "notification body is required" }

        val normalizedType = type.trim().uppercase()
        require(NotificationType.entries.any { it.name == normalizedType }) {
            "unsupported notification type"
        }

        val stored = StoredNotification(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = normalizedType,
            title = title.trim(),
            body = body.trim(),
            imageUrl = imageUrl,
            targetType = targetType,
            targetId = targetId,
            mediaId = mediaId,
            actorId = actorId,
            metadata = metadata.toMap(),
            read = false,
            createdAt = Instant.now().toString()
        )
        write(read() + stored)
        toApi(stored)!!
    }

    override fun markRead(userId: String, notificationId: String): Boolean =
        synchronized(lock) {
            val items = read()
            if (items.none { it.id == notificationId && it.userId == userId }) return false
            write(items.map {
                if (it.id == notificationId && it.userId == userId) it.copy(read = true) else it
            })
            true
        }

    override fun markAllRead(userId: String): Int =
        synchronized(lock) {
            val items = read()
            val count = items.count { it.userId == userId && !it.read }
            if (count == 0) return 0
            write(items.map {
                if (it.userId == userId && !it.read) it.copy(read = true) else it
            })
            count
        }

    private fun toApi(item: StoredNotification): ShinigamiNotification? {
        val type = runCatching { NotificationType.valueOf(item.type) }.getOrNull() ?: return null
        return ShinigamiNotification(
            id = item.id,
            type = type,
            title = item.title,
            body = item.body,
            imageUrl = item.imageUrl,
            targetType = item.targetType,
            targetId = item.targetId,
            mediaId = item.mediaId,
            actor = item.actorId?.let(users::findById),
            metadata = item.metadata,
            read = item.read,
            createdAt = item.createdAt
        )
    }

    private fun read(): List<StoredNotification> {
        val file = root.resolve("notifications.json")
        if (!Files.exists(file)) return emptyList()
        val text = Files.readString(file)
        if (text.isBlank()) return emptyList()
        return gson.fromJson(text, object : TypeToken<List<StoredNotification>>() {}.type) ?: emptyList()
    }

    private fun write(items: List<StoredNotification>) {
        val file = root.resolve("notifications.json")
        val temp = root.resolve("notifications.json.tmp")
        Files.writeString(temp, gson.toJson(items))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun <T> paginate(items: List<T>, page: Int, perPage: Int): List<T> {
        val safePage = page.coerceAtLeast(1)
        val safePerPage = perPage.coerceIn(1, 100)
        return items.drop((safePage - 1) * safePerPage).take(safePerPage)
    }
}
