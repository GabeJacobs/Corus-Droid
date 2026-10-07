package fm.corus.android.data.model

import fm.corus.android.R

enum class FeedEnergy(val value: String, val labelRes: Int, val emptyTitleRes: Int) {
    LOW("low", fm.corus.android.localization.CorusStrings.feed_energy_low, fm.corus.android.localization.CorusStrings.feed_energy_empty_low),
    MEDIUM("medium", fm.corus.android.localization.CorusStrings.feed_energy_medium, fm.corus.android.localization.CorusStrings.feed_energy_empty_medium),
    HIGH("high", fm.corus.android.localization.CorusStrings.feed_energy_high, fm.corus.android.localization.CorusStrings.feed_energy_empty_high);

    fun matches(post: CymbalPost): Boolean = post.isTrack && post.energyLevel == value
    companion object {
        fun fromStored(value: String?): FeedEnergy? = entries.firstOrNull { it.value == value }
    }
}
