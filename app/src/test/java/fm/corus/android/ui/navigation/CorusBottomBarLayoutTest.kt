package fm.corus.android.ui.navigation

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Regression test for the bottom navigation "+" (compose) button alignment.
 *
 * Two bugs were fixed here:
 *  - Horizontal: the Row used Arrangement.SpaceEvenly, so each slot took its
 *    intrinsic label width. Because "Activity"/"Profile" are wider than
 *    "Feed"/"Search", the right side ate more width and pushed "+" left of
 *    center. Fixed by giving every slot Modifier.weight(1f).
 *  - Vertical: the Row was Top-aligned and "+" was nudged up 4dp, so it lined
 *    up with the icon row only, floating above the icon+label center. Fixed by
 *    centering the Row vertically and removing the nudge.
 *
 * These assertions hold regardless of how labels measure (font metrics), which
 * is exactly the point of the fix — centering no longer depends on label width.
 */
@RunWith(RobolectricTestRunner::class)
// Use a vanilla Application so Robolectric doesn't boot CorusApplication (which
// initializes RevenueCat/Firebase in onCreate — irrelevant to this layout test).
@Config(sdk = [34], application = Application::class)
class CorusBottomBarLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun setBar() {
        composeRule.setContent {
            CorusBottomBar(
                selectedTab = CorusTab.FEED,
                notificationTabBadgeCount = 0,
                onTabSelected = {},
                onComposeTapped = {},
            )
        }
    }

    @Test
    fun `compose button is horizontally centered in the bar`() {
        setBar()
        val plus = composeRule
            .onNodeWithContentDescription(context.getString(R.string.tab_cd_compose))
            .getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()

        val plusCenterX = (plus.left + plus.right) / 2f
        val barCenterX = (root.left + root.right) / 2f
        val deltaDp = abs((plusCenterX - barCenterX).value)

        assertTrue(
            "Compose button center X ($plusCenterX) should match bar center X ($barCenterX)",
            deltaDp < 1.5f,
        )
    }

    @Test
    fun `compose button sits just above the icon plus label center (small optical lift)`() {
        setBar()
        val plus = composeRule
            .onNodeWithContentDescription(context.getString(R.string.tab_cd_compose))
            .getUnclippedBoundsInRoot()
        // Reconstruct a tab item's full vertical extent: icon (top) to label (bottom).
        val feedIcon = composeRule
            .onNodeWithContentDescription(context.getString(R.string.tab_feed))
            .getUnclippedBoundsInRoot()
        val feedLabel = composeRule
            .onNodeWithText(context.getString(R.string.tab_feed))
            .getUnclippedBoundsInRoot()

        val plusCenterY = (plus.top + plus.bottom) / 2f
        val tabCenterY = (feedIcon.top + feedLabel.bottom) / 2f
        // The button is centered on the icon+label stack and then lifted ~2dp
        // (ComposeButtonOpticalLift) so the solid disc reads balanced against the
        // icons. So its center should sit just ABOVE the stack center.
        val liftDp = (tabCenterY - plusCenterY).value

        assertTrue(
            "Compose button center Y ($plusCenterY) should sit ~2dp above tab center Y " +
                "($tabCenterY); measured lift = ${liftDp}dp",
            liftDp in 0.5f..3.5f,
        )
    }
}
