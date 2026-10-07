package fm.corus.android.domain

import fm.corus.android.data.model.FeedFilter

/** Pilot behavior is opt-in; old tabs, filters and requests remain the OFF path. */
object FeedNewTabPolicy {
    fun resolveMode(mode: String, enabled: Boolean): String =
        if (mode == FeedModeOrder.NEW_RELEASES && !enabled) FeedModeOrder.FOLLOWING else mode
    fun pageMode(mode: String, enabled: Boolean): String =
        if (enabled && mode == FeedModeOrder.FAVORITES) FeedModeOrder.FOLLOWING else mode
    fun followingMode(saved: String, favoritesAvailable: Boolean): String =
        if (saved == FeedModeOrder.FAVORITES && favoritesAvailable) saved else FeedModeOrder.FOLLOWING
    fun mediaFilter(filter: FeedFilter, isNew: Boolean): FeedFilter = if (!isNew) filter else when (filter) {
        FeedFilter.MUSIC_NEW_RELEASES -> FeedFilter.MUSIC
        FeedFilter.FILM_NEW_RELEASES -> FeedFilter.FILM
        else -> filter
    }
    fun isNew(mode: String, enabled: Boolean) = enabled && mode == FeedModeOrder.NEW_RELEASES
    fun newReleasesOnly(mode: String, enabled: Boolean, filter: FeedFilter) = isNew(mode, enabled) || filter.newReleasesOnly
    fun wireMode(mode: String, enabled: Boolean): String =
        if (isNew(mode, enabled)) FeedModeOrder.TRENDING else resolveMode(mode, enabled)
}
