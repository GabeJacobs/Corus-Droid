package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.FlairStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleSelectionsFlairPaywallTest {

    @Test
    fun `default draft is None and is not premium`() {
        val defaults = StyleSelections()
        assertTrue(defaults.profileFlair == FlairStyle.NONE)
        assertFalse(defaults.hasNonDefaultValues)
        assertFalse(defaults.introducesPremiumValue(StyleSelections()))
    }

    @Test
    fun `picking Checkmark from None is a premium change`() {
        val from = StyleSelections(profileFlair = FlairStyle.NONE)
        val to = from.copy(profileFlair = FlairStyle.CHECKMARK)
        assertTrue(to.hasNonDefaultValues)
        assertTrue(to.introducesPremiumValue(from))
    }

    @Test
    fun `picking Vinyl from None is a premium change`() {
        val from = StyleSelections(profileFlair = FlairStyle.NONE)
        val to = from.copy(profileFlair = FlairStyle.VINYL)
        assertTrue(to.introducesPremiumValue(from))
    }

    @Test
    fun `reverting from Checkmark to None is not premium`() {
        val from = StyleSelections(profileFlair = FlairStyle.CHECKMARK)
        val to = from.copy(profileFlair = FlairStyle.NONE)
        assertFalse(to.hasNonDefaultValues)
        assertFalse(to.introducesPremiumValue(from))
    }
}
