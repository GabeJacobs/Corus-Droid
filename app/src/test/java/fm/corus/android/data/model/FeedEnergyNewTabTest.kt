package fm.corus.android.data.model

import org.junit.Assert.*
import org.junit.Test

class FeedEnergyNewTabTest {
    @Test fun `New hides energy and ignores saved energy while other tabs restore it`() {
        assertFalse(FeedEnergy.isOffered("newReleases", true))
        FeedEnergy.entries.forEach { energy ->
            assertNull(FeedEnergy.effective(energy, "newReleases", true))
            listOf("following", "favorites", "trending", "tasteMatches").forEach { mode ->
                assertTrue(FeedEnergy.isOffered(mode, true))
                assertEquals(energy, FeedEnergy.effective(energy, mode, true))
                assertNull(FeedEnergy.effective(energy, mode, false))
            }
        }
    }
}
