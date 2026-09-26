package ani.dantotsu.connections.shinigami

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object ShinigamiAuthProvider {
    fun create(activity: Activity): ShinigamiAuthGateway = Gateway(activity)

    private class Gateway(
        private val activity: Activity
    ) : ShinigamiAuthGateway {
        private val auth = FirebaseAuth.getInstance()
        private val credentials = CredentialManager.create(activity)
        private val sessionStore = ShinigamiSessionStore(activity)

        override suspend fun signIn(activity: Activity): ShinigamiSession {
            val backend = ShinigamiBackendClient()
            val googleOption = GetGoogleIdOption.Builder()
                .setServerClientId(activity.getString(ani.dantotsu.R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .build()

            val result = credentials.getCredential(
                activity,
                GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()
            )

            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                throw IllegalStateException("Google credential was not returned")
            }

            val googleIdToken = try {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (error: GoogleIdTokenParsingException) {
                throw IllegalStateException("Invalid Google ID token", error)
            }

            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            try {
                auth.signInWithCredential(firebaseCredential).await()
                val firebaseUser = auth.currentUser
                    ?: error("Firebase user is missing after sign-in")
                val firebaseIdToken = firebaseUser.getIdToken(false).await()?.token
                    ?: error("Firebase ID token is missing")

                val session = backend.createSession(
                    provider = "firebase",
                    credential = firebaseIdToken
                )
                sessionStore.save(session)
                return session
            } catch (error: Throwable) {
                auth.signOut()
                throw error
            }
        }

        override suspend fun restoreSession(): ShinigamiSession? {
            val token = sessionStore.getToken() ?: return null
            return try {
                ShinigamiBackendClient().currentSession(token).also(sessionStore::save)
            } catch (_: Throwable) {
                sessionStore.clear()
                auth.signOut()
                null
            }
        }

        override suspend fun signOut() {
            sessionStore.getToken()?.let { token ->
                runCatching { ShinigamiBackendClient().logout(token) }
            }
            sessionStore.clear()
            auth.signOut()
            runCatching {
                credentials.clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }
}
