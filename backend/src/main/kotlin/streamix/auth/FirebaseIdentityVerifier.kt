package streamix.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken

/**
 * Verifies Firebase Authentication ID tokens for the Shinigami backend.
 *
 * The Android client sends a Firebase ID token. The Admin SDK verifies its
 * signature, issuer, audience and expiry before the UID is accepted as the
 * external subject.
 */
class FirebaseIdentityVerifier(
    private val firebaseAuth: FirebaseAuth,
    private val acceptedProvider: String = "firebase",
    private val acceptedSignInProvider: String = "google.com"
) : ExternalIdentityVerifier {

    override fun verify(provider: String, credential: String): ExternalIdentity? {
        if (!provider.equals(acceptedProvider, ignoreCase = true)) return null
        if (credential.isBlank()) return null

        val token = try {
            firebaseAuth.verifyIdToken(credential)
        } catch (_: FirebaseAuthException) {
            return null
        } catch (_: RuntimeException) {
            return null
        }

        val subject = token.uid.trim()
        if (subject.isEmpty()) return null

        val signInProvider = firebaseSignInProvider(token) ?: return null
        if (!signInProvider.equals(acceptedSignInProvider, ignoreCase = true)) return null

        return ExternalIdentity(
            provider = acceptedProvider,
            subject = subject,
            email = token.email?.takeIf { it.isNotBlank() },
            displayName = token.name?.takeIf { it.isNotBlank() }
        )
    }

    private fun firebaseSignInProvider(token: FirebaseToken): String? {
        val firebase = token.claims["firebase"] as? Map<*, *> ?: return null
        return firebase["sign_in_provider"] as? String
    }
}
