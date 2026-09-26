package streamix.identity

import streamix.api.CanonicalAnimeIdentity

/**
 * Canonical identity is supplied by the AniList layer.
 *
 * Streamix does not fuzzy-search provider results and promote them into
 * canonical identity. A resolver may enrich an already canonical identity,
 * but it never invents an AniList ID.
 */
fun interface CanonicalIdentityResolver {
    suspend fun resolve(identity: CanonicalAnimeIdentity): CanonicalAnimeIdentity
}

object NoOpCanonicalIdentityResolver : CanonicalIdentityResolver {
    override suspend fun resolve(identity: CanonicalAnimeIdentity): CanonicalAnimeIdentity = identity
}
