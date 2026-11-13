package fm.corus.android.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveEffectSelectionTest {
    @Test
    fun tapShowsEffectBeforeSaveOrRoomUpdate() {
        val selection = LiveEffectSelection().toggle("rain", observed = "off")
        assertEquals("rain", selection.visible(observed = "off"))
        assertEquals("off", selection.toggle("rain", observed = "off").visible(observed = "rain"))
    }

    @Test
    fun olderSaveDoesNotReplaceNewerTap() {
        val selection = LiveEffectSelection().toggle("rain", "off").toggle("snow", "off")
        val rainSaved = selection.saved("rain", observed = "off")
        assertEquals("snow", rainSaved.visible(observed = "off"))
        assertEquals("snow", rainSaved.observe("rain").visible(observed = "rain"))
    }

    @Test
    fun effectStaysVisibleBetweenSaveAndSnapshot() {
        val saved = LiveEffectSelection().toggle("rain", "off").saved("rain", observed = "off")
        assertEquals("rain", saved.visible(observed = "off"))
        assertNull(saved.observe("rain").awaitingSnapshot)
        assertNull(LiveEffectSelection().toggle("rain", "off").saved("rain", "rain").awaitingSnapshot)
    }

    @Test
    fun failedSaveRevertsToLastSuccessfulEffectAndKeepsNewerTap() {
        val saved = LiveEffectSelection().toggle("rain", "off").saved("rain", observed = "off")
        val snow = saved.toggle("snow", "off")
        assertEquals("rain", snow.finished("snow").visible(observed = "off"))
        assertEquals("party", snow.toggle("party", "off").finished("snow").visible(observed = "off"))
    }
}
