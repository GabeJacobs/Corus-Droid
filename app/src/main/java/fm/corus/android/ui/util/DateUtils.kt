package fm.corus.android.ui.util

import android.content.Context
import fm.corus.android.R
import java.util.Date
import java.util.concurrent.TimeUnit

object DateUtils {
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
}
