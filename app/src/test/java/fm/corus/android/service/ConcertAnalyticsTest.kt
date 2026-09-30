package fm.corus.android.service

import fm.corus.android.data.repository.ConcertShow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parameter schema for `concert_event`, mirroring iOS `ConcertAnalytics.log`. */
class ConcertAnalyticsTest {
    private val show = ConcertShow(
        id = "evt1", cityId = "new-york-us", title = "Tour", date = "2026-10-09",
        time = null, venue = "Venue", city = "NYC", region = "NY", address = null, imageUrl = null,
        url = "https://www.ticketmaster.com/x", lineup = emptyList(), info = null, pleaseNote = null,
        matchedArtist = "Artist", suggestionSource = null, eventStatus = "scheduled",
    )
    private val today = LocalDate.of(2026, 9, 29)

    @Test fun `always carries action and source, adds show identity and derived fields`() {
        val p = ConcertAnalytics.params("detail_viewed", "deep_link", show, today = today)
        assertEquals("detail_viewed", p["action"])
        assertEquals("deep_link", p["source"])
        assertEquals("evt1", p["event_id"])
        assertEquals("new-york-us", p["city_id"])
        assertEquals(10, p["days_until_event"])
        assertEquals("true", p["personalized"])
    }

    @Test fun `optional params are omitted unless set and counts are clamped`() {
        val p = ConcertAnalytics.params("list_impression", "concerts_list", count = -3, durationMs = -1)
        assertFalse(p.containsKey("event_id"))
        assertFalse(p.containsKey("days_until_event"))
        assertFalse(p.containsKey("personalized"))
        assertEquals(0, p["count"])
        assertEquals(0L, p["duration_ms"])
    }

    @Test fun `blank ids are dropped and a show city id can be overridden`() {
        val noCity = ConcertAnalytics.params("concert_selected", "artist_page", show.copy(cityId = ""))
        assertFalse(noCity.containsKey("city_id"))
        assertEquals("other-city", ConcertAnalytics.params("x", "y", show, cityId = "other-city")["city_id"])
        assertEquals("evt9", ConcertAnalytics.params("notification_opened", "push", eventId = "evt9")["event_id"])
    }

    @Test fun `personalized is false without a matched artist`() {
        assertEquals("false", ConcertAnalytics.params("a", "b", show.copy(matchedArtist = null))["personalized"])
    }

    @Test fun `ticket providers are bucketed by host`() {
        assertEquals("ticketmaster", ConcertAnalytics.ticketProvider("https://www.ticketmaster.com/e/1"))
        assertEquals("eventbrite", ConcertAnalytics.ticketProvider("https://eventbrite.com/e/1"))
        assertEquals("dice", ConcertAnalytics.ticketProvider("https://dice.fm/event/1"))
        assertEquals("axs", ConcertAnalytics.ticketProvider("https://www.axs.com/events/1"))
        assertEquals("ticketweb", ConcertAnalytics.ticketProvider("https://www.ticketweb.com/e"))
        assertEquals("other", ConcertAnalytics.ticketProvider("https://example.com"))
        assertEquals("other", ConcertAnalytics.ticketProvider(null))
        assertTrue(ConcertAnalytics.ticketProvider("not a url").isNotEmpty())
    }
}
