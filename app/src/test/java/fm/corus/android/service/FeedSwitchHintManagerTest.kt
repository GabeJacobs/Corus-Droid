package fm.corus.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate logic for the one-time feed-switch hint. Mirrors web `shouldShowHint`
 * (Corus-Web lib/feed/switch-hint.ts) and iOS FeedSwitchHintManager: the hint
 * shows only for an engaged, undiscovered user who hasn't hit the impression cap.
 */
class FeedSwitchHintManagerTest {

    private fun show(
        enabled: Boolean = true,
        minSession: Int = 3,
        maxImpressions: Int = 3,
        hasOpened: Boolean = false,
        hasDismissed: Boolean = false,
        shownThisSession: Boolean = false,
        sessionCount: Int = 3,
        shownCount: Int = 0,
        hasExploredOtherFeed: Boolean = false,
    ) = FeedSwitchHintManager.computeShouldShow(
        enabled = enabled,
        minSession = minSession,
        maxImpressions = maxImpressions,
        hasOpened = hasOpened,
        hasDismissed = hasDismissed,
        shownThisSession = shownThisSession,
        sessionCount = sessionCount,
        shownCount = shownCount,
        hasExploredOtherFeed = hasExploredOtherFeed,
    )

    @Test fun `shows on the happy path`() = assertTrue(show())

    @Test fun `hidden when the flag is disabled`() = assertFalse(show(enabled = false))

    @Test fun `hidden below the min session`() = assertFalse(show(sessionCount = 2))

    @Test fun `shows at exactly the min session`() =
        assertTrue(show(sessionCount = 3, minSession = 3))

    @Test fun `hidden once the switcher was opened`() = assertFalse(show(hasOpened = true))

    @Test fun `hidden once the hint was dismissed`() = assertFalse(show(hasDismissed = true))

    @Test fun `hidden once another feed was explored`() =
        assertFalse(show(hasExploredOtherFeed = true))

    @Test fun `hidden after it already showed this session`() =
        assertFalse(show(shownThisSession = true))

    @Test fun `hidden at the impression cap`() =
        assertFalse(show(shownCount = 3, maxImpressions = 3))

    @Test fun `shows below the impression cap`() =
        assertTrue(show(shownCount = 2, maxImpressions = 3))
}
