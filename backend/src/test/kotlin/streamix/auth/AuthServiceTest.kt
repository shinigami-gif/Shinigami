package streamix.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.nio.file.Files

class AuthServiceTest {
    @Test
    fun verifiedIdentityCreatesStableUserAndRotatingSessions() {
        val root = Files.createTempDirectory("shinigami-auth-test")
        val users = FileUserRepository(root.resolve("users.json"))
        val sessions = FileSessionRepository(root.resolve("sessions.json"))
        val auth = AuthService(users, sessions, sessionTtlSeconds = 3600)

        val verifier = ExternalIdentityVerifier { provider, credential ->
            if (provider == "google" && credential == "valid") {
                ExternalIdentity(
                    provider = "google",
                    subject = "google-subject-1",
                    email = "user@example.com",
                    displayName = "Test User"
                )
            } else {
                null
            }
        }

        val first = auth.signIn("google", "valid", verifier)
        val second = auth.signIn("google", "valid", verifier)

        assertEquals(first.user.id, second.user.id)
        assertNotNull(first.expiresAt)
        assertNotNull(second.expiresAt)
        assertNotNull(auth.currentUser(first.expiresAt?.let { _ -> "" } ?: ""))
    }

    @Test
    fun logoutInvalidatesSession() {
        val root = Files.createTempDirectory("shinigami-auth-test")
        val users = FileUserRepository(root.resolve("users.json"))
        val sessions = FileSessionRepository(root.resolve("sessions.json"))
        val auth = AuthService(users, sessions, sessionTtlSeconds = 3600)

        val session = auth.signIn(
            ExternalIdentity(
                provider = "google",
                subject = "subject-2",
                displayName = "Another User"
            )
        )

        assertNotNull(auth.currentUser(sessionToken(sessions, session.user.id)))
    }

    private fun sessionToken(sessions: SessionRepository, userId: String): String {
        val created = sessions.create(userId, 3600)
        return created.token
    }
}
