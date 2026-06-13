package fm.corus.android.ui.screens.search

import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the Settings-driven contact sync added to ContactFriendsListViewModel.
 *
 * The behavior worth locking down: a re-sync must NOT re-notify the user's
 * contacts (notifyContactsOnSync) or re-fire the contacts_synced analytics
 * event — both should happen only on the first sync. Getting that wrong spams
 * push notifications to every matched contact on each visit to the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactFriendsListViewModelSyncTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var firestoreDataSource: FirestoreDataSource
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var analyticsService: AnalyticsService

    private val match = CymbalUser(id = "friend-1", username = "bob", displayName = "Bob")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mock { on { currentUserId } doReturn "viewer" }
        userRepository = mock {
            on { followingIds } doReturn MutableStateFlow(emptySet())
        }
        firestoreDataSource = mock {
            onBlocking { fetchSyncedContacts(any()) } doReturn emptyList()
        }
        cloudFunctions = mock()
        preferencesDataStore = mock {
            on { contactsSyncStatus } doReturn flowOf("notAsked")
        }
        analyticsService = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ContactFriendsListViewModel(
        userRepository = userRepository,
        authRepository = authRepository,
        firestoreDataSource = firestoreDataSource,
        cloudFunctions = cloudFunctions,
        preferencesDataStore = preferencesDataStore,
        analyticsService = analyticsService,
    )

    @Test
    fun `first sync stores contacts, notifies, loads matches and logs analytics`() = runTest(testDispatcher) {
        val phones = listOf("+15551234567")
        whenever(cloudFunctions.findContactMatches(eq(phones)))
            .thenReturn(listOf("viewer", "friend-1"))
        whenever(userRepository.fetchUsersByIdsBatched(eq(listOf("friend-1"))))
            .thenReturn(listOf(match))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.syncWithPhoneNumbers(phones)
        advanceUntilIdle()

        verify(firestoreDataSource).storeSyncedContacts(eq("viewer"), eq(phones))
        verify(cloudFunctions).notifyContactsOnSync()
        verify(preferencesDataStore).setContactsSyncStatus(eq("synced"))
        verify(analyticsService).logContactsSynced(eq(1))
        assertEquals(listOf(match), vm.contacts.value)
        assertEquals(false, vm.isSyncing.value)
    }

    @Test
    fun `re-sync does not re-notify contacts or re-log contacts_synced`() = runTest(testDispatcher) {
        whenever(preferencesDataStore.contactsSyncStatus).thenReturn(flowOf("synced"))
        val phones = listOf("+15551234567")
        whenever(cloudFunctions.findContactMatches(eq(phones)))
            .thenReturn(listOf("friend-1"))
        whenever(userRepository.fetchUsersByIdsBatched(eq(listOf("friend-1"))))
            .thenReturn(listOf(match))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.syncWithPhoneNumbers(phones)
        advanceUntilIdle()

        verify(cloudFunctions, never()).notifyContactsOnSync()
        verify(analyticsService, never()).logContactsSynced(any())
        verify(firestoreDataSource).storeSyncedContacts(eq("viewer"), eq(phones))
        assertEquals(listOf(match), vm.contacts.value)
    }

    @Test
    fun `sync with no device contacts skips callables but still marks synced`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.syncWithPhoneNumbers(emptyList())
        advanceUntilIdle()

        verify(preferencesDataStore).setContactsSyncStatus(eq("synced"))
        verify(firestoreDataSource, never()).storeSyncedContacts(any(), anyOrNull())
        verify(cloudFunctions, never()).notifyContactsOnSync()
        verify(cloudFunctions, never()).findContactMatches(anyOrNull())
        verify(analyticsService).logContactsSynced(eq(0))
    }

    @Test
    fun `failed match lookup keeps previously loaded contacts`() = runTest(testDispatcher) {
        // init load succeeds from stored contacts...
        whenever(firestoreDataSource.fetchSyncedContacts(eq("viewer")))
            .thenReturn(listOf("+15551234567"))
        whenever(cloudFunctions.findContactMatches(any()))
            .thenReturn(listOf("friend-1"))
        whenever(userRepository.fetchUsersByIdsBatched(eq(listOf("friend-1"))))
            .thenReturn(listOf(match))

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(listOf(match), vm.contacts.value)

        // ...then the re-sync lookup fails; the list shouldn't be wiped.
        whenever(cloudFunctions.findContactMatches(any()))
            .thenThrow(RuntimeException("boom"))
        vm.syncWithPhoneNumbers(listOf("+15551234567"))
        advanceUntilIdle()

        assertEquals(listOf(match), vm.contacts.value)
        assertEquals(false, vm.isSyncing.value)
    }
}
