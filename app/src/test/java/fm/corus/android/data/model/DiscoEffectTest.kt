package fm.corus.android.data.model

import fm.corus.android.ui.components.DiscoRandom
import fm.corus.android.ui.components.DiscoVec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * Lights overlay: `style_pack_1_enabled` gating, forward-compat fallback, and
 * the mirror-ball geometry the renderer is built on. Mirrors iOS DiscoEffectTests.
 */
class DiscoEffectTest {

    @Test
    fun pageHiddenWhenPackOffAndNothingSaved() {
        assertFalse(DiscoEffectGate.isPageVisible(stylePack1Enabled = false, saved = DiscoIntensity.OFF))
    }

    @Test
    fun pageShownWhenPackOn() {
        for (saved in DiscoIntensity.entries) {
            assertTrue(DiscoEffectGate.isPageVisible(stylePack1Enabled = true, saved = saved))
        }
    }

    @Test
    fun pageStaysReachableForExistingSelectionWhenPackOff() {
        for (saved in DiscoIntensity.entries) {
            if (saved == DiscoIntensity.OFF) continue
            assertTrue(DiscoEffectGate.isPageVisible(stylePack1Enabled = false, saved = saved))
        }
    }

    @Test
    fun unknownDiscoRawValueCoalescesToOff() {
        assertEquals(DiscoIntensity.OFF, DiscoIntensity.resolved("fog"))
    }

    @Test
    fun judgingRenamesKeepTheLook() {
        assertEquals(DiscoIntensity.DANCE_PARTY, DiscoIntensity.resolved("prism"))
        assertEquals(DiscoIntensity.SPOTIFLIGHT, DiscoIntensity.resolved("searchlight"))
        assertEquals(DiscoIntensity.DANCE_PARTY, DiscoIntensity.resolved("disco"))
        assertEquals(DiscoIntensity.DANCE_PARTY, DiscoIntensity.resolved("strobe"))
        assertEquals(DiscoIntensity.DISCO_BALL, DiscoIntensity.resolved("heavy"))
        assertEquals(DiscoIntensity.OFF, DiscoIntensity.resolved("candlelight"))
        assertEquals(DiscoIntensity.OFF, DiscoIntensity.resolved("bonfire"))
    }

    @Test
    fun rawValuesAreStableAcrossPlatforms() {
        assertEquals("off", DiscoIntensity.OFF.value)
        assertEquals("danceParty", DiscoIntensity.DANCE_PARTY.value)
        assertEquals("light", DiscoIntensity.LIGHT.value)
        assertEquals("discoBall", DiscoIntensity.DISCO_BALL.value)
        assertEquals("spotiflight", DiscoIntensity.SPOTIFLIGHT.value)
    }

    @Test
    fun pickerOrderAndLabels() {
        assertEquals(
            listOf("Off", "Slow Dance", "Disco Ball", "Dance Party", "Spotiflight"),
            DiscoIntensity.entries.map { it.displayName },
        )
    }

    @Test
    fun offDrawsNothing() {
        assertEquals(0.0, DiscoIntensity.OFF.rotationSpeed, 0.0)
        assertEquals(0, DiscoIntensity.OFF.facetRows)
        assertEquals(0, DiscoIntensity.OFF.equatorFacetCount)
        assertEquals(0f, DiscoIntensity.OFF.scrimOpacity, 0f)
        assertEquals(0.0, DiscoIntensity.OFF.spillStrength, 0.0)
        assertEquals(0.0, DiscoIntensity.OFF.beamShare, 0.0)
    }

    @Test
    fun discoBallCoversMoreThanLight() {
        assertTrue(DiscoIntensity.DISCO_BALL.scrimOpacity > DiscoIntensity.LIGHT.scrimOpacity)
        assertTrue(DiscoIntensity.DISCO_BALL.spillStrength > DiscoIntensity.LIGHT.spillStrength)
        assertTrue(DiscoIntensity.DISCO_BALL.equatorFacetCount > DiscoIntensity.LIGHT.equatorFacetCount)
        assertTrue(DiscoIntensity.DISCO_BALL.facetRows > DiscoIntensity.LIGHT.facetRows)
        assertTrue(DiscoIntensity.DISCO_BALL.beamShare > DiscoIntensity.LIGHT.beamShare)
        assertTrue(DiscoIntensity.LIGHT.beamShare > 0)
    }

    @Test
    fun tileLooksKeepBeamsAsAMinority() {
        for (intensity in listOf(DiscoIntensity.DANCE_PARTY, DiscoIntensity.LIGHT, DiscoIntensity.DISCO_BALL)) {
            assertTrue(intensity.beamShare > 0 && intensity.beamShare < 0.35)
        }
        assertEquals(0.0, DiscoIntensity.SPOTIFLIGHT.beamShare, 0.0)
    }

    @Test
    fun discoBallMovesFasterThanSlowDance() {
        assertTrue(DiscoIntensity.DISCO_BALL.rotationSpeed > DiscoIntensity.LIGHT.rotationSpeed)
    }

    @Test
    fun silverLooksStayUncolored() {
        assertFalse(DiscoIntensity.LIGHT.usesColoredSpotlights)
        assertFalse(DiscoIntensity.SPOTIFLIGHT.usesColoredSpotlights)
        assertTrue(DiscoIntensity.DANCE_PARTY.usesColoredSpotlights)
    }

    @Test
    fun dancePartyHasShaftsAtItsOwnSpeed() {
        assertTrue(DiscoIntensity.DANCE_PARTY.beamShare > 0)
        assertTrue(DiscoIntensity.DISCO_BALL.beamShare > DiscoIntensity.LIGHT.beamShare)
        assertTrue(DiscoIntensity.DANCE_PARTY.rotationSpeed < DiscoIntensity.DISCO_BALL.rotationSpeed)
    }

    @Test
    fun pulsesStayChill() {
        for (intensity in DiscoIntensity.entries) {
            assertTrue(
                "${intensity.value} pulses at ${intensity.pulseRate} Hz",
                intensity.pulseRate < 0.5,
            )
        }
    }

    @Test
    fun rotationSpeedStaysSlowOnASmallCard() {
        for (intensity in DiscoIntensity.entries) {
            if (intensity == DiscoIntensity.OFF || intensity.rotationSpeed <= 0) continue
            val rpm = intensity.rotationSpeed * 60 / (2 * PI)
            assertTrue(
                "${intensity.value} spins at $rpm rpm",
                rpm > 0.2 && rpm < 1.5,
            )
        }
    }

    @Test
    fun keptLooksStayDistinct() {
        assertEquals(DiscoPalette.RAINBOW, DiscoIntensity.DANCE_PARTY.palette)
        assertTrue(DiscoIntensity.DANCE_PARTY.scrimOpacity >= 0.12f)
        assertTrue(DiscoIntensity.SPOTIFLIGHT.hasSearchlights)
        assertEquals(DiscoMark.NONE, DiscoIntensity.SPOTIFLIGHT.mark)
    }

    @Test
    fun normalizedVectorIsUnitLength() {
        val v = DiscoVec3(-0.48, -0.70, -1.0).normalized
        assertTrue(abs(v.length - 1) < 1e-12)
    }

    @Test
    fun zeroVectorNormalizesWithoutDividingByZero() {
        val v = DiscoVec3(0.0, 0.0, 0.0).normalized
        assertEquals(0.0, v.length, 0.0)
    }

    @Test
    fun headOnReflectionReversesTheRay() {
        val normal = DiscoVec3(0.0, 0.0, 1.0)
        val incoming = DiscoVec3(0.0, 0.0, -1.0)
        val reflected = incoming - normal * (2 * incoming.dot(normal))
        assertTrue(abs(reflected.z - 1) < 1e-12)
        assertTrue(abs(reflected.x) < 1e-12)
        assertTrue(abs(reflected.y) < 1e-12)
    }

    @Test
    fun fortyFiveDegreeTileTurnsTheRaySideways() {
        val normal = DiscoVec3(1.0, 0.0, 1.0).normalized
        val incoming = DiscoVec3(0.0, 0.0, -1.0)
        val reflected = incoming - normal * (2 * incoming.dot(normal))
        assertTrue(abs(reflected.x - 1) < 1e-9)
        assertTrue(abs(reflected.z) < 1e-9)
        assertTrue(abs(reflected.y) < 1e-9)
    }

    @Test
    fun reflectionPreservesLength() {
        val normal = DiscoVec3(0.3, 0.8, 0.5).normalized
        val incoming = DiscoVec3(-0.4, -0.6, -1.0).normalized
        val reflected = incoming - normal * (2 * incoming.dot(normal))
        assertTrue(abs(reflected.length - 1) < 1e-9)
    }

    @Test
    fun seededGeneratorIsReproducible() {
        val a = DiscoRandom(seed = 0x0D15C0BA11uL)
        val b = DiscoRandom(seed = 0x0D15C0BA11uL)
        repeat(8) {
            assertEquals(a.next(), b.next())
        }
    }

    @Test
    fun differentSeedsDiverge() {
        val a = DiscoRandom(seed = 1uL)
        val b = DiscoRandom(seed = 2uL)
        assertTrue(a.next() != b.next())
    }
}
