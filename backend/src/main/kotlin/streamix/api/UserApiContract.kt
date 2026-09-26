package streamix.api

/**
 * User/account contract owned by the Shinigami backend.
 *
 * User IDs are backend-owned opaque strings. They must never be treated as
 * AniList IDs. Anime references remain canonical AniList media IDs.
 */
object UserApiContract {
    const val SESSION = "/api/v1/auth/session"
    const val LOGOUT = "/api/v1/auth/logout"

    const val ME = "/api/v1/users/me"
    const val PROFILE = "/api/v1/users/{userId}"
    const val SEARCH = "/api/v1/users/search"

    const val FOLLOWERS = "/api/v1/users/{userId}/followers"
    const val FOLLOWING = "/api/v1/users/{userId}/following"
    const val FOLLOW = "/api/v1/users/{userId}/follow"
    const val BLOCK = "/api/v1/users/{userId}/block"
}

data class ShinigamiUser(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val isFollowing: Boolean = false,
    val isFollower: Boolean = false,
    val isBlocked: Boolean = false,
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,
    val createdAt: String? = null
)

data class UserRelationship(
    val userId: String,
    val following: Boolean,
    val follower: Boolean,
    val blocked: Boolean
)

data class UserPage(
    val users: List<ShinigamiUser>,
    val page: Int,
    val perPage: Int,
    val hasNextPage: Boolean,
    val total: Long? = null
)

data class SessionResponse(
    val user: ShinigamiUser,
    val expiresAt: String? = null
)
