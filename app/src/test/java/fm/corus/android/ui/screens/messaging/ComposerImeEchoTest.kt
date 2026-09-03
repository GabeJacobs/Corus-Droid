package fm.corus.android.ui.screens.messaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerImeEchoTest {

    @Test
    fun dropsExactOutgoingString() {
        val sent = "Fighting t go\nField\nJdjdjd"
        assertTrue(dropComposerImeEcho(sent, sent))
    }

    @Test
    fun dropsSubstantialPrefixOfOutgoingString() {
        val sent = "Fighting t go cufjfjt giving rigidities\nIsidore\nField\nCurio\nJdjdjd\nDidjjdjdjd\nJdjdjd"
        val stale = "Fighting t go cufjfjt giving rigidities\nIsidore\nField\nCurio\nJdjdjd"
        assertTrue(dropComposerImeEcho(sent, stale))
    }

    @Test
    fun keepsEmptyIncoming() {
        assertFalse(dropComposerImeEcho("hello", ""))
    }

    @Test
    fun keepsFirstCharacterOfANewMessage() {
        assertFalse(dropComposerImeEcho("Hello there", "H"))
    }

    @Test
    fun keepsUnrelatedNewTyping() {
        assertFalse(dropComposerImeEcho("Hello there", "yo"))
    }

    @Test
    fun nothingToIgnoreWhenSendTokenIsCleared() {
        assertFalse(dropComposerImeEcho(null, "hello"))
        assertFalse(dropComposerImeEcho("", "hello"))
    }
}
