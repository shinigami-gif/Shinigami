package streamix.auth

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

private data class StoredSession(
    val token: String,
    val userId: String,
    val createdAt: String,
    val expiresAt: String
)

class FileSessionRepository(
    private val file: Path,
    private val gson: Gson = Gson()
) : SessionRepository {
    private val lock = Any()
    private val random = SecureRandom()

    override fun create(userId: String, ttlSeconds: Long): AuthSession = synchronized(lock) {
        require(ttlSeconds > 0) { "session TTL must be positive" }
        val now = Instant.now()
        val session = AuthSession(
            token = newToken(),
            userId = userId,
            createdAt = now,
            expiresAt = now.plusSeconds(ttlSeconds)
        )
        write(read() + session.toStored())
        session
    }

    override fun find(token: String): AuthSession? = synchronized(lock) {
        read().firstOrNull { it.token == token }
            ?.toSession()
            ?.takeIf { it.expiresAt.isAfter(Instant.now()) }
    }

    override fun delete(token: String) = synchronized(lock) {
        write(read().filterNot { it.token == token })
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun read(): List<StoredSession> {
        if (!Files.exists(file)) return emptyList()
        val json = Files.readString(file)
        if (json.isBlank()) return emptyList()
        return gson.fromJson<List<StoredSession>>(
            json,
            object : TypeToken<List<StoredSession>>() {}.type
        ) ?: emptyList()
    }

    private fun write(sessions: List<StoredSession>) {
        file.parent?.let(Files::createDirectories)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(sessions))
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun AuthSession.toStored() = StoredSession(
        token = token,
        userId = userId,
        createdAt = createdAt.toString(),
        expiresAt = expiresAt.toString()
    )

    private fun StoredSession.toSession() = AuthSession(
        token = token,
        userId = userId,
        createdAt = Instant.parse(createdAt),
        expiresAt = Instant.parse(expiresAt)
    )
}
