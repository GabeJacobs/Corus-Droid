package fm.corus.android.ui.screens.live

import org.junit.Assert.*
import org.junit.Test

class LivePrivacyTest {
    @Test fun publicByDefaultWithNotifications() {
        val setup = LiveSetup("draft")
        assertFalse(setup.isPrivate)
        assertTrue(setup.notifyFollowers)
    }

    @Test fun privateStartsSilentButHostCanNotifyFollowersExplicitly() {
        val privateRoom = LiveSetup("draft").withPrivacy(true)
        assertTrue(privateRoom.isPrivate)
        assertFalse(privateRoom.notifyFollowers)
        val withInvitations = privateRoom.copy(notifyFollowers = true)
        assertTrue(withInvitations.withPrivacy(true).notifyFollowers)
        assertTrue(withInvitations.withPrivacy(false).notifyFollowers)
    }

    @Test fun returningToPublicRestoresItsOwnNotificationChoice() {
        val setup = LiveSetup("draft", notifyFollowers = false)
        assertFalse(setup.withPrivacy(true).copy(notifyFollowers = true).withPrivacy(false).notifyFollowers)
        assertTrue(LiveSetup("new-draft").notifyFollowers)
        assertFalse(LiveSetup("new-draft").isPrivate)
    }
}
