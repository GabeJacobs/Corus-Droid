package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton event bus that fires after a post is deleted, allowing any
 * screen displaying that post (Profile, ProfileFeed, Feed, OtherProfile,
 * etc.) to remove it from its local state immediately. Mirrors the iOS
 * `cymbalDeleted` NotificationCenter pattern.
 */
@Singleton
class PostDeletionEvent @Inject constructor() {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun notifyPostDeleted(postId: String) {
        _events.tryEmit(postId)
    }
}
