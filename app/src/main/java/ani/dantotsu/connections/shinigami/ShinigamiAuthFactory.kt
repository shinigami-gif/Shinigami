package ani.dantotsu.connections.shinigami

import android.app.Activity

object ShinigamiAuthFactory {
    fun create(activity: Activity): ShinigamiAuthGateway = object : ShinigamiAuthGateway {
        override suspend fun signIn(activity: Activity): ShinigamiSession =
            error("Shinigami Google authentication is unavailable in this build")

        override suspend fun restoreSession(): ShinigamiSession? = null

        override suspend fun signOut() = Unit
    }
}
