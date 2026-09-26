package streamix.identity

/**
 * Resolves a provider-specific anime identifier from the canonical AniList ID.
 *
 * The mapping source is external/deterministic; Streamix does not invent or
 * infer provider IDs from titles when a canonical AniList ID is available.
 */
fun interface ProviderMappingResolver {
    suspend fun resolve(anilistId: Long, providerId: String): String?
}
