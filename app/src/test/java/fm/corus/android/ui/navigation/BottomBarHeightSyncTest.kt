package fm.corus.android.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the invariant that `LocalBottomBarHeight` tracks the real height of the tab
 * bar.
 *
 * `MainTabScreen` publishes `LocalBottomBarHeight` from the Scaffold's own
 * `innerPadding.calculateBottomPadding()`, and every frosted scrollable adds that
 * value to its bottom `contentPadding` so its last row clears the bar. When the bar
 * grew a floored gesture strip ([gestureNavBottomPadding]), the published value had
 * to grow with it, or the extra height would have quietly clipped the bottom of
 * every feed, profile, and notification list.
 *
 * The Scaffold measures the bottomBar slot, so the two stay in sync for free. This
 * test is what keeps that true: if someone later moves the gesture padding outside
 * the measured bar (an offset, a draw-layer inset, a hardcoded height), the coupling
 * breaks silently and this fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BottomBarHeightSyncTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * The bottom inset each Scaffold hands to its content (i.e. what
     * `LocalBottomBarHeight` is fed), one entry per requested nav strip.
     *
     * All cases render in a single composition because `setContent` may only be
     * called once per rule. Each Scaffold gets a fixed-height Box so they lay out
     * independently instead of competing for the root.
     */
    private fun publishedBottomBarHeights(navInsets: List<Dp>): List<Dp> {
        val published = MutableList(navInsets.size) { Dp.Unspecified }
        composeRule.setContent {
            Column {
                navInsets.forEachIndexed { index, navInset ->
                    // requiredHeight, not height: the cases stack past the bottom of
                    // the test screen, and a plain height would let the Column hand
                    // the later ones a squashed constraint and mis-measure the bar.
                    Box(modifier = Modifier.requiredHeight(300.dp)) {
                        Scaffold(
                            bottomBar = {
                                CorusBottomBar(
                                    selectedTab = CorusTab.FEED,
                                    notificationTabBadgeCount = 0,
                                    onTabSelected = {},
                                    navInset = navInset,
                                )
                            },
                        ) { innerPadding ->
                            published[index] = innerPadding.calculateBottomPadding()
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return published
    }

    @Test
    fun `published bar height grows by the floored gesture strip`() {
        val (withoutStrip, withThinStrip) = publishedBottomBarHeights(listOf(0.dp, 8.dp))

        // 8dp of OEM strip is floored to 24dp, and the Scaffold measures the bar,
        // so content is told to clear a full 24dp more than the no-strip case.
        assertEquals(
            "LocalBottomBarHeight must include the floored gesture padding",
            24f,
            (withThinStrip - withoutStrip).value,
            0.5f,
        )
    }

    @Test
    fun `three-button nav publishes its full inset unchanged`() {
        val (withoutStrip, withButtonBar) = publishedBottomBarHeights(listOf(0.dp, 48.dp))

        // Well above the floor, so it passes through untouched: the bar the user
        // already liked with the button bar on does not move.
        assertEquals(
            "Three-button nav must pass through its own inset",
            48f,
            (withButtonBar - withoutStrip).value,
            0.5f,
        )
    }
}
