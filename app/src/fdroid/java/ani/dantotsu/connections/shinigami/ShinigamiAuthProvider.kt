package ani.dantotsu.connections.shinigami

import android.app.Activity

object ShinigamiAuthProvider {
    fun create(activity: Activity): ShinigamiAuthGateway = object : ShinigamiAuthGateway {
        override suspend fun signIn(activity: Activity): ShinigamiSession =
            error("Google authentication is unavailable in the F-Droid build")

        override suspend fun restoreSession(): ShinigamiSession? = null

        override suspend fun signOut() = Unit
    }
}
