package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistGatingTest {
    @Test
    fun `shouldPaywallPlaylist when trial used and no full access`() {
        val used = PlaylistTrialUsed(feed = true)
        assertTrue(PlaylistGatingUX.shouldPaywallPlaylist(used, PlaylistTrialField.Feed, hasFullAccess = false))
    }

    @Test
    fun `shouldPaywallPlaylist false for club members`() {
        val used = PlaylistTrialUsed(feed = true)
        assertFalse(PlaylistGatingUX.shouldPaywallPlaylist(used, PlaylistTrialField.Feed, hasFullAccess = true))
    }

    @Test
    fun `shouldShowFirstTimeConfirmation only for unconfirmed free users`() {
        assertTrue(PlaylistGatingUX.shouldShowFirstTimeConfirmation(confirmed = false, hasFullAccess = false))
        assertFalse(PlaylistGatingUX.shouldShowFirstTimeConfirmation(confirmed = true, hasFullAccess = false))
        assertFalse(PlaylistGatingUX.shouldShowFirstTimeConfirmation(confirmed = false, hasFullAccess = true))
    }
}
