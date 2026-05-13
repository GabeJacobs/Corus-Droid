package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires after a comment edit succeeds. Lets screens that render preview
 * comments under post cards (Feed, hashtag feeds, profile, detail) patch the
 * matching `previewComments` entry immediately instead of waiting for the
 * server-side `onCommentUpdatedUpdatePreview` rebuild to land on the next
 * fetch. Mirrors `PostDeletionEvent`.
 */
@Singleton
class CommentEditedEvent @Inject constructor() {
    data class Payload(val postId: String, val commentId: String, val newText: String)

    private val _events = MutableSharedFlow<Payload>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun notifyCommentEdited(postId: String, commentId: String, newText: String) {
        _events.tryEmit(Payload(postId, commentId, newText))
    }
}
