package fm.corus.android.ui.screens.search

import fm.corus.android.data.model.SuggestedUserMatch

internal fun filteredMusicMatchUsers(
    enabled: Boolean,
    musicMatchUsers: List<SuggestedUserMatch>,
    followedIds: Set<String>,
): List<SuggestedUserMatch> {
    if (!enabled) return musicMatchUsers
    val filtered = musicMatchUsers.filter { it.user.id !in followedIds }
    return if (filtered.isEmpty()) musicMatchUsers else filtered
}

internal fun showUnfollowedMatchesToggle(
    musicMatchUsers: List<SuggestedUserMatch>,
    followedIds: Set<String>,
): Boolean {
    val followedCount = musicMatchUsers.count { it.user.id in followedIds }
    return followedCount > 0 && followedCount < musicMatchUsers.size
}
