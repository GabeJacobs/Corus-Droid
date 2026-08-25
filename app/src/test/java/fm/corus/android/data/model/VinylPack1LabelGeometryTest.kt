package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pack-1 vinyls share the 582 x 440 Updated Screen canvas. Label slots are
 * pinned to Figma InnerCircle inspect numbers where we have them.
 */
class VinylPack1LabelGeometryTest {

    private val delta = 1e-4f
    private val canvas440 = listOf(
        VinylStyle.ORANGE,
        VinylStyle.YELLOW,
        VinylStyle.PINK_MATTE,
        VinylStyle.LIME,
        VinylStyle.PURPLE_TIE_DYE,
        VinylStyle.BLUE_TIE_DYE,
        VinylStyle.ORANGE_TIE_DYE,
        VinylStyle.ICY_BLUE,
        VinylStyle.GALAXY,
        VinylStyle.PEACH,
        VinylStyle.LAVENDER,
        VinylStyle.BLOOD_RED,
    )

    @Test fun `pack 1 extras use the 582 x 440 canvas`() {
        canvas440.forEach { style ->
            assertEquals(440f / 582f, style.canvasRatio, delta)
        }
    }

    @Test fun `lime label matches Figma InnerCircle`() {
        assertEquals(317f / 582f, VinylStyle.LIME.labelXFrac, delta)
        assertEquals(146f / 440f, VinylStyle.LIME.labelYFrac, delta)
        assertEquals(95f / 582f, VinylStyle.LIME.labelWFrac, delta)
        assertEquals(97f / 440f, VinylStyle.LIME.labelHFrac, delta)
    }

    @Test fun `blue tie dye label matches Figma InnerCircle`() {
        assertEquals(318f / 582f, VinylStyle.BLUE_TIE_DYE.labelXFrac, delta)
        assertEquals(150f / 440f, VinylStyle.BLUE_TIE_DYE.labelYFrac, delta)
        assertEquals(91f / 582f, VinylStyle.BLUE_TIE_DYE.labelWFrac, delta)
        assertEquals(92f / 440f, VinylStyle.BLUE_TIE_DYE.labelHFrac, delta)
        assertEquals(57f / 440f, VinylStyle.BLUE_TIE_DYE.artYFrac, delta)
    }

    @Test fun `peach label matches Figma InnerCircle`() {
        assertEquals(321f / 582f, VinylStyle.PEACH.labelXFrac, delta)
        assertEquals(151f / 440f, VinylStyle.PEACH.labelYFrac, delta)
        assertEquals(85f / 582f, VinylStyle.PEACH.labelWFrac, delta)
        assertEquals(86f / 440f, VinylStyle.PEACH.labelHFrac, delta)
        assertEquals(57f / 440f, VinylStyle.PEACH.artYFrac, delta)
    }

    @Test fun `icy blue label covers the printed center`() {
        assertEquals(321f / 582f, VinylStyle.ICY_BLUE.labelXFrac, delta)
        assertEquals(150f / 440f, VinylStyle.ICY_BLUE.labelYFrac, delta)
        assertEquals(88f / 582f, VinylStyle.ICY_BLUE.labelWFrac, delta)
        assertEquals(89f / 440f, VinylStyle.ICY_BLUE.labelHFrac, delta)
        assertEquals(57f / 440f, VinylStyle.ICY_BLUE.artYFrac, delta)
    }

    @Test fun `orange tie dye label covers the printed center`() {
        assertEquals(324f / 582f, VinylStyle.ORANGE_TIE_DYE.labelXFrac, delta)
        assertEquals(150f / 440f, VinylStyle.ORANGE_TIE_DYE.labelYFrac, delta)
        assertEquals(89f / 582f, VinylStyle.ORANGE_TIE_DYE.labelWFrac, delta)
        assertEquals(90f / 440f, VinylStyle.ORANGE_TIE_DYE.labelHFrac, delta)
        assertEquals(57f / 440f, VinylStyle.ORANGE_TIE_DYE.artYFrac, delta)
    }

    @Test fun `tie dye label matches Figma InnerCircle`() {
        assertEquals(325f / 582f, VinylStyle.PURPLE_TIE_DYE.labelXFrac, delta)
        assertEquals(147f / 440f, VinylStyle.PURPLE_TIE_DYE.labelYFrac, delta)
        assertEquals(85f / 582f, VinylStyle.PURPLE_TIE_DYE.labelWFrac, delta)
        assertEquals(86f / 440f, VinylStyle.PURPLE_TIE_DYE.labelHFrac, delta)
        assertEquals(57f / 440f, VinylStyle.PURPLE_TIE_DYE.artYFrac, delta)
    }

    @Test fun `orange label matches Figma InnerCircle`() {
        assertEquals(326f / 582f, VinylStyle.ORANGE.labelXFrac, delta)
        assertEquals(152f / 440f, VinylStyle.ORANGE.labelYFrac, delta)
        assertEquals(82f / 582f, VinylStyle.ORANGE.labelWFrac, delta)
        assertEquals(83f / 440f, VinylStyle.ORANGE.labelHFrac, delta)
        assertEquals(57f / 440f, VinylStyle.ORANGE.artYFrac, delta)
    }

    @Test fun `yellow label matches Figma InnerCircle`() {
        assertEquals(324f / 582f, VinylStyle.YELLOW.labelXFrac, delta)
        assertEquals(151f / 440f, VinylStyle.YELLOW.labelYFrac, delta)
        assertEquals(85f / 582f, VinylStyle.YELLOW.labelWFrac, delta)
        assertEquals(87f / 440f, VinylStyle.YELLOW.labelHFrac, delta)
        assertEquals(57f / 440f, VinylStyle.YELLOW.artYFrac, delta)
    }

    @Test fun `pink matte label matches Figma InnerCircle`() {
        assertEquals(319f / 582f, VinylStyle.PINK_MATTE.labelXFrac, delta)
        assertEquals(147f / 440f, VinylStyle.PINK_MATTE.labelYFrac, delta)
        assertEquals(91f / 582f, VinylStyle.PINK_MATTE.labelWFrac, delta)
        assertEquals(93f / 440f, VinylStyle.PINK_MATTE.labelHFrac, delta)
        assertEquals(57f / 440f, VinylStyle.PINK_MATTE.artYFrac, delta)
    }

    @Test fun `pack 1 album art matches Figma Big album Art`() {
        val artY58 = canvas440.filter {
            it != VinylStyle.PURPLE_TIE_DYE && it != VinylStyle.BLUE_TIE_DYE && it != VinylStyle.ORANGE_TIE_DYE && it != VinylStyle.ICY_BLUE && it != VinylStyle.GALAXY && it != VinylStyle.PEACH && it != VinylStyle.LAVENDER && it != VinylStyle.YELLOW && it != VinylStyle.BLOOD_RED && it != VinylStyle.PINK_MATTE && it != VinylStyle.ORANGE
        }
        artY58.forEach { style ->
            assertEquals(105f / 582f, style.artXFrac, delta)
            assertEquals(58f / 440f, style.artYFrac, delta)
            assertEquals(270f / 582f, style.artSizeFrac, delta)
        }
    }
}
