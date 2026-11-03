package fm.corus.android.ui.components

import android.content.Context
import com.revenuecat.purchases.models.Period
import fm.corus.android.R

/** Format the eligible free phase on the option RevenueCat will purchase. */
internal fun clubTrialDurationText(context: Context, period: Period?): String? {
    period ?: return null
    val value = period.value
    if (value <= 0) return null
    return when (period.unit) {
        Period.Unit.YEAR -> if (value == 1) context.getString(R.string.club_period_year_one) else context.getString(R.string.club_period_year_format, value)
        Period.Unit.MONTH -> if (value == 1) context.getString(R.string.club_period_month_one) else context.getString(R.string.club_period_month_format, value)
        Period.Unit.WEEK -> if (value == 1) context.getString(R.string.club_period_week_one) else context.getString(R.string.club_period_week_format, value)
        Period.Unit.DAY -> if (value == 1) context.getString(R.string.club_period_day_one) else context.getString(R.string.club_period_day_format, value)
        Period.Unit.UNKNOWN -> null
    }
}
