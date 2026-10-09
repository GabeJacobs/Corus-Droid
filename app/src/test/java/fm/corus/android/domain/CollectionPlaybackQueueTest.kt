package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.TrackSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionPlaybackQueueTest {
    private fun post(id: String, trackId: String = "t-$id", source: TrackSource = TrackSource.SPOTIFY) = CymbalPost(
        id = id, user = CymbalUser(id = "owner", username = "owner", displayName = "Owner"),
        track = CymbalTrack(id = trackId, name = id, artistName = "Artist", albumName = "Album", source = source),
        mediaType = MediaType.TRACK,
    )
    private val origin = PlaybackOrigin.ProfileCollection("owner", "gallery-session")

    @Test fun preservesGalleryOrderAndDistinctPostsOfTheSameSong() = runTest {
        val data = listOf(post("popular", "same"), post("next", "same"), post("selected")).associateBy { it.id }
        val source = CollectionPlaybackQueue(origin,
            CollectionPlaybackPage(listOf("popular", "selected", "popular", "next"), emptyList(), false),
            { data[it] }, { error("No next page") }, { true })
        source.prepare(data.getValue("selected"))
        assertEquals(listOf("popular", "selected", "next"), source.tracks.map { it.sourcePostId })
        assertEquals(2, source.tracks.count { it.trackId == "same" })
    }

    @Test fun nextPageKeepsSourceAndSkipsMissingAndLinkOutOnlyPosts() = runTest {
        val data = listOf(post("selected"), post("next"), post("link", source = TrackSource.TIDAL)).associateBy { it.id }
        val source = CollectionPlaybackQueue(origin,
            CollectionPlaybackPage(listOf("selected"), emptyList(), true), { data[it] },
            { CollectionPlaybackPage(listOf("selected", "next", "missing", "link"), emptyList(), false) }, { true })
        source.prepare(data.getValue("selected"))
        source.loadMore()
        assertEquals(listOf("selected", "next"), source.tracks.map { it.sourcePostId })
        assertFalse(source.hasMore)
    }

    @Test fun accountSwitchRejectsHydration() = runTest {
        var allowed = true
        val source = CollectionPlaybackQueue(origin,
            CollectionPlaybackPage(listOf("selected", "next"), emptyList(), false),
            { allowed = false; post(it) }, { error("No next page") }, { allowed })
        var rejected = false
        try { source.prepare(post("selected")) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertTrue(source.tracks.isEmpty())
    }

    @Test fun failedHydrationRetriesTheSameFinalPage() = runTest {
        var fetched = 0
        var fail = true
        val source = CollectionPlaybackQueue(origin,
            CollectionPlaybackPage(listOf("selected"), emptyList(), true),
            { if (fail) { fail = false; error("offline") }; post(it) },
            { fetched++; CollectionPlaybackPage(listOf("next"), emptyList(), false) }, { true })
        source.prepare(post("selected"))
        try { source.loadMore() } catch (_: IllegalStateException) { }
        source.loadMore()
        assertEquals(1, fetched)
        assertEquals(listOf("selected", "next"), source.tracks.map { it.sourcePostId })
        assertFalse(source.hasMore)
    }
}
