package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import java.util.UUID

/** Hydrates an already-shuffled ID snapshot in bounded, retryable batches. */
class ProfileShuffleQueue(
    private val ids: List<String>,
    private val fetchPosts: suspend (List<String>) -> List<CymbalPost>,
) {
    private var offset = 0
    private val loaded = mutableListOf<QueuedTrack>()
    val tracks: List<QueuedTrack> get() = loaded.toList()
    val hasMore: Boolean get() = offset < ids.size

    suspend fun loadMore() {
        val oldCount = loaded.size
        while (hasMore && loaded.size == oldCount) {
            val end = minOf(offset + 30, ids.size)
            val posts = fetchPosts(ids.subList(offset, end))
            loaded.addAll(posts.asPlayableQueuedTracks())
            offset = end
        }
    }
}

/** Owns the queue even after the profile screen is closed. */
class ProfileShuffleSession(
    userId: String,
    ids: List<String>,
    private val player: NowPlayingManager,
    fetchPosts: suspend (List<String>) -> List<CymbalPost>,
) {
    private val origin = PlaybackOrigin.ProfileCollection(userId, UUID.randomUUID().toString())
    private val source = ProfileShuffleQueue(ids, fetchPosts)

    suspend fun start(): Boolean {
        source.loadMore()
        val first = source.tracks.firstOrNull() ?: return false
        player.playProfileShuffle(first, source.tracks, origin, source.hasMore) { loadMore() }
        return true
    }

    private fun syncQueue() {
        player.updateFeedQueue(source.tracks, source.hasMore) { loadMore() }
    }

    private suspend fun loadMore() {
        if (player.activeContext != origin) return
        source.loadMore()
        if (player.activeContext == origin) syncQueue()
    }
}
