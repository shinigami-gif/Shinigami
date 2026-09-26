package streamix.admin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import streamix.api.AdminRole
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private data class StoredRole(
    val userId: String,
    val role: String
)

class FileAdminRoleRepository(
    private val directory: Path,
    private val gson: Gson = Gson()
) : AdminRoleRepository {
    private val file = directory.resolve("roles.json")
    private val lock = Any()

    override fun role(userId: String): AdminRole = synchronized(lock) {
        read().firstOrNull { it.userId == userId }?.role
            ?.let { runCatching { AdminRole.valueOf(it) }.getOrNull() }
            ?: AdminRole.MEMBER
    }

    override fun setRole(userId: String, role: AdminRole): AdminRole = synchronized(lock) {
        val current = read()
        val updated = current.filterNot { it.userId == userId } + StoredRole(userId, role.name)
        write(updated)
        role
    }

    private fun read(): List<StoredRole> {
        if (!Files.exists(file)) return emptyList()
        val json = Files.readString(file)
        if (json.isBlank()) return emptyList()
        return gson.fromJson<List<StoredRole>>(
            json,
            object : TypeToken<List<StoredRole>>() {}.type
        ) ?: emptyList()
    }

    private fun write(roles: List<StoredRole>) {
        directory.let(Files::createDirectories)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(roles))
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
