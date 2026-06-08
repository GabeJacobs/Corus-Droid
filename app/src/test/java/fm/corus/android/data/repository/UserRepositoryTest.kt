package fm.corus.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryTest {

    private lateinit var repo: UserRepository
    private val firestoreDataSource = mock<FirestoreDataSource>()
    private val storageDataSource = mock<FirebaseStorageDataSource>()
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val preferencesDataStore = mock<PreferencesDataStore>()
    private val auth = mock<FirebaseAuth>()
    private val subscriptionRepository = mock<SubscriptionRepository>()

    private val currentUid = "me"
    private val targetUid = "target"

    private fun user(id: String, followers: Int) = CymbalUser(
        id = id,
        username = id,
        displayName = id,
        followerCount = followers,
    )

    @Before
    fun setUp() {
        // No signed-in user needed for these paths; auth.currentUser is null.
        whenever(auth.currentUser).thenReturn(null)
        repo = UserRepository(
            firestoreDataSource,
            storageDataSource,
            cloudFunctions,
            preferencesDataStore,
            auth,
            subscriptionRepository,
        )
    }

    // Regression: following a user must invalidate the target's cached profile
    // so the next fetch reflects the server-updated followerCount instead of
    // serving the stale pre-follow count for the 5-minute TTL window.
    @Test
    fun `followUser invalidates target profile cache so next fetch is fresh`() = runTest {
        whenever(firestoreDataSource.fetchUserProfile(targetUid))
            .thenReturn(user(targetUid, 5), user(targetUid, 6))

        // Seed the cache with the pre-follow count.
        val before = repo.fetchUserProfile(targetUid)
        assertEquals(5, before?.followerCount)

        repo.followUser(currentUid, targetUid)

        // Next fetch must hit Firestore again (cache invalidated), not serve 5.
        val after = repo.fetchUserProfile(targetUid)
        assertEquals(6, after?.followerCount)
        verify(firestoreDataSource, times(2)).fetchUserProfile(targetUid)
    }

    @Test
    fun `unfollowUser invalidates target profile cache so next fetch is fresh`() = runTest {
        whenever(firestoreDataSource.fetchUserProfile(targetUid))
            .thenReturn(user(targetUid, 6), user(targetUid, 5))

        val before = repo.fetchUserProfile(targetUid)
        assertEquals(6, before?.followerCount)

        repo.unfollowUser(currentUid, targetUid)

        val after = repo.fetchUserProfile(targetUid)
        assertEquals(5, after?.followerCount)
        verify(firestoreDataSource, times(2)).fetchUserProfile(targetUid)
    }

    // Regression: the Search rail must serve taste matches from the 4h cache on
    // repeat opens instead of re-hitting the expensive getSuggestedUsers cloud
    // function every time the screen is shown.
    @Test
    fun `getSuggestedUsers serves second call from cache within TTL`() = runTest {
        whenever(cloudFunctions.getSuggestedUsers(currentUid))
            .thenReturn(listOf(SuggestedUserMatch(user("a", 1))))

        repo.getSuggestedUsers(currentUid)
        repo.getSuggestedUsers(currentUid)

        verify(cloudFunctions, times(1)).getSuggestedUsers(currentUid)
    }

    // forceRefresh (pull-to-refresh / cold-start poll) must bypass the cache.
    @Test
    fun `getSuggestedUsers with forceRefresh bypasses cache`() = runTest {
        whenever(cloudFunctions.getSuggestedUsers(currentUid))
            .thenReturn(listOf(SuggestedUserMatch(user("a", 1))))

        repo.getSuggestedUsers(currentUid)
        repo.getSuggestedUsers(currentUid, forceRefresh = true)

        verify(cloudFunctions, times(2)).getSuggestedUsers(currentUid)
    }

    // ── User search: followed-set fetch (the iOS-parity perf fix) ──

    private suspend fun seedFollowing(vararg ids: String) {
        whenever(firestoreDataSource.fetchFollowingIds(currentUid)).thenReturn(ids.toSet())
        repo.prefetchFollowingSet(currentUid)
        // Username/token queries are irrelevant to these tests — keep them empty.
        whenever(firestoreDataSource.searchUsersByUsername(any(), any())).thenReturn(emptyList())
        whenever(firestoreDataSource.searchUsersByToken(any(), any())).thenReturn(emptyList())
    }

    // Regression: the @mention/compose hot path (includeFollowed = false, the
    // default) must NOT fetch the following set on each keystroke — that was the
    // Android-only slowness vs iOS, which passes no followed users on that path.
    @Test
    fun `searchUsers without includeFollowed never fetches the following set`() = runTest {
        seedFollowing("f1", "f2")

        repo.searchUsers("f", limit = 4)

        verify(firestoreDataSource, never()).fetchUsersByIds(any())
    }

    // Followed users must still surface for people-finder surfaces, but the
    // profiles are fetched once and reused across keystrokes (in-memory cache),
    // mirroring iOS SearchView's pre-fetched cachedFollowedUsers.
    @Test
    fun `searchUsers with includeFollowed caches the following set across calls`() = runTest {
        seedFollowing("f1")
        whenever(firestoreDataSource.fetchUsersByIds(any())).thenReturn(listOf(user("f1", 3)))

        val first = repo.searchUsers("f", includeFollowed = true)
        val second = repo.searchUsers("f1", includeFollowed = true)

        assertEquals("f1", first.firstOrNull()?.id)
        assertEquals("f1", second.firstOrNull()?.id)
        // Two searches, but the followed profiles are fetched exactly once.
        verify(firestoreDataSource, times(1)).fetchUsersByIds(any())
    }

    // Warming the cache on screen-appear must make the first real search resolve
    // followed matches from memory (no extra fetch).
    @Test
    fun `prefetchFollowedProfiles warms the cache so the first search does not refetch`() = runTest {
        seedFollowing("f1")
        whenever(firestoreDataSource.fetchUsersByIds(any())).thenReturn(listOf(user("f1", 3)))

        repo.prefetchFollowedProfiles()
        repo.searchUsers("f", includeFollowed = true)

        verify(firestoreDataSource, times(1)).fetchUsersByIds(any())
    }
}
