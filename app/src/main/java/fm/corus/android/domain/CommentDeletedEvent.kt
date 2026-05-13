package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires after a comment delete succeeds. Mirrors `CommentEditedEvent` — lets
 * screens that render preview comments under post cards drop the deleted
 * entry from their in-memory `previewComments` snapshot immediately, without
 * waiting for the server-side `onCommentDeletedUpdatePreview` rebuild plus a
 * refetch. Distinct from `PostDeletionEvent`, which fires on *post* deletes.
 */
@Singleton
class CommentDeletedEvent @Inject constructor() {
    data class Payload(val postId: String, val commentId: String)

    private val _events = MutableSharedFlow<Payload>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun notifyCommentDeleted(postId: String, commentId: String) {
        _events.tryEmit(Payload(postId, commentId))
    }
}
