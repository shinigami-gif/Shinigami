package streamix.auth

import streamix.api.SessionResponse
import streamix.api.ShinigamiUser
import java.time.Instant

/**
 * Authentication orchestration.
 *
 * External credentials are intentionally represented by provider + subject.
 * Token verification belongs to the concrete auth adapter (for example,
 * Firebase/Google) and must happen before this service is called.
 */
data class ExternalIdentity(
    val provider: String,
    val subject: String,
    val email: String? = null,
    val displayName: String? = null
)

class AuthService(
    private val users: UserRepository,
    private val sessions: SessionRepository,
    private val sessionTtlSeconds: Long = 60L * 60L * 24L * 30L
) {
    fun signIn(identity: ExternalIdentity): SessionResponse {
        require(identity.provider.isNotBlank()) { "auth provider is required" }
        require(identity.subject.isNotBlank()) { "auth subject is required" }

        val user = users.findByExternalIdentity(identity.provider, identity.subject)
            ?: users.create(
                username = defaultUsername(identity),
                externalProvider = identity.provider,
                externalSubject = identity.subject,
                displayName = identity.displayName,
                email = identity.email
            )

        val session = sessions.create(user.id, sessionTtlSeconds)
        return SessionResponse(
            user = user,
            expiresAt = session.expiresAt.toString()
        )
    }

    fun signIn(
        provider: String,
        credential: String,
        verifier: ExternalIdentityVerifier
    ): SessionResponse {
        require(provider.isNotBlank()) { "auth provider is required" }
        require(credential.isNotBlank()) { "auth credential is required" }
        val identity = verifier.verify(provider, credential)
            ?: throw IllegalArgumentException("invalid authentication credential")
        return signIn(identity)
    }

    fun currentUser(token: String): ShinigamiUser? {
        val session = sessions.find(token) ?: return null
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.delete(token)
            return null
        }
        return users.findById(session.userId)
    }

    fun logout(token: String) {
        sessions.delete(token)
    }

    private fun defaultUsername(identity: ExternalIdentity): String {
        val base = identity.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: identity.email
                ?.substringBefore("@")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: "user"

        val suffix = identity.subject.takeLast(8)
            .filter(Char::isLetterOrDigit)
            .lowercase()

        return "$base-$suffix"
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .take(24)
            .ifBlank { "user-$suffix" }
    }
}
