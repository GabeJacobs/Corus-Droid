package fm.corus.android.ui.screens.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionPaginationTest {
    @Test fun `prefetches within two rows of the end without a button`() {
        assertFalse(shouldLoadCollectionPage(true, false, false, 30, 22))
        assertTrue(shouldLoadCollectionPage(true, false, false, 30, 23))
        assertTrue(shouldLoadCollectionPage(true, false, false, 30, 29))
        assertFalse(shouldLoadCollectionPage(true, false, false, 30, -1))
    }

    @Test fun `stops for in flight requests errors and the final page`() {
        assertFalse(shouldLoadCollectionPage(true, true, false, 30, 29))
        assertFalse(shouldLoadCollectionPage(true, false, true, 30, 29))
        assertFalse(shouldLoadCollectionPage(false, false, false, 30, 29))
        // After appending a page, do not load again until its end is approached.
        assertFalse(shouldLoadCollectionPage(true, false, false, 60, 29))
    }

    @Test fun `continues sparse gift scans even when a batch has no gifted posts`() {
        assertTrue(shouldLoadCollectionPage(true, false, false, 0, -1))
        assertFalse(shouldLoadCollectionPage(false, false, false, 0, -1))
        assertFalse(shouldLoadCollectionPage(true, true, false, 0, -1))
        assertFalse(shouldLoadCollectionPage(true, false, true, 0, -1))
    }
}
