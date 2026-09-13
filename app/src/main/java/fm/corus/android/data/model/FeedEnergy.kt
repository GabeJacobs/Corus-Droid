package fm.corus.android.data.model

import fm.corus.android.R

enum class FeedEnergy(val value: String, val labelRes: Int, val emptyTitleRes: Int) {
    LOW("low", R.string.feed_energy_low, R.string.feed_energy_empty_low),
    MEDIUM("medium", R.string.feed_energy_medium, R.string.feed_energy_empty_medium),
    HIGH("high", R.string.feed_energy_high, R.string.feed_energy_empty_high);

    fun matches(post: CymbalPost): Boolean = post.isTrack && post.energyLevel == value
    companion object {
        fun fromStored(value: String?): FeedEnergy? = entries.firstOrNull { it.value == value }
    }
}
