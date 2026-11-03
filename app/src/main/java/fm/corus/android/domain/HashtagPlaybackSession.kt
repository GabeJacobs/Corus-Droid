package fm.corus.android.domain

import fm.corus.android.data.remote.CloudFunctionsDataSource

/** Retained by the player after leaving the hashtag screen. */
class HashtagPlaybackSession(
    tag: String,
    private val player: NowPlayingManager,
    private val fetchPage: suspend (Long?) -> CloudFunctionsDataSource.HashtagPostsPage,
) {
    private val origin = PlaybackOrigin.Hashtag(tag)
    private var before: Long? = null
    private var hasMore = true
    private val tracks = mutableListOf<QueuedTrack>()
    private val seen = mutableSetOf<String>()

    suspend fun start(): Boolean {
        fetchNextPlayablePage()
        val first = tracks.firstOrNull() ?: return false
        player.playPaginatedSource(first, tracks.toList(), origin, hasMore) { loadMore() }
        return true
    }

    private suspend fun fetchNextPlayablePage() {
        val count = tracks.size
        while (hasMore && count == tracks.size) {
            val page = fetchPage(before)
            val cursor = page.posts.lastOrNull()?.timestamp?.time
            hasMore = page.hasMore && cursor != null && (before == null || cursor < before!!)
            if (cursor != null) before = cursor
            for (track in page.posts.asPlayableQueuedTracks()) {
                if (seen.add(track.sourcePostId ?: track.trackId)) tracks.add(track)
            }
        }
    }

    private suspend fun loadMore() {
        if (player.activeContext != origin) return
        fetchNextPlayablePage()
        if (player.activeContext == origin) player.updateFeedQueue(tracks.toList(), hasMore) { loadMore() }
    }
}
