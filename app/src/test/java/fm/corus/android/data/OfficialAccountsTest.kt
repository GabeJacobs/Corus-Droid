package fm.corus.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OfficialAccounts.isOfficial] gates the Block/Mute affordances: the official
 * @corusteam account can never be blocked or muted (also enforced server-side
 * in firestore.rules + the blockUser/muteUser callables).
 */
class OfficialAccountsTest {

    @Test
    fun `corusteam uid is official`() {
        assertTrue(OfficialAccounts.isOfficial(OfficialAccounts.CORUS_TEAM_UID))
        assertTrue(OfficialAccounts.isOfficial("v9s6F4HKLLbEmfX3Tc6MX3lHfpl2"))
    }

    @Test
    fun `other uids are not official`() {
        assertFalse(OfficialAccounts.isOfficial("someRandomUid"))
        assertFalse(OfficialAccounts.isOfficial(""))
        assertFalse(OfficialAccounts.isOfficial(null))
    }
}
