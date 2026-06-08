package fm.corus.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
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
}
