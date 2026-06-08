package fm.corus.android.domain

import android.content.Context
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * Regression tests for [NowPlayingManager.lookupPreviewUrl].
 *
 * Context: we deleted the on-device iTunes Search API lookup tiers. The
 * `appleMusicLookup` Cloud Function is now the sole preview resolver
 * (backend matching is covered by `appleMusicMatch.test.mjs` — 59 cases).
 * These tests guard the delegation shell: cache behavior, error handling,
 * and that we pass through the right params.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingManagerLookupTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mock<Context>()
    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val userRepository = mock<UserRepository> {
        on { unfollowEvents } doReturn MutableSharedFlow()
    }

    private fun newManager(cloudFunctions: CloudFunctionsDataSource): NowPlayingManager =
        NowPlayingManager(context, cloudFunctions, preferencesDataStore, userRepository, mock(), mock(), mock())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `returns url when CF resolves the track`() = runTest(testDispatcher) {
        val cloudFunctions = mock<CloudFunctionsDataSource> {
            onBlocking {
                appleMusicLookup(
                    name = eq("Tomorrow Never Knows - Remastered 2009"),
                    artist = eq("The Beatles"),
                    isrc = eq("GBAYE0601506"),
                    spotifyTrackId = eq("00oZhqZIQfL9P5CjOP6JsO"),
                )
            } doReturn "https://audio-ssl.itunes.apple.com/preview.m4a"
        }
        val manager = newManager(cloudFunctions)

        val result = manager.lookupPreviewUrl(
            trackId = "00oZhqZIQfL9P5CjOP6JsO",
            name = "Tomorrow Never Knows - Remastered 2009",
            artist = "The Beatles",
            isrc = "GBAYE0601506",
        )

        assertEquals("https://audio-ssl.itunes.apple.com/preview.m4a", result)
        assertFalse(
            "successful resolve must not be added to noMatchCache",
            manager.noMatchCache.contains("00oZhqZIQfL9P5CjOP6JsO"),
        )
    }

    @Test
    fun `returns null and caches when CF has no match`() = runTest(testDispatcher) {
        val cloudFunctions = mock<CloudFunctionsDataSource> {
            onBlocking { appleMusicLookup(any(), any(), anyOrNull(), anyOrNull()) } doReturn null
        }
        val manager = newManager(cloudFunctions)

        val trackId = "track-without-apple-match"
        val result = manager.lookupPreviewUrl(trackId, "Obscure", "Unknown Artist", isrc = null)

        assertNull(result)
        assertTrue(
            "no-match must be cached so we don't hammer the CF on every tap",
            manager.noMatchCache.contains(trackId),
        )
    }

    @Test
    fun `subsequent call for a known no-match track is instant and does not hit CF`() = runTest(testDispatcher) {
        val cloudFunctions = mock<CloudFunctionsDataSource> {
            onBlocking { appleMusicLookup(any(), any(), anyOrNull(), anyOrNull()) } doReturn null
        }
        val manager = newManager(cloudFunctions)
        val trackId = "t1"

        // First call populates noMatchCache.
        manager.lookupPreviewUrl(trackId, "n", "a", null)
        // Second call should short-circuit.
        val result = manager.lookupPreviewUrl(trackId, "n", "a", null)

        assertNull(result)
        // Exactly one CF call — the second lookup was served from noMatchCache.
        verifyBlocking(cloudFunctions) { appleMusicLookup(any(), any(), anyOrNull(), anyOrNull()) }
        verifyNoMoreInteractions(cloudFunctions)
    }

    @Test
    fun `transient CF error returns null but does NOT poison noMatchCache`() = runTest(testDispatcher) {
        val cloudFunctions = mock<CloudFunctionsDataSource> {
            onBlocking { appleMusicLookup(any(), any(), anyOrNull(), anyOrNull()) } doThrow RuntimeException("simulated network blip")
        }
        val manager = newManager(cloudFunctions)
        val trackId = "t-network-blip"

        val result = manager.lookupPreviewUrl(trackId, "n", "a", null)

        assertNull(result)
        assertFalse(
            "network errors are retryable — must not land in noMatchCache",
            manager.noMatchCache.contains(trackId),
        )
    }

    @Test
    fun `isrc null is forwarded as null, not as empty string`() = runTest(testDispatcher) {
        val cloudFunctions = mock<CloudFunctionsDataSource> {
            onBlocking { appleMusicLookup(any(), any(), anyOrNull(), anyOrNull()) } doReturn "https://p"
        }
        val manager = newManager(cloudFunctions)

        manager.lookupPreviewUrl("t", "n", "a", isrc = null)

        // Exact null passthrough — the CF handles missing ISRC by fetching from Spotify.
        verifyBlocking(cloudFunctions) {
            appleMusicLookup(
                name = eq("n"),
                artist = eq("a"),
                isrc = eq<String?>(null),
                spotifyTrackId = eq("t"),
            )
        }
    }
}
