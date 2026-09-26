package streamix.admin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import streamix.api.AdminAuditEntry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class FileAdminAuditRepository(
    private val directory: Path,
    private val gson: Gson = Gson()
) : AdminAuditRepository {
    private val file = directory.resolve("audit-log.json")
    private val lock = Any()

    override fun append(entry: AdminAuditEntry) = synchronized(lock) {
        write(read() + entry)
    }

    override fun recent(limit: Int): List<AdminAuditEntry> = synchronized(lock) {
        read().asReversed().take(limit.coerceIn(1, 500))
    }

    private fun read(): List<AdminAuditEntry> {
        if (!Files.exists(file)) return emptyList()
        val json = Files.readString(file)
        if (json.isBlank()) return emptyList()
        return gson.fromJson<List<AdminAuditEntry>>(
            json,
            object : TypeToken<List<AdminAuditEntry>>() {}.type
        ) ?: emptyList()
    }

    private fun write(entries: List<AdminAuditEntry>) {
        directory.let(Files::createDirectories)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(entries))
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
