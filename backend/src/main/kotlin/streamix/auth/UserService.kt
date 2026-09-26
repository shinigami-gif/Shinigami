package streamix.auth

import streamix.api.UserPage
import streamix.api.UserProfile
import streamix.api.UserStats
import streamix.api.ShinigamiUser

class UserService(
    private val users: UserRepository
) {
    fun me(userId: String): UserProfile =
        profile(userId, userId)

    fun profile(userId: String, viewerId: String? = null): UserProfile {
        val user = users.findById(userId) ?: error("user not found")
        val viewer = viewerId?.let(users::findById)
        val relationship = viewerId?.takeIf { it != userId }?.let { users.relationship(it, userId) }
        return UserProfile(
            user = user.copy(
                isFollowing = relationship?.following ?: false,
                isFollower = relationship?.follower ?: false,
                isBlocked = relationship?.blocked ?: false
            ),
            stats = UserStats(),
            followerCount = countFollowers(userId),
            followingCount = countFollowing(userId)
        )
    }

    private fun countFollowing(userId: String): Long =
        users.search("", 1, 100000).count { it.id != userId && users.relationship(userId, it.id).following }.toLong()

    private fun countFollowers(userId: String): Long =
        users.search("", 1, 100000).count { it.id != userId && users.relationship(it.id, userId).following }.toLong()

    fun search(query: String, page: Int = 1, perPage: Int = 20): UserPage {
        val safePage = page.coerceAtLeast(1)
        val safePerPage = perPage.coerceIn(1, 100)
        val usersOnPage = users.search(query, safePage, safePerPage)
        return UserPage(
            users = usersOnPage,
            page = safePage,
            perPage = safePerPage,
            hasNextPage = usersOnPage.size == safePerPage,
            total = null
        )
    }

    fun updateProfile(
        userId: String,
        username: String,
        displayName: String?,
        bio: String?,
        avatarUrl: String?,
        bannerUrl: String?
    ): ShinigamiUser {
        val current = users.findById(userId) ?: error("user not found")
        return users.update(
            current.copy(
                username = username.trim(),
                displayName = displayName?.trim()?.ifBlank { null },
                bio = bio,
                avatarUrl = avatarUrl,
                bannerUrl = bannerUrl
            )
        )
    }
}
