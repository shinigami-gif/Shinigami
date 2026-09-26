package ani.dantotsu.media

interface Type {
    fun asText(): String
}

enum class MediaType : Type {
    ANIME,
    MANGA,
    NOVEL;

    override fun asText(): String {
        return when (this) {
            ANIME -> "Anime"
            MANGA -> "Manga"
            NOVEL -> "Novel"
        }
    }

    companion object {
        fun fromText(string: String): MediaType? {
            return when (string) {
                "Anime" -> ANIME
                "Manga" -> MANGA
                "Novel" -> NOVEL
                else -> {
                    null
                }
            }
        }
    }
}

