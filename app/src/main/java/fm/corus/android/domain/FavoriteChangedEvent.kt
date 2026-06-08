package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton event bus fired when the viewer favorites/unfavorites a user, so
 * the Favorites feed can update itself without a manual pull-to-refresh:
 * unfavoriting drops that author's posts instantly, favoriting triggers a
 * background refetch so their posts appear. Mirrors the [PostDeletionEvent]
 * pattern (and iOS's NotificationCenter broadcasts).
 */
@Singleton
class FavoriteChangedEvent @Inject constructor() {
    data class Change(val userId: String, val isFavorited: Boolean)

    private val _events = MutableSharedFlow<Change>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun notify(userId: String, isFavorited: Boolean) {
        _events.tryEmit(Change(userId, isFavorited))
    }
}
