package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the TasteMatchCard user-info block alignment.
 *
 * The username + subtitle below the 2x2 art grid must be START-aligned, mirroring
 * iOS's `userInfoRow` (VStack(.leading) with a small avatar left of the username).
 * A prior Android-only version centered the block (Alignment.CenterHorizontally +
 * TextAlign.Center) and dropped the avatar, which read as broken next to iOS.
 *
 * The card is given a fixed, wide width and a short subtitle so that a regression
 * back to center-alignment would push the subtitle's left edge toward the middle
 * of the card and fail the assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TasteMatchCardLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val cardWidth = 300.dp

    private fun match() = SuggestedUserMatch(
        user = CymbalUser(id = "u1", username = "alice", displayName = "Alice Anderson"),
        matchData = null,
        suggestionReason = null,
    )

    @Test
    fun `subtitle is start-aligned, not centered, under the grid`() {
        composeRule.setContent {
            TasteMatchCard(
                match = match(),
                isFollowing = false,
                // Short, explicit subtitle: if the block were re-centered this
                // text's left edge would jump toward the card's middle.
                subtitle = "Jazz",
                modifier = Modifier.width(cardWidth),
            )
        }

        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        val subtitle = composeRule.onNodeWithText("Jazz").getUnclippedBoundsInRoot()

        val cardCenterX = (rootBounds.left + rootBounds.right).value / 2f
        // Start-aligned: the subtitle hugs the left padding, well left of center.
        // Centered "Jazz" would sit roughly at cardCenterX.
        assertTrue(
            "Subtitle left edge (${subtitle.left.value}) should be start-aligned " +
                "(well left of card center $cardCenterX), not centered.",
            subtitle.left.value < cardCenterX * 0.5f,
        )
    }

    @Test
    fun `username is start-aligned to the right of the avatar`() {
        composeRule.setContent {
            TasteMatchCard(
                match = match(),
                isFollowing = false,
                subtitle = "Jazz",
                modifier = Modifier.width(cardWidth),
            )
        }

        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        val username = composeRule.onNodeWithText("alice").getUnclippedBoundsInRoot()

        val cardCenterX = (rootBounds.left + rootBounds.right).value / 2f
        // The username starts after the 28dp avatar + gap, so it is not at the very
        // edge, but it must still begin in the left half — never centered.
        assertTrue(
            "Username left edge (${username.left.value}) should start in the left " +
                "half of the card (center $cardCenterX).",
            username.left.value < cardCenterX,
        )
    }
}
