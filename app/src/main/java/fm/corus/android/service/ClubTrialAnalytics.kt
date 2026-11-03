package fm.corus.android.service

/** Offering labels, not inferred eligibility or proof of experiment enrollment. */
object ClubTrialAnalytics {
    fun parameters(offeringIdentifier: String?): Map<String, String> {
        if (offeringIdentifier.isNullOrEmpty()) return emptyMap()
        val variant = when (offeringIdentifier) {
            "default" -> "two_weeks"
            "club_trial_test_no_trial" -> "no_trial"
            "club_trial_test_1_month" -> "one_month"
            else -> "other"
        }
        return mapOf("club_offering_id" to offeringIdentifier, "club_trial_variant" to variant)
    }
}

class ClubPaywallExposure {
    private val offerings = mutableSetOf<String>()
    fun claim(offeringIdentifier: String?): Boolean =
        !offeringIdentifier.isNullOrEmpty() && offerings.add(offeringIdentifier)
    fun reset() = offerings.clear()
}
