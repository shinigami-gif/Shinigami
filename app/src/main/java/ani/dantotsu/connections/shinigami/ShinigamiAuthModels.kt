package ani.dantotsu.connections.shinigami

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

data class ShinigamiSession(
    val user: ShinigamiUser,
    val expiresAt: String? = null,
    val token: String? = null
)
