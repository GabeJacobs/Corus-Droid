package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileArtistLinkGateTest {

    @Test
    fun defaultOffForEveryoneElse() {
        assertFalse(
            ProfileArtistLinkGate.isEnabled(
                flag = false,
                artistPagesEnabled = true,
                viewerUsername = "emily",
            ),
        )
    }

    @Test
    fun gabeIsOnEvenWhenFlagIsOff() {
        assertTrue(
            ProfileArtistLinkGate.isEnabled(
                flag = false,
                artistPagesEnabled = true,
                viewerUsername = "gabe",
            ),
        )
        assertTrue(
            ProfileArtistLinkGate.isEnabled(
                flag = false,
                artistPagesEnabled = true,
                viewerUsername = "Gabe",
            ),
        )
        assertTrue(
            ProfileArtistLinkGate.isEnabled(
                flag = false,
                artistPagesEnabled = true,
                viewerUsername = " gabe ",
            ),
        )
    }

    @Test
    fun gabeIsOnEvenWhenArtistPagesAreOff() {
        assertTrue(
            ProfileArtistLinkGate.isEnabled(
                flag = false,
                artistPagesEnabled = false,
                viewerUsername = "gabe",
            ),
        )
    }

    @Test
    fun flagOnRequiresArtistPagesForNonTesters() {
        assertFalse(
            ProfileArtistLinkGate.isEnabled(
                flag = true,
                artistPagesEnabled = false,
                viewerUsername = "emily",
            ),
        )
        assertTrue(
            ProfileArtistLinkGate.isEnabled(
                flag = true,
                artistPagesEnabled = true,
                viewerUsername = "emily",
            ),
        )
    }

    @Test
    fun missingUsernameStaysOffWhenFlagIsOff() {
        assertFalse(
            ProfileArtistLinkGate.isEnabled(
                flag = false,
                artistPagesEnabled = true,
                viewerUsername = null,
            ),
        )
    }
}
