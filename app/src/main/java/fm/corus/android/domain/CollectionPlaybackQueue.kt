package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CollectionPlaybackPage(val ids: List<String>, val posts: List<CymbalPost>, val hasMore: Boolean)

/** This source retains its own cursor/order when the profile sheet closes. */
class CollectionPlaybackQueue(
    val origin: PlaybackOrigin.ProfileCollection,
    initial: CollectionPlaybackPage,
    private val fetchPost: suspend (String) -> CymbalPost?,
    private val fetchPage: suspend () -> CollectionPlaybackPage,
    private val allowed: () -> Boolean,
) {
    private val mutex = Mutex()
    private var ids = initial.ids.distinct()
    private val posts = initial.posts.associateBy { it.id }.toMutableMap()
    private var pendingPage: CollectionPlaybackPage? = null
    var hasMore = initial.hasMore
        private set
    var tracks: List<QueuedTrack> = emptyList()
        private set

    fun contains(postId: String) = postId in ids

    suspend fun prepare(playing: CymbalPost) = mutex.withLock {
        check(allowed())
        posts[playing.id] = playing
        hydrate()
    }

    private suspend fun hydrate() {
        check(allowed())
        for (batch in ids.filter { it !in posts }.chunked(4)) {
            val resolved = coroutineScope { batch.map { id -> async { fetchPost(id) } }.awaitAll() }
            check(allowed())
            for (post in resolved.filterNotNull()) posts[post.id] = post
        }
        tracks = ids.mapNotNull { posts[it] }.asPlayableQueuedTracks()
    }

    suspend fun loadMore() = mutex.withLock {
        check(allowed())
        if (hasMore || pendingPage != null) {
            val page = pendingPage ?: fetchPage()
            pendingPage = page
            check(allowed())
            ids = (ids + page.ids).distinct()
            for (post in page.posts) posts[post.id] = post
            hydrate()
            hasMore = page.hasMore
            pendingPage = null
        } else hydrate()
    }

    fun activate(manager: NowPlayingManager, playing: CymbalPost) {
        if (!allowed()) return
        manager.setPlaybackOrigin(origin)
        manager.setQueueFromCoordinator(tracks, playing.track.id, playing.id, hasMore) {
            if (!allowed() || manager.activeContext != origin) return@setQueueFromCoordinator
            loadMore()
            if (allowed() && manager.activeContext == origin) syncPagination(manager)
        }
    }

    private fun syncPagination(manager: NowPlayingManager) {
        manager.updateFeedQueue(tracks, hasMore) {
            if (!allowed() || manager.activeContext != origin) return@updateFeedQueue
            loadMore()
            if (allowed() && manager.activeContext == origin) syncPagination(manager)
        }
    }
}
