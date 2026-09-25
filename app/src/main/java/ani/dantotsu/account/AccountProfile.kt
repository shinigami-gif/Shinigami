package ani.dantotsu.account

import android.content.Context

data class AccountProfile(
    val id: String = "local",
    val username: String = "Shinigami User",
    val bio: String = "Welcome to Shinigami.",
    val avatarUri: String? = null,
    val bannerUri: String? = null,
)

object AccountRepository {
    private const val PREFS = "shinigami_account"
    private const val USERNAME = "username"
    private const val BIO = "bio"
    private const val AVATAR = "avatar_uri"
    private const val BANNER = "banner_uri"

    fun getCurrentUser(context: Context): AccountProfile {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AccountProfile(
            username = prefs.getString(USERNAME, "Shinigami User") ?: "Shinigami User",
            bio = prefs.getString(BIO, "Welcome to Shinigami.") ?: "Welcome to Shinigami.",
            avatarUri = prefs.getString(AVATAR, null),
            bannerUri = prefs.getString(BANNER, null),
        )
    }

    fun updateProfile(
        context: Context,
        username: String,
        bio: String,
        avatarUri: String?,
        bannerUri: String?,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(USERNAME, username)
            .putString(BIO, bio)
            .putString(AVATAR, avatarUri)
            .putString(BANNER, bannerUri)
            .apply()
    }
}
