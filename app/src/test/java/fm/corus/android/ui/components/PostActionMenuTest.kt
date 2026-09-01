package fm.corus.android.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostActionMenuTest {

    private val testContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `add to queue shown for spotify tracks`() {
        assertTrue(showAddToQueueRow(trackPost()))
    }

    @Test
    fun `add to queue hidden for tidal and deezer exclusives`() {
        assertFalse(showAddToQueueRow(trackPost(source = TrackSource.TIDAL)))
        assertFalse(showAddToQueueRow(trackPost(source = TrackSource.DEEZER)))
    }

    @Test
    fun `add to queue hidden for movies`() {
        assertFalse(showAddToQueueRow(moviePost()))
    }

    @Test
    fun `report and block shown for non-owner human-authored posts`() {
        assertTrue(showPostReportBlockActions(isMine = false, authorIsBot = false))
    }

    @Test
    fun `report and block hidden for bot-authored posts`() {
        assertFalse(showPostReportBlockActions(isMine = false, authorIsBot = true))
    }

    @Test
    fun `report and block hidden for own posts`() {
        assertFalse(showPostReportBlockActions(isMine = true, authorIsBot = false))
    }

    @Test
    fun `report and block hidden for own bot posts`() {
        assertFalse(showPostReportBlockActions(isMine = true, authorIsBot = true))
    }

    // ── Go to Artist / Go to Album gating ──

    private fun user() = CymbalUser(id = "u1", username = "amy", displayName = "Amy")

    private fun trackPost(
        artistIds: List<String> = listOf("artist1"),
        albumId: String? = "album1",
        source: TrackSource = TrackSource.SPOTIFY,
        audiomackAlbumUrl: String? = null,
        audiomackArtistUrl: String? = null,
    ) = CymbalPost(
        id = "p1",
        user = user(),
        track = CymbalTrack(
            id = "t1",
            name = "Track",
            artistName = "Artist",
            albumName = "Album",
            artistIds = artistIds,
            albumId = albumId,
            source = source,
            audiomackAlbumUrl = audiomackAlbumUrl,
            audiomackArtistUrl = audiomackArtistUrl,
        ),
        mediaType = MediaType.TRACK,
    )

    private fun moviePost(
        directorIds: List<String> = emptyList(),
        directorName: String? = "Director",
    ) = CymbalPost(
        id = "m1",
        user = user(),
        track = CymbalTrack(
            id = "t1",
            name = "Film",
            artistName = "Director",
            albumName = "Film",
            // Even if a movie post somehow carried these, films never get the rows.
            artistIds = listOf("artist1"),
            albumId = "album1",
        ),
        mediaType = MediaType.MOVIE,
        directorName = directorName,
        directorIds = directorIds,
    )

    @Test
    fun `go to artist shown for track with artist id and flag on`() {
        assertTrue(showGoToArtistRow(post = trackPost(), artistPagesEnabled = true))
    }

    @Test
    fun `go to artist hidden when flag off`() {
        assertFalse(showGoToArtistRow(post = trackPost(), artistPagesEnabled = false))
    }

    // Spotify/Apple tracks show the row even with no artistIds yet — it resolves
    // the artist on tap (mirrors Go to Album). REGRESSION: an Apple-Music search
    // post lands with artistIds:[] (the backend resolves the Spotify ids minutes
    // later); "Go to Artist" used to stay hidden during that whole window.
    @Test
    fun `go to artist shown for spotify track even without an artist id`() {
        assertTrue(showGoToArtistRow(post = trackPost(artistIds = emptyList()), artistPagesEnabled = true))
        assertTrue(showGoToArtistRow(post = trackPost(artistIds = listOf("", " ")), artistPagesEnabled = true))
    }

    @Test
    fun `go to artist shown for apple-native track without an artist id`() {
        assertTrue(
            showGoToArtistRow(
                post = trackPost(artistIds = emptyList(), source = TrackSource.APPLEMUSIC),
                artistPagesEnabled = true,
            )
        )
    }

    @Test
    fun `go to artist hidden for soundcloud tidal and deezer tracks`() {
        assertFalse(showGoToArtistRow(post = trackPost(artistIds = emptyList(), source = TrackSource.SOUNDCLOUD), artistPagesEnabled = true))
        assertFalse(showGoToArtistRow(post = trackPost(artistIds = emptyList(), source = TrackSource.TIDAL), artistPagesEnabled = true))
        assertFalse(showGoToArtistRow(post = trackPost(artistIds = emptyList(), source = TrackSource.DEEZER), artistPagesEnabled = true))
    }

    @Test
    fun `go to artist shown for bandcamp track when flag on`() {
        assertTrue(
            showGoToArtistRow(
                post = trackPost(artistIds = emptyList(), source = TrackSource.BANDCAMP),
                artistPagesEnabled = true,
            )
        )
    }

    @Test
    fun `go to artist hidden for bandcamp track when flag off`() {
        assertFalse(
            showGoToArtistRow(
                post = trackPost(artistIds = emptyList(), source = TrackSource.BANDCAMP),
                artistPagesEnabled = false,
            )
        )
    }

    @Test
    fun `go to album hidden for bandcamp tracks`() {
        assertFalse(
            showGoToAlbumRow(
                post = trackPost(albumId = null, source = TrackSource.BANDCAMP),
                artistPagesEnabled = true,
            )
        )
    }

    @Test
    fun `go to artist shown for audiomack track with artist link-out even when flag off`() {
        assertTrue(
            showGoToArtistRow(
                post = trackPost(
                    artistIds = emptyList(),
                    source = TrackSource.AUDIOMACK,
                    audiomackArtistUrl = "https://audiomack.com/artist/x",
                ),
                artistPagesEnabled = false,
            )
        )
    }

    @Test
    fun `go to artist never shown for movie posts`() {
        assertFalse(showGoToArtistRow(post = moviePost(), artistPagesEnabled = true))
    }

    @Test
    fun `go to album shown for track with album id and flag on`() {
        assertTrue(showGoToAlbumRow(post = trackPost(), artistPagesEnabled = true))
    }

    @Test
    fun `go to album hidden when flag off`() {
        assertFalse(showGoToAlbumRow(post = trackPost(), artistPagesEnabled = false))
    }

    @Test
    fun `go to album shown for spotify track even without an album id`() {
        assertTrue(showGoToAlbumRow(post = trackPost(albumId = null), artistPagesEnabled = true))
        assertTrue(showGoToAlbumRow(post = trackPost(albumId = "   "), artistPagesEnabled = true))
    }

    @Test
    fun `go to album shown for apple-native track without an album id`() {
        assertTrue(
            showGoToAlbumRow(
                post = trackPost(albumId = null, source = TrackSource.APPLEMUSIC),
                artistPagesEnabled = true,
            )
        )
    }

    @Test
    fun `go to album hidden for soundcloud tidal and deezer tracks`() {
        assertFalse(showGoToAlbumRow(post = trackPost(albumId = null, source = TrackSource.SOUNDCLOUD), artistPagesEnabled = true))
        assertFalse(showGoToAlbumRow(post = trackPost(albumId = null, source = TrackSource.TIDAL), artistPagesEnabled = true))
        assertFalse(showGoToAlbumRow(post = trackPost(albumId = null, source = TrackSource.DEEZER), artistPagesEnabled = true))
    }

    @Test
    fun `go to album shown for audiomack track with album link-out`() {
        assertTrue(
            showGoToAlbumRow(
                post = trackPost(
                    albumId = null,
                    source = TrackSource.AUDIOMACK,
                    audiomackAlbumUrl = "https://audiomack.com/album/x",
                ),
                artistPagesEnabled = true,
            )
        )
    }

    @Test
    fun `go to album never shown for movie posts`() {
        assertFalse(showGoToAlbumRow(post = moviePost(), artistPagesEnabled = true))
    }

    @Test
    fun `go to album accepts apple-prefixed album id`() {
        assertTrue(showGoToAlbumRow(post = trackPost(albumId = "am:123456"), artistPagesEnabled = true))
    }

    // ── Tap builders no-op when the nav callback is null (flag off) ──

    @Test
    fun `artist tap builder is null when nav callback null`() {
        assertNull(artistTap(trackPost(), onNavigateToArtist = null))
    }

    private fun albumTap(
        post: CymbalPost,
        onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)?,
        scope: CoroutineScope = TestScope(),
        resolveDestinations: suspend (CymbalTrack) -> fm.corus.android.data.remote.CloudFunctionsDataSource.TrackDestinations = {
            fm.corus.android.data.remote.CloudFunctionsDataSource.TrackDestinations()
        },
        onAlbumNotFound: () -> Unit = {},
        onResolvingChange: (Boolean) -> Unit = {},
    ) = onGoToAlbumTap(
        testContext,
        post,
        onNavigateToAlbum,
        onNavigateToSong = {},
        prereleaseAlbumPagesEnabled = false,
        scope,
        resolveDestinations,
        onAlbumNotFound,
        onResolvingChange,
    )

    private fun artistTap(
        post: CymbalPost,
        onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)?,
        scope: CoroutineScope = TestScope(),
        resolveArtistId: suspend (CymbalTrack) -> String? = { null },
        onArtistNotFound: () -> Unit = {},
        onResolvingChange: (Boolean) -> Unit = {},
    ) = onGoToArtistTap(testContext, post, onNavigateToArtist, scope, resolveArtistId, onArtistNotFound, onResolvingChange)

    @Test
    fun `album tap builder is null when nav callback null`() {
        assertNull(albumTap(trackPost(), onNavigateToAlbum = null))
    }

    @Test
    fun `album tap builder is null for soundcloud tracks`() {
        assertNull(albumTap(trackPost(source = TrackSource.SOUNDCLOUD), onNavigateToAlbum = {}))
    }

    @Test
    fun `artist tap builder is null for movie posts`() {
        assertNull(artistTap(moviePost(), onNavigateToArtist = {}))
    }

    @Test
    fun `artist tap builder is null for soundcloud tracks`() {
        assertNull(artistTap(trackPost(source = TrackSource.SOUNDCLOUD), onNavigateToArtist = {}))
    }

    @Test
    fun `album tap builder is null for movie posts`() {
        assertNull(albumTap(moviePost(), onNavigateToAlbum = {}))
    }

    @Test
    fun `artist tap builder routes to first artist id`() {
        var routed: fm.corus.android.ui.navigation.ArtistPageRoute? = null
        val tap = artistTap(trackPost(artistIds = listOf("artistA", "artistB")), onNavigateToArtist = { routed = it })
        tap?.invoke()
        assertTrue(routed?.artistId == "artistA")
    }

    @Test
    fun `artist tap resolves the artist id on tap when the post carries none`() = runTest {
        var routed: fm.corus.android.ui.navigation.ArtistPageRoute? = null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val tap = artistTap(
            trackPost(artistIds = emptyList(), source = TrackSource.APPLEMUSIC),
            onNavigateToArtist = { routed = it },
            scope = scope,
            resolveArtistId = { "resolvedArtist" },
        )
        tap?.invoke()
        assertTrue(routed?.artistId == "resolvedArtist")
    }

    @Test
    fun `artist tap reports a miss when the resolve finds nothing`() = runTest {
        var routed: fm.corus.android.ui.navigation.ArtistPageRoute? = null
        var missed = false
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val tap = artistTap(
            trackPost(artistIds = emptyList(), source = TrackSource.APPLEMUSIC),
            onNavigateToArtist = { routed = it },
            scope = scope,
            resolveArtistId = { null },
            onArtistNotFound = { missed = true },
        )
        tap?.invoke()
        assertNull(routed)
        assertTrue(missed)
    }

    @Test
    fun `artist tap toggles the resolving flag true then false around the resolve`() = runTest {
        val states = mutableListOf<Boolean>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val tap = artistTap(
            trackPost(artistIds = emptyList(), source = TrackSource.APPLEMUSIC),
            onNavigateToArtist = {},
            scope = scope,
            resolveArtistId = { "resolvedArtist" },
            onResolvingChange = { states.add(it) },
        )
        tap?.invoke()
        assertTrue(states == listOf(true, false))
    }

    @Test
    fun `fast-path artist tap never toggles the resolving flag`() {
        val states = mutableListOf<Boolean>()
        val tap = artistTap(
            trackPost(artistIds = listOf("artistA")),
            onNavigateToArtist = {},
            onResolvingChange = { states.add(it) },
        )
        tap?.invoke()
        assertTrue(states.isEmpty())
    }

    @Test
    fun `album tap builder passes album id through untouched`() {
        var routed: fm.corus.android.ui.navigation.AlbumPageRoute? = null
        val tap = albumTap(trackPost(albumId = "am:987"), onNavigateToAlbum = { routed = it })
        tap?.invoke()
        assertTrue(routed?.albumId == "am:987")
    }

    @Test
    fun `album tap resolves the album id on tap when the post carries none`() = runTest {
        var routed: fm.corus.android.ui.navigation.AlbumPageRoute? = null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val tap = albumTap(
            trackPost(albumId = null),
            onNavigateToAlbum = { routed = it },
            scope = scope,
            resolveDestinations = { CloudFunctionsDataSource.TrackDestinations(albumId = "resolvedAlbum") },
        )
        tap?.invoke()
        assertTrue(routed?.albumId == "resolvedAlbum")
    }

    @Test
    fun `album tap reports a miss when the resolve finds nothing`() = runTest {
        var routed: fm.corus.android.ui.navigation.AlbumPageRoute? = null
        var missed = false
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val tap = albumTap(
            trackPost(albumId = null),
            onNavigateToAlbum = { routed = it },
            scope = scope,
            resolveDestinations = { CloudFunctionsDataSource.TrackDestinations(albumId = null) },
            onAlbumNotFound = { missed = true },
        )
        tap?.invoke()
        assertNull(routed)
        assertTrue(missed)
    }

    @Test
    fun `album tap toggles the resolving flag true then false around the resolve`() = runTest {
        val states = mutableListOf<Boolean>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val tap = albumTap(
            trackPost(albumId = null),
            onNavigateToAlbum = {},
            scope = scope,
            resolveDestinations = { CloudFunctionsDataSource.TrackDestinations(albumId = "resolvedAlbum") },
            onResolvingChange = { states.add(it) },
        )
        tap?.invoke()
        assertTrue(states == listOf(true, false))
    }

    @Test
    fun `fast-path album tap never toggles the resolving flag`() {
        val states = mutableListOf<Boolean>()
        val tap = albumTap(
            trackPost(albumId = "am:987"),
            onNavigateToAlbum = {},
            onResolvingChange = { states.add(it) },
        )
        tap?.invoke()
        assertTrue(states.isEmpty())
    }

    // ── albumId deserialization ──

    @Test
    fun `fromCloudData parses albumId`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "p1",
                "mediaType" to "track",
                "trackId" to "t1",
                "albumId" to "album1",
            )
        )
        assertTrue(post.track.albumId == "album1")
    }

    @Test
    fun `fromCloudData missing albumId is null`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "p1",
                "mediaType" to "track",
                "trackId" to "t1",
            )
        )
        assertNull(post.track.albumId)
    }

    @Test
    fun `fromCloudData empty albumId is null`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "p1",
                "mediaType" to "track",
                "trackId" to "t1",
                "albumId" to "",
            )
        )
        assertNull(post.track.albumId)
    }

    // ── Go to Director gating (movies) ──

    @Test
    fun `go to director shown for movie with director id and flag on`() {
        assertTrue(showGoToDirectorRow(post = moviePost(directorIds = listOf("d1")), artistPagesEnabled = true))
    }

    @Test
    fun `go to director hidden when flag off`() {
        assertFalse(showGoToDirectorRow(post = moviePost(directorIds = listOf("d1")), artistPagesEnabled = false))
    }

    @Test
    fun `go to director hidden when no director id`() {
        assertFalse(showGoToDirectorRow(post = moviePost(directorIds = emptyList()), artistPagesEnabled = true))
    }

    @Test
    fun `go to director hidden for blank director ids`() {
        assertFalse(showGoToDirectorRow(post = moviePost(directorIds = listOf("", " ")), artistPagesEnabled = true))
    }

    @Test
    fun `go to director hidden for track post`() {
        assertFalse(showGoToDirectorRow(post = trackPost(), artistPagesEnabled = true))
    }

    @Test
    fun `director tap null when nav callback null`() {
        assertNull(onGoToDirectorTap(moviePost(directorIds = listOf("d1")), onNavigateToDirector = null))
    }

    @Test
    fun `director tap null for track post`() {
        assertNull(onGoToDirectorTap(trackPost(), onNavigateToDirector = {}))
    }

    @Test
    fun `director tap null when no director id`() {
        assertNull(onGoToDirectorTap(moviePost(directorIds = emptyList()), onNavigateToDirector = {}))
    }

    @Test
    fun `director tap builder passes director id and name through`() {
        var routed: fm.corus.android.ui.navigation.DirectorPageRoute? = null
        val tap = onGoToDirectorTap(
            moviePost(directorIds = listOf("d99"), directorName = "Greta Gerwig"),
            onNavigateToDirector = { routed = it },
        )
        tap?.invoke()
        assertTrue(routed?.directorId == "d99")
        assertTrue(routed?.name == "Greta Gerwig")
    }

    // ── Post-card tappable artist subtitle (postSubtitleTap) ──
    // Track posts delegate to onGoToArtistTap (resolve-on-tap, same as the menu
    // row); movie posts route to the director page. The resolve logic itself is
    // covered by the onGoToArtistTap tests above — these lock the delegation.

    private fun subtitleTap(
        post: CymbalPost,
        onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)? = {},
        onNavigateToDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)? = {},
        scope: CoroutineScope = TestScope(),
        resolveArtistId: suspend (CymbalTrack) -> String? = { null },
        onArtistNotFound: () -> Unit = {},
        onResolvingChange: (Boolean) -> Unit = {},
    ) = postSubtitleTap(testContext, post, onNavigateToArtist, onNavigateToDirector, scope, resolveArtistId, onArtistNotFound, onResolvingChange)

    @Test
    fun `subtitle tap is tappable for apple track without an artist id`() {
        // REGRESSION: the name used to be plain text (null tap) until the backend
        // backfilled artistIds; now it's tappable and resolves the id on tap.
        assertNotNull(subtitleTap(trackPost(artistIds = emptyList(), source = TrackSource.APPLEMUSIC)))
    }

    @Test
    fun `subtitle tap stays plain text for soundcloud track`() {
        assertNull(subtitleTap(trackPost(source = TrackSource.SOUNDCLOUD)))
    }

    @Test
    fun `subtitle tap stays plain text when artist nav callback null`() {
        assertNull(subtitleTap(trackPost(), onNavigateToArtist = null))
    }

    @Test
    fun `subtitle tap resolves the artist id on tap when the post carries none`() = runTest {
        var routed: fm.corus.android.ui.navigation.ArtistPageRoute? = null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val tap = subtitleTap(
            trackPost(artistIds = emptyList(), source = TrackSource.APPLEMUSIC),
            onNavigateToArtist = { routed = it },
            scope = scope,
            resolveArtistId = { "resolvedArtist" },
        )
        tap?.invoke()
        assertTrue(routed?.artistId == "resolvedArtist")
    }

    @Test
    fun `subtitle tap routes movie posts to the director page`() {
        var routed: fm.corus.android.ui.navigation.DirectorPageRoute? = null
        val tap = subtitleTap(
            moviePost(directorIds = listOf("d7"), directorName = "Greta Gerwig"),
            onNavigateToDirector = { routed = it },
        )
        tap?.invoke()
        assertTrue(routed?.directorId == "d7")
    }
}
