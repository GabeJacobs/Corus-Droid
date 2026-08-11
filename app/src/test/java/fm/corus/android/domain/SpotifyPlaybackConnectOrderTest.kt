package fm.corus.android.domain

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock
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
 * The invariant: a silent connect is always attempted first, and interactive
 * auth is the fallback for when App Remote actually refuses.
 */
@RunWith(RobolectricTestRunner::class)
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
    @Test
    fun `expired cached token still attempts a silent connect`() = runTest {
        whenever(authService.cachedAccessToken()).thenReturn(null)

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

        playOnce()

        assertTrue(connectAttempts.isNotEmpty())
        assertTrue(
            "First connect attempt must be the silent one",
            !connectAttempts.first().shouldShowAuthView(),
        )
    }

    /** A live token behaves exactly as before: one silent attempt, no consent trip. */
    @Test
    fun `live cached token attempts a silent connect`() = runTest {
        whenever(authService.cachedAccessToken()).thenReturn("live-token")

        playOnce()

        assertEquals(1, connectAttempts.size)
    }
}
