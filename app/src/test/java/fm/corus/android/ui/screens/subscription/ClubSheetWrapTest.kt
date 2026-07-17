package fm.corus.android.ui.screens.subscription

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
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
 * Verifies the height mechanism CymbalClubOfferSheet uses so the paywall sheet
 * WRAPS its content on tall screens (a right-sized card) instead of always
 * filling a fixed 94% of the screen (a card with a dead band of empty space).
 *
 * The sheet skeleton is: an outer Column capped with `heightIn(max = …)`, a fixed
 * close button, a `weight(1f, fill = false)` scrolling middle region, and a fixed
 * pinned bottom. This test replicates that skeleton with dummy content — the
 * subtlety being verified is purely in the modifiers (`fill = false` + the cap),
 * not in the paywall's specific views, and the real sheet needs a Hilt view model
 * that can't be built in a unit test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w500dp-h1200dp-xxhdpi")
class ClubSheetWrapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val closeHeight = 48.dp
    private val pinnedHeight = 200.dp

    private fun setSkeleton(cap: Dp, contentHeight: Dp) {
        composeRule.setContent {
            // A roomy parent so the capped Column is free to wrap below the cap.
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = cap)
                        .testTag("sheet"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(closeHeight).fillMaxWidth().testTag("close"))
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .testTag("scroll"),
                    ) {
                        Box(Modifier.height(contentHeight).fillMaxWidth().testTag("content"))
                    }
                    Box(Modifier.height(pinnedHeight).fillMaxWidth().testTag("pinned"))
                }
            }
        }
    }

    private fun heightOf(tag: String): Float {
        val b = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return (b.bottom - b.top).value
    }

    @Test
    fun `sheet wraps to content when it fits under the cap`() {
        // Content well short of the cap → the sheet should hug the content, not
        // stretch to the cap (this is the Pixel 9 Pro case).
        setSkeleton(cap = 900.dp, contentHeight = 300.dp)

        val expected = (closeHeight + 300.dp + pinnedHeight).value // 548
        assertEquals(
            "Sheet should wrap to close+content+pinned, not fill the cap",
            expected,
            heightOf("sheet"),
            2f,
        )
        assertTrue("Wrapped sheet must stay under the cap", heightOf("sheet") < 900f)
    }

    @Test
    fun `sheet caps and its middle region scrolls when content overflows`() {
        // Content taller than the leftover space (short-screen case). The sheet
        // must NOT wrap past the cap; the middle region is bounded and scrolls.
        setSkeleton(cap = 500.dp, contentHeight = 900.dp)

        assertEquals("Sheet should be pinned to the cap", 500f, heightOf("sheet"), 2f)
        // Leftover for the scroll region = cap - close - pinned = 252.
        assertEquals(
            "Middle region should be bounded to the leftover space so it scrolls",
            (500.dp - closeHeight - pinnedHeight).value,
            heightOf("scroll"),
            2f,
        )
        // The content keeps its full height inside the scroll viewport.
        assertEquals("Content keeps full height (scrollable)", 900f, heightOf("content"), 2f)
    }
}
