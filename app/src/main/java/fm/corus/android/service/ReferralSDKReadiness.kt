package fm.corus.android.service

/** Readiness may arrive before Remote Config, and again after an SDK session resets. */
internal class ReferralSDKReadiness {
    private var generation = 0
    private var requested = 0

    fun ready() {
        generation++
    }

    fun takeStart(enabled: Boolean): Boolean {
        if (!enabled || generation == 0 || requested == generation) return false
        requested = generation
        return true
    }
}
