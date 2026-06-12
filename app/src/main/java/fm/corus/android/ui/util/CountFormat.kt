package fm.corus.android.ui.util

/**
 * Compact form for stat counts: 7240 -> "7240", 61_600 -> "61.6K", 1_200_000 -> "1.2M".
 *
 * Under 10,000 renders the raw number (Instagram-style: a follower count like 7,240
 * stays legible in full). 10K-999,999 abbreviates with one decimal place (dropped
 * when it would be ".0"); 1M+ likewise. Mirrors iOS `formattedCount` and the web
 * `formatNumber` helper so follower/following/post counts read the same across platforms.
 */
fun formattedCount(count: Int): String {
    return when {
        count >= 1_000_000 -> {
            val s = String.format("%.1f", count / 1_000_000.0)
            if (s.endsWith(".0")) "${s.dropLast(2)}M" else "${s}M"
        }
        count >= 10_000 -> {
            val s = String.format("%.1f", count / 1000.0)
            if (s.endsWith(".0")) "${s.dropLast(2)}K" else "${s}K"
        }
        else -> count.toString()
    }
}
