package fm.corus.android.ui.screens.search

import java.time.*

internal object ConcertCalendarPolicy {
    fun startMillis(date: String, time: String?, timezone: String?, deviceZone: ZoneId = ZoneId.systemDefault()): Long? = runCatching {
        val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: deviceZone
        LocalDate.parse(date).atTime(time?.let(LocalTime::parse) ?: LocalTime.MIDNIGHT).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    fun hasStarted(date: String, time: String?, timezone: String?, now: Instant = Instant.now()): Boolean {
        val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        if (time == null) return runCatching { LocalDate.parse(date) < now.atZone(zone).toLocalDate() }.getOrDefault(false)
        return startMillis(date, time, timezone)?.let { it <= now.toEpochMilli() } ?: false
    }
}
