package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.R
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.TasteDiscoveryAccess
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TasteMatchesRailEmptyStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `successful empty carousel keeps heading and shows existing empty message`() {
        val failed = MutableStateFlow(false)
        val loading = MutableStateFlow(true)
        val endReached = MutableStateFlow(false)
        val vm = mock<TasteMatchesRailViewModel>()
        whenever(vm.matches).thenReturn(MutableStateFlow(emptyList<SuggestedUserMatch>()))
        whenever(vm.discovery).thenReturn(MutableStateFlow(TasteDiscoveryAccess()))
        whenever(vm.isLoading).thenReturn(loading)
        whenever(vm.endReached).thenReturn(endReached)
        whenever(vm.loadFailed).thenReturn(failed)
        whenever(vm.isFilling).thenReturn(MutableStateFlow(false))
        val message = ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.search_taste_matches_empty)
        composeRule.setContent {
            HorizontalTasteMatchesRail(
                followedIds = emptySet(), onUserTap = {}, onFollowTap = {}, viewModel = vm,
            )
        }
        composeRule.onNodeWithText(message).assertDoesNotExist()
        composeRule.runOnIdle {
            endReached.value = true
            loading.value = false
        }
        composeRule.onNodeWithText("TASTE MATCHES").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.runOnIdle { failed.value = true }
        composeRule.onNodeWithText(message).assertDoesNotExist()
        composeRule.runOnIdle { failed.value = false }
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }
}
