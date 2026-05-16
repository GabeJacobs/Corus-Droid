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

    // ── requiresStylePack1 slot infrastructure ──
    // No existing colors should require the flag; all currently-shipped
    // colors must remain visible regardless of `style_pack_1_enabled`.

    @Test fun `no existing vinyl color requires style_pack_1`() {
        VinylStyle.entries.forEach { style ->
            assertFalse(
                "Existing vinyl ${style.value} must not require style_pack_1",
                style.requiresStylePack1,
            )
        }
    }

    @Test fun `no existing frame color requires style_pack_1`() {
        FrameStyle.entries.forEach { style ->
            assertFalse(
                "Existing frame ${style.value} must not require style_pack_1",
                style.requiresStylePack1,
            )
        }
    }

    // ── Picker filter logic (mirrors StylePickerSheet's visible-styles filter) ──

    @Test fun `vinyl filter returns all entries when flag off (no pack 1 colors yet)`() {
        val pack1On = false
        val visible = VinylStyle.entries.filter { !it.requiresStylePack1 || pack1On }
        assertEquals(VinylStyle.entries.size, visible.size)
    }

    @Test fun `vinyl filter returns all entries when flag on`() {
        val pack1On = true
        val visible = VinylStyle.entries.filter { !it.requiresStylePack1 || pack1On }
        assertEquals(VinylStyle.entries.size, visible.size)
    }

    @Test fun `frame filter returns all entries when flag off (no pack 1 frames yet)`() {
        val pack1On = false
        val visible = FrameStyle.entries.filter { !it.requiresStylePack1 || pack1On }
        assertEquals(FrameStyle.entries.size, visible.size)
    }

}
