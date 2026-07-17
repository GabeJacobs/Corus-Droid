package fm.corus.android.ui.screens.subscription

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the Club paywall's empty-space bug.
 *
 * The header + features region used to be a plain scrolling Column, which
 * top-aligns its content. On a tall phone (Pixel 9 Pro) that dumped ~250dp of
 * slack into a single dead gap between the disclaimer and the plan cards, while
 * the vinyl sat jammed against the top. [CenteredScrollRegion] min-height-matches
 * the region to its viewport so the slack splits evenly above and below, matching
 * iOS's `.frame(minHeight: proxy.size.height, alignment: .center)`.
 *
 * The centering must not come at the cost of short screens: content taller than
 * the viewport still has to scroll from the top rather than center (and clip).
 */
@RunWith(RobolectricTestRunner::class)
// Vanilla Application so Robolectric doesn't boot CorusApplication (which
// initializes RevenueCat/Firebase in onCreate — irrelevant to a layout test).
// The window must be taller than any height these tests ask for: Robolectric's
// default screen is only 470dp, which would silently clamp the host Column and
// make a centered region look like an overflowing one.
@Config(sdk = [34], application = Application::class, qualifiers = "w500dp-h1200dp-xxhdpi")
class CenteredScrollRegionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val pinnedHeight = 100.dp

    /** Mirrors the paywall's shape: a flexible region above a pinned bottom bar. */
    private fun setRegion(viewportHeight: Dp, contentHeight: Dp) {
        composeRule.setContent {
            Column(
                modifier = Modifier
                    .height(viewportHeight)
                    .fillMaxWidth(),
            ) {
                CenteredScrollRegion(verticalPadding = 0.dp) {
                    Box(
                        modifier = Modifier
                            .height(contentHeight)
                            .fillMaxWidth()
                            .testTag("content"),
                    )
                }
                Box(
                    modifier = Modifier
                        .height(pinnedHeight)
                        .fillMaxWidth()
                        .testTag("pinned"),
                )
            }
        }
    }

    @Test
    fun `content shorter than the viewport is centered, not top-aligned`() {
        // Pixel 9 Pro is ~923dp tall; the header block is nowhere near that.
        setRegion(viewportHeight = 923.dp, contentHeight = 400.dp)

        val content = composeRule.onNodeWithTag("content").getUnclippedBoundsInRoot()
        val pinned = composeRule.onNodeWithTag("pinned").getUnclippedBoundsInRoot()

        // The region spans from the root's top down to the pinned bar.
        val gapAbove = content.top.value
        val gapBelow = pinned.top.value - content.bottom.value

        assertEquals(
            "Slack should split evenly above/below the content; " +
                "above=${gapAbove}dp below=${gapBelow}dp",
            gapAbove,
            gapBelow,
            1.5f,
        )
        // Guard the actual bug: top-alignment would leave gapAbove == 0.
        assertTrue("Content should not be jammed against the top", gapAbove > 100f)
    }

    @Test
    fun `content taller than the viewport starts at the top and scrolls`() {
        setRegion(viewportHeight = 400.dp, contentHeight = 900.dp)

        val content = composeRule.onNodeWithTag("content").getUnclippedBoundsInRoot()

        // Overflow must not be centered — that would clip equally at both ends and
        // push the vinyl off-screen on short phones. It starts at the top instead.
        assertTrue(
            "Overflowing content should start at the region's top, was ${content.top}",
            content.top.value <= 0.5f,
        )
        assertEquals(
            "Overflowing content should keep its full height (scrollable)",
            900f,
            (content.bottom - content.top).value,
            0.5f,
        )
    }
}
