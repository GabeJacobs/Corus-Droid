package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedDecadeTest {

    @Test
    fun `offers exactly the seven decades web offers, newest first`() {
        assertEquals(listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960), FeedDecade.OFFERED)
    }

    @Test
    fun `labels 2000s and later in full and earlier decades in two digits`() {
        assertEquals("2020s", FeedDecade.label(2020))
        assertEquals("2010s", FeedDecade.label(2010))
        assertEquals("2000s", FeedDecade.label(2000))
        assertEquals("90s", FeedDecade.label(1990))
        assertEquals("80s", FeedDecade.label(1980))
        assertEquals("70s", FeedDecade.label(1970))
        assertEquals("60s", FeedDecade.label(1960))
    }

    @Test
    fun `normalize keeps every offered decade`() {
        FeedDecade.OFFERED.forEach { assertEquals(it, FeedDecade.normalize(it)) }
    }

    @Test
    fun `normalize rejects a decade that is not offered`() {
        assertNull(FeedDecade.normalize(1950))
        assertNull(FeedDecade.normalize(2030))
        assertNull(FeedDecade.normalize(1995))
        assertNull(FeedDecade.normalize(0))
        assertNull(FeedDecade.normalize(null))
    }

    @Test
    fun `fromStored round-trips every offered decade`() {
        FeedDecade.OFFERED.forEach {
            assertEquals(it, FeedDecade.fromStored(FeedDecade.toStored(it)))
        }
    }

    @Test
    fun `fromStored resolves a value that is no longer offered to no narrowing`() {
        assertNull(FeedDecade.fromStored("1950"))
        assertNull(FeedDecade.fromStored(""))
        assertNull(FeedDecade.fromStored(null))
        assertNull(FeedDecade.fromStored("ninety"))
        assertNull(FeedDecade.fromStored("1990.0"))
        assertNull(FeedDecade.fromStored("99999999999999999999"))
    }

    @Test
    fun `fromStored tolerates surrounding whitespace`() {
        assertEquals(1990, FeedDecade.fromStored(" 1990 "))
    }

    @Test
    fun `toStored writes an empty string for no narrowing`() {
        assertEquals("", FeedDecade.toStored(null))
        assertEquals("1990", FeedDecade.toStored(1990))
    }

    @Test
    fun `analytics value is the digits when set and none when cleared`() {
        assertEquals("none", FeedDecade.analyticsValue(null))
        assertEquals("1990", FeedDecade.analyticsValue(1990))
        assertEquals("2020", FeedDecade.analyticsValue(2020))
    }
}
