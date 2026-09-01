package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MediaType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fired after a new post is created so Feed / Profile can update without a
 * manual refresh. [post] is the optimistic card when the composer could
 * build one (in-app compose, share sheet); Profile inserts it immediately.
 * [mediaType] is always set so listeners can switch tabs / refresh feeds
 * even when the card is missing.
 */
data class PostCreated(
    val mediaType: MediaType,
    val post: CymbalPost? = null,
)

@Singleton
class PostCreationEvent @Inject constructor() {
    private val _events = MutableSharedFlow<PostCreated>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun notifyPostCreated(mediaType: MediaType, post: CymbalPost? = null) {
        _events.tryEmit(PostCreated(mediaType, post))
    }
}
