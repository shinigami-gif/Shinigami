package ani.dantotsu.database

import ani.dantotsu.media.Media

fun AnimeStateRecord.applyTo(media: Media): Media {
    media.isFav = isFavorite
    media.userStatus = listStatus
    media.userProgress = progress
    media.userScore = score?.toInt() ?: 0
    media.userRepeat = repeat
    return media
}

fun Media.toAnimeStateRecord(now: Long = System.currentTimeMillis()): AnimeStateRecord =
    AnimeStateRecord(
        animeId = id,
        isFavorite = isFav,
        listStatus = userStatus,
        progress = userProgress ?: 0,
        score = if (userScore == 0) null else userScore.toDouble(),
        repeat = userRepeat,
        updatedAt = userUpdatedAt ?: now,
        lastWatchedAt = now.takeIf { userProgress != null && userProgress!! > 0 },
    )
