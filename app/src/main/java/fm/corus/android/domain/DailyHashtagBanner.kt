package fm.corus.android.domain

import java.time.Instant
import java.time.ZoneOffset

data class DailyHashtagBannerCandidate(val name: String, val day: String, val updatedAtMillis: Long) {
    fun isCurrent(now: Long): Boolean = day == utcDay(now) && now - updatedAtMillis < 7_200_000L
}

fun utcDay(now: Long): String = Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).toLocalDate().toString()

fun parseDailyHashtagBanner(
    day: String?, updatedAt: Long?, items: List<Map<String, Any>>?, now: Long,
): DailyHashtagBannerCandidate? {
    if (day != utcDay(now) || updatedAt == null || now - updatedAt >= 7_200_000L || updatedAt > now + 60_000L) return null
    val item = items?.firstOrNull() ?: return null
    val name = (item["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
    val posts = (item["postCount"] as? Number)?.toDouble() ?: return null
    val people = (item["distinctAuthors"] as? Number)?.toDouble() ?: return null
    if (!posts.isFinite() || !people.isFinite() || posts < 10 || people < 7) return null
    return DailyHashtagBannerCandidate(name, day, updatedAt)
}
