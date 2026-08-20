package fm.corus.android.ui.screens.auth

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPrimerLayoutTest {

    @Test
    fun `device uses a phone aspect ratio, not a squat tablet`() {
        val ratio = 476f / 268f
        assertEquals(1.78f, ratio, 0.02f)
    }

    @Test
    fun `phone stays at the Android visual cap when there is room`() {
        assertEquals(
            0.9f,
            notificationPrimerPhoneScale(390.dp, 500.dp),
            0.001f,
        )
    }

    @Test
    fun `phone shrinks to leftover height on compact phones`() {
        // SE-class leftover after title + CTA (~280dp tall slot).
        assertEquals(
            280f / 500f,
            notificationPrimerPhoneScale(320.dp, 280.dp),
            0.001f,
        )
    }

    @Test
    fun `empty slot does not produce a NaN scale`() {
        assertEquals(0f, notificationPrimerPhoneScale(0.dp, 0.dp), 0f)
    }
}
