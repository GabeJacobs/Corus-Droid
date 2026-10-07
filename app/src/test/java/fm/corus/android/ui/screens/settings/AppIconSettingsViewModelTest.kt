package fm.corus.android.ui.screens.settings

import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.service.AppIcon
import fm.corus.android.service.AppIconManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppIconSettingsViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `direct change request cannot bypass Club access check`() {
        val manager = mock<AppIconManager>()
        val subscription = mock<SubscriptionRepository>()
        whenever(manager.selectedIcon()).thenReturn(AppIcon.DEFAULT)
        whenever(subscription.hasFullAccess).thenReturn(false)
        whenever(subscription.hasFullAccessFlow).thenReturn(MutableStateFlow(false))
        val viewModel = AppIconSettingsViewModel(manager, subscription)
        viewModel.changeIcon(AppIcon.CORUS_BLUE)
        verify(manager, never()).changeIcon(any())
    }

    @Test fun `currently selected icon is a no-op even for full access users`() {
        val manager = mock<AppIconManager>()
        val subscription = mock<SubscriptionRepository>()
        whenever(manager.selectedIcon()).thenReturn(AppIcon.CORUS_BLUE)
        whenever(subscription.hasFullAccess).thenReturn(true)
        whenever(subscription.hasFullAccessFlow).thenReturn(MutableStateFlow(true))
        val viewModel = AppIconSettingsViewModel(manager, subscription)
        viewModel.changeIcon(AppIcon.CORUS_BLUE)
        verify(manager, never()).changeIcon(any())
    }
}
