package fm.corus.android.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for the bio length cap applied as the user edits their profile.
 * New bios are capped at [EditProfileViewModel.BIO_MAX_LENGTH] (500), while an
 * existing longer bio is grandfathered: its current length becomes the ceiling
 * so the editor never force-truncates it, and the user can't grow it further.
 */
class BioLengthCapTest {

    private fun cap(value: String, originalBio: String = "") =
        EditProfileViewModel.capBio(value, originalBio)

    @Test
    fun `cap is 400`() {
        assertEquals(400, EditProfileViewModel.BIO_MAX_LENGTH)
    }

    @Test
    fun `short bio is unchanged`() {
        val v = "Making music for the fun of it."
        assertEquals(v, cap(v))
    }

    @Test
    fun `bio exactly at the cap is unchanged`() {
        val v = "a".repeat(400)
        assertEquals(v, cap(v))
        assertEquals(400, cap(v).length)
    }

    @Test
    fun `new bio over the cap is truncated to 400`() {
        val v = "a".repeat(800)
        assertEquals(400, cap(v).length)
    }

    @Test
    fun `empty value stays empty`() {
        assertEquals("", cap(""))
    }

    // --- Grandfathering: existing bio longer than the cap ---

    @Test
    fun `existing long bio is preserved as the ceiling, not truncated`() {
        val existing = "a".repeat(700)
        assertEquals(existing, cap(existing, originalBio = existing))
    }

    @Test
    fun `grandfathered bio cannot grow beyond its existing length`() {
        val existing = "a".repeat(700)
        val grown = existing + "bbbbb"
        assertEquals(700, cap(grown, originalBio = existing).length)
    }

    @Test
    fun `grandfathered user editing below their length is unchanged`() {
        val existing = "a".repeat(700)
        val trimmed = "a".repeat(400)
        assertEquals(trimmed, cap(trimmed, originalBio = existing))
    }
}
