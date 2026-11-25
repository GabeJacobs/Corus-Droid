package fm.corus.android.domain

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.PlayerApi
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.ErrorCallback
import com.spotify.protocol.types.Empty
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import javax.inject.Provider

/**
 * Order of connect attempts behind a play tap.
 *
 * Context: App Remote's authorization lives inside the Spotify app.
 * [ConnectionParams] carries only a client id and redirect URI, and the token
 * [SpotifyAuthService] caches is never handed to the SDK — it is a marker that
 * we authorized once, and it self-destructs after an hour with no refresh.
 * Gating the silent connect attempt on that marker meant every play tap past
 * the hour opened Spotify's auth-lib consent flow, which on devices that fall
 * back to its WebView handler renders a full-screen "Loading…" ProgressDialog
 * over Corus. Reported 2026-08-04 by an Android tester who saw it on most songs.
 *
 * The invariant: after this install has used App Remote once, a silent
 * connect is always attempted first, and interactive auth is the fallback
 * for when App Remote actually refuses. A first-time Link (no token, no
 * last-usage stamp) skips silent so Spotify's Agree page is not delayed
 * behind a mini-player spinner.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
// Vanilla Application so Robolectric doesn't boot CorusApplication, which
// initializes RevenueCat/Firebase in onCreate.
@Config(sdk = [34], application = Application::class)
class SpotifyPlaybackConnectOrderTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var authService: SpotifyAuthService
    private lateinit var analyticsService: fm.corus.android.service.AnalyticsService
    private lateinit var saveAutoAdd: Provider<SpotifySaveAutoAdd>
    private lateinit var appRemoteStatic: MockedStatic<SpotifyAppRemote>

    /** Every [SpotifyAppRemote.connect] the service attempted, in order. */
    private val connectAttempts = mutableListOf<ConnectionParams>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        SpotifyConnectContext.setActivity(null)

        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        installSpotifyApp()

        authService = mock<SpotifyAuthService>()
        analyticsService = mock()
        saveAutoAdd = mock<Provider<SpotifySaveAutoAdd>>()

        // Stand in for the Spotify app: record the attempt, then refuse, so the
        // service takes whatever fallback it has rather than hanging on IPC.
        appRemoteStatic = Mockito.mockStatic(SpotifyAppRemote::class.java)
        appRemoteStatic.`when`<Unit> {
            SpotifyAppRemote.connect(
                Mockito.any(Context::class.java),
                Mockito.any(ConnectionParams::class.java),
                Mockito.any(Connector.ConnectionListener::class.java),
            )
        }.thenAnswer { invocation ->
            connectAttempts += invocation.arguments[1] as ConnectionParams
            (invocation.arguments[2] as Connector.ConnectionListener)
                .onFailure(RuntimeException("Spotify app unavailable in test"))
            null
        }
    }

    /** [SpotifyPlaybackService.isSpotifyAppInstalled] resolves a launcher intent. */
    private fun installSpotifyApp() {
        val launcher = ComponentName("com.spotify.music", "com.spotify.music.MainActivity")
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)
        shadowPackageManager.addActivityIfNotPresent(launcher)
        shadowPackageManager.addIntentFilterForActivity(
            launcher,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    @After
    fun tearDown() {
        appRemoteStatic.close()
        SpotifyConnectContext.setActivity(null)
        Dispatchers.resetMain()
    }

    private fun service() = SpotifyPlaybackService(context, authService, analyticsService, saveAutoAdd)

    @Test
    fun `unresponsive connected player disconnects before trying to wake Spotify`() = runTest {
        val service = service()
        val remote = attachUnresponsivePlayer(service)

        val failure = runCatching { service.play("4cOdK2wGLETKBW3PvgPWqT", null, replaceQueue = false) }.exceptionOrNull()

        appRemoteStatic.verify { SpotifyAppRemote.disconnect(remote) }
        // This test has no foreground Activity to present Spotify authorization.
        assertEquals(SpotifyPlaybackError.NotConnected::class.java, failure?.javaClass)
    }

    @Test
    fun `locked playback attempts silent reconnect after the current connection drops`() = runTest {
        val service = service()
        val remote = attachUnresponsivePlayer(service)

        runCatching {
            service.play("4cOdK2wGLETKBW3PvgPWqT", null, replaceQueue = false, allowInteractiveRecovery = { false })
        }

        appRemoteStatic.verify { SpotifyAppRemote.disconnect(remote) }
        assertTrue("Background recovery must reconnect without opening Spotify authorization", connectAttempts.isNotEmpty())
    }

    private fun attachUnresponsivePlayer(service: SpotifyPlaybackService): SpotifyAppRemote {
        val remote = mock<SpotifyAppRemote>()
        val player = mock<PlayerApi>()
        val play = mock<CallResult<com.spotify.protocol.types.Empty>>()
        whenever(remote.isConnected).thenReturn(true)
        whenever(remote.playerApi).thenReturn(player)
        whenever(player.play(any())).thenReturn(play)
        whenever(play.setErrorCallback(any())).thenAnswer {
            it.getArgument<ErrorCallback>(0).onError(IllegalStateException("Connection lost"))
            play
        }
        SpotifyPlaybackService::class.java.getDeclaredField("appRemote").apply {
            isAccessible = true
            set(service, remote)
        }

        return remote
    }

    @Test
    fun `Live listener permits an early fresh Spotify playlist pick while backgrounded`() = runTest {
        val service = service()
        val remote = mock<SpotifyAppRemote>()
        val player = mock<PlayerApi>()
        whenever(remote.playerApi).thenReturn(player)
        whenever(remote.isConnected).thenReturn(true)
        whenever(player.pause()).thenReturn(mock<CallResult<Empty>>())
        listOf("appRemote", "fastPathAppRemote").forEach { name ->
            SpotifyPlaybackService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, remote)
        }
        var previewPosition = 0L
        whenever(player.getPlayerState()).thenAnswer {
            previewPosition += 150
            val result = mock<CallResult<PlayerState>>()
            whenever(result.setResultCallback(any())).thenAnswer { call ->
                call.getArgument<CallResult.ResultCallback<PlayerState>>(0).onResult(
                    PlayerState(playingState("spotify:track:picked").track, false, 1f, previewPosition, null, null))
                result
            }
            result
        }
        val room = "spotify:track:room"
        service.followLiveListenerTrack(room, 1)
        service.setLiveListenerPreviewEnvironment(backgrounded = true, allowed = true)
        fun context(uri: String) {
            val ctx = com.spotify.protocol.types.PlayerContext(uri, "Context", "", "playlist")
            SpotifyPlaybackService::class.java.getDeclaredMethod(
                "applyPlayerContext", com.spotify.protocol.types.PlayerContext::class.java,
            ).apply { isAccessible = true }.invoke(service, ctx)
        }
        fun expected() = PlayerState(playingState(room).track, false, 1f, 21_000, null, null)
        context(room)
        service.pauseUnexpectedLiveTrack(remote, expected())
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(9))
        service.pauseUnexpectedLiveTrack(remote, expected())
        context("spotify:playlist:chosen")
        service.pauseUnexpectedLiveTrack(remote, playingState("spotify:track:picked"))
        Mockito.verify(player, Mockito.never()).pause()
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertTrue(service.isLiveListenerPreviewSuspended)
        assertFalse(service.isConnected)
        appRemoteStatic.verify { SpotifyAppRemote.disconnect(remote) }
    }

    @Test
    fun `Live listener resumes a guarded pick only after its delayed context confirms it`() = runTest {
        val service = service()
        val remote = mock<SpotifyAppRemote>()
        val player = mock<PlayerApi>()
        whenever(remote.playerApi).thenReturn(player)
        whenever(remote.isConnected).thenReturn(true)
        whenever(player.pause()).thenReturn(mock<CallResult<Empty>>())
        var observed = playingState("spotify:track:picked")
        whenever(player.getPlayerState()).thenAnswer {
            val result = mock<CallResult<PlayerState>>()
            whenever(result.setResultCallback(any())).thenAnswer { call ->
                call.getArgument<CallResult.ResultCallback<PlayerState>>(0).onResult(observed)
                result
            }
            result
        }
        val resumed = mock<CallResult<Empty>>()
        whenever(player.resume()).thenReturn(resumed)
        whenever(resumed.setResultCallback(any())).thenAnswer {
            observed = PlayerState(observed.track, false, 1f, 200L, null, null)
            it.getArgument<com.spotify.protocol.client.CallResult.ResultCallback<Empty>>(0).onResult(Empty())
            resumed
        }
        listOf("appRemote", "fastPathAppRemote").forEach { name ->
            SpotifyPlaybackService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, remote)
        }
        val room = "spotify:track:room"
        fun context(uri: String) {
            SpotifyPlaybackService::class.java.getDeclaredMethod(
                "applyPlayerContext", com.spotify.protocol.types.PlayerContext::class.java,
            ).apply { isAccessible = true }.invoke(service,
                com.spotify.protocol.types.PlayerContext(uri, "Context", "", "playlist"))
        }
        service.followLiveListenerTrack(room, 1)
        service.setLiveListenerPreviewEnvironment(backgrounded = true, allowed = true)
        context(room)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(9))
        service.pauseUnexpectedLiveTrack(remote, PlayerState(playingState(room).track, false, 1f, 21_000, null, null))
        service.pauseUnexpectedLiveTrack(remote, playingState("spotify:track:picked"))
        Mockito.verify(player).pause()
        Mockito.verify(player, Mockito.never()).resume()
        context("spotify:playlist:chosen")
        runCurrent()
        // The command can be accepted before Spotify actually applies pause.
        // Resuming here races that late pause and leaves the selection silent.
        Mockito.verify(player, Mockito.never()).resume()
        assertTrue(service.isConnected)
        observed = PlayerState(observed.track, true, 1f, 0L, null, null)
        advanceTimeBy(100)
        runCurrent()
        Mockito.verify(player).resume()
        observed = PlayerState(observed.track, false, 1f, 400L, null, null)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(service.isLiveListenerPreviewSuspended)
        assertFalse(service.isConnected)
        appRemoteStatic.verify { SpotifyAppRemote.disconnect(remote) }
    }

    @Test
    fun `Live pauses Spotify queue playback and permits the room track`() {
        val service = service()
        val remote = mock<SpotifyAppRemote>()
        val player = mock<PlayerApi>()
        whenever(remote.playerApi).thenReturn(player)
        whenever(player.pause()).thenReturn(mock<CallResult<Empty>>())
        val roomUri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
        val queueUri = "spotify:track:2takcwOaAZWiXQijPHIx7B"

        service.followLiveListenerTrack(roomUri, 1)
        assertFalse(service.pauseUnexpectedLiveTrack(remote, playingState(roomUri)))
        assertTrue(service.pauseUnexpectedLiveTrack(remote, playingState(queueUri)))

        Mockito.verify(player).pause()
        Mockito.verifyNoMoreInteractions(player)
    }

    @Test
    fun `Live guard remains active during next song startup and ignores rejected pause acknowledgements`() {
        val service = service()
        val remote = mock<SpotifyAppRemote>()
        val player = mock<PlayerApi>()
        whenever(remote.playerApi).thenReturn(player)
        whenever(player.pause()).thenReturn(mock<CallResult<Empty>>())
        val nextUri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
        val stale = playingState("spotify:track:2takcwOaAZWiXQijPHIx7B")
        service.followLiveListenerTrack(nextUri, 2)
        SpotifyPlaybackService::class.java.getDeclaredField("playInFlight").apply {
            isAccessible = true
            setBoolean(service, true)
        }

        assertTrue(service.pauseUnexpectedLiveTrack(remote, stale))
        assertTrue(service.pauseUnexpectedLiveTrack(remote,
            PlayerState(stale.track, true, 1f, 0, null, null)))
        assertFalse(service.pauseUnexpectedLiveTrack(remote, playingState(nextUri)))
        Mockito.verify(player).pause()

        service.liveListenerTrackUri = null
        assertFalse(service.pauseUnexpectedLiveTrack(remote, stale))
        Mockito.verifyNoMoreInteractions(player)
    }

    private fun playingState(uri: String): PlayerState = PlayerState(
        Track(null, emptyList(), null, 180_000, "Track", uri, null, false, false),
        false, 1f, 0, null, null,
    )

    @Test
    fun `Live pauses before Spotify can advance its own queue`() = runTest(testDispatcher) {
        val service = service()
        val player = attachLivePlayer(service)
        val uri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
        service.liveListenerTrackUri = uri
        applyPlayback(service, playingState(uri))
        runCurrent()

        advanceTimeBy(179_699)
        runCurrent()
        Mockito.verify(player, Mockito.never()).pause()
        advanceTimeBy(1)
        runCurrent()

        Mockito.verify(player).pause()
        assertTrue(service.liveListenerEndHeld)
    }

    @Test
    fun `leaving Live cancels the pending end pause`() = runTest(testDispatcher) {
        val service = service()
        val player = attachLivePlayer(service)
        val uri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
        service.liveListenerTrackUri = uri
        applyPlayback(service, playingState(uri))
        runCurrent()
        advanceTimeBy(1_000)
        service.liveListenerTrackUri = null
        advanceTimeBy(180_000)
        runCurrent()
        Mockito.verify(player, Mockito.never()).pause()
        assertFalse(service.liveListenerEndHeld)
    }

    @Test
    fun `a paused or unrelated track never arms the Live end timer`() = runTest(testDispatcher) {
        val service = service()
        val player = attachLivePlayer(service)
        val uri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
        service.liveListenerTrackUri = uri
        applyPlayback(service, PlayerState(playingState(uri).track, true, 1f, 179_900, null, null))
        runCurrent()
        advanceTimeBy(180_000)
        applyPlayback(service, playingState("spotify:track:2takcwOaAZWiXQijPHIx7B"))
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        Mockito.verify(player, Mockito.never()).pause()
    }

    @Test
    fun `updated Spotify position reschedules the pause and repeated room songs release the hold`() = runTest(testDispatcher) {
        val service = service()
        val player = attachLivePlayer(service)
        val uri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
        service.followLiveListenerTrack(uri, 1)
        applyPlayback(service, playingState(uri))
        runCurrent()
        advanceTimeBy(1_000)
        applyPlayback(service, PlayerState(playingState(uri).track, false, 1f, 179_500, null, null))
        runCurrent()
        advanceTimeBy(200)
        runCurrent()
        Mockito.verify(player).pause()
        assertTrue(service.liveListenerEndHeld)
        service.followLiveListenerTrack(uri, 1)
        assertTrue(service.liveListenerEndHeld)
        service.followLiveListenerTrack(uri, 2)
        assertFalse(service.liveListenerEndHeld)
        applyPlayback(service, playingState(uri))
        runCurrent()
        advanceTimeBy(179_700)
        runCurrent()
        Mockito.verify(player, Mockito.times(2)).pause()
    }

    private fun attachLivePlayer(service: SpotifyPlaybackService): PlayerApi {
        val remote = mock<SpotifyAppRemote>()
        val player = mock<PlayerApi>()
        whenever(remote.isConnected).thenReturn(true)
        whenever(remote.playerApi).thenReturn(player)
        whenever(player.pause()).thenReturn(mock<CallResult<Empty>>())
        SpotifyPlaybackService::class.java.getDeclaredField("appRemote").apply {
            isAccessible = true
            set(service, remote)
        }
        return player
    }

    private fun applyPlayback(service: SpotifyPlaybackService, state: PlayerState) {
        SpotifyPlaybackService::class.java.getDeclaredMethod("applyPlayerState", PlayerState::class.java).apply {
            isAccessible = true
            invoke(service, state)
        }
    }

    private suspend fun playOnce() {
        runCatching {
            service().play(
                spotifyTrackId = "4cOdK2wGLETKBW3PvgPWqT",
                uri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
                replaceQueue = true,
                queueSessionId = 1,
            )
        }
    }

    /**
     * The regression. An hour after linking, [SpotifyAuthService.cachedAccessToken]
     * returns null even though the Spotify app still holds the grant. Corus used
     * to read that as "not authorized" and open the consent flow without ever
     * asking App Remote, which is what put the "Loading…" box on screen.
     */
    private fun stampPriorAppRemoteUsage() {
        context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("fm.corus.spotify.lastAppRemoteUsage", System.currentTimeMillis())
            .commit()
    }

    @Test
    fun `expired cached token still attempts a silent connect`() = runTest {
        whenever(authService.cachedAccessToken()).thenReturn(null)
        stampPriorAppRemoteUsage()

        playOnce()

        assertTrue(
            "A play tap with an expired token must ask App Remote before opening Spotify's consent flow",
            connectAttempts.isNotEmpty(),
        )
    }

    /**
     * The silent attempt must not carry Spotify's own auth view either: Android
     * 14+ blocks it from launching while the Spotify app is backgrounded, which
     * is why interactive auth runs through auth-lib from Corus instead.
     */
    @Test
    fun `silent attempt does not ask the SDK to show its auth view`() = runTest {
        whenever(authService.cachedAccessToken()).thenReturn(null)
        stampPriorAppRemoteUsage()

        playOnce()

        assertTrue(connectAttempts.isNotEmpty())
        assertTrue(
            "First connect attempt must be the silent one",
            !connectAttempts.first().shouldShowAuthView(),
        )
    }

    /**
     * First Link Spotify tap: no grant exists yet. Silent App Remote retries
     * cannot succeed and leave the mini-player spinning like a stuck buffer.
     */
    @Test
    fun `first link skips silent connect and opens interactive auth`() = runTest {
        whenever(authService.cachedAccessToken()).thenReturn(null)

        playOnce()

        assertTrue(
            "A never-linked install must not wait on silent App Remote before Spotify's Agree page",
            connectAttempts.isEmpty(),
        )
    }

    @Test
    fun `interactive fallback waits for the caller to permit the Spotify handoff`() = runTest {
        val roomVisible = CompletableDeferred<Boolean>()
        val playback = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                service().play(
                    spotifyTrackId = "4cOdK2wGLETKBW3PvgPWqT",
                    uri = null,
                    replaceQueue = false,
                    allowInteractiveRecovery = { roomVisible.await() },
                )
            }
        }

        assertTrue(playback.isActive)

        roomVisible.complete(false)
        assertEquals(SpotifyPlaybackError.NotConnected::class.java, playback.await().exceptionOrNull()?.javaClass)
    }

    /** A live token still starts with a silent connect (may retry IPC after idle). */
    @Test
    fun `live cached token attempts a silent connect`() = runTest {
        whenever(authService.cachedAccessToken()).thenReturn("live-token")

        playOnce()

        assertTrue(connectAttempts.isNotEmpty())
        assertTrue(
            "First connect attempt must be the silent one",
            !connectAttempts.first().shouldShowAuthView(),
        )
        assertTrue(
            "Dropped IPC after idle should retry silent App Remote before opening consent",
            connectAttempts.count { !it.shouldShowAuthView() } >= 3,
        )
    }
}
