package ani.dantotsu.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnimeStateRepository(
    private val database: ShinigamiDatabase,
) {
    private val queries get() = database.animeStateQueries

    suspend fun get(animeId: Int): AnimeStateRecord? = withContext(Dispatchers.IO) {
        queries.selectById(animeId.toLong()).executeAsOneOrNull()?.toRecord()
    }

    suspend fun upsert(state: AnimeStateRecord) = withContext(Dispatchers.IO) {
        queries.upsert(
            animeId = state.animeId.toLong(),
            isFavorite = if (state.isFavorite) 1 else 0,
            listStatus = state.listStatus,
            progress = state.progress.toLong(),
            score = state.score,
            repeat = state.repeat.toLong(),
            startedAt = state.startedAt,
            completedAt = state.completedAt,
            updatedAt = state.updatedAt,
            lastWatchedAt = state.lastWatchedAt,
        )
    }

    suspend fun updateProgress(animeId: Int, progress: Int, now: Long = System.currentTimeMillis()) {
        val current = get(animeId) ?: AnimeStateRecord(animeId = animeId)
        upsert(
            current.copy(
                progress = progress,
                updatedAt = now,
                lastWatchedAt = now,
            ),
        )
    }

    suspend fun updateStatus(animeId: Int, status: String?, now: Long = System.currentTimeMillis()) {
        val current = get(animeId) ?: AnimeStateRecord(animeId = animeId)
        upsert(current.copy(listStatus = status, updatedAt = now))
    }

    suspend fun updateFavorite(animeId: Int, favorite: Boolean, now: Long = System.currentTimeMillis()) {
        val current = get(animeId) ?: AnimeStateRecord(animeId = animeId)
        upsert(current.copy(isFavorite = favorite, updatedAt = now))
    }

    suspend fun delete(animeId: Int) = withContext(Dispatchers.IO) {
        queries.deleteById(animeId.toLong())
    }

    private fun AnimeState.toRecord() = AnimeStateRecord(
        animeId = animeId.toInt(),
        isFavorite = isFavorite != 0L,
        listStatus = listStatus,
        progress = progress.toInt(),
        score = score,
        repeat = repeat.toInt(),
        startedAt = startedAt,
        completedAt = completedAt,
        updatedAt = updatedAt,
        lastWatchedAt = lastWatchedAt,
    )
}

data class AnimeStateRecord(
    val animeId: Int,
    val isFavorite: Boolean = false,
    val listStatus: String? = null,
    val progress: Int = 0,
    val score: Double? = null,
    val repeat: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val updatedAt: Long? = null,
    val lastWatchedAt: Long? = null,
)
