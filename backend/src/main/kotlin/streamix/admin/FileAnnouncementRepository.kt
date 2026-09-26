package streamix.admin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import streamix.api.Announcement
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class FileAnnouncementRepository(
    private val directory: Path,
    private val gson: Gson = Gson()
) : AnnouncementRepository {
    private val file = directory.resolve("announcements.json")
    private val lock = Any()

    override fun list(page: Int, perPage: Int): List<Announcement> = synchronized(lock) {
        val size = perPage.coerceIn(1, 100)
        read().asReversed()
            .drop((page.coerceAtLeast(1) - 1) * size)
            .take(size)
    }

    override fun find(id: String): Announcement? = synchronized(lock) {
        read().firstOrNull { it.id == id }
    }

    override fun create(announcement: Announcement): Announcement = synchronized(lock) {
        require(read().none { it.id == announcement.id }) { "announcement already exists" }
        write(read() + announcement)
        announcement
    }

    override fun update(announcement: Announcement): Announcement = synchronized(lock) {
        require(read().any { it.id == announcement.id }) { "announcement not found" }
        write(read().map { if (it.id == announcement.id) announcement else it })
        announcement
    }

    override fun delete(id: String): Boolean = synchronized(lock) {
        val current = read()
        if (current.none { it.id == id }) return false
        write(current.filterNot { it.id == id })
        true
    }

    private fun read(): List<Announcement> {
        if (!Files.exists(file)) return emptyList()
        val json = Files.readString(file)
        if (json.isBlank()) return emptyList()
        return gson.fromJson<List<Announcement>>(
            json,
            object : TypeToken<List<Announcement>>() {}.type
        ) ?: emptyList()
    }

    private fun write(items: List<Announcement>) {
        directory.let(Files::createDirectories)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(items))
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
