package fm.corus.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class CityChatJoinHandoffTest {
    private fun repo() = MessageRepository(mock<CloudFunctionsDataSource>(), mock<FirebaseStorageDataSource>(), mock<FirebaseFirestore>())
    private val thread = CymbalThread(id = "grp_city_london", isGroup = true, cityChatId = "london", cityName = "London", memberIds = listOf("alice"))

    @Test fun authorizedJoinSeedsInboxAndIsConsumedOnlyOnceForItsAccountAndThread() {
        val repo = repo()
        repo.cacheInbox(MessageRepository.CachedInbox("alice", listOf(CymbalThread("existing")), 42L, false))
        repo.recordJoinedCityThread("alice", thread, 100L)
        assertEquals(setOf(thread.id, "existing"), repo.cachedInbox!!.threads.map { it.id }.toSet())
        assertEquals(42L, repo.cachedInbox!!.nextCursor)
        assertFalse(repo.cachedInbox!!.hasMore)
        assertEquals(thread, repo.takeJoinedCityThread("alice", thread.id, 101L))
        assertNull(repo.takeJoinedCityThread("alice", thread.id, 102L))
        repo.recordJoinedCityThread("alice", thread, 100L)
        assertNull(repo.takeJoinedCityThread("bob", thread.id, 101L))
        assertNull(repo.takeJoinedCityThread("alice", thread.id, 102L))
        repo.recordJoinedCityThread("alice", thread, 100L)
        assertNull(repo.takeJoinedCityThread("alice", "other", 101L))
        repo.recordJoinedCityThread("alice", thread, 100L)
        assertNull(repo.takeJoinedCityThread("alice", thread.id, 30_101L))
    }

    @Test fun invalidMembershipCannotSeedAnAuthorizedHandoffAndOtherAccountsInboxIsNotInherited() {
        val repo = repo()
        for (invalid in listOf(thread.copy(isGroup = false), thread.copy(cityChatId = null), thread.copy(memberIds = listOf("bob")))) {
            repo.recordJoinedCityThread("alice", invalid, 100L)
            assertNull(repo.takeJoinedCityThread("alice", thread.id, 101L))
            assertNull(repo.cachedInbox)
        }
        repo.cacheInbox(MessageRepository.CachedInbox("bob", listOf(CymbalThread("bobs-private-chat")), 42L, false))
        repo.recordJoinedCityThread("alice", thread, 100L)
        assertEquals(listOf(thread), repo.cachedInbox!!.threads)
        assertEquals("alice", repo.cachedInbox!!.userId)
    }
}
