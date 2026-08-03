package fm.corus.android.domain

import android.content.Context
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.TidalPlaylistService
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * [NowPlayingManager.hashtagPlaylistNaming] titles the TIDAL (client-side)
 * hashtag playlist. The backend titles the Spotify one "Corus · #tag"; the two
 * paths must stay in lockstep so the same tag exports under the same name
 * regardless of the user's service.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingManagerHashtagNamingTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mock<Context>()
    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val userRepository = mock<UserRepository> {
        on { unfollowEvents } doReturn MutableSharedFlow()
    }

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `matches the backend's spotify playlist naming`() {
        val manager = NowPlayingManager(
            context, mock(), preferencesDataStore, userRepository,
            mock(), mock(), mock<TidalPlaylistService>(),
            mock(), mock(), mock(),
            mock(),
        )

        val (title, description) = manager.hashtagPlaylistNaming("indierock")

        assertEquals("Corus · #indierock", title)
        assertEquals("Songs tagged #indierock on Corus", description)
    }
}
