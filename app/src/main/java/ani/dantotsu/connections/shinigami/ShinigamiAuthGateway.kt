package ani.dantotsu.connections.shinigami

import android.app.Activity

interface ShinigamiAuthGateway {
    suspend fun signIn(activity: Activity): ShinigamiSession
    suspend fun restoreSession(): ShinigamiSession?
    suspend fun signOut()
}
