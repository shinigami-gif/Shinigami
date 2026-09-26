package streamix.auth

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import streamix.api.ShinigamiUser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

private data class StoredUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val bio: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val isFollowing: Boolean,
    val isFollower: Boolean,
    val isBlocked: Boolean,
    val isAdmin: Boolean,
    val isModerator: Boolean,
    val createdAt: String?,
    val externalProvider: String,
    val externalSubject: String,
    val email: String?
)

class FileUserRepository(
    private val file: Path,
    private val gson: Gson = Gson()
) : UserRepository {
    private val lock = Any()

    override fun findById(id: String): ShinigamiUser? = synchronized(lock) {
        read().firstOrNull { it.id == id }?.toPublic()
    }

    override fun findByExternalIdentity(provider: String, subject: String): ShinigamiUser? =
        synchronized(lock) {
            read().firstOrNull {
                it.externalProvider == provider && it.externalSubject == subject
            }?.toPublic()
        }

    override fun findByUsername(username: String): ShinigamiUser? = synchronized(lock) {
        read().firstOrNull { it.username.equals(username, ignoreCase = true) }?.toPublic()
    }

    override fun search(query: String, page: Int, perPage: Int): List<ShinigamiUser> =
        synchronized(lock) {
            val normalized = query.trim().lowercase()
            val safePage = page.coerceAtLeast(1)
            val safeSize = perPage.coerceIn(1, 100)
            read()
                .asSequence()
                .filter {
                    normalized.isBlank() ||
                        it.username.lowercase().contains(normalized) ||
                        it.displayName.orEmpty().lowercase().contains(normalized)
                }
                .sortedBy { it.username.lowercase() }
                .drop((safePage - 1) * safeSize)
                .take(safeSize)
                .map(StoredUser::toPublic)
                .toList()
        }

    override fun create(
        username: String,
        externalProvider: String,
        externalSubject: String,
        displayName: String?,
        email: String?
    ): ShinigamiUser = synchronized(lock) {
        val users = read()
        require(users.none { it.externalProvider == externalProvider && it.externalSubject == externalSubject }) {
            "user already exists for external identity"
        }
        require(users.none { it.username.equals(username, ignoreCase = true) }) {
            "username already exists"
        }

        val stored = StoredUser(
            id = UUID.randomUUID().toString(),
            username = username,
            displayName = displayName,
            bio = null,
            avatarUrl = null,
            bannerUrl = null,
            isFollowing = false,
            isFollower = false,
            isBlocked = false,
            isAdmin = false,
            isModerator = false,
            createdAt = Instant.now().toString(),
            externalProvider = externalProvider,
            externalSubject = externalSubject,
            email = email
        )
        write(users + stored)
        stored.toPublic()
    }

    override fun update(user: ShinigamiUser): ShinigamiUser = synchronized(lock) {
        val users = read()
        val current = users.firstOrNull { it.id == user.id }
            ?: error("user not found: ${user.id}")
        require(users.none { it.id != user.id && it.username.equals(user.username, ignoreCase = true) }) {
            "username already exists"
        }

        val updated = current.copy(
            username = user.username,
            displayName = user.displayName,
            bio = user.bio,
            avatarUrl = user.avatarUrl,
            bannerUrl = user.bannerUrl,
            isFollowing = user.isFollowing,
            isFollower = user.isFollower,
            isBlocked = user.isBlocked,
            isAdmin = user.isAdmin,
            isModerator = user.isModerator
        )
        write(users.map { if (it.id == user.id) updated else it })
        updated.toPublic()
    }

    private fun read(): List<StoredUser> {
        if (!Files.exists(file)) return emptyList()
        val json = Files.readString(file)
        if (json.isBlank()) return emptyList()
        return gson.fromJson<List<StoredUser>>(
            json,
            object : TypeToken<List<StoredUser>>() {}.type
        ) ?: emptyList()
    }

    private fun write(users: List<StoredUser>) {
        file.parent?.let(Files::createDirectories)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(users))
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun StoredUser.toPublic() = ShinigamiUser(
        id = id,
        username = username,
        displayName = displayName,
        bio = bio,
        avatarUrl = avatarUrl,
        bannerUrl = bannerUrl,
        isFollowing = isFollowing,
        isFollower = isFollower,
        isBlocked = isBlocked,
        isAdmin = isAdmin,
        isModerator = isModerator,
        createdAt = createdAt
    )
}
