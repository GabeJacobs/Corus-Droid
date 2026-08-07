package fm.corus.android.ui.screens.settings

import android.content.SharedPreferences
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SettingsViewModel
    private lateinit var subscriptionRepo: SubscriptionRepository
    private val remoteConfig = mock<RemoteConfigService>()
    private val analyticsService = mock<AnalyticsService>()
    private val prefs = mock<SharedPreferences>()
    private val prefsEditor = mock<SharedPreferences.Editor>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(remoteConfig.corusClubEnabled).thenReturn(true)
        whenever(prefs.getBoolean("cached_isClubMember", false)).thenReturn(false)
        whenever(prefs.getBoolean("cached_isVerified", false)).thenReturn(false)
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
        val preferencesDataStore = mock<PreferencesDataStore> {
            on { autoplayNextSong } doReturn MutableStateFlow(true)
            on { autoAddSavedToSpotify } doReturn MutableStateFlow(false)
        }
        subscriptionRepo = SubscriptionRepository(mock<CloudFunctionsDataSource>(), remoteConfig, analyticsService, prefs)
        viewModel = SettingsViewModel(
            mock<MusicServicePreference>(),
            subscriptionRepo,
            preferencesDataStore,
            remoteConfig,
            analyticsService,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isClubMember is false by default`() {
        assertFalse(viewModel.isClubMember.value)
    }

    @Test
    fun `isVerified is false by default`() {
        assertFalse(viewModel.isVerified.value)
    }

    @Test
    fun `isVerified reflects subscription repo state`() {
        subscriptionRepo.updateVerifiedStatus(true)
        assertTrue(viewModel.isVerified.value)
    }

    @Test
    fun `join club should show when not member and not verified`() {
        val showJoinClub = !viewModel.isClubMember.value && !viewModel.isVerified.value
        assertTrue(showJoinClub)
    }

    @Test
    fun `join club should hide when verified`() {
        subscriptionRepo.updateVerifiedStatus(true)
        val showJoinClub = !viewModel.isClubMember.value && !viewModel.isVerified.value
        assertFalse(showJoinClub)
    }

    @Test
    fun `restorePurchases sets in-progress flag while running`() {
        val mockRepo = mock<SubscriptionRepository> {
            on { isClubMember } doReturn MutableStateFlow(false)
            on { isVerified } doReturn MutableStateFlow(false)
        }
        val vm = SettingsViewModel(
            mock<MusicServicePreference>(),
            mockRepo,
            mock<PreferencesDataStore> {
                on { autoplayNextSong } doReturn MutableStateFlow(true)
                on { autoAddSavedToSpotify } doReturn MutableStateFlow(false)
            },
            remoteConfig,
            analyticsService,
        )

        vm.restorePurchases()
        assertTrue(vm.restoreInProgress.value)

        // Capture and invoke the callback to simulate RevenueCat resolving.
        val captor = argumentCaptor<(Boolean) -> Unit>()
        verify(mockRepo).restorePurchases(captor.capture())
        captor.firstValue.invoke(true)

        assertFalse(vm.restoreInProgress.value)
        org.junit.Assert.assertEquals(SettingsViewModel.RestoreResult.Success, vm.restoreResult.value)
        verify(analyticsService).logPurchaseRestored()
    }

    @Test
    fun `restorePurchases reports no-subscription when repo returns false`() {
        val mockRepo = mock<SubscriptionRepository> {
            on { isClubMember } doReturn MutableStateFlow(false)
            on { isVerified } doReturn MutableStateFlow(false)
        }
        val vm = SettingsViewModel(
            mock<MusicServicePreference>(),
            mockRepo,
            mock<PreferencesDataStore> {
                on { autoplayNextSong } doReturn MutableStateFlow(true)
                on { autoAddSavedToSpotify } doReturn MutableStateFlow(false)
            },
            remoteConfig,
            analyticsService,
        )

        vm.restorePurchases()
        val captor = argumentCaptor<(Boolean) -> Unit>()
        verify(mockRepo).restorePurchases(captor.capture())
        captor.firstValue.invoke(false)

        org.junit.Assert.assertEquals(SettingsViewModel.RestoreResult.NoSubscription, vm.restoreResult.value)
        verify(analyticsService).logPurchaseRestoreFailed(any())
    }

    @Test
    fun `restorePurchases is no-op while another restore is already running`() {
        val mockRepo = mock<SubscriptionRepository> {
            on { isClubMember } doReturn MutableStateFlow(false)
            on { isVerified } doReturn MutableStateFlow(false)
        }
        val vm = SettingsViewModel(
            mock<MusicServicePreference>(),
            mockRepo,
            mock<PreferencesDataStore> {
                on { autoplayNextSong } doReturn MutableStateFlow(true)
                on { autoAddSavedToSpotify } doReturn MutableStateFlow(false)
            },
            remoteConfig,
            analyticsService,
        )

        vm.restorePurchases()
        vm.restorePurchases() // second call should be ignored

        verify(mockRepo, org.mockito.kotlin.times(1)).restorePurchases(any())
    }

    @Test
    fun `clearRestoreResult resets the result flow`() {
        val mockRepo = mock<SubscriptionRepository> {
            on { isClubMember } doReturn MutableStateFlow(false)
            on { isVerified } doReturn MutableStateFlow(false)
        }
        val vm = SettingsViewModel(
            mock<MusicServicePreference>(),
            mockRepo,
            mock<PreferencesDataStore> {
                on { autoplayNextSong } doReturn MutableStateFlow(true)
                on { autoAddSavedToSpotify } doReturn MutableStateFlow(false)
            },
            remoteConfig,
            analyticsService,
        )

        vm.restorePurchases()
        val captor = argumentCaptor<(Boolean) -> Unit>()
        verify(mockRepo).restorePurchases(captor.capture())
        captor.firstValue.invoke(true)
        assertTrue(vm.restoreResult.value != null)

        vm.clearRestoreResult()
        org.junit.Assert.assertNull(vm.restoreResult.value)
    }
}
