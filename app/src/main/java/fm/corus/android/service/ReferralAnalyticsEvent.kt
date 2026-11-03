package fm.corus.android.service

internal data class ReferralAnalyticsEvent(val name: String, val action: String? = null, val source: String? = null)

/** Match the existing iOS dashboard vocabulary, while retaining granular server event types. */
internal fun referralAnalyticsEvent(type: String): ReferralAnalyticsEvent = when (type) {
    "feed_banner_shown", "feed_banner_tapped", "feed_banner_dismissed" ->
        ReferralAnalyticsEvent("referral_feed_banner", type.substringAfterLast('_'), "following")
    "search_banner_shown", "search_banner_tapped", "search_banner_dismissed" ->
        ReferralAnalyticsEvent("referral_search_card", type.substringAfterLast('_'), "users_search")
    else -> ReferralAnalyticsEvent("referral_$type")
}
