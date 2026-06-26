package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires after a comment like/unlike persists. Lets the Activity/Notifications
 * screen keep its own `likedCommentIds` cache in sync with a like made from the
 * post detail / comments sheet, without waiting for a notifications refetch (which
 * otherwise only happens on pull-to-refresh or an incidental listener fire from
 * unrelated incoming activity). Mirrors `CommentDeletedEvent` / `CommentEditedEvent`.
 */
@Singleton
class CommentLikeChangedEvent @Inject constructor() {
    data class Payload(val postId: String, val commentId: String, val isLiked: Boolean)

    private val _events = MutableSharedFlow<Payload>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun notifyCommentLikeChanged(postId: String, commentId: String, isLiked: Boolean) {
        _events.tryEmit(Payload(postId, commentId, isLiked))
    }
}
