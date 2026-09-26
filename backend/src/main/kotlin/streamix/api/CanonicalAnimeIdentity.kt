package streamix.api

/**
 * Canonical anime identity follows the Saikou/AniList-first boundary.
 *
 * AniList owns identity. Providers only contribute deterministic mappings
 * from that identity to their own show identifiers.
 */
data class CanonicalAnimeIdentity(
    val anilistId: Long,
    val titles: List<String> = emptyList(),
    val providerMappings: Map<String, String> = emptyMap()
) {
    init {
        require(anilistId > 0L) { "anilistId must be positive" }
    }

    fun mapping(providerId: String): String? =
        providerMappings[providerId.trim().lowercase()]
}
