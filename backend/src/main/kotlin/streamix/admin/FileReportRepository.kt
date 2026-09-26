package streamix.admin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import streamix.api.AdminReport
import streamix.api.ReportStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class FileReportRepository(
    private val directory: Path,
    private val gson: Gson = Gson()
) : ReportRepository {
    private val file = directory.resolve("reports.json")
    private val lock = Any()

    override fun list(page: Int, perPage: Int): List<AdminReport> = synchronized(lock) {
        val size = perPage.coerceIn(1, 100)
        read().asReversed()
            .drop((page.coerceAtLeast(1) - 1) * size)
            .take(size)
    }

    override fun find(id: String): AdminReport? = synchronized(lock) {
        read().firstOrNull { it.id == id }
    }

    override fun create(report: AdminReport): AdminReport = synchronized(lock) {
        require(read().none { it.id == report.id }) { "report already exists" }
        write(read() + report)
        report
    }

    override fun update(report: AdminReport): AdminReport = synchronized(lock) {
        require(read().any { it.id == report.id }) { "report not found" }
        write(read().map { if (it.id == report.id) report else it })
        report
    }

    override fun count(status: ReportStatus?): Long = synchronized(lock) {
        read().count { status == null || it.status == status }.toLong()
    }

    override fun countForUser(userId: String): Long = synchronized(lock) {
        read().count { it.targetUserId == userId }.toLong()
    }

    private fun read(): List<AdminReport> {
        if (!Files.exists(file)) return emptyList()
        val json = Files.readString(file)
        if (json.isBlank()) return emptyList()
        return gson.fromJson<List<AdminReport>>(
            json,
            object : TypeToken<List<AdminReport>>() {}.type
        ) ?: emptyList()
    }

    private fun write(reports: List<AdminReport>) {
        directory.let(Files::createDirectories)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(reports))
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
