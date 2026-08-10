package fm.corus.android.ui.screens.profile

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the FOLLOWING / PLAYLIST button row on another user's
 * profile. The row clipped twice:
 *  - With neither pill weighted, PLAYLIST was measured second and clipped to
 *    "PLAYLI" once the FOLLOW pill's padding grew (Pixel 9 Pro wide header).
 *  - After weighting the FOLLOW pill it kept its padding + a fixed-size label,
 *    so the *weighted* pill clipped instead: "FOLLOWING" -> "FOLLOWI".
 *
 * The fix makes [ProfileFollowPill] mirror the own-profile EDIT button: weighted
 * to absorb the width the (unweighted, measured-first) PLAYLIST pill leaves over,
 * no horizontal padding, shrink-to-fit label.
 *
 * Compose text truncation is a paint-time effect and is NOT visible in semantics
 * bounds (a node's layout size is coerced to its constraints either way), so we
 * can't assert "the glyphs weren't clipped" directly. Instead we assert the two
 * structural invariants the fix relies on, both of which regressed at some point:
 *  1. the FOLLOW pill is weighted and fills the leftover row width, and
 *  2. its label stays on a single line (never wraps).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProfileFollowPillLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val rowWidthDp = 300
    private val siblingDp = 120 // stand-in for the unweighted, full-width PLAYLIST pill

    /**
     * Mirrors production: a fixed-width unweighted sibling (the PLAYLIST pill,
     * measured first) at the start of the row, then the real weighted
     * [ProfileFollowPill] absorbing the remainder. A single-line reference label
     * of the same style sits above it so the height check self-calibrates instead
     * of hard-coding Robolectric's font metrics.
     */
    @Composable
    private fun setRow() {
        Column {
            Text(
                text = "REFERENCE",
                // Match ProfileFollowPill's wide-phone base (13sp).
                style = profileActionButtonBaseStyle(widthDp = 400),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("singleLineRef"),
            )
            Row(modifier = Modifier.width(rowWidthDp.dp)) {
                Text("PLAYLIST", modifier = Modifier.width(siblingDp.dp))
                ProfileFollowPill(isFollowing = true, onClick = {})
            }
        }
    }

    @Test
    fun `follow pill is weighted and fills the leftover row width`() {
        composeRule.setContent { setRow() }

        val label = composeRule
            .onNodeWithText(context.getString(R.string.other_profile_button_following))
            .getUnclippedBoundsInRoot()

        val labelCenterX = (label.left + label.right).value / 2f
        // The pill occupies [siblingDp, rowWidthDp]; its centered label should sit
        // at the middle of that leftover band.
        val expectedCenterX = siblingDp + (rowWidthDp - siblingDp) / 2f

        assertTrue(
            "FOLLOWING label center ($labelCenterX) should be centered in the leftover " +
                "band around $expectedCenterX — the pill is not filling the row (lost its weight)",
            kotlin.math.abs(labelCenterX - expectedCenterX) < 8f,
        )
    }

    @Test
    fun `following label stays on a single line`() {
        composeRule.setContent { setRow() }

        val refBounds = composeRule.onNodeWithTag("singleLineRef").getUnclippedBoundsInRoot()
        val followBounds = composeRule
            .onNodeWithText(context.getString(R.string.other_profile_button_following))
            .getUnclippedBoundsInRoot()

        val refHeight = (refBounds.bottom - refBounds.top).value
        val followHeight = (followBounds.bottom - followBounds.top).value

        // A wrapped label would be ~2x the single-line reference; 1.5x cleanly
        // separates one line from two without depending on absolute font metrics.
        assertTrue(
            "FOLLOWING height ($followHeight) exceeds 1.5x the single-line reference " +
                "($refHeight) — the label is wrapping instead of shrinking to fit",
            followHeight <= refHeight * 1.5f,
        )
    }
}
