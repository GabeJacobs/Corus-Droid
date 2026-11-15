package fm.corus.android.domain

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.MusicService
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class AppleMusicFirstPlayChoiceTest {
    private val preferences = mock<PreferencesDataStore>()
    private val user = mock<FirebaseUser> { on { uid } doReturn "corus-listener" }
    private val auth = mock<FirebaseAuth> { on { currentUser } doReturn user }
    private val manager = PlaybackModePromptManager(preferences, mock<AnalyticsService>(), auth)

    private fun presentAppleMusicChoice(scope: CoroutineScope, play: suspend () -> Unit) {
        manager.present(
            PendingPlaybackModeChoice(
                kind = SpotifyFtuePromptKind.CHOOSE_LISTEN,
                surface = SpotifyFtuePromptSurface.FIRST_PLAY,
                onPreview = play,
                nowPlaying = mock(),
                remoteConfig = mock(),
                musicService = MusicService.APPLE_MUSIC,
                scope = scope,
            ),
        )
    }

    @Test
    fun previewsPersistsAppleChoiceAndContinuesTheOriginalTap() = runTest {
        var plays = 0
        presentAppleMusicChoice(this) { plays++ }

        manager.choosePreviews()
        advanceUntilIdle()

        assertNull(manager.pending.value)
        assertEquals(1, plays)
        verify(preferences).markAppleMusicFirstPlayChoiceMade("corus-listener")
        verifyBlocking(preferences) { setAlwaysPlayFullSongs(false) }
        verifyBlocking(preferences, never()) { setSpotifyFtueFirstPlayChooserConsumed() }
    }

    @Test
    fun connectEnablesFullPlaybackBeforeContinuingTheOriginalTap() = runTest {
        var plays = 0
        presentAppleMusicChoice(this) {
            verifyBlocking(preferences) { setAlwaysPlayFullSongs(true) }
            plays++
        }

        manager.chooseConnect()
        advanceUntilIdle()

        assertNull(manager.pending.value)
        assertEquals(1, plays)
        verify(preferences).markAppleMusicFirstPlayChoiceMade("corus-listener")
        verifyBlocking(preferences, never()) { setSpotifyFtueLinkPromptConsumed() }
    }
}
