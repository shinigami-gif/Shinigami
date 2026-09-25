package ani.dantotsu.database

import ani.dantotsu.media.Media

fun AnimeStateRecord.applyTo(media: Media): Media {
    media.isFav = isFavorite
    media.userStatus = listStatus
    media.userProgress = progress
    media.userScore = score?.toInt() ?: 0
    media.userRepeat = repeat
    media.userStartedAt = epochDayToFuzzyDate(startedAt)
    media.userCompletedAt = epochDayToFuzzyDate(completedAt)
    media.userUpdatedAt = updatedAt
    return media
}

private fun ani.dantotsu.connections.anilist.api.FuzzyDate.toEpochDayOrNull(): Long? {
    val year = year ?: return null
    val month = month ?: return null
    val day = day ?: return null
    return try { java.time.LocalDate.of(year, month, day).toEpochDay() } catch (_: Exception) { null }
}

private fun epochDayToFuzzyDate(epochDay: Long?): ani.dantotsu.connections.anilist.api.FuzzyDate {
    if (epochDay == null) return ani.dantotsu.connections.anilist.api.FuzzyDate()
    return try {
        val date = java.time.LocalDate.ofEpochDay(epochDay)
        ani.dantotsu.connections.anilist.api.FuzzyDate(
            year = date.year,
            month = date.monthValue,
            day = date.dayOfMonth,
        )
    } catch (_: Exception) {
        ani.dantotsu.connections.anilist.api.FuzzyDate()
    }
}

fun Media.toAnimeStateRecord(now: Long = System.currentTimeMillis()): AnimeStateRecord =
    AnimeStateRecord(
        animeId = id,
        isFavorite = isFav,
        listStatus = userStatus,
        progress = userProgress ?: 0,
        score = if (userScore == 0) null else userScore.toDouble(),
        repeat = userRepeat,
        startedAt = userStartedAt.toEpochDayOrNull(),
        completedAt = userCompletedAt.toEpochDayOrNull(),
        updatedAt = userUpdatedAt ?: now,
        lastWatchedAt = null,
    )
