package streamix.admin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import streamix.api.ModerationStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class FileModerationRepository(
    private val directory: Path,
    private val gson: Gson = Gson()
) : ModerationRepository {
    private val file = directory.resolve("moderation.json")
    private val lock = Any()

    override fun state(userId: String): ModerationState = synchronized(lock) {
        read().firstOrNull { it.userId == userId } ?: ModerationState(userId)
    }

    override fun setStatus(
        userId: String,
        status: ModerationStatus,
        suspendedUntil: String?,
        reason: String?
    ): ModerationState = synchronized(lock) {
        val next = state(userId).copy(
            status = status,
            suspendedUntil = suspendedUntil,
            reason = reason
        )
        write(read().filterNot { it.userId == userId } + next)
        next
    }

    override fun addWarning(userId: String, reason: String?): ModerationState = synchronized(lock) {
        val next = state(userId).copy(
            warningCount = state(userId).warningCount + 1,
            reason = reason ?: state(userId).reason
        )
        write(read().filterNot { it.userId == userId } + next)
        next
    }

    private fun read(): List<ModerationState> {
        if (!Files.exists(file)) return emptyList()
        val json = Files.readString(file)
        if (json.isBlank()) return emptyList()
        return gson.fromJson<List<ModerationState>>(
            json,
            object : TypeToken<List<ModerationState>>() {}.type
        ) ?: emptyList()
    }

    private fun write(states: List<ModerationState>) {
        directory.let(Files::createDirectories)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(states))
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
