package fm.corus.android.ui.screens.destination

import fm.corus.android.data.model.AlbumCatalog
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumPageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var analyticsService: AnalyticsService
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var remoteConfigService: RemoteConfigService
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cloudFunctions = mock()
        nowPlayingManager = mock()
        analyticsService = mock()
        authRepository = mock()
        userRepository = mock()
        messageRepository = mock()
        remoteConfigService = mock()
        context = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AlbumPageViewModel(
        cloudFunctions = cloudFunctions,
        nowPlayingManager = nowPlayingManager,
        analyticsService = analyticsService,
        authRepository = authRepository,
        userRepository = userRepository,
        messageRepository = messageRepository,
        remoteConfigService = remoteConfigService,
        context = context,
    )

    private fun catalog(id: String) = AlbumCatalog(
        id = id,
        title = "Divers",
        artistName = "Joanna Newsom",
        artistIds = emptyList(),
        year = 2015,
        tracks = listOf(CymbalTrack(id = "t1", name = "Sapokanikan", artistName = "Joanna Newsom", albumName = "Divers")),
    )

    @Test
    fun `load fetches catalog and posts with the albumId passed through untouched`() =
        runTest(testDispatcher) {
            // `am:`-prefixed ids (Apple-resolved albums) must reach the backend
            // verbatim — the server branches on the prefix.
            whenever(cloudFunctions.fetchAlbumCatalog(eq("am:12345"))).thenReturn(catalog("am:12345"))
            whenever(cloudFunctions.fetchAlbumPosts(eq("am:12345"), pageSize = any(), beforeMs = anyOrNull()))
                .thenReturn(
                    CloudFunctionsDataSource.AlbumPostsPage(
                        posts = listOf(
                            CymbalPost(
                                id = "p1",
                                user = CymbalUser(id = "u1", username = "amy", displayName = "Amy"),
                                track = CymbalTrack(id = "t1", name = "Sapokanikan", artistName = "Joanna Newsom", albumName = "Divers"),
                                timestamp = Date(1000L),
                            ),
                        ),
                        uniquePosterCount = 2,
                        firstPosterId = "u1",
                    )
                )

            val vm = createViewModel()
            vm.load("am:12345")
            advanceUntilIdle()

            verify(cloudFunctions).fetchAlbumCatalog(eq("am:12345"))
            verify(cloudFunctions).fetchAlbumPosts(eq("am:12345"), pageSize = any(), beforeMs = anyOrNull())
            assertEquals("Divers", vm.catalog.value?.title)
            assertTrue(vm.catalog.value?.artistIds?.isEmpty() == true)
            assertEquals(1, vm.posts.value.size)
            assertEquals(2, vm.uniquePosterCount.value)
            assertFalse(vm.isCatalogLoading.value)
            assertFalse(vm.isPostsLoading.value)
        }

    @Test
    fun `catalog failure flags the error and retry refetches`() = runTest(testDispatcher) {
        whenever(cloudFunctions.fetchAlbumCatalog(eq("sp1")))
            .thenThrow(RuntimeException("boom"))
            .thenReturn(catalog("sp1"))
        whenever(cloudFunctions.fetchAlbumPosts(any(), pageSize = any(), beforeMs = anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.AlbumPostsPage())

        val vm = createViewModel()
        vm.load("sp1")
        advanceUntilIdle()

        assertTrue(vm.catalogError.value)
        assertFalse(vm.isCatalogLoading.value)

        // "Try again" path.
        vm.loadCatalog("sp1")
        advanceUntilIdle()

        assertFalse(vm.catalogError.value)
        assertEquals("Divers", vm.catalog.value?.title)
        verify(cloudFunctions, times(2)).fetchAlbumCatalog(eq("sp1"))
    }

    @Test
    fun `repeat load for the same album does not refetch`() = runTest(testDispatcher) {
        whenever(cloudFunctions.fetchAlbumCatalog(eq("sp1"))).thenReturn(catalog("sp1"))
        whenever(cloudFunctions.fetchAlbumPosts(any(), pageSize = any(), beforeMs = anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.AlbumPostsPage())

        val vm = createViewModel()
        vm.load("sp1")
        advanceUntilIdle()
        vm.load("sp1")
        advanceUntilIdle()

        verify(cloudFunctions, times(1)).fetchAlbumCatalog(eq("sp1"))
        verify(cloudFunctions, times(1)).fetchAlbumPosts(any(), pageSize = any(), beforeMs = anyOrNull())
    }
}
