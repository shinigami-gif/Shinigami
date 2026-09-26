package streamix.auth

import com.google.gson.Gson
import streamix.library.FileUserLibraryRepository
import streamix.library.UserLibraryRepository
import streamix.library.UserLibraryService
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
}
