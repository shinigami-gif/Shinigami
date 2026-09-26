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
        assertNotNull(first.token)
        assertNotNull(second.token)
        assertEquals(first.user.id, auth.currentUser(first.token!!)?.id)
        assertEquals(first.user.id, auth.currentUser(second.token!!)?.id)
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

        val token = session.token!!
        assertNotNull(auth.currentUser(token))
        auth.logout(token)
        assertNull(auth.currentUser(token))
    }
}
