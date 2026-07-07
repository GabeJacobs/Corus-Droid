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
import org.mockito.kotlin.anyOrNull
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

    // ── User search: no follow-list fetch on any path ──

    private suspend fun seedFollowing(vararg ids: String) {
        whenever(firestoreDataSource.fetchFollowingIds(currentUid)).thenReturn(ids.toSet())
        repo.prefetchFollowingSet(currentUid)
        // Default the username/token queries to empty; ranking tests override.
        whenever(firestoreDataSource.searchUsersByUsername(any(), any())).thenReturn(emptyList())
        whenever(firestoreDataSource.searchUsersByToken(any(), any())).thenReturn(emptyList())
    }

    // Regression: search must NEVER bulk-fetch the following set, on any path.
    // Followed users surface through the same username/token queries as everyone
    // else (searchTokens is kept complete server-side), and the follow graph is
    // applied as a ranking signal from the in-memory id set only. The old code
    // pulled every followed profile here, which made people-search take ~10s for
    // users who follow thousands of accounts (e.g. 1,569 follows → ~53 reads per
    // search session).
    @Test
    fun `searchUsers never bulk-fetches the following set`() = runTest {
        seedFollowing("f1", "f2", "f3")

        repo.searchUsers("f", limit = 4)                       // mention/compose path
        repo.searchUsers("f", limit = 4, includeFollowed = true) // people-finder path

        verify(firestoreDataSource, never()).fetchUsersByIds(any())
    }

    // includeFollowed = true floats people you follow to the top of results, even
    // when a non-followed match has a far higher follower count. The followed
    // user is surfaced by the token query (not a separate fetch) and the in-memory
    // following set drives the ranking.
    @Test
    fun `searchUsers with includeFollowed ranks followed users first`() = runTest {
        seedFollowing("f1")
        whenever(firestoreDataSource.searchUsersByToken(any(), any()))
            .thenReturn(listOf(user("popular", 9999), user("f1", 2)))

        val results = repo.searchUsers("a", includeFollowed = true)

        assertEquals("f1", results.firstOrNull()?.id)
        verify(firestoreDataSource, never()).fetchUsersByIds(any())
    }

    // Without includeFollowed there is no follow-based boost, so the higher
    // follower-count match ranks first.
    @Test
    fun `searchUsers without includeFollowed ranks by follower count`() = runTest {
        seedFollowing("f1")
        whenever(firestoreDataSource.searchUsersByToken(any(), any()))
            .thenReturn(listOf(user("popular", 9999), user("f1", 2)))

        val results = repo.searchUsers("a")

        assertEquals("popular", results.firstOrNull()?.id)
    }

    // NEW ON CORUS must go through the server-filtered getNewUsers callable so
    // shadow/hard-banned users are reliably hidden (the old client-side ban set
    // races the ban-list sync). The direct Firestore read is only a fallback.
    @Test
    fun `fetchNewUsers uses the server-filtered getNewUsers callable`() = runTest {
        whenever(cloudFunctions.getNewUsers(any(), any(), anyOrNull()))
            .thenReturn(Pair(listOf(user("newbie", 0)), null))

        val result = repo.fetchNewUsers(limit = 10)

        assertEquals(listOf("newbie"), result.map { it.id })
        verify(firestoreDataSource, never()).fetchNewUsers(any(), any())
    }

    // If the callable errors (e.g. offline), fall back to the direct read so the
    // rail still renders instead of going blank.
    @Test
    fun `fetchNewUsers falls back to the direct read when the callable fails`() = runTest {
        whenever(cloudFunctions.getNewUsers(any(), any(), anyOrNull()))
            .thenThrow(RuntimeException("network"))
        whenever(firestoreDataSource.fetchNewUsers(any(), any()))
            .thenReturn(listOf(user("fallback", 0)))

        val result = repo.fetchNewUsers(limit = 10)

        assertEquals(listOf("fallback"), result.map { it.id })
        verify(firestoreDataSource).fetchNewUsers(any(), any())
    }
}
