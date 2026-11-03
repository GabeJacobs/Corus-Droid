package fm.corus.android.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReferralHistoryRefreshBoxTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `initial fetch and subsequent refresh each show exactly one progress indicator`() {
        val loading = mutableStateOf(true)
        compose.setContent {
            ReferralHistoryRefreshBox(loading.value, onRefresh = {}) {
                Box(Modifier.fillMaxSize())
            }
        }
        fun indicators() = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true,
        )
        indicators().assertCountEquals(1)
        compose.runOnIdle { loading.value = false }
        indicators().assertCountEquals(0)
        compose.runOnIdle { loading.value = true }
        indicators().assertCountEquals(1)
    }
}
