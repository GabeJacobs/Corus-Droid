package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.TrackSource

/**
 * Isolated post play (notification / deep link / post detail with no feed
 * queue) continues into that poster's Corus: newest-first song posts, starting
 * at the playing post, then their next most recent.
 */
object PosterCorusQueue {
    const val PAGE_SIZE = 15

    /**
     * If the playing post is on the first (newest) page, use that page so Next
     * is the following older song. If it isn't — someone liked an older corus —
     * Next is songs older than the playing post, not the newer ones on page 1.
     */
    fun assemblePosts(
        playing: CymbalPost,
        firstPage: List<CymbalPost>,
        olderThanPlaying: List<CymbalPost>,
    ): List<CymbalPost> {
        if (firstPage.any { it.id == playing.id }) return firstPage
        val seen = mutableSetOf(playing.id)
        val out = mutableListOf(playing)
        for (post in olderThanPlaying) {
            if (seen.add(post.id)) out.add(post)
        }
        return out
    }

}

fun List<CymbalPost>.asPlayableQueuedTracks(): List<QueuedTrack> =
    filter { it.isTrack && it.track.source != TrackSource.TIDAL && it.track.source != TrackSource.DEEZER }
        .map { it.toQueuedTrack() }
