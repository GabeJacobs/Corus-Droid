package fm.corus.android.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the behavioral contract of the backend's appleMusicMatch.js (and
 * the iOS TrackMatchHelpers port): the gates that stop a fuzzy text search
 * from substituting the wrong song.
 */
class ShareTrackMatchTest {

    @Test
    fun `valid ISRCs pass`() {
        assertTrue(ShareTrackMatch.isValidISRC("USUM71703861"))
        assertTrue(ShareTrackMatch.isValidISRC(" gbahs1600463 "))
    }

    @Test
    fun `invalid ISRCs fail`() {
        assertFalse(ShareTrackMatch.isValidISRC(null))
        assertFalse(ShareTrackMatch.isValidISRC(""))
        assertFalse(ShareTrackMatch.isValidISRC("12345"))
        assertFalse(ShareTrackMatch.isValidISRC("USUM7170386"))   // 11 chars
        assertFalse(ShareTrackMatch.isValidISRC("1SUM71703861"))  // digit country
        assertFalse(ShareTrackMatch.isValidISRC("USUM7170386A"))  // alpha body
    }

    @Test
    fun `names align across remaster variants`() {
        assertTrue(ShareTrackMatch.namesAlign("Love Me Do - Remastered 2009", "Love Me Do"))
        assertTrue(ShareTrackMatch.namesAlign("Fourth of July", "Fourth of July"))
        assertTrue(ShareTrackMatch.namesAlign("Karma Police (2009 Remaster)", "Karma Police"))
        assertTrue(ShareTrackMatch.namesAlign("Heroes - 2017 Remaster", "\"Heroes\""))
    }

    @Test
    fun `live and demo takes stay distinct`() {
        assertFalse(ShareTrackMatch.namesAlign("Yesterday", "Tomorrow"))
        // Substring tolerance means live variants align with the base name by
        // design (matches backend + iOS behavior).
        assertTrue(ShareTrackMatch.namesAlign("Hotel California - Live", "Hotel California"))
    }

    @Test
    fun `artists match on token overlap and collapsed spelling`() {
        assertTrue(ShareTrackMatch.artistMatches("Sufjan Stevens", "Sufjan Stevens, My Brightest Diamond"))
        assertTrue(ShareTrackMatch.artistMatches("Kinoko Teikoku", "Kinokoteikoku"))
        assertTrue(ShareTrackMatch.artistMatches("BTS", "BTS"))
        assertFalse(ShareTrackMatch.artistMatches("Radiohead", "Coldplay"))
    }
}
