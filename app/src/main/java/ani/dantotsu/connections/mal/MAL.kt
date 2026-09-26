package ani.dantotsu.connections.mal

/**
 * MyAnimeList is a public anime/manga metadata provider only.
 *
 * Authentication, user profile, list mutation, and tracking are intentionally
 * not part of the Shinigami MAL integration. User state belongs to Shinigami
 * Backend; MAL/Jikan are used only to enrich anime metadata.
 */
object MAL {
    val query: MALQueries = MALQueries()
    val jikan: JikanQueries = JikanQueries()
}
