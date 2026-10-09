package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileHeaderStylePolicyTest {
    @Test fun `shortcut requires rollout collection and an eligible owner`() {
        assertTrue(ProfileHeaderStylePolicy.visible(true, true, true))
        assertFalse(ProfileHeaderStylePolicy.visible(false, true, true))
        assertFalse(ProfileHeaderStylePolicy.visible(true, false, true))
        assertFalse(ProfileHeaderStylePolicy.visible(true, true, false))
    }
}
