package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayNameValidatorTest {

    @Test fun `empty string has no visible character`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter(""))
    }

    @Test fun `plain ascii name is visible`() {
        assertTrue(DisplayNameValidator.hasVisibleCharacter("Alma"))
    }

    @Test fun `single letter is visible`() {
        assertTrue(DisplayNameValidator.hasVisibleCharacter("a"))
    }

    @Test fun `whitespace only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("   "))
    }

    @Test fun `tab and newline only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("\t\n"))
    }

    @Test fun `non-breaking space only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter(" "))
    }

    @Test fun `LRM only is not visible (alma case)`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("‎"))
    }

    @Test fun `RLM only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("‏"))
    }

    @Test fun `zero-width space only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("​"))
    }

    @Test fun `zero-width joiner only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("‍"))
    }

    @Test fun `BOM only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("﻿"))
    }

    @Test fun `bidi embedding only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("‪‫‬"))
    }

    @Test fun `bidi isolate only is not visible`() {
        assertFalse(DisplayNameValidator.hasVisibleCharacter("⁦⁩"))
    }

    @Test fun `mixed visible plus invisible is visible`() {
        assertTrue(DisplayNameValidator.hasVisibleCharacter("Alma‎"))
    }

    @Test fun `arabic with embedded LRM is visible`() {
        assertTrue(DisplayNameValidator.hasVisibleCharacter("‎علي"))
    }

    @Test fun `emoji only is visible`() {
        assertTrue(DisplayNameValidator.hasVisibleCharacter("🎵"))
    }
}
