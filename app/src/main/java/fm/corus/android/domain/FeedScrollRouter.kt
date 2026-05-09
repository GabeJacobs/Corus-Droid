package fm.corus.android.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes "scroll to a specific post" requests from MainTabScreen's mini-player
 * tap handler to whichever feed-style screen is currently visible at root
 * (the main feed, or a ProfileFeedScreen).
 *
 * Each screen registers a handler when it's the topmost visible feed and
 * clears it on disposal. If a handler returns `true`, it scrolled; the
 * mini-player tap handler then skips its default "push PostDetail"
 * navigation. If no handler is registered or the registered handler returns
 * `false` (e.g. the post isn't in its loaded list), the mini-player falls
 * through to the existing detail-push.
 *
 * Mirrors iOS .feedScrollToPost / .profileFeedScrollToPost notifications and
 * the ActiveProfileFeedHandle gating logic in one place.
 */
@Singleton
class FeedScrollRouter @Inject constructor() {
    /**
     * Set by the currently-visible feed at root. Returns `true` if it
     * scrolled to [postId]; `false` if it can't (e.g. post not loaded).
     *
     * Marked @Volatile because writes happen on the main thread but reads
     * may briefly cross threads if a callback runs from a coroutine; the
     * single-foreground-screen invariant means there's no actual contention.
     */
    @Volatile
    var handler: ((postId: String) -> Boolean)? = null
}
