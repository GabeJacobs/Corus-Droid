package fm.corus.android.domain

import fm.corus.android.data.model.*
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.util.Date

class HashtagPlaybackSessionTest {
    private fun post(id: String, time: Long, media: MediaType = MediaType.TRACK) = CymbalPost(
        id=id, user=CymbalUser(id="owner",username="owner",displayName="Owner"),
        track=CymbalTrack(id=id,name=id,artistName="Artist",albumName="Album"), timestamp=Date(time),mediaType=media,
    )
    @Test fun `skips film page and owns pagination after starting playback`() = runTest {
        val player=mock<NowPlayingManager>()
        val cursors=mutableListOf<Long?>()
        val session=HashtagPlaybackSession("jazz",player) { before ->
            cursors.add(before)
            when(before) {
                null -> CloudFunctionsDataSource.HashtagPostsPage(listOf(post("film",300,MediaType.MOVIE)),3,true)
                300L -> CloudFunctionsDataSource.HashtagPostsPage(listOf(post("a",200)),3,true)
                else -> CloudFunctionsDataSource.HashtagPostsPage(listOf(post("b",100)),3,false)
            }
        }
        assertTrue(session.start())
        val origin=argumentCaptor<PlaybackOrigin>()
        val more=argumentCaptor<suspend () -> Unit>()
        verify(player).playPaginatedSource(any(),any(),origin.capture(),eq(true),more.capture())
        assertEquals(listOf(null,300L),cursors)
        whenever(player.activeContext).thenReturn(origin.firstValue)
        more.firstValue.invoke()
        val queue=argumentCaptor<List<QueuedTrack>>()
        verify(player).updateFeedQueue(queue.capture(),eq(false),any())
        assertEquals(listOf("a","b"),queue.firstValue.map { it.sourcePostId })
    }
    @Test fun `empty source never starts playback`() = runTest {
        val player=mock<NowPlayingManager>()
        val session=HashtagPlaybackSession("jazz",player) {CloudFunctionsDataSource.HashtagPostsPage(emptyList(),0,false)}
        assertFalse(session.start())
        verifyNoInteractions(player)
    }
    @Test fun `failed next page retries without advancing the cursor`() = runTest {
        val player=mock<NowPlayingManager>()
        var failNext=true
        val cursors=mutableListOf<Long?>()
        val session=HashtagPlaybackSession("jazz",player) { before ->
            cursors.add(before)
            if(before==null) CloudFunctionsDataSource.HashtagPostsPage(listOf(post("a",200)),2,true)
            else {
                if(failNext) {failNext=false;error("offline")}
                CloudFunctionsDataSource.HashtagPostsPage(listOf(post("b",100)),2,false)
            }
        }
        session.start()
        val more=argumentCaptor<suspend () -> Unit>()
        verify(player).playPaginatedSource(any(),any(),any(),any(),more.capture())
        whenever(player.activeContext).thenReturn(PlaybackOrigin.Hashtag("jazz"))
        try {more.firstValue.invoke();fail("expected failure")} catch(_:IllegalStateException) {}
        more.firstValue.invoke()
        assertEquals(listOf(null,200L,200L),cursors)
    }
}
