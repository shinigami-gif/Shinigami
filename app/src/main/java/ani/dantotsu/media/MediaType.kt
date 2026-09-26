package ani.dantotsu.media

interface Type {
    fun asText(): String
}

enum class MediaType : Type {
    ANIME;

    override fun asText(): String = "Anime"

    companion object {
        fun fromText(string: String): MediaType? =
            if (string == "Anime") ANIME else null
    }
}
