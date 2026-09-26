package streamix.auth

import com.google.gson.Gson
import streamix.chat.ChatService
import streamix.chat.FileChatRepository
import streamix.chat.ChatRepository
import streamix.library.FileUserLibraryRepository
import streamix.library.UserLibraryRepository
import streamix.library.UserLibraryService
import streamix.notification.FileNotificationRepository
import streamix.notification.NotificationRepository
import streamix.notification.NotificationService
import streamix.social.FileSocialRepository
import streamix.social.SocialRepository
import streamix.social.SocialService
import java.nio.file.Path

/**
 * Composition root for backend-owned authentication and user state.
 *
 * The default file store is intentionally replaceable. A production deployment
 * can provide another UserRepository/SessionRepository without changing the
 * Android API contracts or auth service.
 */
class AuthRuntime(
    dataRoot: Path,
    gson: Gson = Gson(),
    sessionTtlSeconds: Long = 60L * 60L * 24L * 30L
) {
    val users: UserRepository = FileUserRepository(
        file = dataRoot.resolve("users.json"),
        gson = gson
    )

    val sessions: SessionRepository = FileSessionRepository(
        file = dataRoot.resolve("sessions.json"),
        gson = gson
    )

    val auth: AuthService = AuthService(
        users = users,
        sessions = sessions,
        sessionTtlSeconds = sessionTtlSeconds
    )

    val userService: UserService = UserService(users)
    val library: UserLibraryRepository = FileUserLibraryRepository(dataRoot.resolve("user-library.json"), gson)
    val libraryService: UserLibraryService = UserLibraryService(library)
    val social: SocialRepository = FileSocialRepository(dataRoot.resolve("social"), users, gson)
    val socialService: SocialService = SocialService(social)
    val chat: ChatRepository = FileChatRepository(dataRoot.resolve("chat"), users, gson)
    val notification: NotificationRepository = FileNotificationRepository(
        dataRoot.resolve("notifications"),
        users,
        gson
    )
    val notificationService: NotificationService = NotificationService(notification)
    val chatService: ChatService = ChatService(chat, notificationService)
}
