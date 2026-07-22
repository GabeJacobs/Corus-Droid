package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import fm.corus.android.data.model.FlairStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the Corus-logo flair size in [UsernameWithFlair].
 *
 * The logo is the one flair drawn from a drawable rather than a vector icon,
 * and its artwork carries a transparent margin. Drawn at the icon size (14dp)
 * it read visibly smaller than every other flair, which is what made it look
 * shrunken next to the same username on iOS. iOS compensates in
 * `UsernameWithBotBadge` by drawing the asset at 18pt against 12pt SF Symbols;
 * this asserts Android does the same.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UsernameFlairBadgeSizeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Corus logo flair is drawn larger than a vector-icon flair`() {
        composeRule.setContent {
            Column {
                UsernameWithFlair(
                    username = "gabe",
                    isClubMember = true,
                    flairStyle = FlairStyle.CORUS_LOGO,
                )
                UsernameWithFlair(
                    username = "rollytog",
                    isClubMember = true,
                    flairStyle = FlairStyle.HEART,
                )
            }
        }

        val logoWidth = composeRule
            .onNodeWithContentDescription("Corus", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .let { (it.right - it.left).value }
        val iconWidth = composeRule
            .onNodeWithContentDescription(FlairStyle.HEART.displayName, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .let { (it.right - it.left).value }

        assertEquals("Corus logo flair should be 18dp", 18.0, logoWidth.toDouble(), 0.5)
        assertEquals("Vector-icon flair should stay 14dp", 14.0, iconWidth.toDouble(), 0.5)
    }
}
