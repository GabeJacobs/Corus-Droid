package fm.corus.android.service

enum class ForYouPreviewEndedDismissReason(val value: String) {
    CONTINUE("continue"), DISMISS("dismiss"),
}

data class ForYouPreviewEndedEvent(val name: String, val params: Map<String, Any>)

/** One impression and one terminal action per notice; trial flag is numeric 1/0 on every platform for Firebase. */
class ForYouPreviewEndedAnalytics {
    private var hasClubTrial: Boolean? = null
    private var finished = false

    fun shown(hasClubTrial: Boolean): ForYouPreviewEndedEvent? {
        if (this.hasClubTrial != null) return null
        this.hasClubTrial = hasClubTrial
        return ForYouPreviewEndedEvent("your_mix_preview_ended_shown", mapOf("has_club_trial" to if (hasClubTrial) 1L else 0L))
    }

    fun clubTapped(): ForYouPreviewEndedEvent? {
        val trial = hasClubTrial ?: return null
        if (finished) return null
        finished = true
        return ForYouPreviewEndedEvent("your_mix_preview_ended_club_tapped", mapOf("has_club_trial" to if (trial) 1L else 0L))
    }

    fun dismissed(reason: ForYouPreviewEndedDismissReason): ForYouPreviewEndedEvent? {
        val trial = hasClubTrial ?: return null
        if (finished) return null
        finished = true
        return ForYouPreviewEndedEvent("your_mix_preview_ended_dismissed", mapOf("has_club_trial" to (if (trial) 1L else 0L), "reason" to reason.value))
    }
}
