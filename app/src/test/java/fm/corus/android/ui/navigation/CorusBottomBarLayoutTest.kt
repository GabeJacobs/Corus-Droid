package fm.corus.android.ui.navigation

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Regression test for the bottom navigation Messages (center) tab alignment.
 *
 * Horizontal: the Row used Arrangement.SpaceEvenly, so each slot took its
 * intrinsic label width. Because "Activity"/"Profile" are wider than
 * "Feed"/"Search", the right side ate more width and pushed the center tab
 * left of center. Fixed by giving every slot Modifier.weight(1f).
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
            )
        }
    }

    @Test
    fun `messages tab is horizontally centered in the bar`() {
        setBar()
        val messages = composeRule
            .onNodeWithContentDescription(context.getString(R.string.messaging_list_title))
            .getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()

        val messagesCenterX = (messages.left + messages.right) / 2f
        val barCenterX = (root.left + root.right) / 2f
        val deltaDp = abs((messagesCenterX - barCenterX).value)

        assertTrue(
            "Messages tab center X ($messagesCenterX) should match bar center X ($barCenterX)",
            deltaDp < 1.5f,
        )
    }
}
