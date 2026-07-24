package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameValidatorTest {

    @Test fun `empty input is Empty`() {
        assertEquals(UsernameValidator.Result.Empty, UsernameValidator.validate(""))
    }

    @Test fun `single letter is too short - neutral`() {
        // Under 3 chars but well-formed: TooShort (neutral), not an error, so the
        // field stays quiet while the user is still typing.
        assertEquals(UsernameValidator.Result.TooShort, UsernameValidator.validate("j"))
    }

    @Test fun `single digit is invalid - needs a letter`() {
        val result = UsernameValidator.validate("7")
        assertTrue(result is UsernameValidator.Result.Invalid)
        assertEquals(
            "Username must contain at least one letter",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `digits-only is invalid - needs a letter`() {
        val result = UsernameValidator.validate("123")
        assertTrue(result is UsernameValidator.Result.Invalid)
        assertEquals(
            "Username must contain at least one letter",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `digits with underscores and periods but no letter is invalid`() {
        val result = UsernameValidator.validate("1_2.3")
        assertTrue(result is UsernameValidator.Result.Invalid)
        assertEquals(
            "Username must contain at least one letter",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `two chars is too short - neutral`() {
        // "a1" has a letter and a valid charset but is under the 3-char minimum.
        assertEquals(UsernameValidator.Result.TooShort, UsernameValidator.validate("a1"))
    }

    // Regression for the 07-24 screenshots: "ry" (2 chars) showed a green check.
    // Now it's TooShort — neutral while typing, neither green nor a red error.
    @Test fun `two-letter name is too short - neutral`() {
        assertEquals(UsernameValidator.Result.TooShort, UsernameValidator.validate("ry"))
    }

    // A too-short name that also breaks a format rule reports the format reason,
    // not TooShort (length isn't the only problem).
    @Test fun `too short but malformed reports the format reason`() {
        assertEquals(
            "Only letters, numbers, underscores, and periods",
            (UsernameValidator.validate("a!") as UsernameValidator.Result.Invalid).message,
        )
        assertEquals(
            "Username must contain at least one letter",
            (UsernameValidator.validate("12") as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `three-char name is valid`() {
        assertEquals(UsernameValidator.Result.Valid, UsernameValidator.validate("rya"))
    }

    @Test fun `min length mirrors the server rule`() {
        // isValidUsername in backend/firestore.rules: u.size() >= 3
        assertEquals(3, UsernameValidator.MIN_LENGTH)
    }

    @Test fun `letters numbers underscores periods are valid`() {
        assertEquals(UsernameValidator.Result.Valid, UsernameValidator.validate("jane.doe_42"))
    }

    @Test fun `single period is invalid with alphanumeric message`() {
        val result = UsernameValidator.validate(".")
        assertTrue(result is UsernameValidator.Result.Invalid)
        assertEquals(
            "Username can't start with a period",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `only periods shows consecutive-periods message first`() {
        val result = UsernameValidator.validate("..")
        assertTrue(result is UsernameValidator.Result.Invalid)
        assertEquals(
            "Username can't have two periods in a row",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `only underscore is invalid - needs a letter`() {
        val result = UsernameValidator.validate("_")
        assertTrue(result is UsernameValidator.Result.Invalid)
        assertEquals(
            "Username must contain at least one letter",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `leading period is invalid`() {
        val result = UsernameValidator.validate(".jane")
        assertEquals(
            "Username can't start with a period",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `trailing period is invalid`() {
        val result = UsernameValidator.validate("jane.")
        assertEquals(
            "Username can't end with a period",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `consecutive periods are invalid`() {
        val result = UsernameValidator.validate("ja..ne")
        assertEquals(
            "Username can't have two periods in a row",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `invalid charset wins over everything`() {
        val result = UsernameValidator.validate("jane!")
        assertEquals(
            "Only letters, numbers, underscores, and periods",
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    // Regression for the 07-24 signup bug (hit on iOS, latent here): validate()
    // had no length rule, so any surface that skipped clean() green-lit an
    // over-long name that the Firestore rules then rejected.
    @Test fun `over-limit name is invalid with the cross-platform copy`() {
        val result = UsernameValidator.validate("a".repeat(26))
        assertTrue(result is UsernameValidator.Result.Invalid)
        assertEquals(
            UsernameValidator.lengthRangeMessage,
            (result as UsernameValidator.Result.Invalid).message,
        )
    }

    @Test fun `name at the 25-char limit is valid`() {
        assertEquals(UsernameValidator.Result.Valid, UsernameValidator.validate("a".repeat(25)))
    }

    @Test fun `21-char name fits under the raised limit`() {
        assertEquals(UsernameValidator.Result.Valid, UsernameValidator.validate("musiccontrolsthemoney"))
    }

    @Test fun `max length mirrors the server rule`() {
        // isValidUsername in backend/firestore.rules: u.size() <= 25
        assertEquals(25, UsernameValidator.MAX_LENGTH)
    }

    @Test fun `clean lowercases and strips invalid chars`() {
        assertEquals("jane.doe", UsernameValidator.clean("Jane.Doe!"))
    }

    @Test fun `clean truncates to max length`() {
        val long = "a".repeat(50)
        assertEquals(25, UsernameValidator.clean(long).length)
    }

    @Test fun `reserved brand and system handles are reserved`() {
        assertTrue(UsernameValidator.isReserved("corus"))
        assertTrue(UsernameValidator.isReserved("corushelp"))
        assertTrue(UsernameValidator.isReserved("admin"))
        assertTrue(UsernameValidator.isReserved("support"))
        assertTrue(UsernameValidator.isReserved("official"))
    }

    @Test fun `isReserved is case-insensitive`() {
        assertTrue(UsernameValidator.isReserved("Corus"))
        assertTrue(UsernameValidator.isReserved("ADMIN"))
    }

    @Test fun `ordinary handles are not reserved`() {
        assertFalse(UsernameValidator.isReserved("jane.doe_42"))
        assertFalse(UsernameValidator.isReserved("coruscant"))
    }

    @Test fun `there are exactly 50 reserved handles`() {
        assertEquals(50, UsernameValidator.RESERVED.size)
    }
}
