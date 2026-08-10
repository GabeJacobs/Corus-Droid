package fm.corus.android.ui.player

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
