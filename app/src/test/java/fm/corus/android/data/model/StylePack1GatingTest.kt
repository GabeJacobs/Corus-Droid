package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Style Pack 1 rollout — verifies the forward-compat fallback and the
 * `requiresStylePack1` slot infrastructure that gates new colors behind the
 * `style_pack_1_enabled` Remote Config flag.
 *
 * The fallback behavior is the load-bearing piece: when the picker UI on a
 * new client lets the user choose a style-pack-1 color and stores it in
 * Firestore, an old client viewing that profile must silently fall back to
 * the default color rather than crash.
 */
class StylePack1GatingTest {

    // ── Fallback (forward-compat) ──

    @Test fun `unknown vinyl rawValue falls back to BLACK`() {
        assertEquals(VinylStyle.BLACK, VinylStyle.from("style_pack_1_color_that_does_not_exist"))
    }

    @Test fun `null vinyl rawValue falls back to BLACK`() {
        assertEquals(VinylStyle.BLACK, VinylStyle.from(null))
    }

    @Test fun `unknown frame rawValue falls back to BLACK`() {
        assertEquals(FrameStyle.BLACK, FrameStyle.from("style_pack_1_frame_that_does_not_exist"))
    }

    @Test fun `null frame rawValue falls back to BLACK`() {
        assertEquals(FrameStyle.BLACK, FrameStyle.from(null))
    }

    @Test fun `unknown flair rawValue falls back to CHECKMARK`() {
        assertEquals(FlairStyle.CHECKMARK, FlairStyle.from("style_pack_1_flair_that_does_not_exist"))
    }

    @Test fun `null flair rawValue falls back to CHECKMARK`() {
        assertEquals(FlairStyle.CHECKMARK, FlairStyle.from(null))
    }

    // ── requiresStylePack1 slot infrastructure ──
    // No existing colors should require the flag; all currently-shipped
    // colors must remain visible regardless of `style_pack_1_enabled`.

    private val pack1Vinyls = setOf(
        VinylStyle.PINK,
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

    @Test fun `only pack 1 vinyls require style_pack_1`() {
        VinylStyle.entries.forEach { style ->
            if (style in pack1Vinyls) {
                assertTrue(style.requiresStylePack1)
            } else {
                assertFalse(
                    "Existing vinyl ${style.value} must not require style_pack_1",
                    style.requiresStylePack1,
                )
            }
        }
    }

    private val pack1Frames = setOf(FrameStyle.THEATER)

    @Test fun `only pack 1 frames require style_pack_1`() {
        FrameStyle.entries.forEach { style ->
            if (style in pack1Frames) {
                assertTrue(style.requiresStylePack1)
            } else {
                assertFalse(
                    "Existing frame ${style.value} must not require style_pack_1",
                    style.requiresStylePack1,
                )
            }
        }
    }

    @Test fun `no existing flair requires style_pack_1`() {
        FlairStyle.entries.forEach { style ->
            assertFalse(
                "Existing flair ${style.value} must not require style_pack_1",
                style.requiresStylePack1,
            )
        }
    }

    // ── Picker filter logic (mirrors StylePickerSheet's visible-styles filter) ──

    @Test fun `vinyl filter hides pack 1 when flag off`() {
        val pack1On = false
        val visible = VinylStyle.entries.filter { !it.requiresStylePack1 || pack1On }
        pack1Vinyls.forEach { style ->
            assertFalse(visible.contains(style))
        }
        assertEquals(VinylStyle.entries.size - pack1Vinyls.size, visible.size)
    }

    @Test fun `vinyl filter shows pack 1 when flag on`() {
        val pack1On = true
        val visible = VinylStyle.entries.filter { !it.requiresStylePack1 || pack1On }
        pack1Vinyls.forEach { style ->
            assertTrue(visible.contains(style))
        }
        assertEquals(VinylStyle.entries.size, visible.size)
    }

    @Test fun `frame filter hides pack 1 when flag off`() {
        val pack1On = false
        val visible = FrameStyle.entries.filter { !it.requiresStylePack1 || pack1On }
        pack1Frames.forEach { style ->
            assertFalse(visible.contains(style))
        }
        assertEquals(FrameStyle.entries.size - pack1Frames.size, visible.size)
    }

    @Test fun `frame filter shows pack 1 when flag on`() {
        val pack1On = true
        val visible = FrameStyle.entries.filter { !it.requiresStylePack1 || pack1On }
        pack1Frames.forEach { style ->
            assertTrue(visible.contains(style))
        }
        assertEquals(FrameStyle.entries.size, visible.size)
    }

    // corusLogo visibility is an orthogonal gate; hold it open (showCorus=true)
    // to isolate the style_pack_1 filter, matching FlairPickerPage's filter.
    @Test fun `flair filter returns all entries when flag off (no pack 1 flairs yet)`() {
        val pack1On = false
        val showCorus = true
        val visible = FlairStyle.entries.filter { flair ->
            (flair != FlairStyle.CORUS_LOGO || showCorus) && (!flair.requiresStylePack1 || pack1On)
        }
        assertEquals(FlairStyle.entries.size, visible.size)
    }

    @Test fun `flair filter returns all entries when flag on`() {
        val pack1On = true
        val showCorus = true
        val visible = FlairStyle.entries.filter { flair ->
            (flair != FlairStyle.CORUS_LOGO || showCorus) && (!flair.requiresStylePack1 || pack1On)
        }
        assertEquals(FlairStyle.entries.size, visible.size)
    }

}
