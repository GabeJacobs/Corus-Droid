package fm.corus.android.ui.screens.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AlbumArtHintCohortTest {

    private fun epochMs(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `accounts created before cutoff are not in the hint cohort`() {
        assertFalse(isNewAlbumArtHintAccount(epochMs(2026, 5, 9)))
        assertFalse(isNewAlbumArtHintAccount(epochMs(2024, 1, 1)))
    }

    @Test
    fun `accounts created on cutoff are in the hint cohort`() {
        assertTrue(isNewAlbumArtHintAccount(ALBUM_ART_HINT_CUTOFF_MS))
        assertTrue(isNewAlbumArtHintAccount(epochMs(2026, 5, 10)))
    }

    @Test
    fun `accounts created after cutoff are in the hint cohort`() {
        assertTrue(isNewAlbumArtHintAccount(epochMs(2026, 5, 11)))
        assertTrue(isNewAlbumArtHintAccount(epochMs(2027, 1, 1)))
    }

    @Test
    fun `missing creation timestamp is treated as not in the hint cohort`() {
        // Avoids a flash of the hint while auth metadata is still resolving.
        assertFalse(isNewAlbumArtHintAccount(null))
    }
}
