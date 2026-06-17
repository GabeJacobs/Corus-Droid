package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.NotificationType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRepositoryTest {

    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val firestoreDataSource = mock<FirestoreDataSource>()
    private val userRepository = mock<UserRepository>()

    private val repo = NotificationRepository(cloudFunctions, firestoreDataSource, userRepository)

    private fun rawNotif(id: String, type: NotificationType, fromUserId: String) =
        CymbalNotification(
            id = id,
            type = type,
            // Listener path: doc stores only fromUserId, so username/avatar are blank.
            fromUser = CymbalUser(id = fromUserId, username = "", displayName = ""),
        )

    // Regression: a freshly-pushed listener row whose sender profile can't be
    // resolved (cache-miss/fetch race) must be DROPPED, not rendered as a
    // nameless "posted a new corus." Mirrors iOS listenForNotifications, which
    // `continue`s past notifications with no cached fromUser.
    @Test
    fun observeNotifications_dropsRowsWhoseSenderCannotBeHydrated() = runTest {
        val hydrated = rawNotif("n1", NotificationType.NEW_POST, "jakob")
        val unhydratable = rawNotif("n2", NotificationType.NEW_POST, "boclay")
        // Anonymous rows carry no fromUserId — must always survive hydration.
        val anonymous = rawNotif("n3", NotificationType.FAVORITE, "")

        whenever(firestoreDataSource.observeNotifications(eq("me"), any()))
            .thenReturn(flowOf(listOf(hydrated, unhydratable, anonymous)))
        // Only jakob resolves; boclay is missing from the batch result.
        whenever(userRepository.fetchUsersByIdsBatched(any()))
            .thenReturn(listOf(CymbalUser(id = "jakob", username = "jakob", displayName = "Jakob")))

        val result = repo.observeNotifications("me").first()

        assertEquals(listOf("n1", "n3"), result.map { it.id })
        assertEquals("jakob", result.first { it.id == "n1" }.fromUser.username)
    }

    // Anonymous-only batches short-circuit (no profiles to fetch) and pass
    // through untouched.
    @Test
    fun observeNotifications_keepsAnonymousOnlyBatch() = runTest {
        val favorite = rawNotif("n1", NotificationType.FAVORITE, "")
        val milestone = rawNotif("n2", NotificationType.PLAY_MILESTONE, "")

        whenever(firestoreDataSource.observeNotifications(eq("me"), any()))
            .thenReturn(flowOf(listOf(favorite, milestone)))

        val result = repo.observeNotifications("me").first()

        assertEquals(listOf("n1", "n2"), result.map { it.id })
    }
}
