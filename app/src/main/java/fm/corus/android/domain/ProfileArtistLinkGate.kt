package fm.corus.android.domain

/**
 * Visibility for the profile → artist-page card. RC is the rollout switch
 * (default off). @gabe is hardcoded on so the owner can evaluate without a
 * Remote Config condition. Remove [testerUsernames] before a general launch.
 * Mirrors iOS `ProfileArtistLinkGate`.
 */
object ProfileArtistLinkGate {
    val testerUsernames: Set<String> = setOf("gabe")

    fun isEnabled(
        flag: Boolean,
        artistPagesEnabled: Boolean,
        viewerUsername: String?,
    ): Boolean {
        val name = viewerUsername?.trim()?.lowercase().orEmpty()
        val isTester = name in testerUsernames
        if (!artistPagesEnabled && !isTester) return false
        return flag || isTester
    }
}
