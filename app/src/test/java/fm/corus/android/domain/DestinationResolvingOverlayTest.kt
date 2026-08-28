package fm.corus.android.domain

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationResolvingOverlayTest {

    @After
    fun tearDown() {
        DestinationResolvingOverlay.setResolving(false)
    }

    @Test
    fun `setResolving drives the chrome overlay flag`() {
        assertFalse(DestinationResolvingOverlay.isResolving.value)
        DestinationResolvingOverlay.setResolving(true)
        assertTrue(DestinationResolvingOverlay.isResolving.value)
        DestinationResolvingOverlay.setResolving(false)
        assertFalse(DestinationResolvingOverlay.isResolving.value)
    }
}
