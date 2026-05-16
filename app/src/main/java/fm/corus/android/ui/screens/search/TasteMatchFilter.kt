package fm.corus.android.ui.screens.search

import fm.corus.android.data.model.SuggestedUserMatch

/**
 * Generic "hide already-followed users" filter used by both the Taste Matches
 * section and the Popular on Corus rail. If every user is already followed,
 * falls back to the unfiltered list so the rail isn't empty.
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
