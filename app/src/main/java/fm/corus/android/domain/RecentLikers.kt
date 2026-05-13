package fm.corus.android.domain

import fm.corus.android.data.model.CymbalUser

/**
 * Reconciles a server-supplied "recent likers" preview with the current
 * user's known like state. When the user has liked the post, the current
 * user is guaranteed to be the first entry; when they haven't, they are
 * guaranteed to be absent.
 *
 * This is the single source of truth for the invariant:
 * `isLikedByCurrentUser` and `recentLikers` must never disagree about
 * whether the current user is in the list — otherwise a post can render
 * with a filled heart but a "Liked by" subtitle that omits the user.
 *
 * Pure function — call it wherever server liker data meets local like
 * state (display layer, mutation handlers, repository emissions, etc.).
 */
fun reconcileRecentLikers(
    likers: List<CymbalUser>,
    isLikedByCurrentUser: Boolean,
    currentUser: CymbalUser?,
): List<CymbalUser> {
    if (currentUser == null) return likers
    val others = likers.filter { it.id != currentUser.id }
    return if (isLikedByCurrentUser) listOf(currentUser) + others else others
}
