package streamix.auth

import streamix.api.ShinigamiUser

/**
 * Durable user/account boundary.
 *
 * Authentication providers must resolve into this repository; the repository
 * never depends on AniList identities.
 */
interface UserRepository {
    fun findById(id: String): ShinigamiUser?
    fun findByExternalIdentity(provider: String, subject: String): ShinigamiUser?
    fun findByUsername(username: String): ShinigamiUser?
    fun search(query: String, page: Int = 1, perPage: Int = 20): List<ShinigamiUser>
    fun create(
        username: String,
        externalProvider: String,
        externalSubject: String,
        displayName: String? = null,
        email: String? = null
    ): ShinigamiUser
    fun update(user: ShinigamiUser): ShinigamiUser
}
