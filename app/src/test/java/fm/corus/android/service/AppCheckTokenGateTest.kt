package fm.corus.android.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The signup gate must not send a backend request when App Check has no
 * token. That is the production failure we saw: sendEmailOtpCode left
 * without a header and the backend returned 401 App Check MISSING.
 */
class AppCheckTokenGateTest {

    @Test
    fun `runs block when token is ready`() = runBlocking {
        var ran = false
        val result = withAppCheckToken({ true }) {
            ran = true
            42
        }
        assertTrue(ran)
        assertEquals(42, result)
    }

    @Test
    fun `does not run block when token is missing`() = runBlocking {
        var ran = false
        try {
            withAppCheckToken({ false }) { ran = true }
            fail("expected AppCheckUnavailableException")
        } catch (_: AppCheckUnavailableException) {
            assertFalse(ran)
        }
    }

    @Test
    fun `propagates block exception after token succeeds`() = runBlocking {
        try {
            withAppCheckToken({ true }) { error("backend") }
            fail("expected backend exception")
        } catch (e: IllegalStateException) {
            assertEquals("backend", e.message)
        }
    }
}
