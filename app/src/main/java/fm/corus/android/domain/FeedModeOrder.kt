package fm.corus.android.domain

/**
 * Canonical feed-mode tokens and the parser for the `feed_mode_order` Remote
 * Config string that drives the order of the feed switcher menu.
 *
 * The tokens (`tasteMatches`, `trending`, `following`, `favorites`) are the
 * shared vocabulary across iOS (`FeedMode.rawValue`) and web (the `FeedMode`
 * union), so a single Remote Config value reorders every client identically.
 * Ordering is a pure UI concern — the per-mode availability gates
 * (`tasteMatchesAvailable` / `trendingFeedEnabled` / `favoritesEnabled`) still
 * decide which rows actually render.
 */
object FeedModeOrder {
    const val TASTE_MATCHES = "tasteMatches"
    const val TRENDING = "trending"
    const val FOLLOWING = "following"
    const val FAVORITES = "favorites"

    /** Default order — matches the historical hardcoded menu. */
    val DEFAULT: List<String> = listOf(TASTE_MATCHES, TRENDING, FOLLOWING, FAVORITES)

    /** Comma-separated default, used as the in-app Remote Config default value. */
    const val DEFAULT_RAW = "tasteMatches,trending,following,favorites"

    private fun normalize(token: String): String =
        token.trim().lowercase().replace("_", "").replace(" ", "")

    private val CANONICAL_BY_NORMALIZED: Map<String, String> =
        DEFAULT.associateBy { normalize(it) }

    /**
     * Parses a comma-separated `feed_mode_order` value into the ordered list of
     * canonical mode tokens. Tolerant by design so a Remote Config typo can
     * never hide a mode:
     *  - matching ignores case, underscores, and spaces, so "taste_matches",
     *    "TasteMatches", and "Taste Matches" all resolve to "tasteMatches";
     *  - unknown tokens are dropped;
     *  - duplicates collapse to their first occurrence;
     *  - any canonical mode absent from the string is appended in [DEFAULT]
     *    order, so the result always contains exactly the four modes once each.
     */
    fun parse(raw: String?): List<String> {
        val ordered = LinkedHashSet<String>()
        raw?.split(",")?.forEach { token ->
            CANONICAL_BY_NORMALIZED[normalize(token)]?.let { ordered.add(it) }
        }
        ordered.addAll(DEFAULT)
        return ordered.toList()
    }
}
