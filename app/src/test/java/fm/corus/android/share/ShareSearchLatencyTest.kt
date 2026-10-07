package fm.corus.android.share

import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShareSearchLatencyTest {
    @Test fun fastPeopleAreAvailableBeforeSlowInboxCompletes() = runTest {
        val cloud = mock<CloudFunctionsDataSource> {
            onBlocking { searchShareGroups(any(), any()) } doSuspendableAnswer {
                delay(650)
                emptyList()
            }
        }
        val users = mock<UserRepository>()
        val person = CymbalUser(id = "isa", username = "isa", displayName = "Isabela")
        whenever(users.searchUsers(any(), any(), any())).thenAnswer { listOf(person) }
        // Delay the old full inbox path to demonstrate that its completion gates people.
        val repository = MessageRepository(cloud, mock<FirebaseStorageDataSource>(), mock<FirebaseFirestore>())
        var observed = emptyList<String>()
        val job = launch {
            repository.searchShareRecipients("self", "isa", users) { results ->
                observed = results.map { it.id }
            }
        }
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("isa"), observed)
        job.join()
    }

    @Test fun fastGroupsAreAvailableBeforePeople() = runTest {
        val group = CymbalThread(id = "friends", isGroup = true, groupName = "Friends")
        val cloud = mock<CloudFunctionsDataSource> {
            onBlocking { searchShareGroups(any(), any()) } doSuspendableAnswer { listOf(group) }
        }
        val users = mock<UserRepository> {
            onBlocking { searchUsers(any(), any(), any()) } doSuspendableAnswer {
                delay(650)
                listOf(CymbalUser(id = "isa", username = "isa", displayName = "Isabela"))
            }
        }
        val repository = MessageRepository(cloud, mock(), mock())
        var observed = emptyList<String>()
        val job = launch { repository.searchShareRecipients("self", "isa", users) { observed = it.map { recipient -> recipient.id } } }
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("group:friends"), observed)
        job.join()
        assertEquals(listOf("group:friends", "isa"), observed)
    }

    @Test fun groupFailureKeepsSuccessfulPeopleAndCancellationStopsUpdates() = runTest {
        val cloud = mock<CloudFunctionsDataSource> {
            onBlocking { searchShareGroups(any(), any()) } doSuspendableAnswer { throw IllegalStateException("network") }
        }
        val users = mock<UserRepository> {
            onBlocking { searchUsers(any(), any(), any()) } doSuspendableAnswer {
                delay(650)
                listOf(CymbalUser(id = "isa", username = "isa", displayName = "Isabela"))
            }
        }
        val repository = MessageRepository(cloud, mock(), mock())
        assertEquals(listOf("isa"), repository.searchShareRecipients("self", "isa", users).map { it.id })
        var updates = 0
        val job = launch { repository.searchShareRecipients("self", "isa", users) { updates++ } }
        runCurrent()
        assertEquals(1, updates) // The completed, empty group source.
        job.cancel()
        advanceUntilIdle()
        assertEquals("A cancelled query must not publish its delayed people", 1, updates)
    }
}
