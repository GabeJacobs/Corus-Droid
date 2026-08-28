package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTabGateTest {

    @Test
    fun `tab stays once unlocked`() {
        assertTrue(FavoritesTabGate.showsTab(featureEnabled = true, count = 2, unlocked = false))
        assertTrue(FavoritesTabGate.showsTab(featureEnabled = true, count = 0, unlocked = true))
        assertFalse(FavoritesTabGate.showsTab(featureEnabled = true, count = 0, unlocked = false))
        assertFalse(FavoritesTabGate.showsTab(featureEnabled = false, count = 3, unlocked = true))
    }

    @Test
    fun `profile zero does not wipe unlocked favorites`() {
        val kept = FavoritesTabGate.apply(incoming = 0, current = 4, unlocked = true, allowZero = false)
        assertEquals(4, kept.first)
        assertTrue(kept.second)
        val explicit = FavoritesTabGate.apply(incoming = 0, current = 4, unlocked = true, allowZero = true)
        assertEquals(0, explicit.first)
        assertTrue(explicit.second)
        val firstFavorite = FavoritesTabGate.apply(incoming = 1, current = 0, unlocked = false, allowZero = true)
        assertEquals(1, firstFavorite.first)
        assertTrue(firstFavorite.second)
    }
}
