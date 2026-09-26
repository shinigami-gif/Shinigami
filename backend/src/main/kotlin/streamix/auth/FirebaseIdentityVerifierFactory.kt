package streamix.auth

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

/**
 * Creates the Firebase-backed verifier from Application Default Credentials.
 *
 * No service-account key is stored in the repository.
 */
object FirebaseIdentityVerifierFactory {
    fun fromDefaultCredentials(): FirebaseIdentityVerifier {
        val app = FirebaseApp.getApps().firstOrNull()
            ?: FirebaseApp.initializeApp()
            ?: error("Firebase Admin initialization returned null")
        return FirebaseIdentityVerifier(FirebaseAuth.getInstance(app))
    }
}
