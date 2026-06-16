package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the GOLD vinyl's inner-circle (label) placement to the coordinates
 * measured from the resized `vinyl_gold.png` asset in Figma (canvas origin at
 * the GoldVinyl image bounds; ellipse at X 6607.91 / Y 659.6, 89.29 x 91.25
 * over the 585 x 447 canvas).
 *
 * The gold disc was redrawn smaller, which moved and shrank its center hole.
 * These fractions keep the small reflected album art seated in that hole. If
 * someone swaps the asset again without re-measuring — or reverts to the old
 * 327/149/93/95 values — the inner art drifts off the label and this fails.
 * Mirrors the same constants in iOS VinylStyle.swift and web featured-vinyl.tsx.
 */
class VinylGoldLabelGeometryTest {

    private val delta = 1e-4f

    @Test fun `gold label X fraction matches resized asset`() {
        assertEquals(319.91f / 585f, VinylStyle.GOLD.labelXFrac, delta)
    }

    @Test fun `gold label Y fraction matches resized asset`() {
        assertEquals(151.6f / 447f, VinylStyle.GOLD.labelYFrac, delta)
    }

    @Test fun `gold label width fraction matches resized asset`() {
        assertEquals(89.29f / 585f, VinylStyle.GOLD.labelWFrac, delta)
    }

    @Test fun `gold label height fraction matches resized asset`() {
        assertEquals(91.25f / 447f, VinylStyle.GOLD.labelHFrac, delta)
    }
}
