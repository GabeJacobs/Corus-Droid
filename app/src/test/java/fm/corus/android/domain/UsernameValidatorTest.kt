package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameValidatorTest {

    @Test fun `empty input is Empty`() {
        assertEquals(UsernameValidator.Result.Empty, UsernameValidator.validate(""))
    }

    @Test fun `single letter is valid`() {
        assertEquals(UsernameValidator.Result.Valid, UsernameValidator.validate("j"))
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

    @Test fun `a single letter among digits is valid`() {
        assertEquals(UsernameValidator.Result.Valid, UsernameValidator.validate("a1"))
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

    @Test fun `clean lowercases and strips invalid chars`() {
        assertEquals("jane.doe", UsernameValidator.clean("Jane.Doe!"))
    }

    @Test fun `clean truncates to max length`() {
        val long = "a".repeat(50)
        assertEquals(20, UsernameValidator.clean(long).length)
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
