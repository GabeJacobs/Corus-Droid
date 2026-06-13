package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton event bus fired when the viewer saves/unsaves a post, so the
 * profile Saves tab stays in sync without a manual pull-to-refresh: saving
 * inserts the post at the top, unsaving drops it instantly. [post] is non-null
 * on a save (so the tab can render it without a refetch) and null on an unsave.
 * Fired only after the server write succeeds, so a cap-rejected save never adds
 * a phantom row. Mirrors [FavoriteChangedEvent] / [PostDeletionEvent] (and iOS's
 * NotificationCenter broadcasts).
 */
@Singleton
class SaveChangedEvent @Inject constructor() {
    data class Change(val postId: String, val isSaved: Boolean, val post: CymbalPost?)

    private val _events = MutableSharedFlow<Change>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun notify(postId: String, isSaved: Boolean, post: CymbalPost?) {
        _events.tryEmit(Change(postId, isSaved, post))
    }
}
