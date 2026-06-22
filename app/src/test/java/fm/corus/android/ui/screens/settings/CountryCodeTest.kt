package fm.corus.android.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the phone-auth country picker list ([CountryCode.all]), which powers both
 * sign-up ([fm.corus.android.ui.screens.auth.AuthScreen]) and Settings >
 * Change Phone Number.
 *
 * Regression: a user in Chile could not sign up because the Android list only held
 * 10 countries and omitted Chile (+56), while iOS and Web both offered it. We keep
 * the list in parity with iOS (CountryCode.swift) and Web so no country silently
 * drops off again.
 */
class CountryCodeTest {

    @Test
    fun `Chile is present with the correct dial code`() {
        val chile = CountryCode.all.firstOrNull { it.name == "Chile" }
        assertNotNull("Chile must be selectable in the phone-auth country picker", chile)
        assertEquals("+56", chile!!.dialCode)
    }

    @Test
    fun `list covers the countries iOS and Web already offer`() {
        // Dial codes that previously regressed (only 10 were present on Android).
        val expectedDialCodes = listOf(
            "+1",   // US / Canada
            "+44",  // UK
            "+56",  // Chile  <-- the reported gap
            "+54",  // Argentina
            "+57",  // Colombia
            "+51",  // Peru
            "+52",  // Mexico
            "+55",  // Brazil
        )
        val present = CountryCode.all.map { it.dialCode }.toSet()
        expectedDialCodes.forEach { code ->
            assertTrue("Expected dial code $code to be available", present.contains(code))
        }
    }

    @Test
    fun `every entry is well formed`() {
        assertTrue("List should be comprehensive, not the old 10-country stub", CountryCode.all.size >= 40)
        CountryCode.all.forEach { country ->
            assertTrue("name must not be blank", country.name.isNotBlank())
            assertTrue("flag must not be blank for ${country.name}", country.flag.isNotBlank())
            assertTrue(
                "dialCode for ${country.name} must start with + and have digits, was '${country.dialCode}'",
                country.dialCode.matches(Regex("""^\+\d{1,4}$""")),
            )
        }
    }

    @Test
    fun `country names are unique`() {
        val names = CountryCode.all.map { it.name }
        assertEquals("Duplicate country entries in the picker", names.size, names.toSet().size)
    }

    @Test
    fun `US is the default and leads the list`() {
        assertEquals(CountryCode.US, CountryCode.all.first())
        assertEquals("+1", CountryCode.US.dialCode)
    }
}
