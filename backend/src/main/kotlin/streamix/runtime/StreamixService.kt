package streamix.runtime

import streamix.api.CanonicalAnimeIdentity
import streamix.api.EpisodeRef
import streamix.api.ProviderAnime
import streamix.api.StreamRef
import streamix.identity.NoOpCanonicalIdentityResolver
import streamix.identity.CanonicalIdentityResolver
import streamix.identity.ProviderMappingResolver
import streamix.identity.resolveInto
import streamix.routing.ProviderRouter

/**
 * Application-facing orchestration.
 *
 * AniList supplies canonical identity. Streamix then maps that identity to
 * provider IDs, lets the Router select providers, and delegates the native
 * CloudStream provider lifecycle through to streams.
 */
class StreamixService(
    private val router: ProviderRouter,
    private val identityResolver: CanonicalIdentityResolver = NoOpCanonicalIdentityResolver,
    private val mappingResolver: ProviderMappingResolver? = null
) {
    suspend fun search(query: String, page: Int = 1): List<ProviderAnime> =
        router.search(query, page)

    suspend fun canonical(identity: CanonicalAnimeIdentity): CanonicalAnimeIdentity =
        identityResolver.resolve(identity)

    suspend fun mapProviders(
        anime: CanonicalAnimeIdentity,
        providerIds: Iterable<String>
    ): CanonicalAnimeIdentity =
        mappingResolver?.resolveInto(anime, providerIds) ?: anime

    suspend fun detail(anime: CanonicalAnimeIdentity): List<ProviderAnime> =
        router.detail(canonical(anime))

    suspend fun episodes(anime: CanonicalAnimeIdentity): List<EpisodeRef> =
        router.episodes(canonical(anime))

    suspend fun streams(episode: EpisodeRef): List<StreamRef> =
        router.streams(episode)
}
