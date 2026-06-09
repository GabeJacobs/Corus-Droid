package fm.corus.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceModeTest {
    @Test
    fun `declaration order drives settings order System Light Dark`() {
        assertEquals(
            listOf(AppearanceMode.SYSTEM, AppearanceMode.LIGHT, AppearanceMode.DARK),
            AppearanceMode.values().toList(),
        )
    }

    @Test
    fun `default mode is system when storage value is missing`() {
        assertEquals(AppearanceMode.SYSTEM, "".toAppearanceMode())
    }

    @Test
    fun `unknown stored value falls back to system`() {
        assertEquals(AppearanceMode.SYSTEM, "midnight".toAppearanceMode())
    }

    @Test
    fun `system value parses to SYSTEM`() {
        assertEquals(AppearanceMode.SYSTEM, "system".toAppearanceMode())
    }

    @Test
    fun `dark value parses to DARK`() {
        assertEquals(AppearanceMode.DARK, "dark".toAppearanceMode())
    }

    @Test
    fun `light value parses to LIGHT`() {
        assertEquals(AppearanceMode.LIGHT, "light".toAppearanceMode())
    }

    @Test
    fun `storage values round-trip for every mode`() {
        AppearanceMode.values().forEach { mode ->
            assertEquals(mode, mode.storageValue.toAppearanceMode())
        }
    }
}
