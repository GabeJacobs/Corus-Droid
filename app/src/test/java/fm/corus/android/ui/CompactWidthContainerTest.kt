package fm.corus.android.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CompactWidthContainerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContainer(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeRule.setContent {
            CompactWidthContainer(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().testTag("content"))
            }
        }
    }

    private fun assertClose(expected: Dp, actual: Dp) {
        if (abs((expected - actual).value) > 1f) {
            assertEquals(expected, actual)
        }
    }

    private fun contentBounds() = composeRule.onNodeWithTag("content").getUnclippedBoundsInRoot()

    private fun rootBounds() = composeRule.onRoot().getUnclippedBoundsInRoot()

    @Test
    fun `content is capped at the compact width on a large window`() {
        setContainer("w1280dp-h800dp")

        assertClose(CompactWindowMaxWidth, contentBounds().width)
    }

    @Test
    fun `capped content is horizontally centered on a large window`() {
        setContainer("w1280dp-h800dp")

        val root = rootBounds()
        val content = contentBounds()
        assertClose(content.left - root.left, root.right - content.right)
    }

    @Test
    fun `content fills the window on a phone-width display`() {
        setContainer("w411dp-h891dp")

        assertClose(rootBounds().width, contentBounds().width)
    }

    @Test
    fun `content fills the window at exactly the compact width`() {
        setContainer("w600dp-h891dp")

        assertClose(rootBounds().width, contentBounds().width)
    }
}
