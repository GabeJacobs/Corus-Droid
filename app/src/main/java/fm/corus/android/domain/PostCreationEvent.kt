package fm.corus.android.domain

import fm.corus.android.data.model.MediaType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton event bus that fires after a new post is created,
 * allowing the Feed and Profile screens to refresh automatically.
 * Carries the [MediaType] of the post so listeners (e.g. the profile
 * screen) can switch to the tab that surfaces the new content.
 */
@Singleton
class PostCreationEvent @Inject constructor() {
    private val _events = MutableSharedFlow<MediaType>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun notifyPostCreated(mediaType: MediaType) {
        _events.tryEmit(mediaType)
    }
}
