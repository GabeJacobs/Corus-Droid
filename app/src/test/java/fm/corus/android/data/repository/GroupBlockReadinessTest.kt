package fm.corus.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class GroupBlockReadinessTest {
    private val firestore = mock<FirestoreDataSource>()
    private val repo = UserRepository(firestore, mock<FirebaseStorageDataSource>(), mock<CloudFunctionsDataSource>(), mock<PreferencesDataStore>(), mock<FirebaseAuth>(), mock<SubscriptionRepository>())

    @Test fun emptySuccessfulListIsReadyWithoutExtraReads() = runTest {
        whenever(firestore.fetchBlockedIds("me")).thenReturn(emptySet())
        whenever(firestore.fetchBlockedByIds("me")).thenReturn(emptySet())
        assertFalse(repo.blockedIdsLoaded.value)
        repo.prefetchBlockedSet("me")
        assertTrue(repo.blockedIdsLoaded.value)
        assertFalse(repo.blockedIdsLoadFailed.value)
        verify(firestore, times(1)).fetchBlockedIds("me")
        verify(firestore, times(1)).fetchBlockedByIds("me")
    }

    @Test fun failedListDoesNotExposeGroupContentAndCanRetry() = runTest {
        whenever(firestore.fetchBlockedIds("me")).thenThrow(RuntimeException("offline")).thenReturn(setOf("blocked"))
        whenever(firestore.fetchBlockedByIds("me")).thenReturn(emptySet())
        repo.prefetchBlockedSet("me")
        assertFalse(repo.blockedIdsLoaded.value)
        assertTrue(repo.blockedIdsLoadFailed.value)
        repo.prefetchBlockedSet("me")
        assertTrue(repo.blockedIdsLoaded.value)
        assertFalse(repo.blockedIdsLoadFailed.value)
        assertEquals(setOf("blocked"), repo.blockedIds.value)
    }

    @Test fun signingOutClearsReadiness() = runTest {
        whenever(firestore.fetchBlockedIds("me")).thenReturn(setOf("blocked"))
        whenever(firestore.fetchBlockedByIds("me")).thenReturn(emptySet())
        repo.prefetchBlockedSet("me")
        repo.clearCaches()
        assertFalse(repo.blockedIdsLoaded.value)
        assertTrue(repo.blockedIds.value.isEmpty())
    }
}
