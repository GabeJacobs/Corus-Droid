package fm.corus.android.domain
import org.junit.Assert.*
import org.junit.Test
class ProfileCollectionPolicyTest {
    private val gabe = "FUQZIrZR08T2Ux2vYpPzWx7B1rv1"
    @Test fun offForEveryoneAndAvailableToAnySignedInViewerWhenEnabled() {
        for (viewer in listOf(gabe, "new-account")) assertFalse(ProfileCollectionPolicy.visible(false, viewer, "owner"))
        assertTrue(ProfileCollectionPolicy.visible(true, gabe, "owner"))
        for (viewer in listOf(null, "")) assertFalse(ProfileCollectionPolicy.visible(true, viewer, "owner"))
        assertTrue(ProfileCollectionPolicy.visible(true, "new-account", "owner"))
        assertFalse(ProfileCollectionPolicy.visible(true, gabe, ""))
    }
    @Test fun unknownIsNotEmptyAndGiftsTabRequiresReceipts() {
        assertFalse(ProfileCollectionPolicy.empty(0, null)); assertTrue(ProfileCollectionPolicy.empty(0, 0))
        assertFalse(ProfileCollectionPolicy.hasGifts(null)); assertFalse(ProfileCollectionPolicy.hasGifts(0)); assertTrue(ProfileCollectionPolicy.hasGifts(1))
        for (count in listOf(-1, "0", null, 1.2)) assertNull(ProfileCollectionPolicy.count(count))
    }
    @Test fun actualDistinctGiftTypesOnly() {
        assertEquals(emptyList<String>(), ProfileCollectionPolicy.giftLineup(emptyList()))
        assertEquals(listOf("flowers", "boombox"), ProfileCollectionPolicy.giftLineup(listOf("flowers", "flowers", "boombox", "mind_blown")))
        assertEquals(listOf("corus_heart"), ProfileCollectionPolicy.giftLineup(listOf("invalid"), listOf("corus_heart")))
    }
}
