package fm.corus.android.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExpansionHitTestingTest {

    private val travel = 700f

    @Test
    fun collapsedMiniReceivesHitsAndFullDoesNotStackAbove() {
        val expansion = 0f
        assertTrue(miniPlayerInteractive(allowsMiniInteraction = true, expansion = expansion, travelPx = travel))
        assertFalse(fullPlayerInteractive(expansion))
        assertFalse(fullPlayerLayerAboveMini(expansion))
    }

    @Test
    fun fullyExpandedFullReceivesHitsAndStacksAboveMini() {
        val expansion = 1f
        assertTrue(fullPlayerInteractive(expansion))
        assertTrue(fullPlayerLayerAboveMini(expansion))
        assertFalse(miniPlayerInteractive(allowsMiniInteraction = true, expansion = expansion, travelPx = travel))
        assertTrue(miniOpacity(expansion, travel) <= 0.05f)
    }

    @Test
    fun expandedMiniLayerDoesNotStealHitsEvenIfHostClaimsMiniAllowed() {
        val expansion = 0.98f
        assertFalse(miniPlayerInteractive(allowsMiniInteraction = true, expansion = expansion, travelPx = travel))
        assertTrue(fullPlayerLayerAboveMini(expansion))
    }

    @Test
    fun midExpandBeforeFullInteractiveKeepsFullBelowMini() {
        val expansion = 0.4f
        assertFalse(fullPlayerInteractive(expansion))
        assertFalse(fullPlayerLayerAboveMini(expansion))
        assertFalse(miniPlayerInteractive(allowsMiniInteraction = false, expansion = expansion, travelPx = travel))
    }

    @Test
    fun crossingInteractiveThresholdPutsFullAboveMini() {
        assertFalse(fullPlayerLayerAboveMini(0.55f))
        assertTrue(fullPlayerLayerAboveMini(0.55001f))
        assertTrue(fullPlayerInteractive(0.56f))
    }

    @Test
    fun midCollapseDragKeepsFullInteractiveSoScrollHandoffSurvivesHalfway() {
        // Regression: interactive used to flip off at expansion 0.55 (~45% drag),
        // killing nested-scroll before the collapse settle threshold — sheet
        // sprang back open on slow dismisses.
        assertTrue(fullPlayerInteractive(expansion = 0.4f, isMoving = true))
        assertTrue(fullPlayerLayerAboveMini(expansion = 0.4f, isMoving = true))
        assertFalse(fullPlayerInteractive(expansion = 0.4f, isMoving = false))
    }

    @Test
    fun fullOpacityKeepsChromeVisibleUntilNearCollapsedPark() {
        assertEquals(1f, fullOpacity(1f), 0.001f)
        assertEquals(1f, fullOpacity(0.55f), 0.001f)
        // Mid-dismiss (~halfway down) used to be ~0.44 / already gone by 0.20.
        assertEquals(0.70f, fullOpacity(0.40f), 0.001f)
        assertEquals(0.30f, fullOpacity(0.20f), 0.001f)
        assertEquals(0f, fullOpacity(0.05f), 0.001f)
        assertEquals(0f, fullOpacity(0f), 0.001f)
    }

    @Test
    fun movingHostDisablesMiniHitsWhileCollapsedThresholdWouldOtherwiseAllow() {
        assertFalse(
            miniPlayerInteractive(
                allowsMiniInteraction = false,
                expansion = 0f,
                travelPx = travel,
            ),
        )
    }
}
