package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton event bus that fires after a new post is created,
 * allowing the Feed and Profile screens to refresh automatically.
 */
@Singleton
class PostCreationEvent @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun notifyPostCreated() {
        _events.tryEmit(Unit)
    }
}
