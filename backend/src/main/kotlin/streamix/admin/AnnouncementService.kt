package streamix.admin

import streamix.api.AdminPermission
import streamix.api.AdminAuditEntry
import streamix.api.Announcement
import streamix.auth.UserRepository
import streamix.notification.AnnouncementEvent
import streamix.notification.NotificationEventPublisher
import java.time.Instant
import java.util.UUID

class AnnouncementService(
    private val users: UserRepository,
    private val access: AdminAccessService,
    private val announcements: AnnouncementRepository,
    private val notifications: NotificationEventPublisher,
    private val audit: AdminAuditRepository
) {
    fun list(actorId: String, page: Int, perPage: Int): List<Announcement> {
        val actor = users.findById(actorId) ?: error("user not found")
        access.require(actor, AdminPermission.MANAGE_ANNOUNCEMENTS)
        return announcements.list(page, perPage)
    }

    fun create(actorId: String, title: String, body: String, imageUrl: String?): Announcement {
        val actor = users.findById(actorId) ?: error("user not found")
        access.require(actor, AdminPermission.MANAGE_ANNOUNCEMENTS)
        require(title.isNotBlank()) { "announcement title is required" }
        require(body.isNotBlank()) { "announcement body is required" }

        val announcement = Announcement(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            body = body.trim(),
            imageUrl = imageUrl?.trim()?.ifBlank { null },
            published = false,
            createdBy = actor.id,
            createdAt = Instant.now().toString()
        )
        announcements.create(announcement)
        record(actor.id, "ANNOUNCEMENT_CREATED", announcement.id)
        return announcement
    }

    fun publish(actorId: String, id: String): Announcement {
        val actor = users.findById(actorId) ?: error("user not found")
        access.require(actor, AdminPermission.MANAGE_ANNOUNCEMENTS)
        val current = announcements.find(id) ?: error("announcement not found")
        if (current.published) return current

        val published = current.copy(
            published = true,
            publishedAt = Instant.now().toString()
        )
        announcements.update(published)
        notifications.publish(
            AnnouncementEvent(
                title = published.title,
                body = published.body,
                recipientUserIds = users.allIds(),
                imageUrl = published.imageUrl,
                announcementId = published.id
            )
        )
        record(actor.id, "ANNOUNCEMENT_PUBLISHED", published.id)
        return published
    }

    fun delete(actorId: String, id: String): Boolean {
        val actor = users.findById(actorId) ?: error("user not found")
        access.require(actor, AdminPermission.MANAGE_ANNOUNCEMENTS)
        val deleted = announcements.delete(id)
        if (deleted) record(actor.id, "ANNOUNCEMENT_DELETED", id)
        return deleted
    }

    private fun record(actorId: String, action: String, announcementId: String) {
        audit.append(
            AdminAuditEntry(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = action,
                targetType = "announcement",
                targetId = announcementId,
                createdAt = Instant.now().toString()
            )
        )
    }
}
