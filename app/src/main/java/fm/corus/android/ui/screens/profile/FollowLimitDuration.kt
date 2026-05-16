package fm.corus.android.ui.screens.profile

/**
 * Humanized duration shape for the follow-limit toast. The ViewModel calls
 * [describeFollowLimitRetry] to convert a `retryAfterSeconds` value from the
 * backend into one of these cases, then resolves the right string resource.
 *
 * Kept as a discriminated union of plain data so it's testable without an
 * Android Context.
 */
internal sealed class FollowLimitDuration {
    data class Hours(val count: Int) : FollowLimitDuration()
    data class Minutes(val count: Int) : FollowLimitDuration()
    data object Soon : FollowLimitDuration()
}

internal fun describeFollowLimitRetry(retryAfterSeconds: Int): FollowLimitDuration = when {
    retryAfterSeconds >= 3600 ->
        FollowLimitDuration.Hours(maxOf(1, Math.round(retryAfterSeconds / 3600.0).toInt()))
    retryAfterSeconds >= 60 ->
        FollowLimitDuration.Minutes(maxOf(1, Math.round(retryAfterSeconds / 60.0).toInt()))
    else -> FollowLimitDuration.Soon
}
