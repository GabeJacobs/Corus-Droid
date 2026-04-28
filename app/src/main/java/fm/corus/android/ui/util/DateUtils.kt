package fm.corus.android.ui.util

import android.content.Context
import fm.corus.android.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

object DateUtils {
    /**
     * Compact form: "now" / "5m" / "2h" / "3d" / "1w" / "2mo" / "5y".
     * Used for comments, notifications, message-thread timestamps.
     * Mirrors iOS Date.relativeTimeString.
     */
    fun relativeTime(context: Context, date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            seconds < 60 -> context.getString(R.string.relative_time_just_now)
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 7 -> "${days}d"
            days < 30 -> "${days / 7}w"
            days < 365 -> "${days / 30}mo"
            else -> "${days / 365}y"
        }
    }

    /**
     * Long prose form: "Just now" / "5 minutes ago" / "2 hours ago" / "3 days ago",
     * then a localized date once the post is older than a week.
     * Used for the main feed post timestamp. Mirrors iOS Date.postTimeString.
     */
    fun relativeTimeLong(context: Context, date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff).toInt()
        val hours = TimeUnit.MILLISECONDS.toHours(diff).toInt()
        val days = TimeUnit.MILLISECONDS.toDays(diff).toInt()
        val res = context.resources

        return when {
            seconds < 60 -> context.getString(R.string.post_time_just_now)
            minutes < 60 -> res.getQuantityString(R.plurals.post_time_minutes_ago, minutes, minutes)
            hours < 24 -> res.getQuantityString(R.plurals.post_time_hours_ago, hours, hours)
            days < 7 -> res.getQuantityString(R.plurals.post_time_days_ago, days, days)
            else -> {
                val locale = context.resources.configuration.locales[0]
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val postYear = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
                val patternRes = if (currentYear == postYear) {
                    R.string.post_time_date_format_same_year
                } else {
                    R.string.post_time_date_format_other_year
                }
                SimpleDateFormat(context.getString(patternRes), locale).format(date)
            }
        }
    }
}
