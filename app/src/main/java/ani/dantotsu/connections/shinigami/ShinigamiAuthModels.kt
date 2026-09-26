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

data class ShinigamiUserStats(
    val animeTotal: Int = 0,
    val animeWatching: Int = 0,
    val animeCompleted: Int = 0,
    val animePlanned: Int = 0,
    val animePaused: Int = 0,
    val animeDropped: Int = 0,
    val episodesWatched: Int = 0,
    val meanScore: Double? = null
)

data class ShinigamiUserProfile(
    val user: ShinigamiUser,
    val stats: ShinigamiUserStats = ShinigamiUserStats(),
    val followerCount: Long = 0,
    val followingCount: Long = 0
)

data class ShinigamiSession(
    val user: ShinigamiUser,
    val expiresAt: String? = null,
    val token: String? = null
)
