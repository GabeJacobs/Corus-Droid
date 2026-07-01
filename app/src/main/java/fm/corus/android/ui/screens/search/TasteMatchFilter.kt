package fm.corus.android.ui.screens.search

import fm.corus.android.data.model.SuggestedUserMatch

/**
 * Generic "hide already-followed users" filter used by the Taste Matches
 * section. If every user is already followed, falls back to the unfiltered list
 * so the section isn't empty.
 *
 * The Popular on Corus rail does NOT use this — it excludes followed accounts at
 * the fetch level (see [HorizontalPopularUsersRail]) the way iOS does, so it
 * never has to fall back to showing already-followed users.
 */
internal fun filteredUnfollowedUsers(
    enabled: Boolean,
    users: List<SuggestedUserMatch>,
    followedIds: Set<String>,
): List<SuggestedUserMatch> {
    if (!enabled) return users
    val filtered = users.filter { it.user.id !in followedIds }
    return if (filtered.isEmpty()) users else filtered
}

/** Toggle is only worth showing when the section contains both followed and unfollowed users. */
internal fun shouldShowUnfollowedFilter(
    users: List<SuggestedUserMatch>,
    followedIds: Set<String>,
): Boolean {
    val followedCount = users.count { it.user.id in followedIds }
    return followedCount > 0 && followedCount < users.size
}
