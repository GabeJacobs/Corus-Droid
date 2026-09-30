package fm.corus.android.service

import fm.corus.android.data.repository.ConcertShow
import java.net.URI
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Builds the `concert_event` Firebase parameters. Mirrors iOS `ConcertAnalytics`
 * (docs/concert-analytics.md there): `action` and `source` are always present, the
 * rest stay low-cardinality. Never pass titles, venue names, city-search text,
 * usernames, or raw errors.
 */
object ConcertAnalytics {
    const val EVENT = "concert_event"

    fun params(
        action: String,
        source: String,
        show: ConcertShow? = null,
        cityId: String? = null,
        filter: String? = null,
        result: String? = null,
        provider: String? = null,
        method: String? = null,
        statusFrom: String? = null,
        statusTo: String? = null,
        dateRange: String? = null,
        genre: String? = null,
        count: Int? = null,
        durationMs: Long? = null,
        eventId: String? = null,
        today: LocalDate = LocalDate.now(),
    ): Map<String, Any> = buildMap {
        put("action", action)
        put("source", source)
        (show?.id ?: eventId)?.takeIf { it.isNotEmpty() }?.let { put("event_id", it) }
        (cityId ?: show?.cityId)?.takeIf { it.isNotEmpty() }?.let { put("city_id", it) }
        filter?.let { put("filter", it) }
        result?.let { put("result", it) }
        provider?.let { put("provider", it) }
        method?.let { put("method", it) }
        statusFrom?.let { put("status_from", it) }
        statusTo?.let { put("status_to", it) }
        dateRange?.let { put("date_range", it) }
        genre?.let { put("genre", it) }
        count?.let { put("count", maxOf(0, it)) }
        durationMs?.let { put("duration_ms", maxOf(0L, it)) }
        show?.let { s ->
            runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { eventDate ->
                put("days_until_event", ChronoUnit.DAYS.between(today, eventDate).toInt())
            }
            put("personalized", if (s.matchedArtist != null) "true" else "false")
        }
    }

    fun ticketProvider(url: String?): String {
        val host = runCatching { URI(url.orEmpty()).host?.lowercase() }.getOrNull() ?: return "other"
        return when {
            host.contains("ticketmaster") -> "ticketmaster"
            host.contains("ticketweb") -> "ticketweb"
            host.contains("eventbrite") -> "eventbrite"
            host.contains("axs") -> "axs"
            host.contains("dice.fm") -> "dice"
            else -> "other"
        }
    }
}
