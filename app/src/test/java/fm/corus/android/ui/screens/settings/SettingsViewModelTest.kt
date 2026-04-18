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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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
        }
        subscriptionRepo = SubscriptionRepository(mock<CloudFunctionsDataSource>(), remoteConfig, analyticsService, prefs)
        viewModel = SettingsViewModel(mock<MusicServicePreference>(), subscriptionRepo, preferencesDataStore)
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
}
