package fm.corus.android.ui.screens.destination

import fm.corus.android.data.model.AlbumSummary
import fm.corus.android.data.model.ArtistDetail
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.UserLite
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
class ArtistPageViewModelTest {

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

    private fun createViewModel() = ArtistPageViewModel(
        cloudFunctions = cloudFunctions,
        nowPlayingManager = nowPlayingManager,
        analyticsService = analyticsService,
        authRepository = authRepository,
        userRepository = userRepository,
        messageRepository = messageRepository,
        remoteConfigService = remoteConfigService,
        musicServicePreference = mock(),
        preferencesDataStore = mock(),
        context = context,
    )

    private fun detail() = ArtistDetail(
        id = "a1",
        name = "Mount Kimbie",
        imageUrl = "https://img/a.jpg",
        topTracks = listOf(CymbalTrack(id = "t1", name = "Song", artistName = "Mount Kimbie", albumName = "Album")),
        albums = listOf(AlbumSummary(id = "al1", title = "Album", year = 2013)),
    )

    private fun post(id: String, userId: String, ms: Long) = CymbalPost(
        id = id,
        user = CymbalUser(id = userId, username = userId, displayName = userId),
        track = CymbalTrack(id = "t-$id", name = "Song", artistName = "Artist", albumName = ""),
        timestamp = Date(ms),
    )

    @Test
    fun `load success populates catalog and posts sections independently`() = runTest(testDispatcher) {
        whenever(cloudFunctions.fetchArtistDetail(eq("a1"), anyOrNull())).thenReturn(detail())
        whenever(
            cloudFunctions.fetchArtistPosts(
                artistId = eq("a1"), artistName = anyOrNull(), pageSize = any(),
                beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
            )
        ).thenReturn(
            CloudFunctionsDataSource.DestinationPostsPage(
                posts = listOf(post("p1", "u1", 1000L)),
                uniquePosterCount = 4,
                posters = listOf(UserLite(id = "u1", username = "amy", displayName = "Amy")),
                viewerPosts = listOf(post("vp1", "viewer", 2000L)),
            )
        )

        val vm = createViewModel()
        vm.loadCatalog("a1", "Mount Kimbie")
        vm.loadPosts("a1", "Mount Kimbie")
        advanceUntilIdle()

        assertEquals("Mount Kimbie", vm.detail.value?.name)
        assertFalse(vm.isCatalogLoading.value)
        assertFalse(vm.catalogError.value)
        assertEquals(1, vm.posts.value.size)
        assertEquals(1, vm.viewerPosts.value.size)
        assertEquals(4, vm.uniquePosterCount.value)
        assertEquals(1, vm.posters.value.size)
        assertFalse(vm.isPostsLoading.value)
        assertFalse(vm.postsError.value)
    }

    @Test
    fun `catalog error flags the section without an infinite skeleton`() = runTest(testDispatcher) {
        whenever(cloudFunctions.fetchArtistDetail(eq("a1"), anyOrNull()))
            .thenThrow(RuntimeException("boom"))
        whenever(
            cloudFunctions.fetchArtistPosts(
                artistId = any(), artistName = anyOrNull(), pageSize = any(),
                beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
            )
        ).thenReturn(CloudFunctionsDataSource.DestinationPostsPage())

        val vm = createViewModel()
        vm.loadCatalog("a1")
        vm.loadPosts("a1")
        advanceUntilIdle()

        assertTrue(vm.catalogError.value)
        assertFalse(vm.isCatalogLoading.value)
        // Posts section loaded fine — errors are per-section.
        assertFalse(vm.postsError.value)
        assertFalse(vm.isPostsLoading.value)
    }

    @Test
    fun `posts error flags only the posts section`() = runTest(testDispatcher) {
        whenever(cloudFunctions.fetchArtistDetail(eq("a1"), anyOrNull())).thenReturn(detail())
        whenever(
            cloudFunctions.fetchArtistPosts(
                artistId = any(), artistName = anyOrNull(), pageSize = any(),
                beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
            )
        ).thenThrow(RuntimeException("boom"))

        val vm = createViewModel()
        vm.loadCatalog("a1")
        vm.loadPosts("a1")
        advanceUntilIdle()

        assertTrue(vm.postsError.value)
        assertFalse(vm.isPostsLoading.value)
        assertFalse(vm.catalogError.value)
        assertEquals("Mount Kimbie", vm.detail.value?.name)
    }

    @Test
    fun `repeat loads for the same artist do not refetch`() = runTest(testDispatcher) {
        whenever(cloudFunctions.fetchArtistDetail(eq("a1"), anyOrNull())).thenReturn(detail())

        val vm = createViewModel()
        vm.loadCatalog("a1")
        advanceUntilIdle()
        vm.loadCatalog("a1")
        advanceUntilIdle()

        verify(cloudFunctions, times(1)).fetchArtistDetail(eq("a1"), anyOrNull())
    }

    @Test
    fun `posts request opts into viewer posts`() = runTest(testDispatcher) {
        whenever(
            cloudFunctions.fetchArtistPosts(
                artistId = any(), artistName = anyOrNull(), pageSize = any(),
                beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
            )
        ).thenReturn(CloudFunctionsDataSource.DestinationPostsPage())

        val vm = createViewModel()
        vm.loadPosts("a1", "Mount Kimbie")
        advanceUntilIdle()

        verify(cloudFunctions).fetchArtistPosts(
            artistId = eq("a1"),
            artistName = eq("Mount Kimbie"),
            pageSize = eq(ArtistPageViewModel.PAGE_SIZE),
            beforeMs = anyOrNull(),
            postersLimit = any(),
            includeViewerPosts = eq(true),
        )
    }
}
