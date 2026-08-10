package fm.corus.android.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayerArtworkBackdropTest {

    @Test
    fun `backdrop identity includes art url and color scheme`() {
        assertEquals(
            "https://img/a.jpg|d",
            backdropIdentity("https://img/a.jpg", darkTheme = true),
        )
        assertEquals(
            "https://img/a.jpg|l",
            backdropIdentity("https://img/a.jpg", darkTheme = false),
        )
    }

    @Test
    fun `same art url in dark vs light is a different identity so wash rebakes`() {
        // iOS task id is "url|d" / "url|l" — scheme changes must not reuse the
        // other mode's frost/tint commit.
        assertNotEquals(
            backdropIdentity("https://img/a.jpg", darkTheme = true),
            backdropIdentity("https://img/a.jpg", darkTheme = false),
        )
    }

    @Test
    fun `nil art still has a stable identity`() {
        assertEquals("nil|d", backdropIdentity(null, darkTheme = true))
    }
}
