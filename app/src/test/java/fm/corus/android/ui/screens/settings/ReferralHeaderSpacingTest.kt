package fm.corus.android.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReferralHeaderSpacingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `invite header avoids extra bottom padding below the explanation link`() {
        compose.setContent {
            Column(Modifier.testTag("header")) {
                ReferralSettingsHeader("ABCDEFGH", onExplain = {})
            }
        }
        val header = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
        val link = compose.onNodeWithText("How does it work?", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("Header must not add a second bottom gap", header.bottom - link.bottom <= 18.dp)
    }
}
