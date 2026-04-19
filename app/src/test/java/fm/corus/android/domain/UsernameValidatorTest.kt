package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameValidatorTest {

    @Test fun `empty input is Empty`() {
        assertEquals(UsernameValidator.Result.Empty, UsernameValidator.validate(""))
    }

    @Test fun `single letter is valid`() {
        assertEquals(UsernameValidator.Result.Valid, UsernameValidator.validate("j"))
    }

    @Test fun `single digit is valid`() {
        assertEquals(UsernameValidator.Result.Valid, UsernameValidator.validate("7"))
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

    @Test fun `only underscore is invalid - needs alphanumeric`() {
        val result = UsernameValidator.validate("_")
        assertTrue(result is UsernameValidator.Result.Invalid)
        assertEquals(
            "Username must contain at least one letter or number",
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
}
