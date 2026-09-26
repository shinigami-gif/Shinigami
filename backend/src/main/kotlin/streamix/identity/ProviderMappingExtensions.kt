package streamix.identity

import streamix.api.CanonicalAnimeIdentity

/**
 * Resolve provider IDs from the canonical AniList identity.
 *
 * Missing mappings stay missing; Streamix never guesses a provider ID from a
 * title or search result.
 */
suspend fun ProviderMappingResolver.resolveInto(
    anime: CanonicalAnimeIdentity,
    providerIds: Iterable<String>
): CanonicalAnimeIdentity {
    val mappings = providerIds
        .map { it.trim().lowercase() }
        .filter(String::isNotBlank)
        .associateWith { providerId -> resolve(anime.anilistId, providerId) }
        .filterValues { !it.isNullOrBlank() }
        .mapValues { it.value!! }

    return anime.copy(providerMappings = anime.providerMappings + mappings)
}
