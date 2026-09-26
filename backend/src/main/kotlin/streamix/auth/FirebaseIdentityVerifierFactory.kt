package streamix.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp

/**
 * Creates the Firebase-backed identity verifier from Application Default
 * Credentials (ADC).
 *
 * The credential itself is supplied by the deployment environment, typically
 * GOOGLE_APPLICATION_CREDENTIALS or the hosting platform's workload identity.
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
