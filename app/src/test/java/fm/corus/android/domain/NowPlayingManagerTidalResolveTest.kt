package fm.corus.android.domain

import android.content.Context
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.CloudFunctionsDataSource.PlaylistTrackDescriptor
import fm.corus.android.data.remote.TidalPlaylistService
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * [NowPlayingManager.resolveTidalIds] turns backend track descriptors into TIDAL
 * ids for client-side playlist building. It must preserve feed order, drop
 * unresolved tracks, and de-dupe ids (e.g. the same recording posted twice).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingManagerTidalResolveTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mock<Context>()
    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val userRepository = mock<UserRepository> {
        on { unfollowEvents } doReturn MutableSharedFlow()
    }

    private fun descriptor(name: String) =
        PlaylistTrackDescriptor(trackId = name, isrc = null, name = name, artist = "x", album = "")

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `preserves order, drops unresolved, de-dupes`() = runTest(testDispatcher) {
        // A→tA, B→null (unresolved), C→tC, D→tA (dup of A).
        val mapping = mapOf("A" to "tA", "C" to "tC", "D" to "tA")
        val cloudFunctions = mock<CloudFunctionsDataSource> {
            onBlocking { tidalLookupId(any(), any(), anyOrNull(), anyOrNull()) } doSuspendableAnswer { inv ->
                mapping[inv.getArgument<String>(0)]
            }
        }
        val manager = NowPlayingManager(
            context, cloudFunctions, preferencesDataStore, userRepository,
            mock(), mock(), mock<TidalPlaylistService>(),
        )

        val ids = manager.resolveTidalIds(listOf("A", "B", "C", "D").map(::descriptor))

        assertEquals(listOf("tA", "tC"), ids)
    }

    @Test
    fun `caches resolved ids so repeats skip the lookup`() = runTest(testDispatcher) {
        val cloudFunctions = mock<CloudFunctionsDataSource>()
        cloudFunctions.stub {
            onBlocking { tidalLookupId(any(), any(), anyOrNull(), anyOrNull()) } doReturn "tA"
        }
        val manager = NowPlayingManager(
            context, cloudFunctions, preferencesDataStore, userRepository,
            mock(), mock(), mock<TidalPlaylistService>(),
        )

        manager.resolveTidalIds(listOf(descriptor("A")))
        assertEquals("tA", manager.tidalIdCache["A"])
    }
}
